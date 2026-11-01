<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 051 — Selected feature 01: Named queue slots

**Status: planned — reconcile with existing plan 031 before implementation.** Plan 031 already specifies the core 2–3 persistent queue-slot concept. This plan is the selected-list entry and must not create a second implementation.

## Objective

Deliver 2–3 user-named queue slots that preserve ordered tracks and a useful resume position. Users can save the current queue, load another slot, rename or delete a slot, and return to the queue they were using before a swap.

## Why

The live queue is easy to replace accidentally. Slots preserve work-in-progress listening sessions without turning queues into another full playlist system.

## What exists today

- `playback/PersistQueue.kt` persists the live queue.
- `playback/PlaybackTarget.kt` and `playback/PlaybackIntent.kt` centralize playback actions.
- `playback/QueueHistory.kt` and `playback/QueueCoordinator.kt` manage queue behavior.
- Plan 031 contains the initial design; inspect current code and plan drift before implementation.

## Scope

**In scope:** a deliberate 2–3 slot cap; editable names; ordered queue snapshot; current-item identity and playback position where safely restorable; save, load, overwrite, rename, delete; clear empty/error states; guest restrictions for Listen Together; persistence and restore behavior.

**Out of scope:** unlimited slots, playlist folders, cloud sync, sharing, automatic context switching, schema migrations unless separately approved.

## Affected files

| File | Expected change |
|------|-----------------|
| `playback/PersistQueue.kt` | Reuse or extend live-queue persistence only where needed |
| `playback/QueueCoordinator.kt` | Integrate slot load/save without bypassing queue invariants |
| `playback/PlaybackIntent.kt` | Add a typed intent only if existing verbs cannot express the action |
| `ui/components/MiniPlayer.kt` | Expose a compact slot entry point if the current layout supports it |
| `MainActivity.kt` / queue UI | Wire the picker to existing navigation and state ownership |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels and feedback |
| focused playback tests | Cover slot state and queue restoration |

Do not assume a new Room entity is permitted. The repository planning rules prohibit schema changes; prefer an existing supported sidecar/settings store or stop and request approval if durable structured storage cannot be done safely.

## Implementation

1. Re-audit plan 031 and current queue persistence; record drift and reuse existing work.
2. Define a bounded, versioned slot snapshot model and validate track IDs, order, and position.
3. Implement save/load/overwrite/rename/delete behind one manager with atomic updates.
4. Preserve the outgoing live queue for an in-session resume action; define restart behavior explicitly.
5. Add a compact picker with clear confirmation for overwrite/delete and guest-mode gating.
6. Add tests before wiring UI; verify existing live queue and Listen Together behavior remain unchanged.

## Testing

- Unit tests for slot cap, empty queues, ordering, invalid IDs, overwrite, rename, delete, and resume position.
- Persistence round-trip and interrupted-write tests.
- Regression tests for live queue restore, shuffle/repeat, queue edits, and Listen Together guest restrictions.
- Manual emulator test across process death and app restart.

## Performance notes

Keep snapshots bounded and avoid resolving all song metadata on the UI thread. Loading a slot must not trigger duplicate stream resolution for unchanged items.

## Rollback

Remove the slot UI and manager integration while retaining the existing live queue persistence unchanged. Do not delete or migrate user data as part of rollback.

## Rejected alternatives

- Unlimited saved queues: duplicates playlist management and expands scope.
- Persisting a second copy of the entire song catalogue: unnecessary and fragile.
- Replacing the live queue before validating a slot: risks losing the user's current session.

## Implementation contract and acceptance criteria

### State and invariants
- A slot snapshot contains a stable slot ID, normalized non-empty display name, ordered occurrence IDs/media references, current occurrence identity, playback position in milliseconds, shuffle/repeat state only if those states can be restored without changing current queue semantics, and update timestamp.
- Preserve duplicate occurrences: identify queue rows by occurrence identity, not media ID alone.
- Slot capacity is exactly the product decision in plan 031 (2–3); settle the exact cap from that plan before coding. Creating beyond capacity must be blocked with a clear explanation, never evict silently.
- Loading validates the entire snapshot before touching the live queue. If any required item cannot be reconstructed, report affected entries and let the user cancel or load the valid subset explicitly.
- Define resume precisely: previous live queue is an in-memory one-level return target; restart restores the last live queue using existing persistence, not a hidden stack.

### UX states
- Empty slots, empty live queue, full capacity, duplicate names, invalid snapshot, unavailable tracks, guest mode, save-in-progress, and storage failure each need explicit UI behavior.
- Overwrite and delete require confirmation; rename is inline or dialog-based and rejects blank names.

### Done when
- Save/load/overwrite/rename/delete and one-level resume work across process recreation as specified.
- No slot action bypasses QueueCoordinator invariants or guest restrictions.
- Existing live-queue persistence, duplicate occurrences, shuffle/repeat, and Listen Together regressions pass.
- A migration is not introduced without approval; if the existing store cannot safely hold snapshots, stop and request a storage decision.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
