# Plan 059 — Selected feature 09: Smarter queue management

**Status: planned — builds on existing queue sections/history; avoid duplicating shipped behavior.**

## Objective

Improve queue editing with clear upcoming/played sections, multi-select, bulk removal, stable reorder, and predictable previous/next navigation while preserving playback identity.

## Why

Queue editing becomes error-prone when the playing item moves or the list changes during playback. Bulk operations must preserve the current track and queue invariants.

## What exists today

- `shared/src/jvmSharedMain/.../playback/QueueSections.kt`, `QueueTimeline.kt`, and `QueueWindow.kt` model queue presentation.
- `app/.../playback/QueueHistory.kt`, `QueueCoordinator.kt`, and `BoundedQueueEdits.kt` cover queue operations.
- `sharedUi/.../ui/player/PlayerQueue.kt` and `ui/player/Queue.kt` (verify exact current path) render queue UI.
- Queue skip marks and the “Earlier” boundary shipped under plan 020.

## Scope

**In scope:** explicit queue sections; multi-select; remove selected; move selected to next; clear upcoming; stable drag reorder; preserve current media item and playback position; accessibility semantics; empty/large queue states.

**Out of scope:** named queue persistence (plan 031); playlist editing; new queue synchronization protocol; schema changes.

## Affected files

| File | Expected change |
|------|-----------------|
| `playback/QueueCoordinator.kt` | Atomic queue-edit operations |
| `playback/BoundedQueueEdits.kt` | Extend pure edit planner where appropriate |
| `shared/.../playback/QueueSections.kt` | Section boundary logic |
| `sharedUi/.../ui/player/PlayerQueue.kt` | Multi-select and section UI |
| queue view model/state owner | Selection state and action dispatch |
| focused queue tests | Preserve identity/order invariants |

## Implementation

1. Inventory existing queue sections, skip marks, drag reorder, and queue actions; list only missing interactions.
2. Define stable item keys and selection behavior under concurrent queue transitions.
3. Implement pure bulk-edit operations that preserve current item and deduplicate correctly.
4. Add contextual selection mode and bulk action bar; retain one-tap actions outside selection mode.
5. Ensure accessibility labels and keyboard/TV navigation remain functional.
6. Validate interaction with Automix and Listen Together before enabling bulk edits there.

## Testing

- Property-style tests for stable IDs, reorder, removal, duplicate media IDs, and current-item preservation.
- Race tests for queue transition during selection/edit.
- Regression for shuffle, repeat, Automix, queue restore, and Listen Together.
- Manual touch, TalkBack, keyboard, and TV remote checks.

## Performance notes

Use lazy list rendering and stable keys; bulk operations should be one state transition, not N sequential queue rebuilds.

## Rollback

Remove selection mode and bulk actions while retaining existing single-item queue actions and section display.

## Rejected alternatives

- Rebuild queue from displayed rows: UI ordering may differ from canonical playback order.
- Remove by media ID only: duplicate occurrences can exist in one queue.
- Add more persistent queue features here: keep plan 051 separate.

## Implementation contract and acceptance criteria

### Queue operation semantics
- Every row is keyed by queue occurrence ID, not media ID; duplicate songs remain independently selectable and movable.
- Canonical queue order is owned by playback queue state, never reconstructed from visible filtered/sectioned rows.
- Define exact semantics for remove selected, move selected to next, clear upcoming, reorder selection, and undo. The currently playing occurrence is protected unless user explicitly chooses a stop/remove-current action.
- Selection remains stable through queue-window pagination, queue transitions, and list recomposition; stale selected IDs are dropped with predictable feedback.

### Interaction behavior
- Enter selection mode through explicit row selection; provide select-all for the visible scope and state whether it means current section or entire queue.
- Bulk actions apply as one atomic queue state transition. Confirm only destructive whole-upcoming/whole-queue actions, not routine single-row operations.
- Shuffle/repeat/Automix/Listen Together must either support the operation with defined semantics or disable it with an explanation.

### Done when
- Property tests prove reorder/removal preserve all unselected relative order and current occurrence identity.
- Race tests cover playback advancing while selection is active and queue changes from another controller.
- Large queues remain lazy and responsive; TalkBack, keyboard, and TV focus behavior are verified.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
