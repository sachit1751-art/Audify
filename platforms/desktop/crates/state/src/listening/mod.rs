//! Audify Listen Together: one Cloudflare party shared by Android and desktop. Cross-device
//! playback uses YouTube video IDs; local files and other providers remain local to the client.

mod link;
mod server;
mod wire;

use std::collections::VecDeque;
use std::time::Duration;

use gpui::{App, Context, Entity, Task};
use music::Track;
use tokio::sync::mpsc::UnboundedReceiver;

pub use wire::Member;

use crate::{
    AppSettings, Io, Outcome, Playback, Queue, Session, SessionEvent, SessionState, Toasts,
};
use link::{Heard, Link};
use wire::{Reason, Reply, Request, Shared, VERSION};

/// How many alphanumeric characters a session code has.
pub const CODE_LENGTH: usize = 6;
/// How many upcoming tracks are shared. The server keeps no more than this either.
const UPCOMING: usize = 25;
/// How far playback here may stray from the session's before it is moved back in step.
const DRIFT: Duration = Duration::from_millis(2500);
/// How far it may stray when the session pauses or resumes, which is when a seek goes unheard.
const NUDGE: Duration = Duration::from_millis(400);
/// How often playback here is compared with the session's.
const CHECK: Duration = Duration::from_secs(2);
/// How many readings of the server's clock are kept. The one that took the shortest round trip
/// is trusted.
const SAMPLES: usize = 8;
/// How many times a dropped connection is tried again before the session is given up.
const RETRIES: u32 = 8;

/// The session the listener is in, as the server last described it.
#[derive(Clone, Debug)]
pub struct Room {
    pub code: String,
    /// Cross-device sessions use YouTube video IDs on every client.
    pub provider: String,
    pub members: Vec<Member>,
    /// The id the server knows this app by.
    you: String,
}

impl Room {
    /// Whether this app hosts the session, so leaving it ends it for everyone.
    pub fn hosting(&self) -> bool {
        self.members
            .iter()
            .any(|member| member.host && member.id == self.you)
    }

    pub fn host(&self) -> Option<&Member> {
        self.members.iter().find(|member| member.host)
    }

    pub fn is_you(&self, member: &Member) -> bool {
        member.id == self.you
    }
}

/// What playback here looked like when it was last shared or brought in step, so a change made
/// here can be told apart from one the session made.
#[derive(Clone, Debug, PartialEq, Eq)]
struct Mark {
    track: Option<String>,
    playing: bool,
    upcoming: Vec<String>,
}

/// The listener's Audify Session: the link to the server, the room it reports, and the work of
/// keeping playback here and in the session the same.
pub struct Listening {
    playback: Entity<Playback>,
    queue: Entity<Queue>,
    session: Entity<Session>,
    io: Io,
    room: Option<Room>,
    link: Option<Link>,
    hearing: Option<Task<()>>,
    /// Whether a start or join is waiting for the server's answer.
    asking: bool,
    /// Whether starting should continue once the stored YouTube account switches in.
    start_when_ready: bool,
    /// The wait before a dropped connection is tried again.
    retry: Option<Task<()>>,
    retries: u32,
    /// The session's playback as last heard or sent.
    shared: Option<Shared>,
    /// A state to bring playback in step with once the session's provider is signed in here.
    waiting: Option<Shared>,
    baseline: Option<Mark>,
    /// `Playback::seeks` when playback was last shared or brought in step.
    seeks: u64,
    /// Round trips and clock offsets of the latest pings, in milliseconds.
    clock: VecDeque<(u64, i64)>,
    /// Compares playback here with the session's every few seconds while in one.
    checking: Option<Task<()>>,
    code_shown: bool,
    join_shown: bool,
    settings: Entity<AppSettings>,
    http: reqwest::Client,
    /// The health check of a server the listener asked to save.
    saving: Option<Task<()>>,
    /// Whether the last save was turned down. It stays so until a save goes through.
    rejected: bool,
}

impl Listening {
    pub fn new(
        playback: Entity<Playback>,
        queue: Entity<Queue>,
        session: Entity<Session>,
        settings: Entity<AppSettings>,
        io: Io,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&playback, |this, _, cx| this.publish(cx))
            .detach();
        cx.observe(&queue, |this, _, cx| this.publish(cx)).detach();
        cx.subscribe(&session, |this, _, event, cx| this.on_session(event, cx))
            .detach();
        Self {
            playback,
            queue,
            session,
            io,
            room: None,
            link: None,
            hearing: None,
            asking: false,
            start_when_ready: false,
            retry: None,
            retries: 0,
            shared: None,
            waiting: None,
            baseline: None,
            seeks: 0,
            clock: VecDeque::new(),
            checking: None,
            code_shown: false,
            join_shown: false,
            settings,
            http: reqwest::Client::new(),
            saving: None,
            rejected: false,
        }
    }

    /// Whether YouTube playback is available for cross-device sessions. Membership is
    /// authenticated separately by the party token; guest playback uses a stable device identity.
    pub fn available(&self, cx: &App) -> bool {
        self.session
            .read(cx)
            .providers()
            .any(|provider| provider.slug == "youtube" && provider.stored)
    }

    /// The session the listener is in.
    pub fn room(&self) -> Option<&Room> {
        self.room.as_ref()
    }

    /// Whether a start or join is on its way.
    pub fn asking(&self) -> bool {
        self.start_when_ready || self.asking && self.room.is_none()
    }

    /// Whether the connection dropped and is being made again.
    pub fn reconnecting(&self) -> bool {
        self.room.is_some() && (self.retry.is_some() || self.asking)
    }

    /// Starts a YouTube session. Its code shows once the server hands it out.
    pub fn start(&mut self, cx: &mut Context<Self>) {
        if self.room.is_some() || self.asking || self.start_when_ready {
            return;
        }
        let switch_to_youtube = {
            let session = self.session.read(cx);
            if !session
                .providers()
                .any(|provider| provider.slug == "youtube" && provider.stored)
            {
                return Toasts::show(Outcome::Info, "toast-session-needs-provider", cx);
            }
            session.provider_slug() != Some("youtube") || session.client().is_none()
        };
        if switch_to_youtube {
            self.start_when_ready = true;
            self.session
                .update(cx, |session, cx| session.switch("youtube", cx));
            cx.notify();
            return;
        }
        let (name, image) = self.me(cx);
        self.code_shown = true;
        self.connect(
            Request::Create {
                version: VERSION,
                name,
                image,
                provider: "youtube".to_owned(),
            },
            cx,
        );
    }

    /// Joins the six-character session code and switches playback to YouTube when needed.
    pub fn join(&mut self, code: &str, cx: &mut Context<Self>) {
        if !self.available(cx) {
            return Toasts::show(Outcome::Info, "toast-session-needs-provider", cx);
        }
        let code: String = code
            .chars()
            .filter(char::is_ascii_alphanumeric)
            .collect::<String>()
            .to_ascii_uppercase();
        if code.len() != CODE_LENGTH || self.asking {
            return;
        }
        if self.room.as_ref().is_some_and(|room| room.code == code) {
            return;
        }
        if self.room.is_some() {
            self.leave(cx);
        }
        let request = self.join_request(code, cx);
        self.connect(request, cx);
    }

    /// Leaves the session, which ends it for everyone when this app hosts it.
    pub fn leave(&mut self, cx: &mut Context<Self>) {
        let Some(room) = self.room.as_ref() else {
            if self.asking {
                self.reset(cx);
            }
            return;
        };
        let key = match room.hosting() {
            true => "toast-session-ended",
            false => "toast-session-left",
        };
        if let Some(link) = &self.link {
            link.send(Request::Leave);
        }
        self.reset(cx);
        Toasts::show(Outcome::Done, key, cx);
    }

    /// Removes the member `id` from the session this app hosts. The server ignores it from anyone
    /// who does not host.
    pub fn kick(&self, id: &str) {
        let hosting = self.room.as_ref().is_some_and(Room::hosting);
        if let (true, Some(link)) = (hosting, &self.link) {
            link.send(Request::Kick {
                member: id.to_owned(),
            });
        }
    }

    /// Whether the code of the session this app is in is on show.
    pub fn code_shown(&self) -> bool {
        self.code_shown && self.room.is_some()
    }

    pub fn show_code(&mut self, shown: bool, cx: &mut Context<Self>) {
        self.code_shown = shown;
        cx.notify();
    }

    /// Whether the dialog that takes a code to join is open.
    pub fn join_shown(&self) -> bool {
        self.join_shown
    }

    pub fn show_join(&mut self, shown: bool, cx: &mut Context<Self>) {
        self.join_shown = shown;
        cx.notify();
    }

    /// The listener's name and picture, as the others in the session see them.
    fn me(&self, cx: &App) -> (String, Option<String>) {
        match self.session.read(cx).state() {
            SessionState::SignedIn(profile) => {
                (profile.display_name.clone(), profile.avatar.clone())
            }
            _ => (String::new(), None),
        }
    }

    fn join_request(&self, code: String, cx: &App) -> Request {
        let (name, image) = self.me(cx);
        Request::Join {
            version: VERSION,
            code,
            name,
            image,
            providers: vec!["youtube".to_owned()],
        }
    }

    /// Whether the last save of the listener's own server was turned down, which lasts until
    /// one goes through.
    pub fn rejected(&self) -> bool {
        self.rejected
    }

    /// Whether a server the listener asked to save is being checked.
    pub fn saving(&self) -> bool {
        self.saving.is_some()
    }

    /// The official Cloudflare party API, unless a development server was explicitly selected.
    fn server(&self, cx: &App) -> Option<String> {
        if let Ok(forced) = std::env::var(server::VARIABLE)
            && !forced.trim().is_empty()
        {
            return server::http_base(&forced);
        }
        let settings = self.settings.read(cx);
        if settings.sessions_official() {
            return Some(server::OFFICIAL.to_owned());
        }
        server::http_base(settings.sessions_server())
    }

    fn reject(&mut self, cx: &mut Context<Self>) {
        self.rejected = true;
        cx.notify();
    }

    /// Checks the server at `typed` and keeps it as the listener's own once it answers its health
    /// check. A toast says how it went, so an address that does not lead to a Audify Sessions
    /// server is never saved.
    pub fn save_server(&mut self, typed: &str, cx: &mut Context<Self>) {
        let typed = typed.trim().to_owned();
        if typed.is_empty() {
            self.reject(cx);
            return Toasts::show(Outcome::Info, "toast-sessions-server-empty", cx);
        }
        let Some(socket) = server::socket(&typed) else {
            self.reject(cx);
            return Toasts::show(Outcome::Failed, "toast-sessions-server-malformed", cx);
        };
        let http = self.http.clone();
        let io = self.io.clone();
        self.saving = Some(cx.spawn(async move |this, cx| {
            let found = io.spawn(server::check(http, socket.clone())).await;
            this.update(cx, |this, cx| {
                this.saving = None;
                match found {
                    Ok(Ok(())) => {
                        this.rejected = false;
                        this.settings
                            .update(cx, |settings, cx| settings.set_sessions_server(typed, cx));
                        Toasts::show(Outcome::Done, "toast-sessions-server-saved", cx);
                    }
                    Ok(Err(error)) => {
                        log::info!("listening: {socket} did not pass its health check: {error:#}");
                        this.rejected = true;
                        Toasts::show(Outcome::Failed, "toast-sessions-server-silent", cx);
                    }
                    Err(_) => {
                        this.rejected = true;
                        Toasts::show(Outcome::Failed, "toast-sessions-server-silent", cx)
                    }
                }
                cx.notify();
            })
            .ok();
        }));
        cx.notify();
    }

    fn connect(&mut self, first: Request, cx: &mut Context<Self>) {
        let Some(url) = self.server(cx) else {
            return Toasts::show(Outcome::Info, "toast-session-server-unavailable", cx);
        };
        let (link, heard) = Link::open(&self.io, url, first);
        self.link = Some(link);
        self.asking = true;
        self.hearing = Some(self.listen(heard, cx));
        cx.notify();
    }

    fn listen(&mut self, mut heard: UnboundedReceiver<Heard>, cx: &mut Context<Self>) -> Task<()> {
        cx.spawn(async move |this, cx| {
            while let Some(first) = heard.recv().await {
                let lost = this.update(cx, |this, cx| {
                    let mut lost = false;
                    for one in
                        std::iter::once(first).chain(std::iter::from_fn(|| heard.try_recv().ok()))
                    {
                        match one {
                            Heard::Reply(reply) => this.on_reply(*reply, cx),
                            Heard::Lost => lost = true,
                        }
                    }
                    if lost {
                        this.lost(cx);
                    }
                    lost
                });
                if !matches!(lost, Ok(false)) {
                    break;
                }
            }
        })
    }

    fn on_reply(&mut self, reply: Reply, cx: &mut Context<Self>) {
        match reply {
            Reply::Joined {
                code,
                provider,
                you,
                members,
                state,
            } => {
                let first = self.room.is_none();
                self.asking = false;
                self.retries = 0;
                self.retry = None;
                self.room = Some(Room {
                    code,
                    provider,
                    members,
                    you,
                });
                self.baseline = None;
                self.shared = state.clone();
                self.seeks = self.playback.read(cx).seeks();
                self.follow_provider(cx);
                match state.filter(|state| state.track.is_some()) {
                    Some(state) => {
                        self.waiting = Some(state);
                        self.catch_up(cx);
                    }
                    None => self.publish(cx),
                }
                self.checking = Some(self.check_every(cx));
                if first {
                    log::info!("listening: joined a session");
                }
            }
            Reply::Members { members } => {
                if let Some(room) = self.room.as_mut() {
                    room.members = members;
                }
            }
            Reply::Queue { upcoming } => {
                if let Some(state) = self.shared.as_mut() {
                    state.upcoming = upcoming;
                    let state = state.clone();
                    self.waiting = Some(state);
                    self.catch_up(cx);
                }
            }
            Reply::State { state, by, seq } => {
                let Some(room) = &self.room else {
                    return;
                };
                if by == room.you {
                    self.shared = Some(state);
                    return;
                }
                log::trace!("listening: applying playback sequence {seq} from member {by}");
                self.shared = Some(state.clone());
                self.waiting = Some(state);
                self.catch_up(cx);
            }
            Reply::Pong { t, now } => self.sample(t, now),
            Reply::Ended => {
                self.reset(cx);
                Toasts::show(Outcome::Info, "toast-session-ended", cx);
            }
            Reply::Kicked => {
                self.reset(cx);
                Toasts::show(Outcome::Info, "toast-session-kicked", cx);
            }
            Reply::Error { reason, provider } => self.refused(reason, provider, cx),
        }
        cx.notify();
    }

    /// Says why the server turned down a start or join. A refusal of anything else only goes to
    /// the log, since the session carries on.
    fn refused(&mut self, reason: Reason, provider: Option<String>, cx: &mut Context<Self>) {
        if !self.asking {
            log::warn!("listening: the server refused a request: {reason:?}");
            return;
        }
        let rejoining = self.room.is_some();
        self.reset(cx);
        let key = match reason {
            Reason::NotFound if rejoining => "toast-session-ended",
            Reason::NotFound => "toast-session-not-found",
            Reason::Full => "toast-session-full",
            Reason::RateLimited => "toast-session-rate-limited",
            Reason::TooMany => "toast-session-too-many",
            Reason::Provider => {
                let slug = provider.unwrap_or_default();
                let name = self
                    .session
                    .read(cx)
                    .account(&slug)
                    .map_or(slug.clone(), |(name, _)| name.to_owned());
                return Toasts::about(Outcome::Failed, "toast-session-provider", name, cx);
            }
            Reason::Busy | Reason::Invalid => "toast-session-failed",
        };
        Toasts::show(Outcome::Failed, key, cx);
    }

    /// Tries a dropped connection again, a little later each time, or gives up on it.
    fn lost(&mut self, cx: &mut Context<Self>) {
        self.link = None;
        self.hearing = None;
        let Some(code) = self.room.as_ref().map(|room| room.code.clone()) else {
            if self.asking {
                self.reset(cx);
                Toasts::show(Outcome::Failed, "toast-session-unreachable", cx);
            }
            return;
        };
        if self.retries >= RETRIES {
            self.reset(cx);
            return Toasts::show(Outcome::Failed, "toast-session-lost", cx);
        }
        let wait = Duration::from_secs(1 << self.retries.min(4));
        self.retries += 1;
        self.asking = false;
        log::info!("listening: connection lost, trying again in {wait:?}");
        self.retry = Some(cx.spawn(async move |this, cx| {
            cx.background_executor().timer(wait).await;
            this.update(cx, |this, cx| {
                this.retry = None;
                if this.room.is_some() {
                    let request = this.join_request(code, cx);
                    this.connect(request, cx);
                }
            })
            .ok();
        }));
        cx.notify();
    }

    /// Drops the session and everything kept about it.
    fn reset(&mut self, cx: &mut Context<Self>) {
        self.room = None;
        self.link = None;
        self.hearing = None;
        self.asking = false;
        self.start_when_ready = false;
        self.retry = None;
        self.retries = 0;
        self.shared = None;
        self.waiting = None;
        self.baseline = None;
        self.checking = None;
        self.code_shown = false;
        cx.notify();
    }

    /// Leaves the session when the listener signs out or moves to another provider than the
    /// session's, and brings playback in step once the session's provider is signed in.
    fn on_session(&mut self, event: &SessionEvent, cx: &mut Context<Self>) {
        if self.start_when_ready {
            let (youtube, signed_in, pending) = {
                let session = self.session.read(cx);
                (
                    session.provider_slug() == Some("youtube"),
                    matches!(session.state(), SessionState::SignedIn(_)),
                    session.is_pending(),
                )
            };
            if youtube && signed_in {
                self.start_when_ready = false;
                self.start(cx);
                return;
            }
            if !pending
                && (!youtube || !signed_in)
                && matches!(event, SessionEvent::SignedOut | SessionEvent::WentOffline)
            {
                self.start_when_ready = false;
                Toasts::show(Outcome::Failed, "toast-session-needs-provider", cx);
                return;
            }
        }
        let Some(provider) = self.room.as_ref().map(|room| room.provider.clone()) else {
            return;
        };
        let session = self.session.read(cx);
        let ours = session.provider_slug() == Some(provider.as_str());
        let gone = !session.is_pending() && (!ours || session.client().is_none());
        match event {
            SessionEvent::SignedIn | SessionEvent::Reconnected if ours => self.catch_up(cx),
            _ if gone => self.leave(cx),
            _ => {}
        }
    }

    /// Switches to YouTube when another provider is current; shared track IDs are YouTube video IDs.
    fn follow_provider(&mut self, cx: &mut Context<Self>) {
        let Some(provider) = self.room.as_ref().map(|room| room.provider.clone()) else {
            return;
        };
        if self.session.read(cx).provider_slug() == Some(provider.as_str()) {
            return;
        }
        log::info!("listening: switching to {provider} for the session");
        self.session
            .update(cx, |session, cx| session.switch(&provider, cx));
    }

    /// Whether the session's provider is the one signed in here, so its tracks can play.
    fn ready(&self, cx: &App) -> bool {
        let Some(room) = &self.room else {
            return false;
        };
        let session = self.session.read(cx);
        room.provider == "youtube"
            && session.provider_slug() == Some("youtube")
            && matches!(session.state(), SessionState::SignedIn(_))
    }

    /// Brings playback in step with the state waiting for it, once that can play here.
    fn catch_up(&mut self, cx: &mut Context<Self>) {
        if !self.ready(cx) {
            return;
        }
        let Some(state) = self.waiting.take() else {
            return;
        };
        self.apply(state, cx);
    }

    /// Plays what `state` says, from where the session is now. Only what differs is touched, so
    /// a pause from another member does not reload the track here.
    fn apply(&mut self, state: Shared, cx: &mut Context<Self>) {
        let Some(track) = state.track.clone() else {
            return;
        };
        let Some(id) = track.id.clone() else {
            return;
        };
        let at = self.expected(&state);
        let queue = self.queue.clone();
        let current = queue.read(cx).current().and_then(|track| track.id.clone());
        let upcoming = ids(&state.upcoming);
        let shown = ids(queue.read(cx).chosen_upcoming());
        let Shared {
            upcoming: next,
            playing,
            ..
        } = state;
        self.playback.update(cx, |playback, cx| {
            playback.locally(cx, |playback, cx| {
                if current.as_deref() != Some(id.as_str()) {
                    log::info!("listening: playing {id} from {at:?} for the session");
                    if queue.read(cx).shuffle() {
                        queue.update(cx, |queue, cx| queue.set_shuffle(false, cx));
                    }
                    let tracks: Vec<Track> = std::iter::once(track).chain(next).collect();
                    playback.start_at(tracks, 0, None, at, cx);
                    if !playing {
                        playback.pause(cx);
                    }
                    return;
                }
                if upcoming != shown {
                    queue.update(cx, |queue, cx| queue.share_upcoming(next, cx));
                }
                let toggled = playing != playback.wants_playing();
                match (playing, toggled) {
                    (true, true) => playback.resume(cx),
                    (false, true) => playback.pause(cx),
                    _ => {}
                }
                // a pause or resume lands on the exact spot, where a seek costs nothing heard
                let tolerance = match toggled || !playing {
                    true => NUDGE,
                    false => DRIFT,
                };
                if !playback.is_loading() && apart(playback.live_position(), at) > tolerance {
                    playback.seek(at, cx);
                }
            })
        });
        self.baseline = Some(self.mark(cx));
        self.seeks = self.playback.read(cx).seeks();
    }

    /// Tells the session what plays here when the listener changed it: another track, play or
    /// pause, a seek, or the queue.
    fn publish(&mut self, cx: &mut Context<Self>) {
        if self.room.is_none() || self.asking || self.waiting.is_some() || !self.ready(cx) {
            return;
        }
        let Some(link) = &self.link else {
            return;
        };
        let mark = self.mark(cx);
        let seeks = self.playback.read(cx).seeks();
        if self.baseline.as_ref() == Some(&mark) && seeks == self.seeks {
            return;
        }
        let reason = match &self.baseline {
            None => "first state",
            Some(baseline) if baseline.track != mark.track => "track",
            Some(baseline) if baseline.playing != mark.playing => "play or pause",
            Some(baseline) if baseline.upcoming != mark.upcoming => "queue",
            Some(_) => "seek",
        };
        let state = self.snapshot(cx);
        log::debug!(
            "listening: sharing {:?} for its {reason}, playing {}, at {} ms",
            mark.track,
            state.playing,
            state.position
        );
        link.send(Request::State {
            state: Box::new(state.clone()),
        });
        self.shared = Some(Shared {
            at: self.server_now(),
            ..state
        });
        self.baseline = Some(mark);
        self.seeks = seeks;
    }

    fn mark(&self, cx: &App) -> Mark {
        let queue = self.queue.read(cx);
        let youtube = self.session.read(cx).provider_slug() == Some("youtube");
        Mark {
            track: queue
                .current()
                .filter(|_| youtube)
                .and_then(|track| track.id.clone())
                .filter(|id| !music::is_local_id(id)),
            playing: self.playback.read(cx).wants_playing(),
            upcoming: if youtube {
                ids(queue.chosen_upcoming())
            } else {
                Vec::new()
            },
        }
    }

    /// Playback here as the session sees it. A local file stands for nothing shared.
    fn snapshot(&self, cx: &App) -> Shared {
        let queue = self.queue.read(cx);
        let playback = self.playback.read(cx);
        let youtube = self.session.read(cx).provider_slug() == Some("youtube");
        Shared {
            track: queue
                .current()
                .filter(|track| youtube && shareable(track))
                .cloned(),
            upcoming: queue
                .chosen_upcoming()
                .filter(|track| youtube && shareable(track))
                .take(UPCOMING)
                .cloned()
                .collect(),
            playing: playback.wants_playing(),
            position: playback.live_position().as_millis() as u64,
            at: 0,
        }
    }

    fn check_every(&self, cx: &mut Context<Self>) -> Task<()> {
        cx.spawn(async move |this, cx| {
            loop {
                cx.background_executor().timer(CHECK).await;
                if this.update(cx, |this, cx| this.check(cx)).is_err() {
                    break;
                }
            }
        })
    }

    /// Moves playback back in step with the session when it strayed, as after a stall while the
    /// track buffered here.
    fn check(&mut self, cx: &mut Context<Self>) {
        if self.asking || self.waiting.is_some() || !self.ready(cx) {
            return;
        }
        let Some(state) = &self.shared else {
            return;
        };
        let Some(id) = state.track.as_ref().and_then(|track| track.id.as_deref()) else {
            return;
        };
        let current = self
            .queue
            .read(cx)
            .current()
            .and_then(|track| track.id.as_deref());
        let playback = self.playback.read(cx);
        if current != Some(id) || playback.is_loading() || playback.wants_playing() != state.playing
        {
            return;
        }
        let at = self.expected(state);
        let drift = apart(playback.live_position(), at);
        if drift <= DRIFT {
            return;
        }
        log::info!("listening: {drift:?} out of step, seeking to {at:?}");
        self.playback.update(cx, |playback, cx| {
            playback.locally(cx, |playback, cx| playback.seek(at, cx))
        });
        self.seeks = self.playback.read(cx).seeks();
    }

    /// Where the session's track is now, going by when the server took `state`.
    fn expected(&self, state: &Shared) -> Duration {
        let mut at = state.position;
        if state.playing {
            at += self.server_now().saturating_sub(state.at);
        }
        let at = Duration::from_millis(at);
        match state.track.as_ref().map(|track| track.duration) {
            Some(total) if !total.is_zero() => at.min(total),
            _ => at,
        }
    }

    /// Notes a reading of the server's clock taken by a ping sent at `t`.
    fn sample(&mut self, t: u64, now: u64) {
        let here = link::now();
        let trip = here.saturating_sub(t);
        let offset = now as i64 + (trip / 2) as i64 - here as i64;
        self.clock.push_back((trip, offset));
        if self.clock.len() > SAMPLES {
            self.clock.pop_front();
        }
    }

    /// The server's clock now, going by the reading with the shortest round trip.
    fn server_now(&self) -> u64 {
        let offset = self
            .clock
            .iter()
            .min_by_key(|(trip, _)| *trip)
            .map_or(0, |(_, offset)| *offset);
        link::now().saturating_add_signed(offset)
    }
}

/// Whether a track means anything to the other members, which a file on this disk does not.
fn shareable(track: &Track) -> bool {
    track
        .id
        .as_deref()
        .is_some_and(|id| !music::is_local_id(id))
}

/// The ids of the shareable tracks among the first `UPCOMING` of `tracks`.
fn ids<'a>(tracks: impl IntoIterator<Item = &'a Track>) -> Vec<String> {
    tracks
        .into_iter()
        .filter(|track| shareable(track))
        .take(UPCOMING)
        .filter_map(|track| track.id.clone())
        .collect()
}

fn apart(a: Duration, b: Duration) -> Duration {
    a.abs_diff(b)
}
