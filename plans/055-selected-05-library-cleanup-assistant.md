<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 055 — Selected feature 05: Library cleanup assistant

**Status: planned — extends existing plan 040 (Library hygiene tools).**

## Objective

Scan the user's accessible library and downloaded items for likely duplicates, missing/unreadable files, incomplete metadata, and orphaned download records. Present a reviewable report and require confirmation for every destructive action.

## Why

Library cleanup should identify problems without guessing which copy the user values or deleting files behind their back.

## What exists today

- Local library work is tracked in plan 025.
- Download state and verification live under `download/`, including `DownloadStore.kt` and `DownloadVerification.kt`.
- `HistoryScreen.kt` and library screens provide existing UI patterns. Re-audit plan 040 before implementation.

## Scope

**In scope:** read-only scan; exact duplicate detection by stable IDs/URI and file hash where accessible; probable duplicate grouping by metadata; missing/unreadable URI detection; incomplete metadata report; orphaned app records; per-item explanation; select/ignore/resolve actions; undo where technically possible.

**Out of scope:** automatic deletion; scanning inaccessible device storage; modifying embedded tags; moving files without SAF permission; claiming metadata similarity proves identical audio.

## Affected files

| File | Expected change |
|------|-----------------|
| `download/DownloadStore.kt` | Read-only reconciliation of persisted download records |
| `download/DownloadVerification.kt` | Reuse safe verification primitives |
| `ui/screens/LibraryPhoneParts.kt` / `sharedUi/.../LibraryScreen.kt` | Cleanup entry point and report UI, depending on current ownership |
| new `data/library/LibraryHealthScanner.kt` | Pure classification plus injected file access boundary |
| `MainActivity.kt` / view model | Scan lifecycle and confirmed actions |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels and warnings |

## Implementation

1. Reconcile plan 040 and inspect exact local/download storage representations.
2. Define findings with severity, evidence, and supported action; distinguish confirmed from probable duplicates.
3. Implement a read-only scanner with bounded concurrency and cancellation.
4. Build grouped findings with filters and clear explanation of why each item was flagged.
5. Add explicit per-item or selected-batch cleanup; revalidate URI/record immediately before action.
6. Add undo only for operations with a reliable inverse; otherwise state that action is irreversible before confirmation.

## Testing

- Pure classifier tests for exact/probable duplicates, missing files, inaccessible URIs, and incomplete metadata.
- Permission revocation, file replacement during scan, cancellation, and repeated scan idempotency tests.
- Verify no deletion occurs before confirmation and no probable match is auto-removed.
- Manual SAF and MediaStore tests across Android versions.

## Performance notes

Hash only when needed; cache scan results with freshness metadata; cap concurrent file reads and never hash multi-gigabyte media on the main thread.

## Rollback

Remove the scanner entry point and disable cleanup actions. Leave all media files and persisted download records untouched.

## Rejected alternatives

- Fuzzy metadata match as proof of duplicate: remasters/live versions often share metadata.
- One-tap “clean all”: too destructive for a media library.
- Broad filesystem scan: Android scoped storage does not grant arbitrary access.

## Implementation contract and acceptance criteria

### Finding model
- Each finding has stable item reference, category, severity, evidence, confidence (confirmed/probable/unknown), scan timestamp, and allowed actions. Do not collapse inaccessible into missing.
- Exact duplicate means same stable content identity or verified content hash. Metadata similarity alone is probable duplicate; never auto-select a winner.
- Missing means a previously accessible URI now returns a confirmed not-found result. Permission denied, provider unavailable, and transient I/O are separate states.
- Orphaned record means app-owned record has no corresponding owned artifact after a fresh recheck; user-owned local files are never candidates for deletion.

### Actions and safety
- Initial scan is read-only, cancellable, and scoped to granted MediaStore/SAF/app-owned locations. No broad storage permission.
- Before each action, revalidate item identity, URI grant, and finding freshness. Show exact action and consequences. Batch actions require an itemized review.
- Undo is offered only if the operation has a real inverse (for example, restoring an app-owned record); never promise undo for permanent file deletion.

### Done when
- Tests cover false-positive duplicate groups, permission revocation, provider outages, file replacement during scan, cancellation, stale findings, and repeated scans.
- No destructive operation runs without explicit confirmation; scan-only mode cannot mutate data.
- Scan performance is measured on a representative large library with bounded concurrency and cancellation latency.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
