//! The desktop's internal listening messages and the shared Cloudflare wire format.

use std::time::Duration;

use music::{ArtistRef, Track};
use serde::{Deserialize, Serialize};

pub(crate) const VERSION: u32 = 1;

#[derive(Clone, Debug, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub(crate) enum Request {
    Create {
        version: u32,
        name: String,
        image: Option<String>,
        provider: String,
    },
    Join {
        version: u32,
        code: String,
        name: String,
        image: Option<String>,
        providers: Vec<String>,
    },
    State {
        state: Box<Shared>,
    },
    Leave,
    Kick {
        member: String,
    },
}

#[derive(Clone, Debug)]
pub(crate) enum Reply {
    Joined {
        code: String,
        provider: String,
        you: String,
        members: Vec<Member>,
        state: Option<Shared>,
    },
    Members {
        members: Vec<Member>,
    },
    State {
        state: Shared,
        by: String,
        seq: u64,
    },
    Queue {
        upcoming: Vec<Track>,
    },
    Pong {
        t: u64,
        now: u64,
    },
    Ended,
    Kicked,
    Error {
        reason: Reason,
        provider: Option<String>,
    },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Reason {
    NotFound,
    Provider,
    Full,
    RateLimited,
    Busy,
    TooMany,
    Invalid,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Eq)]
pub struct Member {
    #[serde(alias = "memberId")]
    pub id: String,
    #[serde(alias = "displayName")]
    pub name: String,
    #[serde(default, alias = "avatarUrl")]
    pub image: Option<String>,
    #[serde(alias = "isHost")]
    pub host: bool,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub(crate) struct Shared {
    pub track: Option<Track>,
    #[serde(default)]
    pub upcoming: Vec<Track>,
    pub playing: bool,
    pub position: u64,
    #[serde(default)]
    pub at: u64,
}

#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PartyTrack {
    pub video_id: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub artist: String,
    #[serde(default)]
    pub thumbnail_url: Option<String>,
    #[serde(default)]
    pub duration_ms: Option<u64>,
    #[serde(default)]
    pub from_autoplay: bool,
}

impl PartyTrack {
    pub fn from_track(track: &Track) -> Option<Self> {
        Some(Self {
            video_id: track.id.clone()?,
            title: track.name.clone(),
            artist: track.artists.clone(),
            thumbnail_url: track.cover.clone(),
            duration_ms: (!track.duration.is_zero()).then(|| track.duration.as_millis() as u64),
            from_autoplay: false,
        })
    }

    pub fn into_track(self) -> Track {
        Track {
            id: Some(self.video_id),
            name: self.title,
            playable: true,
            artists: self.artist,
            artist_refs: Vec::<ArtistRef>::new(),
            album: String::new(),
            album_id: None,
            cover: self.thumbnail_url,
            duration: Duration::from_millis(self.duration_ms.unwrap_or_default()),
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
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PartyMember {
    pub member_id: String,
    pub display_name: String,
    pub avatar_url: Option<String>,
    pub is_host: bool,
}

impl From<PartyMember> for Member {
    fn from(member: PartyMember) -> Self {
        Self {
            id: member.member_id,
            name: member.display_name,
            image: member.avatar_url,
            host: member.is_host,
        }
    }
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PartyPlayback {
    pub seq: u64,
    pub track: Option<PartyTrack>,
    pub queue_index: i32,
    pub is_playing: bool,
    pub position_ms: u64,
    pub effective_position_ms: u64,
    #[serde(default)]
    pub updated_by: Option<String>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PartyQueue {
    pub index: i32,
    pub items: Vec<PartyTrack>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PartySnapshot {
    pub code: String,
    pub members: Vec<PartyMember>,
    pub playback: PartyPlayback,
    pub queue: PartyQueue,
}

pub(crate) fn shared(playback: PartyPlayback, queue: &[PartyTrack], server_ms: u64) -> Shared {
    let start = if playback.queue_index >= 0 {
        playback.queue_index as usize + 1
    } else {
        0
    };
    let upcoming = queue
        .iter()
        .skip(start)
        .cloned()
        .map(PartyTrack::into_track)
        .collect();
    Shared {
        track: playback.track.map(PartyTrack::into_track),
        upcoming,
        playing: playback.is_playing,
        position: if playback.effective_position_ms == 0 && playback.position_ms > 0 {
            playback.position_ms
        } else {
            playback.effective_position_ms
        },
        at: server_ms,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cloudflare_snapshot_decodes_android_member_and_youtube_track_fields() {
        let snapshot: PartySnapshot = serde_json::from_value(serde_json::json!({
            "code": "A7K2Q9",
            "members": [{
                "memberId": "member-pc", "userId": "desktop:device", "displayName": "Desktop",
                "avatarUrl": "https://example.com/avatar.png", "isHost": true, "connected": true,
                "joinedAtMs": 1, "lastSeenMs": 2
            }],
            "playback": {
                "seq": 4,
                "track": { "videoId": "yt-video-1", "title": "Shared Song", "artist": "Artist",
                    "thumbnailUrl": "https://example.com/cover.jpg", "durationMs": 183000, "fromAutoplay": false },
                "queueIndex": 0, "queueLength": 2, "isPlaying": true, "positionMs": 900,
                "effectivePositionMs": 1200, "anchorMs": 10, "queueSeq": 3, "updatedBy": "member-pc"
            },
            "queue": { "seq": 3, "index": 0, "items": [
                { "videoId": "yt-video-1", "title": "Shared Song", "artist": "Artist", "durationMs": 183000 },
                { "videoId": "yt-video-2", "title": "Next Song", "artist": "Another Artist", "durationMs": 201000 }
            ] },
            "serverMs": 1234, "maxMembers": 5, "hostOnlyControl": false
        })).expect("Cloudflare party snapshot must decode");

        let member: Member = snapshot
            .members
            .into_iter()
            .next()
            .expect("one member")
            .into();
        assert_eq!(member.id, "member-pc");
        assert_eq!(member.name, "Desktop");
        assert!(member.host);

        let state = shared(snapshot.playback, &snapshot.queue.items, 1234);
        let current = state.track.expect("current YouTube track");
        assert_eq!(current.id.as_deref(), Some("yt-video-1"));
        assert_eq!(current.name, "Shared Song");
        assert_eq!(current.artists, "Artist");
        assert_eq!(current.duration, Duration::from_secs(183));
        assert_eq!(state.upcoming.len(), 1);
        assert_eq!(state.upcoming[0].id.as_deref(), Some("yt-video-2"));
        assert!(state.playing);
        assert_eq!(state.position, 1200);
        assert_eq!(state.at, 1234);
    }

    #[test]
    fn desktop_track_serializes_to_the_shared_youtube_video_contract() {
        let track = PartyTrack {
            video_id: "video-id".to_owned(),
            title: "Song".to_owned(),
            artist: "Artist".to_owned(),
            thumbnail_url: Some("https://example.com/image.jpg".to_owned()),
            duration_ms: Some(90_000),
            from_autoplay: false,
        };
        let value = serde_json::to_value(track).expect("serialize party track");
        assert_eq!(value["videoId"], "video-id");
        assert_eq!(value["title"], "Song");
        assert_eq!(value["artist"], "Artist");
        assert_eq!(value["durationMs"], 90_000);
        assert_eq!(value["fromAutoplay"], false);
    }
}
