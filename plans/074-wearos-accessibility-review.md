# 074 — Harden Wear OS accessibility

- **Status**: TODO
- **Commit**: dd97d775
- **Severity**: HIGH
- **Category**: Accessibility; purpose & frequency
- **Estimated scope**: 5–9 files; medium review and targeted fixes

## Problem

Wear OS uses custom watch-sized controls and list cells, so semantics and hit targets need explicit review rather than inheriting phone assumptions.

Evidence:
- `wearos/src/main/java/com/music/audify/wear/ui/NowPlayingScreen.kt:542-559` scales a control to 0.88 while pressed and exposes a button click label; actual hit bounds and TalkBack action descriptions need verification.
- `NowPlayingScreen.kt:393-399` exposes the central playback control through custom semantics.
- `wearos/src/main/java/com/music/audify/wear/ui/ListScreens.kt:255-269` makes a whole row clickable with button role, but selected toggle state is rendered as text/color rather than explicit selected/toggle semantics.
- Queue rows use `items(live.rows, key = { it.index })` at `ListScreens.kt:81-87`; index is mutable and should be reviewed against queue occurrence identity.
- Ambient display intentionally removes controls and uses minimal black/text content (`wearos/src/main/java/com/music/audify/wear/ui/Ambient.kt:47-56`); ensure accessibility behavior is intentional in ambient mode.

## Target

- All interactive watch controls have accurate TalkBack labels, roles, enabled/disabled state and state descriptions (play/pause, shuffle, repeat mode, liked state, output selection, queue current/past state).
- Controls meet Wear OS recommended minimum interactive bounds for the actual form factor; do not blindly enlarge visual glyphs. Use appropriate `minimumInteractiveComponentSize`/semantics bounds and avoid overlapping targets.
- Logical TalkBack traversal and rotary/crown navigation are verified on round and square watch sizes.
- Progress/buffering is announced only on meaningful state changes; do not announce every playback tick, lyric word or progress frame.
- Ambient mode remains non-interactive and burn-in protection behavior is preserved; live-mode semantics remain available.
- Press feedback is subtle and does not make the effective target feel smaller. Respect system reduced-motion/animator settings for scale and infinite buffering motion where the platform API supports it.

## Repo conventions to follow

- Now-playing controls and custom central semantics: `wearos/src/main/java/com/music/audify/wear/ui/NowPlayingScreen.kt:320-410`, `:530-569`.
- Queue and toggle cells: `wearos/src/main/java/com/music/audify/wear/ui/ListScreens.kt:65-133`, `:200-273`.
- Ambient implementation: `wearos/src/main/java/com/music/audify/wear/ui/Ambient.kt:30-117`.
- Watch app theme/type and navigation should remain Wear Compose-native; do not transplant phone Material components.

## Steps

1. Run a source audit of every interactive composable in `wearos/src/main/java/com/music/audify/wear/ui/` and map each action to its spoken label, role, state and bounds.
2. Inspect Wear Compose and Compose semantics APIs available in the resolved dependency versions. Use toggle semantics/state descriptions for shuffle, repeat, like and output selection where appropriate.
3. Verify play/pause, previous/next, seek/scrub, volume/crown, queue row and output row hit targets. Expand clickable bounds without overlapping adjacent actions or distorting the watch layout.
4. Check TalkBack traversal order, custom action announcements, disabled controls, queue current/past indication and dynamic title/artist changes. Keep progress announcements throttled to meaningful state transitions.
5. Review `ScalingLazyColumn` rotary behavior and list focus/scroll anchoring when queue/output data updates. Replace index keys only if a stable queue-occurrence key is available from the data model; otherwise document the limitation and avoid a false fix.
6. Verify ambient mode is not exposing dead interactive controls, preserves burn-in protection and remains readable on low-bit displays.
7. Add semantics-focused tests for toggle states and action labels where Compose test APIs support them.

## Boundaries

- Do not redesign the watch UI as a scaled phone UI.
- Do not add spoken announcements for every playback position update.
- Do not change phone app accessibility or database/queue persistence.
- Do not change ambient burn-in movement without a device-specific reason and verification.
- Do not claim a target-size pass from source dimensions alone; verify effective clickable bounds on watch hardware/emulator.

## Verification

- **Mechanical**: `./gradlew :wearos:compileFossDebugKotlin --console=plain` (confirm actual task names first); run Wear OS unit/instrumentation tests and assemble the Wear OS debug variant.
- **Feel check**: use Wear OS emulator at round and square sizes with TalkBack enabled. Traverse Now Playing, queue, More and Output; confirm every action is announced once with the right label/state and in visual order.
- Test crown/rotary scrolling and volume control, tap targets, long titles, large font/display size, disabled controls and no-current-track state.
- Test ambient/AOD entry and exit, low-bit mode and burn-in protection; confirm controls are not focusable/tappable in ambient mode.
- Verify system reduced-motion settings do not leave an infinite decorative spinner or press-scale animation running unnecessarily.
- **Done when**: semantics, bounds, traversal, rotary and ambient checks are recorded on emulator and at least one physical watch (or hardware limitation explicitly documented), with build/tests passing.

