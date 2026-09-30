# Plan 019 — "Signal" in-player diagnostics sheet

Written against commit **`605975d5`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`605975d5`, check drift in the in-scope files first.

## Goal

A one-tap **live playback diagnostics sheet** on the player — Audify's take on "stats for
nerds": active codec, sample rate, bitrate, stream client, buffered-vs-played position,
network class, loudness offset, and (after plan 021 lands) source health. Everything shown
already flows through existing StateFlows; the sheet adds **zero** background work when
closed (its 1 Hz ticker lives only while open).

## Existing infrastructure (recon at `605975d5`)

- `ui/utils/ShowMediaInfo.kt`: existing *static* metadata dialog (itag, mime + lossless
  badge, codecs, bitrate, sample rate, LUFS vs target, volume, file size, stream client).
  It mixes `YouTube.getMediaInfo` (network!) with DB format lookups. **The Signal sheet is a
  different thing: live, local-only, cheap.** Do not merge them; ShowMediaInfo stays.
- `PlayerConnection.kt`: `currentStreamClient` (line 193, from service),
  `currentFormat` (flatMapLatest DB flow, line ~177), `mediaMetadata`, plus the attached
  `player` (Media3 `Player`) for live position/bufferedPercentage/audioFormat.
- Live audio facts: Media3 `player.audioFormat` (`AudioFormat`: encoding/sampleRate/
  channelMask/frameSizeInBytes — available when the renderer is initialized). Bitrate comes
  from `currentFormat.bitrate` (DB) — honest label: "format bitrate".
- Loudness: `FormatEntity.perceptualLoudnessDb ?: loudnessDb` + `LoudnessLevelKey` target —
  `ShowMediaInfo.kt:63` (`getLoudnessLevelLabel`) and its `measuredLufs` math are the pattern
  to reuse (extract or duplicate the tiny delta formula).
- Network class: plan 018 introduces `ConnectivityManager.networkClass()` — if 018 is not yet
  implemented, inline a minimal `isActiveNetworkMetered` read behind the same function name so
  both plans converge.
- Sheets: `LocalBottomSheetPageState` + `bottomSheetPageState.show { }` is the app's modal
  bottom-sheet pattern (used throughout `Player.kt`, e.g. lines 1245/1404/1966/2341).
- Entry point: player menu — `ui/menu/PlayerMenu.kt` (where "Start radio", speed dialog,
  etc. live) — one row "Signal" opening the sheet. Alternative: long-press on the time/progress
  area; menu row is simpler and discoverable.

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/ui/player/PlaybackStatsSheet.kt` | **New.** The sheet composable |
| `app/src/main/kotlin/com/sachit/music/ui/menu/PlayerMenu.kt` | "Signal" entry row |
| `app/src/main/kotlin/com/sachit/music/ui/utils/ShowMediaInfo.kt` | Optional: extract `measuredLufs` delta into a shared tiny helper (only if trivially extractable — otherwise duplicate the 3-line formula and note it) |
| `app/src/main/kotlin/com/sachit/music/utils/DevicePerformance.kt` or `NetworkQuality.kt` | Home for `networkClass()` if not already present from plan 018 |
| `app/src/main/res/values/sachit_strings.xml` | Strings (names below) |

Out of scope: `MusicService.kt` (nothing new needed — all data exists), ShowMediaInfo
redesign, lyrics surfaces, `values-*` files.

## Design

Audify-native presentation, deliberately not BitChord's overlay: a modal bottom sheet titled
**"Signal"** with grouped key/value rows in the app's card style (`Material3SettingsGroup`
works for this — read-only rows without icons). Two groups:

- **Stream** — codec (from `currentFormat.mimeType`/`codecs`), sample rate, format bitrate,
  stream client, loudness offset (`+X.X dB vs target`).
- **Live** — played/buffered (`mm:ss of mm:ss · 43% buffered`), network class, rendering
  sample rate from `player.audioFormat` when available (hidden when null rather than "unknown"
  — a missing value is information).

Ticker: `LaunchedEffect` + `while (isActive) { delay(1000) }` refreshing only the Live group —
**only while the sheet is visible**; cancelled on dismiss. No collectors added anywhere else.

## Implementation steps

1. **Drift check**: confirm `bottomSheetPageState.show {}` content contract (what composable
   types it accepts — read one existing call site), confirm `player.audioFormat` accessibility
   from the UI layer via `playerConnection.player`.
2. **`PlaybackStatsSheet.kt`**: build the two groups; all values from
   `LocalPlayerConnection.current` flows + a single position ticker state. Number formatting:
   reuse `Formatter.formatShortFileSize` pattern from ShowMediaInfo for anything byte-ish;
   `String.format(locale)` for dB — respect `LocalLocale.current.platformLocale` as
   ShowMediaInfo does.
3. **Menu row**: add "Signal" row in `PlayerMenu.kt` with the `bottomSheetPageState.show {
   PlaybackStatsSheet(...) }` call; pass `mediaId` + `playerConnection`.
4. **Network class**: use plan 018's `networkClass()` if present; otherwise add the minimal
   version (with a comment pointing at plan 018).
5. **Strings** (exact names): `playback_stats`, `playback_stats_desc`, `playback_stats_codec`,
   `playback_stats_sample_rate`, `playback_stats_bitrate`, `playback_stats_stream_client`,
   `playback_stats_buffered` (`Buffered up to %1$s of %2$s`), `playback_stats_network`,
   `playback_stats_network_wifi`, `playback_stats_network_metered`, `playback_stats_network_offline`,
   `playback_stats_loudness`, `playback_stats_render_rate`.
6. **Build + manual pass**: `./gradlew :app:assembleFossDebug --console=plain`; open sheet in
   player, verify values against ShowMediaInfo for the same track; close sheet and confirm no
   ongoing work (ticker cancellation — Android Studio profiler or `dumpsys` gfxinfo spot check).

## Testing

- **Unit**: none required (pure display; the only logic is formatting + the loudness delta —
  if that formula is extracted as a shared helper, unit-test it there).
- **Manual**: sheet opens < 300 ms; values match ShowMediaInfo; ticker stops on dismiss
  (profiler: no repeated GC/frame activity after close); airplane mode shows Offline; lossless
  badge case renders the same way ShowMediaInfo does; landscape + font-scale 2× legibility.

## Performance notes

Zero cost when closed (no flow collection, no ticker). While open: one 1 Hz coroutine and
existing StateFlow reads — all main-thread-safe reads of primitives. No network calls, ever
(that's what ShowMediaInfo is for).

## Rollback

Delete `PlaybackStatsSheet.kt` + the menu row + strings. Nothing else touched.

## Anti-clone / visual identity

BitChord puts stats-for-nerds inline on its player; Audify uses its standard modal sheet from
its own menu, app-styled rows, and the Audify name "Signal". No overlay on the player surface.

## Considered and rejected for this plan

- **Merging with ShowMediaInfo** — different data models (live vs static), different cost
  profiles; merging would force network calls into a "live" surface or make the metadata dialog
  lazy. Keep both.
- **Buffer-health graph/sparkline** — pretty, but a second composition loop; re-visit if the
  sheet earns its keep.
