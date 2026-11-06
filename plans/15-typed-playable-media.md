<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
Baseline: a88ee53c (2026-11-05)
-->
# Plan 15 — Typed playable-media playback boundary

**Status: architecture designed — implementation not started.**

**Purpose:** provide a safe playback foundation for distinct media types such as RSS podcast episodes without pretending they are YouTube Music `Song` objects. This is a prerequisite for Plan 14. It is not authorization to implement podcast feeds or to rewrite unrelated playback systems.

## Decision

Introduce an Android-app-layer typed playback descriptor and make Media3 `MediaItem` the serialized/session boundary. Preserve `Song` as the existing music domain model. Do not add podcast fields to the shared `Song` class, fabricate YouTube IDs, or generalize every music algorithm to arbitrary media.

The first supported variants are:

- `PlayableMedia.Music(song: Song)` — adapter over today's behavior; existing music paths remain the default.
- `PlayableMedia.Podcast(episode: PodcastEpisodeRef)` — stable podcast feed/episode identity, direct validated enclosure URI, show/episode metadata, and optional duration/artwork.

The descriptor is a projection of durable domain data, not the canonical podcast database. The podcast repository remains the owner of feed and episode metadata/progress. The player owns transient queue occurrence, playback state, and playhead.

## Why this boundary is required

`Song` in `shared/src/commonMain/kotlin/com/music/audify/data/model/Models.kt` represents a YouTube Music/music catalogue track. Its `videoId`, `toMediaItem()` URI construction, matching metadata, download lookup, source quality, artist/album actions, and scrobbling semantics are music-specific.

`PlayerConnection.kt` currently defines `MediaItem.toSong()` as an unconditional conversion. It reconstructs a `Song` from `mediaId` and extras. `Song.toMediaItem()` creates `audify://watch?v=...` or a music source URI and sets `MEDIA_TYPE_MUSIC`.

`PlayerState` exposes `song: Song?` and `queue: List<Song>`. The service converts current items and entire queues to `Song` in transition handling, queue snapshots, bounded-context persistence, autoplay, lyrics, scrobbling, audio cache bookkeeping, queue commands, and source recovery. These conversions are the type-erasure fault line.

## Proposed domain and adapter types

Create `app/src/main/java/com/music/audify/playback/model/PlayableMedia.kt` (or the nearest existing playback-model package after checking current package conventions):

```kotlin
sealed interface PlayableMedia {
    val stableId: String
    val title: String
    val artist: String
    val artworkUri: Uri?
    val durationMs: Long?

    data class Music(val song: Song) : PlayableMedia { /* delegated metadata */ }
    data class Podcast(val episode: PodcastEpisodeRef) : PlayableMedia { /* delegated metadata */ }
}

data class PodcastEpisodeRef(
    val feedId: String,
    val episodeId: String,
    val enclosureUri: Uri,
    val mimeType: String?,
    val showTitle: String,
    val episodeTitle: String,
    val description: String?,
    val artworkUri: Uri?,
    val durationMs: Long?,
)
```

Implementation constraints:

- `stableId` must be namespaced and collision-safe, e.g. `podcast:<feed-id>:<episode-id>`; it is not the enclosure URL and never a YouTube `videoId`.
- A queue occurrence ID is separate from `stableId`. The same episode can appear twice in a queue; each occurrence needs a unique immutable `queueEntryId`.
- Queue tier remains orthogonal metadata. Initially podcast entries may use `USER_QUEUE` or a dedicated `PODCAST` tier only if the existing tier algorithms are proven compatible. Prefer a generic queue-entry tier wrapper over adding podcast-specific behavior to `Song`.
- `PodcastEpisodeRef` must contain only the minimum playback metadata. Feed parsing, validation, subscription metadata, and progress remain in the podcast domain/repository.
- The enclosure URI must already have passed the podcast URL policy. Playback must not infer trust from the presence of the type marker alone; service-side validation remains authoritative.

## Media3 contract

Replace the unconditional inverse converter with explicit APIs:

```kotlin
fun MediaItem.toPlayableMedia(): PlayableMedia
fun MediaItem.toSongOrNull(): Song?
fun PlayableMedia.toMediaItem(queue: QueueEntryMetadata): MediaItem
fun MediaItem.isPodcastEpisode: Boolean
```

Keep `Song.toMediaItem()` temporarily as a compatibility wrapper that delegates to `PlayableMedia.Music(song)`. Migrate call sites in batches; remove the wrapper only after a repository-wide search proves no callers remain.

MediaItem metadata contract:

| Field | Music | Podcast |
|---|---|---|
| `mediaId` | Existing music ID | Namespaced stable episode ID |
| `localConfiguration.uri` | Existing Audify music URI | Validated HTTPS enclosure URI |
| `mediaMetadata.mediaType` | `MEDIA_TYPE_MUSIC` | `MEDIA_TYPE_AUDIO_BOOK_CHAPTER` only if platform semantics and surfaces prove appropriate; otherwise `MEDIA_TYPE_PODCAST_EPISODE` if available in pinned Media3, else `MEDIA_TYPE_UNKNOWN` plus explicit Audify type extra |
| extras | Existing music keys unchanged | `audify.mediaKind=podcast`, feed ID, episode ID, enclosure MIME, queue occurrence ID, queue tier |
| title/artist/artwork | Existing song values | Episode title / show title / show artwork |
| album title | Existing album | Show title only if surfaces display it consistently; otherwise omit |

Before implementation, verify the exact media-type constants available in the pinned Media3 dependency. Do not guess a constant or silently classify podcasts as music. Session/notification metadata must be tested on Android versions supported by the app.

## Playback routing and service behavior

### Data source and URL handling

- Podcast MediaItems carry the enclosure URI directly. They must not use `audify://watch`, `audify://source`, `StreamResolver`, `SourceResolver`, `TrackMatcher`, or the YouTube `v=` query path.
- Route by explicit `mediaKind` before any music URI parsing. A podcast request must never fall through to the existing `v` resolver path.
- Use Media3's normal HTTP data source with explicit request policy. Redirects must be revalidated by the podcast network policy; if the current DataSource cannot enforce that policy on every redirect, add a podcast-specific bounded DataSource or resolve a safe final URI before playback. Do not rely on feed-time validation alone.
- Preserve existing music cache and source behavior byte-for-byte for music items. Do not route podcast enclosures through `AudioCache`'s YouTube-keyed cache.

### Service transition split

Keep `onMediaItemTransition` as the single authoritative queue transition callback, but dispatch typed behavior:

1. Common: reset per-item transient timing/format state, update current media identity, notify media-session/UI state, persist queue cursor.
2. Music branch: existing `AudioCache.notePlayed`, local bitrate resolution, NerdStats/QualityUpgrade, loudness, Automix, source upgrade, lyrics, Discord, ListenBrainz/Last.fm, music history, and radio/autoplay behavior.
3. Podcast branch: cancel music-only jobs; persist podcast progress/played state to the podcast repository; update podcast listening state and media metadata; do not invoke music cache, source resolution, quality upgrade, Automix, music radio/autoplay, lyrics lookup, Last.fm, ListenBrainz, or music favorites/history.

The callback must not call `toSong()` before checking the media kind. A missing/unknown kind is treated as unsupported, not guessed to be music based on title or URI shape.

### Controls and commands

- Common transport controls (play, pause, seek, next, previous, queue insertion/removal) continue through Media3.
- Music-only custom session commands must check the current typed media kind server-side, not only hide their UI buttons. Reject favorite/rating, source quality upgrade, revert-to-original, music download, radio, and music metadata commands for podcast entries.
- Podcast episode progress and played status are updated by a dedicated callback/repository API. Completion threshold and seek-to-end semantics are owned by Plan 14; player transitions must report the actual end reason.
- Queue reordering must operate on MediaItems and preserve extras/queue occurrence IDs. It must not rebuild podcast items via `toSong()`.

## Queue model: preserve the music algorithms

Do not convert `QueueTimeline`, `QueueCoordinator` or bounded-context music continuation to generic podcast-aware algorithms in the first refactor.

Instead:

- Keep the current music context queue, shuffle, autoplay and party queue algorithms typed as `Song`.
- Introduce a generic `QueueEntry`/`QueueEntryMetadata` adapter only at the Media3 boundary, carrying queue occurrence ID, tier, canonical index, and media kind.
- Permit a podcast episode to enter the physical Media3 queue via a dedicated podcast queue API. It can be inserted before/after the current item and reordered as a MediaItem, but it cannot become a member of a music CONTEXT or AUTOPLAY collection.
- Define mixed-queue behavior explicitly: music CONTEXT and AUTOPLAY builders must filter or reject podcast items; podcast entries are user-queued one-offs until a future unified queue design is separately approved. Repeat-all and history trimming must preserve the podcast occurrence and not silently coerce it into a music context tier.
- Listen Together is music-only in this scope. Reject podcast queue sharing and prevent a podcast item from being serialized as a `PartyTrack`.
- Wear OS and Android Auto must display typed metadata and use common transport controls. Do not assume their current music-specific queue editing can round-trip podcast items; disable unsupported edit actions until covered by tests.

This is the central scope limiter: the refactor makes the player type-safe; it does not turn every existing music feature into a multi-format feature.

## Playback state and UI contract

Extend `PlayerState` without removing compatibility fields in the first migration:

```kotlin
data class PlayerState(
    val playable: PlayableMedia? = null,
    val queueItems: List<PlayableMedia> = emptyList(),
    // Existing `song` and `queue` remain music-only compatibility projections.
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    ...
)
```

Rules:

- `song` is non-null only for `PlayableMedia.Music`; it is null for podcasts.
- `queue` contains only music entries. Do not fabricate placeholders or silently omit podcast rows from a UI that claims to show the full queue; migrate that surface to `queueItems` first.
- Player UI renders title/show identity and artwork from `playable`, and conditionally renders music-only actions only for `Music`.
- `AndroidPlayerHost` and `MainActivity` must not infer current media type from `song == null` alone if loading/unknown state is possible; use explicit kind.
- Artwork, notification, lock screen and session metadata are built from the typed descriptor.

## Persistence and restoration

### Physical queue snapshot

`LastPlayed` is currently a serialized `List<Song>` and must remain backwards-compatible. Introduce a versioned `StoredPlayable` union with `kind = music|podcast`:

- `music` stores the same fields as the current `StoredTrack`; decoding old snapshots with no `kind` means `music`.
- `podcast` stores feed ID, episode ID, stable media ID, enclosure URI, MIME hint, display metadata, artwork URI, duration, queue occurrence ID, and queue tier.
- Keep the existing scalar index/position keys and bounded window behavior. Add no Room tables or migrations.
- On restore, podcast entries must be resolved against the podcast repository by `(feedId, episodeId)`. If the feed/episode no longer exists or enclosure validation fails, skip that entry with a recoverable restore notice; never reinterpret it as a music item.
- If the podcast store is temporarily unavailable, keep the stored typed queue snapshot and retry resolution; do not clear the entire mixed queue because one podcast entry is unresolved.

### Bounded context persistence

`BoundedContextPersistence` models a logical music CONTEXT queue and stores `Song` throughout its canonical context, entries and cycles. Keep it music-only. A physical podcast item must never be included in its context snapshot or canonical index. Persist podcast user-queue entries in the versioned physical queue snapshot, not the bounded music-context document.

### Podcast progress

The podcast repository is the canonical owner of resume position and played state. `LastPlayed` only restores the physical queue and current playhead. On restore, the episode repository's saved progress is applied to the episode; current-session position can supersede it only for the same feed/episode identity. Never persist episode progress under a music `videoId` key.

## Explicit call-site migration inventory

The implementation must use repository-wide searches before each batch. This inventory is grounded in the 2026-11-05 source audit and is not exhaustive.

| Area | Current assumption | Required change |
|---|---|---|
| `PlayerConnection.kt` (`MediaItem.toSong`, `Song.toMediaItem`, `PlayerState`) | Every item is a Song | Add typed conversion and state; keep music compatibility adapters |
| `PlaybackService.kt` transition listener / `onTrackBecameCurrent` | Every transition starts a Song lifecycle | Split common transition from Music and Podcast branches |
| `PlaybackService.kt` URI resolver (`audify://watch`, `source`, `v=`) | Playback URI is music | Add explicit podcast direct-URI route before music parsing; revalidate redirects |
| `PlaybackService.kt` queue snapshots (`saveQueueSnapshot*`) | Every physical item converts to Song | Versioned union snapshot; preserve old music JSON |
| `BoundedContextPersistence.kt` | Canonical context and entries are Songs | Keep music-only; reject podcasts from this store |
| `LastPlayed.kt` | StoredTrack is always music | Add versioned typed stored entry and legacy decode |
| `QueueCoordinator.kt` / `QueueTimeline` / `QueueShuffle.kt` | Queue planning may inspect Song fields | Keep context planning music-only; generic MediaItem reorder preserves podcast extras |
| `MainActivity.kt` queue and play-next handlers | Controller queue maps all items to Song | Use typed queue entries; music-only builders filter/reject podcast |
| `PlayerConnection.kt` and player UI | `PlayerState.song` is the current item | Add `playable`; migrate UI actions by kind |
| `ScrobbleManager`, `ListeningRecorder`, ListenBrainz | Current item is a music recording | Dispatch only Music; no podcast scrobble in MVP |
| `AudioCache`, `QualityUpgrade`, `SourceResolver`, Automix | Media ID identifies a YouTube/music track | Music-only dispatch; podcast bypasses all of them |
| Lyrics/autoplay/radio/favorites/download commands | Current item supports music actions | Guard in service and hide/disable UI for podcast |
| `WearBridge.kt` / cast / remote session surfaces | MediaItem can be rebuilt as Song | Use typed metadata; disable unsupported remote editing |

## Implementation phases and gates

1. **Typed model and codec only.** Add sealed model, media-kind extras, `toPlayableMedia`, `toSongOrNull`, and round-trip tests. No service behavior change. Gate: legacy music conversion tests pass; podcast never converts to Song.
2. **Media3 direct playback spike.** Add a test-only or isolated podcast item builder with direct HTTPS URI, MIME metadata and explicit media kind. Verify actual Media3 extractor playback on a local test HTTP server and verify redirects are blocked/revalidated. Gate: no music resolver invoked.
3. **Service transition split.** Extract the existing music transition body unchanged into `handleMusicTransition`; add a minimal podcast transition branch and server-side command guards. Gate: existing music transition/scrobble/cache/source tests pass.
4. **Queue and persistence.** Add typed physical queue snapshots with legacy decoding; keep bounded-context persistence music-only. Add typed queue APIs and preserve queue occurrence IDs across reorder/restore. Gate: cold-start restore for legacy music-only queues is byte-compatible; mixed queue restore retains both kinds.
5. **Player UI state.** Add `PlayerState.playable` and `queueItems`, migrate player and queue surfaces, preserve `song`/`queue` as music-only compatibility projections during rollout. Gate: no surface displays a podcast as a song or drops it silently.
6. **Peripheral integrations.** Audit Android Auto, Wear OS, Cast, Listen Together, notifications, scrobbling, Discord, lyrics, Automix, downloads, and stats. Explicitly support common transport/metadata only; reject unsupported music-only actions.
7. **Regression and device QA.** Run JDK 21 compile/unit tests and physical-device playback, process-death restore, notification, seek, redirect, mixed queue, and music regression tests.

Do not combine phases into one patch. Stop at any gate failure and update this plan with the concrete failing test/call site.

## Required tests

- Music `Song -> MediaItem -> PlayableMedia.Music -> Song` preserves every currently round-tripped field.
- Podcast `PodcastEpisodeRef -> MediaItem -> PlayableMedia.Podcast` preserves feed ID, episode ID, enclosure URI, MIME, title, show, artwork, duration, and queue occurrence ID.
- `MediaItem.toSongOrNull()` returns null for podcast and unknown media kinds; no fake `Song` is created.
- Music URI routes continue through existing SourceResolver/StreamResolver behavior; podcast URI routes never invoke them.
- Redirect to loopback/private/link-local destinations is rejected on every redirect hop.
- Music-only service commands are rejected for podcasts even when invoked directly through MediaSession.
- Transition to/from podcast closes music scrobble/listening state correctly and never starts music history/scrobbling/cache/Automix/quality-upgrade work for podcast.
- Legacy LastPlayed JSON without a kind decodes as music; new mixed snapshots restore both variants and preserve queue occurrence identity.
- BoundedContextPersistence rejects podcast entries and continues to restore existing music context snapshots.
- Mixed queue insertion/reorder/removal/repeat/history trimming preserves queue item kind and occurrence IDs.
- Listen Together cannot publish or accept podcast entries as PartyTrack.
- Existing music, local-file, source-backed, quality-upgrade, Automix, crossfade, and process-death tests remain green.

## Non-goals and constraints

- No shared `Song` model changes.
- No Room schema/migration changes.
- No app version changes, release preparation, README/release-note edits, commits, pushes, tags, or GitHub workflows.
- No podcast feed parser/store/UI implementation in this plan; those remain Plan 14.
- No broad generic queue rewrite or Listen Together podcast support.
- Preserve signing files and all unrelated working-tree changes.

## Rollback

Remove the typed podcast variant and its routing branch while retaining the music adapter. The existing music `Song.toMediaItem`, legacy LastPlayed decoder, and music queue behavior remain available until the migration is complete. Never clear existing music queue/history/download data as rollback cleanup.

## Approval boundary

This document is an architecture design, not implementation authorization. The user has approved Plan 14's product scope and asked for this refactor design. Before coding, confirm the design's typed model, mixed-queue constraints, persistence compatibility, and platform integration scope. Plan 14 remains blocked until the playback foundation passes its gates.