<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 065 — Selected feature 15: Tablet two-pane interface

**Status: planned — extends existing plan 026 (Tablet / two-pane player).**

## Objective

Use available width on tablets and foldables to show library/search/navigation beside a persistent player, queue, or detail pane. Preserve the existing phone layout and navigation behavior.

## Why

Simply stretching a phone layout wastes horizontal space. A two-pane layout should reduce navigation hops without duplicating playback state.

## What exists today

- Plan 026 contains the original tablet/two-pane proposal.
- Shared UI screens are under `sharedUi/src/jvmSharedMain/.../ui/screens/` and player components under `.../ui/player/`.
- `MainActivity.kt` owns Android navigation and player integration; confirm current window-size handling before edits.

## Scope

**In scope:** window-size class adaptation; navigation/list pane plus detail/player pane; fold posture awareness where available; minimum pane widths; selection and back behavior; resize/reflow; keyboard, mouse, and touch support.

**Out of scope:** separate tablet-only feature set; duplicated playback service/view model; forced landscape; redesign of phone UI.

## Affected files

| File | Expected change |
|------|-----------------|
| `MainActivity.kt` | Window-aware top-level layout integration |
| `sharedUi/.../ui/screens/LibraryScreen.kt` | Reusable list pane content |
| `sharedUi/.../ui/player/NowPlayingScreen.kt` / player shell | Reusable detail pane content |
| shared adaptive layout component (new if needed) | Pane sizing and fold-aware policy |
| UI tests/previews | Width and posture coverage |

## Implementation

1. Reconcile plan 026 and inspect existing adaptive/window-size utilities.
2. Define compact/medium/expanded breakpoints from actual supported device widths.
3. Extract pane content without duplicating navigation or player state.
4. Implement two-pane layout with minimum widths and graceful collapse.
5. Handle selection, back stack, deep links, keyboard focus, and fold transitions.
6. Verify phone portrait, phone landscape, tablet portrait/landscape, and foldable postures.

## Testing

- Screenshot/previews at narrow, breakpoint-adjacent, and wide widths.
- Navigation/back behavior tests and state retention when resizing.
- Manual keyboard/mouse/trackpad and touch checks.
- Ensure player playback survives pane changes and configuration changes.

## Performance notes

Avoid composing duplicate player trees. Keep lazy list state stable across pane transitions.

## Rollback

Fall back to the existing single-pane navigation at all widths; preserve shared screen components.

## Rejected alternatives

- Force two panes on narrow phones: harms content width.
- Create a separate tablet activity/player service: duplicates state and lifecycle complexity.
- Use fixed pixel breakpoints: fails across density and window resizing.

## Implementation contract and acceptance criteria

### Adaptive layout contract
- Use window size classes/window metrics rather than device labels. Define compact/medium/expanded behavior and minimum pane widths from actual app content; do not hard-code tablet model checks.
- One navigation/state owner and one playback service remain authoritative. A detail pane is a presentation of existing state, not a second player instance.
- Define default expanded layout, selected destination, empty-detail state, and behavior when width crosses a breakpoint. Preserve scroll/selection state during reflow.
- Fold posture support is conditional on reliable platform APIs; separating hinge regions must not place controls beneath a hinge or split interactive components.

### Navigation and input
- Back first closes transient overlays/selection, then returns detail selection to prior destination, then exits the pane route according to existing navigation semantics. Document exact precedence before implementation.
- Deep links and restored navigation must open the correct detail pane. Keyboard focus, tab order, mouse hover, touch targets, and external keyboard shortcuts remain coherent.
- Phone layout remains the baseline compact presentation; no forced landscape.

### Done when
- Screenshot tests cover compact, breakpoint edges, expanded, portrait/landscape, and fold posture where available.
- Resize/recreation tests prove player state and list scroll survive reflow.
- Manual tablet/foldable checks confirm no duplicated player, clipped pane, focus trap, or broken back/deep-link behavior.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
