<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 061 — Selected feature 11: Playlist health checker

**Status: planned — share scanner primitives with plan 055; keep playlist findings distinct from library cleanup.**

## Objective

Inspect a playlist and report unavailable tracks, duplicate entries, likely wrong-version matches, unresolved references, and metadata inconsistencies. Offer user-reviewed repair suggestions without silently changing playlist order.

## Why

Playlists become stale as catalogue availability and source resolution change. Users need to know which entries are affected and what evidence supports a proposed replacement.

## What exists today

- Playlist import and matching primitives live in `shared/.../data/spotify/SpotifyImporter.kt` and `SpotifyPlaylistFileParser.kt`.
- Queue/source resolution and health logic exist under `playback/` (including plan 021 source health).
- Local/download state is handled under `download/` and local-library plan 025.

## Scope

**In scope:** on-demand scan; playlist entry classification; duplicate occurrence detection; availability check with freshness timestamp; version mismatch suggestions; user-selected replace/remove/keep; undo or export snapshot before bulk edits.

**Out of scope:** background polling of every playlist; automatic replacement; deleting source media; treating temporary network failure as permanently unavailable; schema changes.

## Affected files

| File | Expected change |
|------|-----------------|
| playlist data/repository layer | Read-only scan input and confirmed edit API |
| `shared/.../data/spotify/SpotifyImporter.kt` | Reuse normalized matching only if semantics fit |
| `playback/StreamSourceHealth.kt` (verify exact path) | Reuse transient failure knowledge without equating it to catalogue absence |
| playlist detail UI / `MainActivity.kt` | Health summary and reviewed repair actions |
| new `data/playlist/PlaylistHealthChecker.kt` | Pure classification and suggestion model |

## Implementation

1. Define health states and evidence freshness; distinguish transient resolution errors from confirmed removal.
2. Implement a read-only scan with cancellation and rate limits.
3. Add duplicate detection that preserves repeated tracks when intentional.
4. Generate alternative suggestions with match evidence and confidence; do not auto-apply.
5. Add selected repair actions with before/after preview and stable playlist ordering.
6. Share safe scanner utilities with plan 055 without merging their user-facing purposes.

## Testing

- Temporary offline, removed item, source failure, duplicate occurrence, live/remaster, and wrong-artist cases.
- Ensure repeated entries are not removed without user confirmation.
- Verify repair preserves playlist ordering and unrelated metadata.
- Rate-limit and cancellation tests.

## Performance notes

Batch provider checks where possible; cache only with explicit freshness; never block playlist opening on a full scan.

## Rollback

Hide the health action and repair controls. Do not mutate playlists during rollback.

## Rejected alternatives

- Remove all duplicate IDs automatically: repeated tracks can be intentional.
- Treat one failed stream resolution as unavailable: source health can be transient.
- Replace tracks based on title-only fuzzy matching: wrong versions are common.

## Implementation contract and acceptance criteria

### Health taxonomy
- Each playlist occurrence is assessed independently and retains its position/identity. States include available, unavailable-confirmed, temporarily-unresolved, duplicate-occurrence, metadata-warning, and unknown.
- A single resolver failure or offline check is temporary/unknown, not confirmed unavailable. Record check time and evidence source; stale checks must be re-run before destructive repair.
- Duplicate detection distinguishes intentional repeated occurrences from accidental duplicates; never remove automatically.
- Replacement candidates include artist/title/album/duration/version evidence. Live, remaster, edit, clean/explicit, and cover versions must be treated as meaningful differences.

### Repair transaction
- Scan is on-demand, cancellable, and non-blocking to playlist playback/opening. Rate-limit remote lookups and cache only with explicit TTL.
- Preview exact before/after order and metadata. Keep/remove/replace are per occurrence. Bulk actions require itemized confirmation and export/undo snapshot where supported.
- Revalidate playlist revision and target availability immediately before commit; abort on concurrent edits rather than overwriting them.

### Done when
- Tests cover transient offline, confirmed removal, duplicate occurrences, wrong artist/version, stale evidence, concurrent playlist edits, and rollback.
- No playlist changes occur from scanning alone; original order and unrelated metadata remain intact after repairs.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
