# Plan 002 — QA + land the uncommitted player visualizer

Written against commit **`7ca7a9653`**. Working tree contains an uncommitted feature:
an audio-visualizer overlay on the player artwork. This plan QA's it (battery/lifecycle
correctness), fixes what QA finds, and prepares it to land. **A human commits it** — the
executor never commits (AGENTS.md).

## Context

The feature already exists in the working tree (uncommitted):

- `app/src/main/kotlin/com/sachit/music/ui/player/PlayerArtworkVisualizer.kt` (new) — binds
  `android.media.audiofx.Visualizer` to the player's audio session, renders animated bars
  over the artwork, plus `PlayerArtworkVisualizerOverlay` (scrim pill + bars).
- `app/src/main/kotlin/com/sachit/music/utils/VisualizerLevels.kt` (new) — pure FFT→bar math,
  JVM-testable.
- `app/src/test/kotlin/com/sachit/music/utils/VisualizerLevelsTest.kt` (new).
- Wired in: `Player.kt` (toggle gate at lines ~229, ~1894, ~1973), a setting switch
  `playerVisualizerEnabled` (`PlayerSettings.kt` ~1124), preference key
  `PlayerVisualizerEnabledKey` (`PreferenceKeys.kt:77`), English string
  `player_visualizer`/`player_visualizer_desc` in
  `app/src/main/res/values/sachit_strings.xml`.
- `app/src/main/res/drawable/app_logo.xml` and `small_icon.xml` also modified — treat as
  out of scope unless the diff shows they are needed by the visualizer (they likely belong to
  a separate earlier change; do not revert them either).

The design comments in `PlayerArtworkVisualizer.kt` already anticipate failure modes:
unsupported session, offload playback, missing `RECORD_AUDIO` permission
(`android.permission.RECORD_AUDIO` is declared in the manifest), renderer death, and
timeouts. `VisualizerLevels.kt` is deliberately pure (no Android imports).

## Files in scope

- `app/src/main/kotlin/com/sachit/music/ui/player/PlayerArtworkVisualizer.kt` (QA fixes only)
- `app/src/main/kotlin/com/sachit/music/utils/VisualizerLevels.kt` (only if a QA finding
  requires a pure-logic fix, plus its test)
- `app/src/test/kotlin/com/sachit/music/utils/VisualizerLevelsTest.kt`
- `app/src/main/kotlin/com/sachit/music/ui/player/Player.kt` (only if lifecycle wiring fixes
  are required)
- **Out of scope:** `PlayerSettings.kt`, `PreferenceKeys.kt`, `sachit_strings.xml`
  (already correct), `app_logo.xml`/`small_icon.xml`, DB schema, version bumps.

## Current state notes

The feature is gated off by default (`PlayerVisualizerEnabledKey` default `false`), shown
only when the player sheet is expanded and inline lyrics are hidden (`if (playerVisualizer
&& !showInlineLyrics)` in `Player.kt`). `computeVisualizerLevels` returns zero-filled arrays
on empty/short input instead of throwing.

## Steps

1. **Drift check**: `git status` must show exactly the 11 paths from the session handoff
   (AGENTS.md, PreferenceKeys.kt, Items.kt, Player.kt, PlayerSettings.kt,
   PlayerArtworkVisualizer.kt, VisualizerLevels.kt, VisualizerLevelsTest.kt, app_logo.xml,
   small_icon.xml, sachit_strings.xml). Report if other files changed.

2. **Read the three new/changed files** and review for the QA checklist:
   - **Lifecycle**: `Visualizer` is created when the composable enters composition and
     released on dispose (`visualizer?.enabled = false; visualizer?.release()`). Verify
     release also happens when the audio session changes (session id observed as state —
     confirm the re-creation path exists when session changes on track switch).
   - **Rate/size sanity**: capture rate is `Visualizer.getMaxCaptureRate() / 2` and capture
     size derived from `getCaptureSizeRange()` — confirm values are clamped into the
     reported ranges and that a `Visualizer` construction failure is caught (it throws
     `UnsupportedOperationException` on some devices).
   - **Permission**: `Visualizer(sessionId)` with `setCaptureSize` requires
     `RECORD_AUDIO`; confirm the code handles `SecurityException` and disables rather than
     crashes. If it does not, wrap construction + `enabled = true` in try/catch.
   - **Battery**: Visualizer callbacks fire ~2×/frame at half max rate while composed. The
     composable is only on screen when the player sheet is expanded — confirm no global
     collection when the sheet is collapsed and that the capture listener is removed on
     dispose. Note in a comment that `Visualizer` capture is screen-on only.
   - **State handoff**: when the app is backgrounded, Compose disposes the overlay; verify
     dispose releases the native `Visualizer` (leak check) — an unreleased Visualizer keeps
     the audio session attached and can hold the device awake.
3. **Run the pure-logic tests** to confirm the new unit test passes as-is:
   ```bash
   export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"
   ./gradlew :app:testFossDebugUnitTest --tests "*VisualizerLevels*" --console=plain
   ```
4. **Fix only what QA finds** (expected: at most the SecurityException guard and any
   dispose-ordering issue). If the code is already correct on all checklist items, make **no
   code edits** and say so — the deliverable may be a clean QA report.
5. **Add any missing unit coverage** for pure logic only (e.g. a short-input edge case in
   `computeVisualizerLevels` if untested), following the existing test's style.
6. **Manual QA list for the human** (write it into the plan/PR notes — do not run an
   emulator yourself unless one is available): toggle on in Player settings; play → bars
   move; collapse/expand player; background the app (no crash, no wake lock); play a cached
   (offline) track; disable toggle mid-playback; check no `SecurityException` appears in
   logcat on a device where RECORD_AUDIO was denied.

## Verification

1. Unit tests incl. new file: `./gradlew :app:testFossDebugUnitTest --console=plain` → exit 0.
2. Build: `./gradlew :app:assembleFossDebug --console=plain` → exit 0.
3. `git diff --stat` shows only intended files; no new string keys added; no version change.

## Done criteria

- QA checklist above has written answers (in the completion report), each item pass or a
  code fix applied with a reason.
- No crash paths found on review of construction/dispose (guards added where missing).
- All unit tests pass; APK assembles.
- Diff is limited to the visualizer files + wiring already present; nothing else touched.

## Escape hatches

- If the working tree state differs from step 1 (other uncommitted files appeared), STOP and
  report — do not fold unrelated changes into this plan.
- If `Visualizer` construction cannot be made safe against `SecurityException`/unsupported
  devices within these files, STOP and report the device matrix rather than hacking around
  it in `Player.kt` broadly.

## Maintenance note

Future changes to the player sheet layout must keep the `playerVisualizer &&
!showInlineLyrics` gating consistent at both call sites in `Player.kt` (lines ~1894 and
~1973) — they render the same overlay in two states. The visualizer must stay opt-in
(default off) until battery profiling on-device confirms acceptable drain.
