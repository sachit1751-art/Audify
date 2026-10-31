# Audify improvement plans

This folder is the **execution track**: one self-contained plan file per feature, written so an
executor with zero session context can implement it. Every plan states its goal, the existing
code it builds on (with line-level recon), exact in-scope files, step-by-step implementation,
testing requirements, performance notes, rollback strategy, and rejected alternatives.

**Completed plans are deleted after execution** — they are archived (summary + implementation
commit) in the table at the bottom of this file, and their full text stays in git history.

Related reference docs (context only — not executed):

| Doc | Location | What it is |
|-----|----------|------------|
| `BITCHORD_AUDIFY_FEATURE_AUDIT.md` | repo root | ecosystem audit |
| `COMPETITOR_FEATURE_MATRIX.md` | repo root | feature selection + sources |
| `AUDIFY_ROADMAP.md` | repo root | multi-phase ordering |
| `AUDIFY_INNOVATION_IDEAS.md` | `plans/` | original feature concepts |
| `AUDIFY_DESIGN_SYSTEM.md` | `plans/` | UI rules |
| `ARCHITECTURE_AUDIT.md` | `plans/` | deepening candidates C1–C7 (see plan 022) |
| `CONTEXT.md` | repo root | domain glossary — read this before touching unfamiliar code |
| `docs/adr/` | repo root | recorded architecture decisions; check before re-litigating one |

A local read-only checkout of the reference project lives at `reference-player-sdk/`
(gitignored).

Feature-planning track started **2026-09-26**. Before executing a plan, run
`git rev-parse --short HEAD`; if the tip has moved past the commit named in the plan's header,
check whether the plan's in-scope files changed (each plan lists them) and report drift instead
of blindly applying.

Scope rules that apply to **every** plan (from the repo's AGENTS.md and project rules):

- Never bump the app version (`versionCode` / `versionName` in `app/build.gradle.kts`).
- Never touch the database schema or Room migrations.
- String edits go only in `app/src/main/res/values/sachit_strings.xml` (English). Never
  edit translated `sachit_strings.xml` / `strings.xml` files in `values-*`.
- No commits, pushes, or merges unless a human explicitly authorized them per-plan.
- No changes to README/markdown files except inside `plans/` and the top-level planning docs (audit, matrix, ideas, design system, roadmap).
- Build/verify with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-*` is auto-provisioned if no
  standalone JDK 21 is installed):

  ```bash
  ./gradlew :app:compileFossDebugKotlin --console=plain
  ./gradlew :app:testFossDebugUnitTest --console=plain   # app JVM/Robolectric tests
  ./gradlew :app:assembleFossDebug --console=plain
  ```

## Active plans

Round 3 (019, 020, 021) shipped in `1ef56d88`. Round 3 is closed.

### Phase 2 — Core UX (next up)

Plans written, not started:

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 025 | Local file library beside YTM | Library | H | none | none | **planned — not started** |
| 026 | Tablet / two-pane player | UI | M | none | none | **planned — not started** |
| 027 | Landscape fullscreen lyrics | Lyrics/UI | M | none | none | **verified on Android emulator** |
| 028 | Week-shape recap card | Discovery | M | none | none | **planned — not started** |
| 029 | Time-aware discovery shelf | Discovery/UI | M | none | none | **planned — not started** |
| 030 | Lyrics share-as-image | Social/UI | L | none | none | **planned — not started** |

### Phase 3 — Power user (plans written, not started)

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 031 | Named queue slots with resume | Queue | H | none | none | **planned — not started** |
| 032 | 10-band EQ + AutoEq profiles | Audio/EQ | M | none | none | **planned — not started** |
| 033 | ListenBrainz scrobbling | Social/Integrations | M | none | none | **planned — not started** |
| 034 | SponsorBlock integration | Video/Skip | M | none | **category list + API check at impl time** | **planned — not started** |
| 035 | Download quality upgrade + storage budget | Downloads | M | none | none | **planned — not started** |
| 036 | Tag editor for downloads | Downloads/UI | M | none | none | **planned — not started** |
| 037 | Stats export (CSV) | Stats/Portability | L | none | none | **planned — not started** |
| 038 | Playlist import expansion (Spotify-converted) | Import | M | none | none | **planned — not started** |

### Phase 4 — Trust, privacy, ecosystem (plans written, not started)

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 039 | Offline truthfulness badges | UX/Honesty | M | none | none | **planned — not started** |
| 040 | Library hygiene tools | Library/UX | M | none | none | **planned — not started** |
| 041 | Queue handoff (SharePlay / nearby) | Social/Queue | H | 031 (queue slots) | none | **planned — not started** |
| 042 | "Ask Audify" conversational queue | UX/AI-lite | L | none | none | **planned — not started** |
| 043 | Privacy ledger | Privacy/UX | L | none | none | **planned — not started** |
| 044 | Artist new-release notifications | Discovery/Notifications | M | none | none | **planned — not started** |

### Phase 5 — Accessibility, performance, reach (plans written, not started)

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 045 | Accessibility-first lyrics | Accessibility/Lyrics | H | none | none | **planned — not started** |
| 046 | MotionTokens cleanup | Animation/Behind-the-scenes | M | none | none | **planned — not started** |
| 047 | Performance pass | Performance/General | M | none | none | **planned — not started** |
| 048 | Preferred audio language | Audio/UX | L | none | none | **planned — not started** |
| 049 | TV intents + WebDAV source | Reach/Import | M | none | none | **planned — not started** |
| 050 | Backup auto-schedule + integrity | Backup/Portability | M | none | none | **planned — not started** |

No active plans beyond Phase 5. See completed table below.

## Considered and rejected for this round

- **Lyrics translation on-tap** — already shipped (`LyricsTranslationHelper` + DeepL in
  `ui/menu/LyricsMenu.kt`).
- **Volume-normalization settings** — already shipped end to end: `AudioNormalizationKey`,
  `LoudnessLevelKey` (QUIET/BALANCED/LOUD/AGGRESSIVE) with settings UI in
  `PlayerSettings.kt` and a per-track info display in `ShowMediaInfo.kt`. Nothing left to plan.
- **Playlist folders** — needs a DB schema change (AGENTS.md forbids); revisit if the core
  team approves a migration.
- **Wear OS companion** — high value but a multi-week effort; deserves its own plan round.
- **Wear/Android Auto/external-display audits** — Auto is already covered
  (`MediaLibrarySessionCallback` + settings); nothing actionable without hardware QA.

## Completed and archived

Plans are removed from `plans/` once fully implemented and verified. Record kept here; the
full plan text is in git history (delete commits reference these hashes).

| # | Plan | Implemented | Implementation |
|---|------|-------------|----------------|
| 009 | Smart auto-playlists (no schema change) | 2026-09-27 | commit `41420e57` |
| 010 | One-tap song radio from the player | 2026-09-26 | commit `2cc93084` |
| 011 | Quick-settings tiles (shuffle all) | 2026-09-26 | commit `5c7e60a5` |
| 012 | Monthly mini-Wrapped card on Home | 2026-09-26 | commit `a01085da` |
| 013 | Synced lyric line in the mini player | 2026-09-27 | commit `fbdfd244` |
| 014 | "Play next" from search results | 2026-09-26 | parity verified, no-op: search rows open `YouTubeSongMenu`/`YouTubeAlbumMenu` which already ship Play next + Add to queue, guest-gated, behind a visible trailing button |
| 015 | Monthly & yearly recap (Wrapped with range) | 2026-09-29 | commit `adaad9f5` (`wrapped/{range}` route, range chips on intro, empty state, Stats recap entry; verified on emulator with real DB data) |
| 016 | Lossless / highest-quality audio option | 2026-09-29 | commit `adaad9f5`; fork extended with `AudioQuality.LOSSLESS` + FLAC/ALAC-preferred selection (sachit1751-art/innertubex `0.5.2-lossless.3` via JitPack); app wiring, settings UI, honest ShowMediaInfo badge. Real FLAC still requires a premium-serving client/account; graceful fallback verified on emulator |
| 017 | Artwork-mesh player backdrop (smooth animated background) | 2026-10-07 | commit `d43dd550`: `ui/player/PlayerBackdrop.kt` (`ArtworkMesh`, `rememberArtworkMesh`, `ArtworkMeshBackdrop`, `MESH_GRID`), `constants/PreferenceKeys.kt` (`PlayerBackdropKey`), `ui/player/Player.kt` wiring with `rememberEnumPreference` toggle, settings UI in `PlayerSettings.kt`, `DevicePerformance` heap-aware tuning. Unit tests in `DevicePerformanceTest.kt`. Verified on emulator. |
| 018 | Per-network audio quality ceilings | 2026-09-30 | commit `4a54b391`: `playback/NetworkQuality.kt` pure resolver + `NetworkClass` mapping, `WifiAudioQualityKey`/`MeteredAudioQualityKey` (unset = global fallback, `VERY_HIGH` legacy alias kept), MusicService factory wiring with resolved-quality log, "Audio quality per network" settings group with "Same as general setting" unset option, `NetworkQualityTest` (6 tests). Optional network-flip cache invalidation skipped — `NetworkConnectivityObserver` has no metered awareness; ceilings apply at next stream resolution. Builds + tests green. Emulator-verified 2026-10-01: `FETCHING STREAM` resolves the global key with network keys unset (zero change), then `HIGH` on metered / `LOW` on Wi-Fi with per-network keys set (both directions via `svc wifi/data` flips); no mid-song stream reload on network flip. Preview screenshots abandoned (emulator instability/ANR); APK shipped on the GitHub release instead. |
| 019 | "Signal" in-player diagnostics sheet | 2026-10-02 | commit `c810e1ce`: `ui/player/PlaybackStatsSheet.kt` plus 19 `playback_stats_*` strings. Live codec, sample rate, format bitrate, stream client, buffered-vs-played, network class, loudness offset and source health; 1 Hz ticker only while open. Opened from `PlayerMenu.kt`. Verified on emulator: settings and player screens render, sheet opens without error |
| 020 | Queue skip marks + "Earlier" boundary | 2026-10-02 | commit `c810e1ce`: `playback/QueueHistoryState.kt` (41 lines, pure, session-scoped) + `QueueHistoryStateTest`. `MusicService.onMediaItemTransition` marks rows jumped over by a forward `REASON_SEEK` of more than one index; natural advance, repeat-one rewind, backward and adjacent seeks are deliberately excluded. `queue_earlier` string; `Queue.kt` renders dimmed rows and the boundary. State clears when a new queue starts, not on restore |
| 021 | Stream source health + fresh-resolution fallback | 2026-10-02 | commit `c810e1ce`: `playback/StreamSourceHealth.kt` per-mediaId failure memory with injectable clock, TTL sweep and a 2-strike `FALLBACK` threshold + `StreamSourceHealthTest`. `MusicService.recordStreamSourceFailure` feeds it; a `FALLBACK` state bypasses the stream-URL cache so a poisoned URL/client pair is not retried. Surfaced live in the Signal sheet (019) via 4 `playback_stats_source_health_*` strings |
| 022 | Deepen the architecture (C5 settings → C1 MusicService → C2 transports) | 2026-10-08 | commit `ee8c0aa3`: `playback/PlaybackTarget.kt` (interface + `QueueTarget`/`MediaSourceTarget` adapters, collapsing duplicated transport branches), `playback/PlaybackIntent.kt` (sealed class: `Play`/`EnqueueNext`/`Enqueue`/`Replace`/`ClearAutomix`/`StartRadio`), `playback/StreamResolver.kt` (extracted stream resolution). Step 1 (Settings interface) shipped earlier in `eadfeac3` (`utils/Settings.kt`). All three steps complete. |

## Verification state (2026-10-16)

`./gradlew :app:testFossDebugUnitTest` → **269 tests, 0 failures.** All three signed release
APKs build and pass `apksigner`, and the FOSS release APK was installed and smoke-tested on an
Android 15 (API 35) emulator.

**Known gaps that a green build does not cover:**

- **Crossfade has never been listened to.** `CrossfadeControllerTest` covers the trigger rules,
  but whether the audible overlap is right needs a human on real hardware. Treated as a release
  blocker for promoting a prerelease to a full release.
- **Listen Together has no coverage of the glue.** `ListenTogetherSyncTest` covers the pure sync
  logic (queue de-duplication, upcoming-window bounds, seek tolerance, codec contract). The
  WebSocket client, the 2,069-line `ListenTogetherManager` event loop, and guest reconciliation
  still need a live room with two real clients to verify.
- **The Stats screen has not been reworked** despite being flagged as poor UX. Tracked as
  follow-up work, not yet planned.

## How to update status

When you start a plan: mark it `in-progress`. When done criteria all pass: `done`, move the
plan's summary into the archive table above, and delete the plan file. If an escape hatch
fired: `blocked` and note why in the plan file. Executors update this file.

Before starting any plan, read `CONTEXT.md` for the domain vocabulary and scan `docs/adr/` —
several decisions there look like mistakes but are deliberate, and re-proposing them wastes a
session.
