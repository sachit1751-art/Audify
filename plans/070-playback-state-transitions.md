# 070 — Clarify playback state transitions

- **Status**: TODO
- **Commit**: dd97d775
- **Severity**: HIGH
- **Category**: Purpose & frequency; interruptibility; cohesion
- **Estimated scope**: 5–7 files; medium UI change

## Problem

Playback controls are among the most frequently used controls, so feedback must be immediate and restrained. The current mini-player owns play/loading state but this plan must trace the full player and mini-player to ensure play, pause, buffering and track changes use one truthful state model. The watch now-playing screen also has a continuous buffering spinner (`wearos/.../NowPlayingScreen.kt:320-324`) and a scaling press response (`:330-337`); those are separate platform behaviors and are handled in plan 074.

The shared motion tokens currently define 160ms control state, 180ms short fade and 220ms content transition (`sharedUi/.../MotionTokens.kt:10-20`). Reuse these values unless a measured interaction proves they are unsuitable.

## Target

- Play/pause glyph and accessible action label update immediately from authoritative player state; no optimistic state that can disagree with the service.
- Buffering is distinct from paused and playing. Show progress only while the engine reports buffering/loading; never leave an indefinite spinner after an error or completed prepare.
- Track changes preserve identity: title, artist and artwork update as one coherent content unit. Do not crossfade unrelated metadata independently.
- Frequent play/pause taps do not restart long animations or delay transport response.
- State feedback uses opacity/color and a restrained 100–160ms control transition. Track-content transition is 180–220ms, with no UI animation exceeding 300ms.
- Reduced motion removes non-essential movement while preserving immediate state and opacity feedback.

## Repo conventions to follow

- Motion tokens: `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/motion/MotionTokens.kt:10-20`.
- Mini-player API and state inputs: `app/src/main/java/com/music/audify/ui/components/MiniPlayer.kt:342-360`; its visual controls continue below this range.
- Shared player UI is decomposed by player components; inspect `app/src/main/java/com/music/audify/ui/player/` and `sharedUi/` before selecting the actual owner. Do not duplicate state in a leaf composable.

## Steps

1. Trace authoritative `isPlaying`, loading/buffering, error and current-track flows from the playback service/view model into mini-player and full-player controls. Record exact owners and distinguish buffering from initial loading.
2. Identify all duplicated play/pause and track-title/artwork transition implementations. Select one shared state-transition pattern while retaining platform-specific layouts.
3. Update the play/pause control to use a short, interruptible icon/opacity transition (100–160ms). Keep click handling synchronous and state driven by the player stream.
4. Add explicit buffering presentation and a stable fallback for failed/unknown playback state. Ensure the spinner is not presented for a paused track.
5. Animate track identity as a single keyed content group using a stable media/queue-occurrence key. If the same track is reselected, do not imply a new track transition.
6. Add tests for rapid play/pause toggles, buffering→playing, buffering→error, track A→B, same-track reselect and stale state emissions.

## Boundaries

- Do not alter playback engine behavior, network retry policy, audio focus, service lifecycle or queue selection.
- Do not use animation as the sole indication of playing/buffering/error; expose semantic state.
- Do not add continuous decorative motion to frequently used controls.
- Do not assume a source retry means the track changed.

## Verification

- **Mechanical**: `./gradlew :sharedUi:compileKotlinJvm --console=plain`; `./gradlew :app:compileFossDebugKotlin --console=plain`; `./gradlew :app:testFossDebugUnitTest --console=plain`.
- **Feel check**: on phone, tap play/pause repeatedly at intervals shorter than the transition duration; control must follow the latest real player state without lag or visual bounce.
- Simulate slow prepare, buffering, failure and recovery; each state must be visually and semantically distinct.
- Change tracks from queue, next/previous, and mini-player swipe; verify artwork/title/artist never disagree for a frame and no stale artwork flashes.
- Toggle Reduce Motion and verify state remains clear without spatial movement.
- **Done when**: all state transitions are backed by authoritative state, focused tests pass and phone-device playback scenarios are recorded.

