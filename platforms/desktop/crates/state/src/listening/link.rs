//! Authenticated Cloudflare REST membership and WebSocket transport.

use std::{
    fs,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use futures::{SinkExt as _, StreamExt as _};
use reqwest::Url;
use serde_json::{Value, json};
use tokio::sync::mpsc::{self, UnboundedReceiver, UnboundedSender};
use tokio_tungstenite::tungstenite::{
    Message,
    client::IntoClientRequest,
    http::{
        HeaderValue,
        header::{AUTHORIZATION, SEC_WEBSOCKET_PROTOCOL},
    },
};

use super::wire::{
    self, PartyPlayback, PartyQueue, PartySnapshot, PartyTrack, Reason, Reply, Request, Shared,
};
use crate::Io;

const PING: Duration = Duration::from_secs(15);
const CONNECT: Duration = Duration::from_secs(12);
const DEFAULT_SERVER: &str = "https://audify-listen-together.sachit1751.workers.dev";

pub(crate) enum Heard {
    Reply(Box<Reply>),
    Lost,
}

pub(crate) struct Link {
    requests: UnboundedSender<Request>,
}

impl Link {
    pub fn open(io: &Io, base: String, first: Request) -> (Self, UnboundedReceiver<Heard>) {
        let (requests, outgoing) = mpsc::unbounded_channel();
        let (heard, incoming) = mpsc::unbounded_channel();
        io.spawn(run(base, first, outgoing, heard));
        (Self { requests }, incoming)
    }

    pub fn send(&self, request: Request) {
        self.requests.send(request).ok();
    }
}

pub(crate) fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |since| since.as_millis() as u64)
}

#[derive(Clone)]
struct Identity {
    user_id: String,
    device_id: String,
    name: String,
    avatar: Option<String>,
}

fn identity(name: String, avatar: Option<String>) -> Identity {
    let device_id = persistent_device_id();
    Identity {
        user_id: format!("desktop:{device_id}"),
        device_id,
        name: if name.trim().is_empty() {
            "Audify Desktop".to_owned()
        } else {
            name.chars().take(80).collect()
        },
        avatar: avatar.filter(|url| url.starts_with("https://") || url.starts_with("http://")),
    }
}

fn persistent_device_id() -> String {
    static ID: std::sync::OnceLock<String> = std::sync::OnceLock::new();
    ID.get_or_init(|| {
        let mut path = dirs::config_dir().unwrap_or_else(std::env::temp_dir);
        path.push("Audify");
        path.push("listen-together-device-id");
        if let Ok(value) = fs::read_to_string(&path) {
            let value = value.trim();
            if value.len() == 32 && value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
                return value.to_owned();
            }
        }
        let value = format!("{:016x}{:016x}", fastrand::u64(..), fastrand::u64(..));
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let _ = fs::write(path, &value);
        value
    })
    .clone()
}

fn base_url(value: &str) -> anyhow::Result<Url> {
    let mut url = Url::parse(if value.trim().is_empty() {
        DEFAULT_SERVER
    } else {
        value.trim()
    })?;
    match url.scheme() {
        "ws" => url
            .set_scheme("http")
            .map_err(|_| anyhow::anyhow!("invalid server URL"))?,
        "wss" => url
            .set_scheme("https")
            .map_err(|_| anyhow::anyhow!("invalid server URL"))?,
        "http" | "https" => {}
        _ => anyhow::bail!("unsupported server URL scheme"),
    }
    if url.path() == "/ws" || url.path() == "/ws/" {
        url.set_path("/");
    }
    url.set_query(None);
    url.set_fragment(None);
    Ok(url)
}

async fn membership(
    base: &Url,
    first: &Request,
    who: &Identity,
) -> anyhow::Result<(String, String)> {
    let (path, body) = match first {
        Request::Create { .. } => (
            "/api/parties".to_owned(),
            json!({
                "userId": who.user_id, "deviceId": who.device_id, "displayName": who.name,
                "avatarUrl": who.avatar, "maxMembers": 5, "autoplayEnabled": false
            }),
        ),
        Request::Join { code, .. } => (
            format!("/api/parties/{}/join", code),
            json!({
                "userId": who.user_id, "deviceId": who.device_id, "displayName": who.name, "avatarUrl": who.avatar
            }),
        ),
        _ => anyhow::bail!("a session must be created or joined before connecting"),
    };
    let url = base.join(&path)?;
    let response = reqwest::Client::new()
        .post(url)
        .json(&body)
        .timeout(CONNECT)
        .send()
        .await?;
    let status = response.status();
    let body: Value = response.json().await.unwrap_or(Value::Null);
    if !status.is_success() {
        let code = body
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or("server_error");
        let message = body
            .get("message")
            .and_then(Value::as_str)
            .unwrap_or("The party server refused the request.");
        anyhow::bail!("{code}: {message}");
    }
    let code = body
        .get("code")
        .and_then(Value::as_str)
        .ok_or_else(|| anyhow::anyhow!("party response omitted its code"))?
        .to_owned();
    let token = body
        .get("token")
        .and_then(Value::as_str)
        .ok_or_else(|| anyhow::anyhow!("party response omitted its membership token"))?
        .to_owned();
    Ok((code, token))
}

async fn run(
    base: String,
    first: Request,
    mut outgoing: UnboundedReceiver<Request>,
    heard: UnboundedSender<Heard>,
) {
    let (name, avatar) = match &first {
        Request::Create {
            name,
            image,
            provider,
            ..
        } => {
            if provider != "youtube" {
                send_error(&heard, Reason::Provider);
                return;
            }
            (name.clone(), image.clone())
        }
        Request::Join { name, image, .. } => (name.clone(), image.clone()),
        _ => {
            send_error(&heard, Reason::Invalid);
            return;
        }
    };
    let who = identity(name, avatar);
    let base = match base_url(&base) {
        Ok(url) => url,
        Err(error) => {
            log::warn!("listening: invalid Cloudflare server URL: {error}");
            send_error(&heard, Reason::Invalid);
            return;
        }
    };
    let (code, token) = match tokio::time::timeout(CONNECT, membership(&base, &first, &who)).await {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => {
            log::warn!("listening: REST membership failed: {error:#}");
            send_error_from_text(&heard, &error.to_string());
            return;
        }
        Err(_) => {
            send_error(&heard, Reason::Busy);
            return;
        }
    };
    let mut ws_url = base.clone();
    let _ = ws_url.set_scheme(if base.scheme() == "https" {
        "wss"
    } else {
        "ws"
    });
    ws_url.set_path(&format!("/ws/parties/{code}"));
    let mut request = match ws_url.as_str().into_client_request() {
        Ok(request) => request,
        Err(error) => {
            log::warn!("listening: cannot form WebSocket request: {error}");
            heard.send(Heard::Lost).ok();
            return;
        }
    };
    let bearer = match HeaderValue::from_str(&format!("Bearer {token}")) {
        Ok(value) => value,
        Err(_) => {
            heard.send(Heard::Lost).ok();
            return;
        }
    };
    request.headers_mut().insert(AUTHORIZATION, bearer);
    request.headers_mut().insert(
        SEC_WEBSOCKET_PROTOCOL,
        HeaderValue::from_static("audify.v1"),
    );
    let socket =
        match tokio::time::timeout(CONNECT, tokio_tungstenite::connect_async(request)).await {
            Ok(Ok((socket, _))) => socket,
            Ok(Err(error)) => {
                log::warn!("listening: Cloudflare WebSocket connection failed: {error}");
                heard.send(Heard::Lost).ok();
                return;
            }
            Err(_) => {
                log::warn!("listening: Cloudflare WebSocket connection timed out");
                heard.send(Heard::Lost).ok();
                return;
            }
        };
    let (mut sink, mut stream) = socket.split();
    let mut ping = tokio::time::interval(PING);
    let mut queue: Vec<PartyTrack> = Vec::new();
    let mut last_shared: Option<Shared> = None;
    loop {
        tokio::select! {
            request = outgoing.recv() => match request {
                Some(Request::Leave) => {
                    let _ = reqwest::Client::new().post(base.join(&format!("/api/parties/{code}/leave")).unwrap())
                        .bearer_auth(&token).timeout(CONNECT).send().await;
                    let _ = sink.close().await;
                    return;
                }
                Some(Request::Kick { member }) => {
                    if send_control(&mut sink, "kick", json!({"memberId": member})).await.is_err() { heard.send(Heard::Lost).ok(); return; }
                }
                Some(Request::State { state, .. }) => {
                    if send_state(&mut sink, &state, &mut last_shared).await.is_err() { heard.send(Heard::Lost).ok(); return; }
                }
                Some(_) => {}
                None => { let _ = sink.close().await; return; }
            },
            _ = ping.tick() => {
                if send_json(&mut sink, json!({"type":"ping", "clientMs":now()})).await.is_err() { heard.send(Heard::Lost).ok(); return; }
            }
            message = stream.next() => match message {
                Some(Ok(Message::Text(text))) => {
                    let frame: Value = match serde_json::from_str(&text) { Ok(value) => value, Err(error) => { log::warn!("listening: malformed Cloudflare frame: {error}"); continue; } };
                    match frame.get("type").and_then(Value::as_str).unwrap_or("") {
                        "welcome" => {
                            let Some(party_value) = frame.get("party") else { continue; };
                            let party: PartySnapshot = match serde_json::from_value(party_value.clone()) { Ok(party) => party, Err(error) => { log::warn!("listening: invalid party snapshot: {error}"); continue; } };
                            queue = party.queue.items;
                            let members = party.members.into_iter().map(Into::into).collect();
                            let you = frame.get("you").and_then(|v| v.get("memberId")).and_then(Value::as_str).unwrap_or("").to_owned();
                            let server_ms = frame.get("serverMs").and_then(Value::as_u64).unwrap_or_else(now);
                            let state = wire::shared(party.playback, &queue, server_ms);
                            last_shared = Some(state.clone());
                            if heard.send(Heard::Reply(Box::new(Reply::Joined { code: party.code, provider: "youtube".to_owned(), you, members, state: Some(state) }))).is_err() { return; }
                        }
                        "members" => {
                            let members = frame.get("members").cloned().and_then(|v| serde_json::from_value::<Vec<wire::PartyMember>>(v).ok()).unwrap_or_default().into_iter().map(Into::into).collect();
                            heard.send(Heard::Reply(Box::new(Reply::Members { members }))).ok();
                        }
                        "state" => {
                            let Some(playback) = frame.get("playback").cloned().and_then(|v| serde_json::from_value::<PartyPlayback>(v).ok()) else { continue; };
                            let server_ms = frame.get("serverMs").and_then(Value::as_u64).unwrap_or_else(now);
                            let by = playback.updated_by.clone().unwrap_or_default();
                            let seq = playback.seq;
                            let state = wire::shared(playback, &queue, server_ms);
                            last_shared = Some(state.clone());
                            heard.send(Heard::Reply(Box::new(Reply::State { state, by, seq }))).ok();
                        }
                        "queue" => {
                            let Some(new_queue) = frame.get("queue").cloned().and_then(|v| serde_json::from_value::<PartyQueue>(v).ok()) else { continue; };
                            let queue_index = new_queue.index;
                            queue = new_queue.items;
                            // The player applies the new upcoming list against its latest track.
                            let start = if queue_index >= 0 { queue_index as usize + 1 } else { 0 };
                            let upcoming: Vec<music::Track> = queue.iter().skip(start).cloned().map(PartyTrack::into_track).collect();
                            if let Some(state) = last_shared.as_mut() {
                                state.upcoming = upcoming.clone();
                            }
                            heard.send(Heard::Reply(Box::new(Reply::Queue { upcoming }))).ok();
                        }
                        "pong" => {
                            let t = frame.get("clientMs").and_then(Value::as_u64).unwrap_or_default();
                            let server_ms = frame.get("serverMs").and_then(Value::as_u64).unwrap_or_else(now);
                            heard.send(Heard::Reply(Box::new(Reply::Pong { t, now: server_ms }))).ok();
                        }
                        "bye" => {
                            let kicked = frame.get("reason").and_then(Value::as_str) == Some("kicked");
                            heard.send(Heard::Reply(Box::new(if kicked { Reply::Kicked } else { Reply::Ended }))).ok();
                            return;
                        }
                        "error" => {
                            let reason = frame.get("error").and_then(Value::as_str).unwrap_or("");
                            log::warn!("listening: Cloudflare party error: {reason}");
                            if matches!(reason, "bad_token" | "no_such_party" | "host_only") {
                                let mapped = if reason == "no_such_party" { Reason::NotFound } else { Reason::Invalid };
                                heard.send(Heard::Reply(Box::new(Reply::Error { reason: mapped, provider: None }))).ok();
                            }
                        }
                        _ => {}
                    }
                }
                Some(Ok(Message::Close(_))) | Some(Err(_)) | None => { heard.send(Heard::Lost).ok(); return; }
                Some(Ok(_)) => {}
            }
        }
    }
}

fn send_error(heard: &UnboundedSender<Heard>, reason: Reason) {
    heard
        .send(Heard::Reply(Box::new(Reply::Error {
            reason,
            provider: None,
        })))
        .ok();
}

fn send_error_from_text(heard: &UnboundedSender<Heard>, text: &str) {
    let reason = if text.contains("party_full") {
        Reason::Full
    } else if text.contains("too_many") {
        Reason::TooMany
    } else if text.contains("rate_limited") {
        Reason::RateLimited
    } else if text.contains("no_such_party") {
        Reason::NotFound
    } else if text.contains("host_only") {
        Reason::Invalid
    } else {
        Reason::Busy
    };
    send_error(heard, reason);
}

async fn send_json<F>(sink: &mut F, value: Value) -> anyhow::Result<()>
where
    F: futures::Sink<Message> + Unpin,
    F::Error: std::error::Error + Send + Sync + 'static,
{
    sink.send(Message::Text(value.to_string().into())).await?;
    Ok(())
}

async fn send_control<F>(sink: &mut F, action: &str, fields: Value) -> anyhow::Result<()>
where
    F: futures::Sink<Message> + Unpin,
    F::Error: std::error::Error + Send + Sync + 'static,
{
    let mut frame = json!({"type":"control", "action":action, "eventId":format!("pc-{:016x}", fastrand::u64(..))});
    if let (Some(target), Some(source)) = (frame.as_object_mut(), fields.as_object()) {
        for (key, value) in source {
            target.insert(key.clone(), value.clone());
        }
    }
    send_json(sink, frame).await
}

async fn send_state<F>(
    sink: &mut F,
    state: &Shared,
    last: &mut Option<Shared>,
) -> anyhow::Result<()>
where
    F: futures::Sink<Message> + Unpin,
    F::Error: std::error::Error + Send + Sync + 'static,
{
    // Local files and an empty player are not cross-device tracks. They must not erase the
    // YouTube party's current song or queue.
    if state.track.is_none() {
        return Ok(());
    }
    let old = last.as_ref();
    let current_id = state.track.as_ref().and_then(|track| track.id.as_deref());
    let old_id = old
        .and_then(|state| state.track.as_ref())
        .and_then(|track| track.id.as_deref());
    let queue_ids = |state: &Shared| {
        std::iter::once(state.track.as_ref())
            .chain(state.upcoming.iter().map(Some))
            .filter_map(|track| track.and_then(|track| track.id.clone()))
            .collect::<Vec<_>>()
    };
    let queue_changed = old.is_none_or(|previous| queue_ids(previous) != queue_ids(state));
    let track_changed = current_id != old_id;
    if queue_changed {
        let queue: Vec<PartyTrack> = std::iter::once(state.track.as_ref())
            .chain(state.upcoming.iter().map(Some))
            .flatten()
            .filter_map(PartyTrack::from_track)
            .collect();
        send_control(
            sink,
            "setQueue",
            json!({"queue":queue, "queueIndex":if state.track.is_some() {0} else {-1}}),
        )
        .await?;
    }
    if let Some(track) = &state.track {
        if track_changed || queue_changed && old.is_none() {
            if let Some(track) = PartyTrack::from_track(track) {
                send_control(sink, "setTrack", json!({"track":track, "isPlaying":state.playing, "positionMs":state.position, "queueIndex":0})).await?;
            }
        } else if let Some(previous) = old {
            if state.playing != previous.playing {
                send_control(
                    sink,
                    if state.playing { "play" } else { "pause" },
                    json!({"positionMs":state.position}),
                )
                .await?;
            } else if state.position.abs_diff(previous.position) > 700 {
                send_control(sink, "seek", json!({"positionMs":state.position})).await?;
            }
        }
    } else if old.is_some_and(|previous| previous.playing) {
        send_control(sink, "pause", json!({"positionMs":state.position})).await?;
    }
    *last = Some(state.clone());
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use futures::channel::mpsc;
    use music::Track;

    fn track(id: &str, title: &str) -> Track {
        Track {
            id: Some(id.to_owned()),
            name: title.to_owned(),
            playable: true,
            artists: "AUDIFY".to_owned(),
            artist_refs: Vec::new(),
            album: String::new(),
            album_id: None,
            cover: None,
            duration: Duration::from_secs(180),
            added_at: None,
            added_by: None,
            playcount: None,
            popularity: 0,
            explicit: false,
            track_number: 0,
            disc_number: 1,
            tags: Vec::new(),
            languages: Vec::new(),
            credits: Vec::new(),
        }
    }

    #[tokio::test]
    async fn desktop_state_emits_cloudflare_track_playback_seek_and_queue_controls() {
        let (mut sink, mut frames) = mpsc::unbounded::<Message>();
        let mut last = None;
        let mut state = Shared {
            track: Some(track("yt-current", "Current")),
            upcoming: vec![track("yt-next", "Next")],
            playing: true,
            position: 1_000,
            at: 0,
        };
        send_state(&mut sink, &state, &mut last)
            .await
            .expect("initial state sends");

        state.playing = false;
        state.position = 42_000;
        send_state(&mut sink, &state, &mut last)
            .await
            .expect("pause sends");

        state.position = 90_000;
        send_state(&mut sink, &state, &mut last)
            .await
            .expect("seek sends");

        state.upcoming.push(track("yt-third", "Third"));
        send_state(&mut sink, &state, &mut last)
            .await
            .expect("queue sends");
        drop(sink);

        let mut actions = Vec::new();
        while let Some(Message::Text(text)) = frames.next().await {
            let frame: Value = serde_json::from_str(&text).expect("valid JSON frame");
            assert_eq!(frame["type"], "control");
            assert!(frame["eventId"].as_str().is_some_and(|id| id.len() >= 8));
            actions.push(frame["action"].as_str().unwrap_or_default().to_owned());
        }
        assert_eq!(
            actions,
            ["setQueue", "setTrack", "pause", "seek", "setQueue"]
        );
    }
}
