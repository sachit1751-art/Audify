# 089 - Android balanced-premium micro-interactions

- **Status**: IMPLEMENTED LOCALLY - DEVICE QA PENDING
- **Scope**: Shared Android/JVM Compose controls and Android feedback surfaces
- **Design personality**: Balanced premium; tactile, short, restrained, state-accurate

## Existing implementation audited

- `sharedUi/.../ui/motion/MotionTokens.kt` already owned shared timing and easing values.
- Player controls already used `Crossfade`, custom `ThinSlider`, haptics, and `PlayerSettings.reduceAnimation` in selected paths.
- `Material3` owns the native switch and standard slider interaction animations; these were retained rather than wrapped in duplicate animations.
- The app-wide Coil image loader already applies a 210ms artwork crossfade.
- Navigation selection indicators already have a reduced-motion path. Existing page navigation and artwork continuity are retained; artwork shared-element travel is bypassed when reduced motion is enabled.

## Implemented

- Added named press, icon, toggle, popup, selection, and artwork-crossfade timing tokens.
- Added `Modifier.microPressScale`, driven by the same `InteractionSource` as the clickable control. It uses a short press tween, a non-bouncy spring release, and snaps under reduced motion.
- Applied the shared press response to player transport, like/menu, queue-mode, output/party, and row-more controls.
- Added short, interruptible crossfades for transport, player action, repeat-mode, and download-status icon changes. Selected player-control tint/background states now transition consistently.
- Kept seek and volume values tied directly to their actual state. Only the visible seekbar thickness responds to finger interaction; it does not tween playback position.
- Made seekbar touch response non-bouncy and snap under reduced motion. Automix cover state snaps to the authoritative active/inactive state when reduced motion is enabled; its beat clock remains disabled.
- Added reduced-motion behavior to skeleton shimmer, artwork shared-element travel, Home/Library heading parallax, player capsule movement, and the download indicator's entrance/exit.
- Shortened queue-action notice travel to a small offset and a quick fade/slide.
- Added unit tests that constrain the shared timing tokens to their intended usability ranges.

## Performance and correctness

- Press scaling is a graphics-layer transform; it does not change layout size or click callbacks.
- Playback position and user seeking remain immediate; no decorative interpolation is applied to the seek value.
- Download batch progress is eased between discrete updates, but the download session itself is not delayed.
- No new dependencies, playback-engine changes, database changes, or app-version changes were introduced.
- No 60 FPS claim is made; frame-time profiling and subjective device feel still require physical-device testing.

## Verification

Passed locally:

- `:sharedUi:compileKotlinJvm`
- `:app:compileDevDebugKotlin`
- `:app:compileProdDebugKotlin`
- `:sharedUi:jvmTest`
- `:app:testDevDebugUnitTest`
- `:app:testProdDebugUnitTest`
- `:app:assembleDevDebug`
- `git diff --check`

## Remaining device QA

- Rapidly tap play/pause, next/previous, favorite, shuffle and repeat; verify latest state always wins and no control feels delayed.
- Drag seekbar and volume controls while playback position updates; verify the thumb never fights the finger or drifts from actual playback.
- Test download progress, completion and failure transitions.
- Compare normal and reduced-motion settings on a phone, including Home, Library, player, queue and loading states.
- Verify TalkBack labels and minimum touch targets after press scaling.

Do not mark this plan fully complete until those device checks are recorded. The requested `AUDIFY_DESIGN_SYSTEM.md` does not exist in this checkout; `docs/DESIGN.md` is the current design reference. Project documentation scope rules were respected, and this implementation record plus `plans/README.md` carry the new motion contract.
