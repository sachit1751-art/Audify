# 072 — Make settings expansion feel continuous

- **Status**: TODO
- **Commit**: dd97d775
- **Severity**: MEDIUM
- **Category**: Interruptibility; easing & duration; cohesion
- **Estimated scope**: 4–7 files; medium UI change

## Problem

Detail content already uses `animateContentSize()` in `app/src/main/java/com/music/audify/ui/screens/DetailScreen.kt:1886`, while the same screen uses a 220ms/180ms fade for shelf replacement at `:504-510`. The codebase therefore has local expansion and content-transition patterns that must be audited for consistency and interruptibility rather than replaced wholesale.

## Target

- Expandable descriptions, settings groups and inline controls retain their visual origin and do not jump surrounding content.
- Use `animateContentSize` or a spring only where the content genuinely changes size; keep rapid setting toggles immediate.
- Expanding/collapsing uses 150–250ms with ease-out for entry and ease-in-out for on-screen size/content movement. Do not exceed 300ms for ordinary UI expansion.
- Repeated taps retarget from the current size without restarting from zero or leaving clipped content.
- Reduced motion removes size/position movement but retains a short opacity/state update.

## Repo conventions to follow

- Shared tokens: `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/motion/MotionTokens.kt:10-20`.
- Existing content-size animation: `app/src/main/java/com/music/audify/ui/screens/DetailScreen.kt:1886`.
- Existing settings owner and controls should be discovered in `app/src/main/java/com/music/audify/ui/screens/SettingsSheet.kt`; do not assume every setting belongs to the same component.

## Steps

1. Audit settings screens/sheets and expandable descriptions for fixed-height clipping, abrupt visibility changes, duplicated animation specs and animations attached to frequent controls.
2. Group findings by actual owning composable. Keep separate plans only if the implementation reveals independent components or behavior.
3. Standardize expansion on a shared tokenized spec: 150–250ms, ease-out for entering content and ease-in-out for size movement; use interruptible Compose animation APIs.
4. Ensure hidden content is removed from semantics/focus when collapsed and restored in logical order when expanded. Preserve keyboard/remote Back behavior where applicable.
5. Add Reduce Animation handling: avoid animated size/translation and retain immediate content plus a short opacity/state cue.
6. Add tests for rapid expand-collapse-expand, large font scale, content changing while expanded, and semantics/focus visibility.

## Boundaries

- Do not animate every switch, checkbox or slider value; frequent controls should respond immediately.
- Do not change setting meanings, persistence, defaults or string resources except where an accessibility label is genuinely missing.
- Do not replace all `animateContentSize()` calls indiscriminately; verify each use's frequency and UX purpose.

## Verification

- **Mechanical**: `./gradlew :app:compileFossDebugKotlin --console=plain`; `./gradlew :app:testFossDebugUnitTest --console=plain`.
- **Feel check**: on phone, open/close each changed section repeatedly and interrupt midway. Content must not jump, overlap, clip or leave a blank gap.
- Test rapid switch/slider changes; controls must remain immediate.
- Test font scale 1.0, 1.3 and 2.0; expanded content and focus targets must remain reachable.
- Enable Reduce Motion; confirm no height/position travel and no loss of state communication.
- **Done when**: all in-scope expansions are consistent, interruptible, accessible and verified on a phone device.

