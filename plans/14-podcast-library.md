<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 14 — Podcast library and episode discovery

**Status: blocked — product scope approved; typed playback-model refactor is designed in [Plan 15](15-typed-playable-media.md) and must be implemented and verified before podcast feature work.**

**Product approval:** received 2026-11-05 for the Android-phone-only, user-added RSS/Atom, manual-refresh, local-only MVP.

**Implementation status:** no podcast feature code has been changed. The playback spike/audit found that the existing service cannot safely preserve a podcast content type through its current `Song`-only queue, source-resolution, transition, party-sync, and restart paths. The bounded typed-playback design is now documented in [Plan 15](15-typed-playable-media.md); implement and verify that foundation before starting feed parser/store/UI work.

**Audit baseline:** repository `a88ee53c` (2026-11-05). Re-check the listed files and working-tree drift before execution.

## Objective

Add a separate, RSS/Atom-based podcast library to the Android phone app. A listener can add a podcast by feed URL, browse episodes, play an episode, and resume it later. Podcast state must remain distinct from music identity, music-library actions, and music queue semantics.

This is not a request to turn every provider result labelled `podcast` into a playable Audify track. Podcast feeds have their own identity, refresh lifecycle, episode identity, direct enclosure URLs, and progress semantics.

## Recommended product decision

Approve a deliberately narrow first release:

- **Platform:** Android phone only. Do not add TV, Wear OS, desktop, web, Android Auto-specific browse surfaces, or cross-device sync in this plan.
- **Discovery:** user adds a valid RSS 2.0 or Atom feed URL. No central catalogue, proprietary catalogue scraping, search-provider dependency, or automatic recommendations.
- **Library:** saved shows, show details, episode list, publication date, duration when supplied, and clear feed-refresh state.
- **Playback:** play an episode, play next, add to queue, normal transport/seek, and durable resume position plus played state.
- **Refresh:** manual refresh only in v1. Use conditional HTTP requests when the server supplies validators. No scheduled or background polling.
- **Persistence:** a dedicated app-private podcast store, separate from the music Room database and `Song` tables. Use bounded, versioned JSON with atomic file replacement for v1; do not add a Room migration under the current project constraints.

If the product decision differs from this recommendation, revise this plan before implementation rather than silently broadening scope.

## Current architecture audit

### Existing partial support — not a podcast player

- `YtMusicRepository.LIBRARY_FEEDS` includes a YouTube Music `Podcasts` library feed (`FEmusic_library_non_music_audio_list`). This is an account-provider shelf, not a local RSS subscription store.
- `InnertubeParser.parseBrowseItem` maps page types other than album, artist, and playlist to `BrowseType.OTHER`. Podcast and episode labels appear in subtitle cleanup, but there is no dedicated podcast/episode domain model or feed parser.
- `SpotifyLibrary` requests the `YOUR_EPISODES_V2` library feature in its playlist query, but the inspected library path is playlist-oriented. This is not evidence of a complete Spotify podcast episode implementation.
- `BrowseType` currently has only `ALBUM`, `ARTIST`, `PLAYLIST`, and `OTHER`. `SearchResult` is track/browse oriented.
- `LocalMediaRepository` excludes MediaStore podcast rows from the ordinary music library. That exclusion must remain; this plan must not reclassify device podcast files as songs.

### Playback and queue constraints

- `Song` in `shared/.../data/model/Models.kt` is a music-track model whose identity and source resolution are built around `videoId` and music catalogue/source semantics.
- `Song.toMediaItem` in `app/.../playback/PlayerConnection.kt` normally creates an `audify://watch` URI or a source URI. It sets `MEDIA_TYPE_MUSIC` and carries song-specific extras. `MediaItem.toSong` reconstructs a `Song` from those extras.
- `PlaybackService`, queue operations, source substitution, Automix, scrobbling, downloads, and several UI menus call `MediaItem.toSong`. A podcast episode must not be represented by a fabricated YouTube video ID or allowed to enter YouTube/source matching accidentally.
- `LastPlayed` serializes a bounded `List<Song>` snapshot in SharedPreferences. It currently cannot preserve a podcast feed ID, episode GUID, enclosure URL, or episode-specific state.
- `QueueCoordinator` and `QueueTimeline` preserve music queue tiers and queue-entry identity. Podcast playback must not change those invariants or let music-only Automix/source-upgrade logic act on episodes.

### Persistence and downloads constraints

- `LocalDatabase` is Room version 2 and owns music songs, favorites, history, playlists, sync state, and migration state. No podcast tables exist. Room schema/migration changes are outside the current authorization.
- `AppSettings` and `Downloads` use SharedPreferences. `Downloads` keys saved media by `videoId`, runs through the music source resolver, and exposes music-oriented metadata and quality choices. It is not a safe podcast enclosure downloader as-is.
- `LastPlayed` is a bounded queue snapshot, not a general subscription/episode database. Do not overload its queue JSON as the podcast library store.

### UI and navigation constraints

- The phone Library tab is assembled in `MainActivity.kt`; `LibraryPhoneParts.kt` owns phone-specific library links and device shelves.
- `AppNavigator` owns non-detail routes. `MainViewModel` owns the current music library and album/artist/playlist detail stack.
- `DetailScreen` and `BrowseType` are designed around music release pages and `List<Song>`. Podcast show/episode pages should be separate typed screens/state, not forced into a music release page.
- English Android strings belong only in `app/src/main/res/values/sachit_strings.xml`. Do not edit translated resource files as part of this plan.

## Product boundaries

### In scope

1. Add a feed by URL, validate it, show a useful error for invalid/unsupported feeds, and prevent duplicate subscriptions by canonical feed URL.
2. Parse RSS 2.0 and Atom show metadata and episode entries, including stable GUID/ID, title, description/summary, publication date, duration where present, artwork, enclosure URL, and declared MIME type.
3. Browse saved shows and episodes; show loading, empty, stale, refreshing, and failure states distinctly.
4. Refresh on explicit user action, preserve the last successful episode list on transient failure, and report the last successful refresh time.
5. Play episodes with direct, validated enclosure URLs; support seek, pause/resume, queue insertion, and durable per-episode progress/played state.
6. Keep podcast media type and metadata explicit through Media3 conversion and process-death restoration.

### Explicitly out of scope

- Podcast directory/search service, provider catalogue scraping, YouTube Music/Spotify podcast playback, or automatic show recommendations.
- Automatic subscription import, OPML import/export, account sync, cloud backup, and multi-device progress sync.
- Background polling, push notifications, automatic downloads, ad insertion, transcript fetching, chapters, video podcasts, and premium feed authentication.
- Reusing the music `Downloads` pipeline or adding podcast episodes to the music favorites/history/scrobbling model without a separate reviewed design.
- Room schema/migration changes, app version changes, translated strings, README/release-note edits, commits, pushes, or releases.

## Architecture decisions required before implementation

### 1. Typed podcast identity and playback boundary

Introduce a dedicated `PodcastShow` and `PodcastEpisode` domain model. An episode's stable key must be derived from the feed identity plus its GUID/Atom ID; when absent, use a deterministic fallback from canonical enclosure URL and publication metadata. Never use title alone as identity.

Before building UI, complete a small playback spike proving that an episode can:

- become a Media3 `MediaItem` with podcast media type and explicit feed/episode identity;
- use its validated enclosure URI directly, bypassing `StreamResolver`, YouTube matching, source substitution, and music download lookup;
- round-trip all required fields through `MediaItem` and process-death queue restoration;
- remain distinguishable from music in the player, notification, queue, and menus.

Do not merely set `Song.videoId` to a URL or invent a fake YouTube ID. If the current `Song`-centric service cannot preserve the type boundary safely, introduce a small sealed playable-media abstraction and migrate call sites deliberately. This is a design gate, not permission for an unbounded playback rewrite.

Music-only behavior must be explicitly disabled or adapted for podcast items: source matching/upgrades, YouTube original-version controls, Automix/crossfade analysis, music radio/autoplay, like/dislike, music download-quality selection, and music scrobbling. Queue identity and user queue operations must continue to work.

### 2. Podcast persistence without Room changes

Use a dedicated, versioned JSON document in app-private storage, written through `androidx.core.util.AtomicFile` (or an equally safe atomic replacement primitive already available in the project). Keep the repository behind a typed interface and serialize writes so concurrent refresh/progress updates cannot overwrite one another.

Initial hard limits:

- Maximum 100 subscribed feeds.
- Maximum 500 retained episodes per feed; newest entries win, but never discard an episode currently playing or one with saved progress without an explicit retention policy.
- Maximum 2 MiB decompressed feed document per refresh.
- Maximum 10 MiB persisted podcast metadata document; if exceeded, fail visibly and preserve the last valid file.
- Persist progress on pause, seek, episode transition, completion, and a bounded periodic checkpoint (no per-second disk writes).

The file format must carry a schema version, canonical feed URL, validators (`ETag`/`Last-Modified`), last attempted/successful refresh, last error, feed metadata, episode identity, enclosure metadata, progress, and played state. Unknown fields must be ignored for forward compatibility. Corrupt data must not silently overwrite the last valid file; provide a recoverable error path.

Do not store feed credentials or arbitrary authorization headers in v1. If authenticated feeds are later required, design secure credential storage separately.

### 3. Feed and enclosure network safety

- Accept HTTPS feed URLs only in v1. Reject credentials in URL userinfo, unsupported schemes, malformed hosts, and unsafe ports.
- Limit redirects (maximum 5), revalidate every redirect target, and reject loopback, link-local, private, multicast, and otherwise non-public destination addresses. Do not let a feed or redirect target reach local device/admin services.
- Set explicit connect/read timeouts, a bounded response body, and cancellation support. Do not buffer an unbounded response in memory.
- Parse XML with DTD and external entity processing disabled. Never resolve external entities or fetch linked XML resources.
- Support only RSS 2.0 and Atom v1 for v1. Ignore unsupported extensions safely; do not execute HTML, scripts, or embedded markup from descriptions.
- Accept only audio enclosure URLs with an allowlisted audio MIME type or a verified supported audio extension. Treat declared MIME and content length as untrusted hints; playback remains the final format check.
- Revalidate enclosure redirects and response type/length. Do not automatically follow a feed-provided enclosure to a local/private network destination.
- Sanitize rendered HTML descriptions to plain text or a strict safe subset; do not load remote images from descriptions.

### 4. Refresh semantics

- A refresh is user initiated, cancellable, and limited to one in-flight refresh per feed.
- Use `If-None-Match` and `If-Modified-Since` when available. A 304 keeps the current episode list and updates only refresh metadata.
- Parse into a temporary result, validate the complete response, then atomically replace the feed snapshot. A failed fetch or parse must not erase a previously valid show or episode list.
- Deduplicate by stable episode identity while preserving saved progress and played state across feed updates.
- Do not refresh every feed on app startup, on every Library tab visit, or through a periodic worker.

## Implementation sequence

Do not skip the gates or combine all steps into one large change.

1. **Lock product scope and playback design.** Record approval for the phone-only RSS/Atom MVP above. Build a no-UI playback spike for typed episode identity, direct enclosure playback, MediaItem round-trip, and music-only feature exclusions. Stop if the spike requires a broad player rewrite.
2. **Implement pure feed parsing.** Add `app/src/main/java/com/music/audify/data/podcasts/PodcastFeedParser.kt` and focused parser models. Parse RSS 2.0 and Atom with secure XML settings, strict size/count bounds, safe dates/durations, GUID/ID fallback, artwork selection, and enclosure validation. Keep parser logic independently testable.
3. **Implement the podcast store.** Add `PodcastStore.kt` and `PodcastRepository.kt` under `app/src/main/java/com/music/audify/data/podcasts/`. Use the atomic versioned file format, bounded state, serialized writes, safe corruption handling, canonical feed URL deduplication, and feed refresh validators. Initialize from `AudifyApplication` only after verifying startup ordering.
4. **Implement manual feed management.** Add add/remove/refresh operations, URL validation, duplicate detection, cancellation, and observable repository state. Do not add background workers or credential handling.
5. **Implement typed playback integration.** Complete the spike design in the canonical playback model/conversion path. Update `PlayerConnection.kt`, `PlaybackService.kt`, and `LastPlayed.kt` only as required. Preserve the podcast content type, feed/episode identity, enclosure URL, and progress through queue snapshots and restoration. Keep music code paths unchanged for ordinary `Song` items.
6. **Add the phone Library entry and show/episode screens.** Extend `LibraryPhoneParts.kt`, `MainActivity.kt`, and `AppNavigator.kt`; add dedicated podcast screen components under `app/src/main/java/com/music/audify/ui/screens/`. Keep show/episode state separate from `MainViewModel`'s music `LibraryPage` and `DetailPage` unless a narrow integration is demonstrably cleaner. Provide URL-add, show list, show detail, episode list, refresh status, and actionable empty/error states.
7. **Add player and progress behavior.** Render podcast show/episode identity on the player and system media surfaces. Save and restore position and played state using bounded checkpoints. Mark an episode played only at a documented completion threshold; seeking to the end must not falsely mark it played until playback completion is confirmed.
8. **Harden and validate.** Add parser, store, URL policy, refresh, playback round-trip, restoration, and regression tests. Test real-world public feeds only after the parser and URL safety tests pass. Verify interrupted playback, duplicate GUIDs, feed edits, 304 responses, oversized/malformed feeds, redirects, offline refresh, and unsupported enclosure formats.
9. **Device QA and handoff.** Verify on a physical Android phone: add/remove feed, refresh, queue, background playback, notification metadata, seek/resume after process death, offline error states, TalkBack, large font, and dark/light themes. Record any unverified provider/feed limitations. Update this plan's status only after acceptance criteria pass.

## Expected files

### New files

- `app/src/main/java/com/music/audify/data/podcasts/PodcastModels.kt`
- `app/src/main/java/com/music/audify/data/podcasts/PodcastFeedParser.kt`
- `app/src/main/java/com/music/audify/data/podcasts/PodcastUrlPolicy.kt`
- `app/src/main/java/com/music/audify/data/podcasts/PodcastStore.kt`
- `app/src/main/java/com/music/audify/data/podcasts/PodcastRepository.kt`
- Dedicated phone podcast screen/component files under `app/src/main/java/com/music/audify/ui/screens/`.
- Focused tests under `app/src/test/java/com/music/audify/podcasts/` and playback regression tests under the existing playback test package.

### Existing files likely to change

- `app/src/main/java/com/music/audify/AudifyApplication.kt` — repository initialization, only if required.
- `app/src/main/java/com/music/audify/MainActivity.kt` — Library entry and route/screen composition.
- `app/src/main/java/com/music/audify/ui/screens/LibraryPhoneParts.kt` — podcast Library link/shelf.
- `app/src/main/java/com/music/audify/ui/navigation/AppNavigator.kt` — dedicated show/episode routes.
- `app/src/main/res/values/sachit_strings.xml` — English UI strings only.
- `app/src/main/java/com/music/audify/playback/PlayerConnection.kt` — typed MediaItem conversion and round-trip.
- `app/src/main/java/com/music/audify/playback/PlaybackService.kt` — direct enclosure playback branch and music-feature exclusions.
- `app/src/main/java/com/music/audify/playback/LastPlayed.kt` — backwards-compatible queue snapshot fields if podcast items share the session queue.
- `app/src/test/java/com/music/audify/` — parser/store/playback tests.

This list is a starting scope, not authorization to edit every file. Reconfirm each call site during implementation and keep the diff narrow.

## Acceptance criteria

- A listener can add a valid RSS 2.0 or Atom HTTPS feed, see the show and episodes, remove the feed, and refresh it manually.
- Invalid URLs, unsafe redirects, malformed XML, oversized documents, unsupported feed versions, and unsupported enclosure formats fail safely with actionable states.
- A failed refresh preserves the last known-good feed and episode list.
- Episode identity remains stable across feed refreshes; duplicate entries do not reset progress or create duplicate queue identities.
- A podcast episode plays from its enclosure URL without entering YouTube/source resolution or music download lookup.
- Podcast metadata remains typed and correct in the player, notification, queue, and process-death restoration.
- Resume position and played state persist across app restarts. Progress writes are bounded and do not run every playback tick.
- Music-only actions are hidden, disabled with an explanation, or correctly adapted for podcast episodes; ordinary music playback and queue behavior do not regress.
- No background refresh, analytics upload, feed credential storage, Room schema change, or user-media deletion is introduced.
- Relevant unit tests pass and physical-device QA is recorded. Compilation alone is not feature completion.

## Required tests

### Parser and URL policy

- RSS 2.0 and Atom happy paths, namespace variation, missing optional fields, timezone/date parsing, duration parsing, and HTML description sanitization.
- Missing/duplicate GUIDs, changed enclosure URL, repeated episodes, and deterministic identity fallback.
- Malformed XML, DTD/XXE payloads, entity expansion, invalid encoding, truncated body, over-limit body, and over-limit episode count.
- HTTP/non-HTTPS schemes, userinfo, invalid host, private/loopback/link-local IP, IPv6 edge cases, redirect loops, too many redirects, and redirects to blocked destinations.
- Unsupported MIME/extension, MIME mismatch, absent enclosure, and multiple enclosure elements.

### Store and refresh

- Add/remove/duplicate feed, versioned serialization, unknown fields, corrupt file recovery, atomic-write interruption, and concurrent update serialization.
- 200 response, 304 response, changed ETag, transient network failure, parser failure, cancellation, and preservation of last-known-good state.
- Feed episode reorder/removal/update without losing progress for matching stable IDs.
- Hard limits for feed count, episode count, and document size.

### Playback and regression

- Episode-to-MediaItem-to-episode round-trip preserves identity and metadata.
- Direct enclosure playback bypasses music source resolution; HTTPS redirects remain subject to URL policy.
- Queue insertion, duplicate episode occurrences, play-next, removal, seek, pause/resume, completion threshold, and process-death restoration.
- Podcast episodes do not enter Automix analysis, music source upgrades, YouTube original-version actions, music download lookup, or music scrobbling.
- Existing `Song.toMediaItem`, `MediaItem.toSong`, queue, LastPlayed legacy JSON, downloads, and music playback regression tests remain green.

## Performance and resource limits

- Parse on `Dispatchers.IO`; never parse XML or perform network I/O on the main thread.
- Enforce body, redirect, feed-count, and episode-count limits before allocating large collections.
- Avoid polling and per-second persistence. Coalesce progress writes and cancel network requests when the user leaves/cancels the operation.
- Retain only the newest bounded episode window per feed; preserve the active episode and episodes with user progress.
- Keep feed artwork to one validated URL per show and use existing image loading/cache policy rather than downloading artwork into a new custom cache.

## Security and privacy review

- Feed content is untrusted input. XML parser must disable DTD/external entities; HTML descriptions must never execute.
- Feed and enclosure URLs must be validated before requests and after every redirect. Block private/local destinations to prevent a public feed from probing device or LAN services.
- Use HTTPS only for v1. Do not put credentials, auth headers, or sensitive query strings in logs.
- Do not auto-download enclosures, execute feed-provided links, load embedded scripts, or fetch arbitrary images from descriptions.
- Podcast subscriptions and progress remain on device. No analytics, telemetry, or cloud sync is added.

## Rollback

Remove the podcast Library entry, screens, repository initialization, and playback adapter. Preserve the podcast store file and any downloaded/user-owned media unless the user explicitly chooses to delete them. A rollback must not clear music queues, music history, music downloads, or existing Room data. Keep the store format versioned so a future implementation can recover user subscriptions if the feature is temporarily disabled.

## Handoff and project constraints

- Follow `plans/README.md`, `docs/PRODUCT.md`, and `docs/DESIGN.md`.
- Never change `versionCode` or `versionName`.
- No Room/database schema or migration changes without a separate explicit approval; this plan's proposed v1 uses a dedicated atomic JSON store instead.
- English Android strings only in `app/src/main/res/values/sachit_strings.xml`; no translated string edits.
- No README/release-note edits, commits, pushes, tags, or releases as part of this plan.
- Preserve all unrelated working-tree changes. Inspect `git status` and `git diff` before every implementation batch.
- Build with JDK 21 and test the exact Android variant/module affected. Physical-device playback validation is mandatory.

## Approval gate

The product owner approved the recommended **Android-phone-only, user-added RSS/Atom, manual-refresh, local-only** scope on 2026-11-05. The typed playback gate did not pass: the current playback service assumes `Song` throughout queue conversion, source resolution, transition handling, party synchronization, and `LastPlayed` restoration. Passing this gate requires implementing and verifying the bounded typed playable-media architecture in [Plan 15](15-typed-playable-media.md). Do not represent an episode with a fabricated song ID, route its enclosure through YouTube resolution, or start an unbounded player rewrite under this plan.
