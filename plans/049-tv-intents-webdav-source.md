# Plan 049 — TV intents + WebDAV source

**Status: not started.** Verified at commit `61778ab4` — no TV intent handling and no WebDAV remote source 
exist. The plan is two separate small features under one number: (1) lean-back TV intent handling (a lean-back 
launcher intent routes into Audify's player), (2) a WebDAV remote source (HTTP-based remote file browsing / 
streaming — SMB deferred by design per the roadmap).

## Objective

Two features: (1) TV intents — when Audify is the default lean-back launcher or receives a lean-back intent 
(e.g. from a TV launcher / voice), open the player in a lean-back layout and play the requested content. (2) A 
WebDAV remote source — browse and stream audio files from a WebDAV server (HTTP-based), as a remote source 
alongside YouTube Music. SMB is deferred (per the roadmap: "SMB deferred by design").

## Why

- Matrix #17 (TV intents) + #23 (WebDAV) — TV intent handling is a lean-back feature; WebDAV is a remote 
source that complements local files (plan 025) without the complexity of SMB.
- TV intent handling is a small, focused feature — route a lean-back intent to the player. Low cost, clear 
value for TV / lean-back users.
- WebDAV is a remote source the user controls (their own server) — no third-party service. It complements local 
files (plan 025) and YouTube Music as a third content source. SMB is deferred (BitChord's SMB issues are the 
reason — the roadmap explicitly says SMB deferred by design).

## What exists today

- MediaSession / MediaLibrarySessionCallback exists (the executor checks at implementation time — TV intent 
handling may partially exist via the existing session; the plan adds explicit lean-back intent routing).
- A lean-back layout may partially exist (tablet two-pane is plan 026 — the executor checks at implementation 
time whether a lean-back layout is needed for TV or if the tablet layout covers it). The plan is for TV intent 
routing, not necessarily a full TV UI.
- No WebDAV client, no WebDAV browse/stream UI, no WebDAV source in the library.

## Scope

**In scope — TV intents:**
- Handle lean-back TV intents: the executor checks at implementation time what intents the Android TV / lean-back 
ecosystem uses (e.g. `ACTION_MAIN` with `CATEGORY_LEANBACK_LAUNCHER`, `ACTION_VIEW` with a media intent, 
 MediaSession-based playback from a TV remote). Route the intent to the player — open the player in a lean-back 
 layout and play the requested content (if a content ID is in the intent) or show the player / home (if no 
 content ID).
- A lean-back layout (if needed): the executor checks at implementation time whether the tablet two-pane layout 
 (plan 026) covers TV or if a TV-specific lean-back layout is needed. If a TV layout is needed, it's a 
 focused lean-back UI (big artwork, transport controls, queue) — not a full app UI. If the tablet layout 
 suffices, route TV intents to it.
- Default lean-back launcher handling (optional, v1 may be just intent routing — the executor decides at 
implementation time whether to support `CATEGORY_LEANBACK_LAUNCHER` default handling).

**In scope — WebDAV source:**
- A WebDAV client: browse a WebDAV server's directory tree, list audio files, stream a file. HTTP-based 
WebDAV (the executor checks at implementation time which WebDAV API / library to use — e.g. an Apache 
WebDAV-compatible client, or a simple HTTP file tree if the server is simple).
- A WebDAV source in the library: a new source tab / section ("WebDAV") where the user enters server details 
(host, port, path, username, password) and browses / streams audio files. The files play through the existing 
playback path (as a stream / temporary file).
- Auth: basic auth (username/password) for v1. The executor checks at implementation time if more auth methods 
are needed (digest, etc.) — basic is the common starting point.
- No SMB — SMB is explicitly deferred per the roadmap (BitChord's SMB guest-login bug cluster is the reason).

**Out of scope — TV intents:**
- A full TV app / TV-specific UI beyond a lean-back player — defer; v1 is lean-back intent routing + a lean-back 
player layout (if needed). Don't build a full TV app.
- Voice / Google Assistant integration — defer; v1 is intent routing, not Assistant integration.

**Out of scope — WebDAV:**
- SMB / NAS / FTP / other remote source protocols — defer; this plan is WebDAV only (per the roadmap: SMB 
deferred by design). Other protocols can be added later.
- Auto-discovery of WebDAV servers (Bonjour / SSDP etc.) — defer; v1 is manual server entry.
- WebDAV write / upload / rename / delete — defer; v1 is browse + stream (read-only for v1).
- Per-file artwork for WebDAV files — defer; v1 is stream playback, artwork may be missing (same as local 
files in plan 025).

## Affected files

| File | Change |
|------|--------|
| `ui/screens/TVScreen.kt` (new, or extend existing lean-back / player layout) | Lean-back player layout for TV intent routing (if needed). The executor decides at implementation time whether a TV-specific layout is needed or the tablet layout suffices. |
| `ui/player/` (player composables) | May need a lean-back variant (bigger controls, queue) for TV — the executor decides at implementation time. |
| `playback/MediaSession`/`MediaLibrarySessionCallback` (check at implementation time) | May need tweaks for TV intent routing / MediaSession-based playback from a TV remote. The executor checks at implementation time what's needed. |
| `AndroidManifest.xml` | TV intent filters (e.g. `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`, media intent filters) — at implementation time. |
| `utils/WebDAVClient.kt` (new) | WebDAV HTTP client: browse directory tree, list audio files, stream a file. Basic auth. The executor picks the WebDAV library / HTTP approach at implementation time. |
| `utils/WebDAVSource.kt` (new) | WebDAV source integration: browse → stream path → playback. A "WebDAV source" in the library, alongside local files (plan 025) and YouTube Music. |
| `ui/screens/library/WebDAVSourceScreen.kt` (new) | WebDAV source UI: server config (host/port/path/user/password), browse tree, file list, play. A new source tab/section in the library. |
| `constants/PreferenceKeys.kt` | WebDAV server config preferences (host, port, path, user, password — stored securely; the executor checks at implementation time how to store credentials — encrypted preferences? the plan leaves the storage method to the executor, but credentials shouldn't be plain text if avoidable). |
| `sachit_strings.xml` | TV intent labels, WebDAV labels, server config fields, browse labels, error strings. |

## Technical approach

**TV intents:**
1. **Intent filters** — At implementation time, add the right intent filters to `AndroidManifest.xml` for lean-back 
launch (the executor checks what the Android TV / lean-back ecosystem expects). A lean-back launcher intent 
routes to Audify's player.
2. **Lean-back routing** — On a lean-back intent, route to the player. If the intent carries a content ID / 
media ID, play it. If not, show the player / home in a lean-back layout. The executor checks at implementation 
time whether a TV-specific layout is needed or the tablet layout (plan 026) suffices.
3. **MediaSession** — The executor checks at implementation time whether the existing MediaSession / 
MediaLibrarySessionCallback handles TV remote playback (play/pause/seek/next/prev) via the session. If so, 
TV intent routing may just be about opening the right screen + the session handles transport. If not, add the 
missing session transport handling.
4. **Lean-back layout (if needed)** — If the tablet layout doesn't cover TV, a lean-back player layout: big 
artwork, transport controls, queue. Focused on remote control use. The executor designs it at implementation 
time — it's a lean-back player, not a full app UI.

**WebDAV source:**
1. **WebDAV client** — A WebDAV HTTP client. The executor picks the library / approach at implementation time 
(e.g. a Java/Kotlin WebDAV client library, or a simple HTTP client that speaks WebDAV PROPFIND / GET). 
Browse: PROPFIND a directory → list files/folders. Stream: GET a file → stream it to the player.
2. **Server config** — The user enters WebDAV server details: host, port, base path, username, password. Stored 
securely (the executor checks at implementation time how — encrypted preferences? a credentials store? the plan 
says credentials shouldn't be plain text if avoidable). One server config for v1 (the user can reconfigure, but 
v1 is one server — multi-server is defer).
3. **Browse + stream** — The WebDAV source screen shows the server's directory tree (browsable folders) and audio 
files. Tap an audio file → stream it through the existing playback path (as a stream / temp file). The playback 
path may need a stream URI from the WebDAV client — the executor implements the stream Uri at implementation time.
4. **Library integration** — The WebDAV source is a content source alongside YouTube Music and local files (plan 
025). The executor decides at implementation time how it shows up in the library (a "WebDAV" tab/section, or a 
source within a tab). It plays through the same player — the same transport path.
5. **Auth** — Basic auth for v1. The executor checks at implementation time if the server needs digest or other 
auth; if so, add it. Basic is the common starting point.

## Testing

- **TV intents**: build + manual on an Android TV emulator / device if available. Verify a lean-back intent 
opens Audify's player. If no TV hardware, at least verify the intent filter + routing in a phone emulator 
(simulate the intent). The executor tests at implementation time.
- **WebDAV**: build + manual with a real or mock WebDAV server. Verify: server config saves, browse shows the 
directory tree, audio files list, tap to play streams. Test basic auth. Test error handling (bad server, bad 
credentials, network error). Verify playback works for streamed files. The executor tests at implementation 
time with a real or mock server.
- **Build**: `./gradlew :app:assembleFossDebug` must pass.
- **Regression**: existing playback, existing library sources (YT Music, local files) unchanged. TV intent 
routing doesn't break phone/normal app use. WebDAV doesn't break other sources.

## Performance notes

- **TV intents**: negligible — intent routing is cheap.
- **WebDAV**: browse is a PROPFIND (cheap for small trees, the executor paginates if a tree is large). Stream 
is an HTTP GET — the existing stream handling applies. No new perf concern beyond the network stream cost, which 
is the same as other stream sources.

## Rollback

- **TV intents**: remove the intent filters + routing + lean-back layout → no TV intent handling, the app still 
works on phone/tablet as before.
- **WebDAV**: remove the WebDAV client, source screen, config prefs, and stream path → no WebDAV source, YT 
Music + local files unchanged. Zero impact on other sources.

## Anti-BitChord check

- **TV intents**: a lean-back intent routing is a standard Android feature — implement as Audify: route to the 
player, reuse the existing session/player. Don't copy a competitor's TV app — this is lean-back routing, not a 
full TV app.
- **WebDAV**: a user-controlled remote source — implement as Audify: a WebDAV source alongside YT Music and local 
files, browse + stream, basic auth. Don't copy BitChord's SMB approach (SMB is deferred); WebDAV is the HTTP-
based alternative. Use Audify's existing playback path for streaming.
