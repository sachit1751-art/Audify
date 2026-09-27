# Plan 016 — Lossless / highest-quality audio option

Written against the repo state after `41420e57`. Self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` first; re-verify the seams below if the
tip moved.

## Goal

Give users a real "highest quality" path: a new `LOSSLESS` audio-quality setting that, when
YouTube Music serves it for the account/song, picks the lossless stream end to end
(streaming, download, and a visible indicator), and **gracefully falls back to the best
available lossy stream** when it isn't. Must work properly — verified with real playback,
not just a settings row.

## Existing infrastructure (recon, verified at 41420e57)

- **Quality setting exists**: `constants/PreferenceKeys.kt:118` —
  `AudioQualityKey = stringPreferencesKey("audioQuality")`, `enum AudioQuality { AUTO,
  LOW, HIGH }` (line 120). Settings UI in `ui/screens/settings/PlayerSettings.kt`
  (~lines 258/307 map quality → `audio_quality_*` strings, `EnumDialog` style).
- **Quality is resolved at stream-fetch time**: `playback/MusicService.kt` ~line 3738
  reads the pref and calls
  `InnerTubeXPlayer.playerResponseForPlayback(mediaId, audioQuality, connectivityManager,
  contentHints, allowBoundedRange)` (`utils/InnerTubeXPlayer.kt:55`). The mapper
  `AudioQuality.toInnerTubeX(connectivityManager)` is at `InnerTubeXPlayer.kt:261-269`
  (HIGH→HIGH, LOW→LOW, AUTO→LOW on metered else AUTO).
- The innertube layer is the **JitPack fork `com.github.sachit1751-art:innertubex`**
  (`settings.gradle.kts:24-28`; import alias
  `com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality` at
  `InnerTubeXPlayer.kt:14`). Its `AudioQuality` enum and `ExtractedStream` (has
  `mimeType`, `codecs`, `bitrate`, `sampleRate`) decide what's actually fetchable.
- **Cache invalidation for quality change already exists**: MusicService logs
  "BYPASSING CACHE for $mediaId due to quality change" (line ~3730) — a cache-generation
  mechanism (`songUrlCache.generation(mediaId)`) is keyed on quality. Reuse it.
- `FormatEntity` (db/entities/FormatEntity.kt) already stores `bitrate`, `sampleRate`,
  `codecs`, `mimeType` — enough to detect and display lossless.
- DownloadUtil and AudioExporter also consume `AudioQuality` — keep them consistent.

## What "lossless" means here (reality check for the executor)

YouTube Music's lossless streams are only exposed to **premium accounts** via specific
player clients (e.g. the `IOS`/`ANDROID_VR`-style clients with `contentCheckOk` +
premium auth) and appear as **FLAC / ALAC** in `audioStreams` (mimeType
`audio/flac`, codec `flac`, bitrate ≈ 700–900 kbps+, sampleRate 44.1/48k). If the
account is not premium or the client can't see them, the API simply returns the best
opus/m4a stream — that is the fallback path, not an error. DO NOT hard-fail when
lossless is absent; degrade and report quality honestly in the UI.

## In-scope files

| File | Change |
|---|---|
| `constants/PreferenceKeys.kt` | Add `LOSSLESS` to `enum AudioQuality` (append at the end — ordinal stability) |
| `utils/InnerTubeXPlayer.kt` | Map `LOSSLESS` → the innertube lossless-capable request; add best-stream picker that prefers FLAC/ALAC, then falls back to highest-bitrate opus/m4a |
| `innertubex` (JitPack fork) | **Only if needed**: expose lossless clients/streams. If the fork already surfaces FLAC/ALAC in `audioStreams`, no fork change is required — check first, note in report |
| `playback/MusicService.kt` | Include the new enum in the cache-generation key; no other logic changes |
| `ui/screens/settings/PlayerSettings.kt` | Add the "Lossless" option to the quality dialog + a summary line explaining premium requirement |
| `ui/player/ShowMediaInfo.kt` | Already shows per-track info (format/bitrate/loudness) — lossless streams will show up here; verify, extend only if the label says "Opus" for FLAC |
| `res/values/sachit_strings.xml` | Strings below |
| `app/src/test/kotlin/...` | Unit test for the stream-picker pure function |

Out of scope: DB schema changes (FORBIDDEN — `FormatEntity` already stores everything
needed), version bump, `local.properties`, download format conversion.

## Implementation steps

1. **Inspect the fork first**: `git grep -n "flac\|ALAC\|LOSSLESS"` inside the resolved
   innertubex sources (Gradle cache: `~/.gradle/caches/modules-2/files-2.1/`, or the
   fork repo `github.com/sachit1751-art/innertubex`). Determine whether lossless
   streams already flow through `audioStreams` when the account/client supports them.
   - If yes → app-only change. If no → extend the fork (new client request with the
     lossless-capable client + `AudioQuality.LOSSLESS` in its enum), publish to
     JitPack, bump the dependency. Note the fork commit in the report.
2. **Enum + strings**: add `LOSSLESS` to `AudioQuality`. Strings:
   `audio_quality_lossless` ("Lossless"), `audio_quality_lossless_desc` ("FLAC/ALAC up
   to 24-bit when available — requires YouTube Music Premium; falls back to the
   highest bitrate otherwise"), `lossless_badge` ("Lossless").
3. **Stream picker (pure, unit-testable)**: in `InnerTubeXPlayer`, add
   `fun pickBestStream(streams: List<ExtractedStream>, wantLossless: Boolean):
   ExtractedStream?` — sort key: (mimeType contains flac/alac) DESC, bitrate DESC;
   when `wantLossless == false` or no lossless candidate, return highest-bitrate
   lossy. Unit test with fake streams (flac 44.1k vs opus 256k vs m4a 128k etc.) in
   `app/src/test/kotlin/com/sachit/music/utils/` following `DevicePerformanceTest` style
   (plain JUnit, no Robolectric).
4. **Mapper**: extend `AudioQuality.toInnerTubeX` with
   `AudioQuality.LOSSLESS -> InnerTubeXAudioQuality.LOSSLESS` (or the fork's
   equivalent). Keep AUTO/LOW/HIGH behavior byte-identical.
5. **Cache generation**: find where the quality value feeds
   `songUrlCache.generation(...)` in MusicService (search "quality change") and make
   sure the new enum value participates — otherwise users switching to/from LOSSLESS
   get stale cached streams.
6. **Settings UI**: add the option to the existing quality `EnumDialog` in
   PlayerSettings; when selected, show `audio_quality_lossless_desc` as the summary so
   the premium requirement is explicit.
7. **Honest indicator**: on `ShowMediaInfo.kt`, when the active format's mimeType is
   flac/alac, show the `lossless_badge` string. No badge for lossy — no lying.
8. **Verify it works** (all of them):
   ```bash
   export JAVA_HOME="C:/Users/HP/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
   ./gradlew :app:testFossDebugUnitTest --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Emulator pass (AVD `audify_shots`): set quality to LOSSLESS → play a popular song →
   `adb shell dumpsys media_session` shows playback; open ShowMediaInfo and record the
   reported codec/bitrate; switch to HIGH, restart the track, confirm the codec
   changes back (proves the generation/cache path). Repeat once with airplane-mode
   toggle to confirm no crash on fetch failure. If the signed-in account is not
   premium, document the observed fallback (this is expected, not a failure).
9. **Download consistency**: play/download one song in LOSSLESS via DownloadUtil and
   verify the downloaded file's codec (dumpsys or `adb shell ls -la` size sanity vs
   lossy). If DownloadUtil's quality mapping needs the same one-line enum arm, add it.

## Edge cases

- Non-premium account: silent fallback to best lossy + honest ShowMediaInfo (no badge).
- Metered network: LOSSLESS still obeys the user's explicit choice (it's opt-in);
  AUTO behavior is untouched.
- Cached streams: generation key must change (step 5) or stale opus plays.
- Songs without any lossless variant even for premium (rare uploads): fallback picker.
- Offline/downloads: LOSSLESS affects new downloads only; never re-fetch old ones.

## Done criteria

- Setting exists, persists, and visibly changes the actual stream (verified via
  ShowMediaInfo codec/bitrate and cache-bypass logs), with graceful fallback.
- Unit tests for `pickBestStream` pass; full test suite + `assembleFossDebug` green.
- Zero DB schema changes; version untouched.

## Escape hatches

- If the innertubex fork cannot surface lossless without premium-only endpoints the
  account can't access: still ship the setting + honest fallback + detection/badge, and
  report "lossless requires premium account; code path verified via
  pickBestStream tests" — do NOT fake a badge.
- If `playerResponseForPlayback` needs new parameters, thread them explicitly and keep
  every existing call site compiling (DownloadUtil, AudioExporter, WrappedAudioService).
