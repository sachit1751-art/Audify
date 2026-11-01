# Plan 053 — Selected feature 03: Full listening history

**Status: planned — extends existing stats recorder; no per-play event history is currently documented.**

## Objective

Provide a privacy-conscious, queryable history of individual listening sessions: track identity, start time, listened duration, completion/skip outcome, and source category where available. Add date, artist, and track filters and per-track history.

## Why

Aggregate listening totals cannot answer “when did I last hear this?” or distinguish a short skip from a completed listen. Event-level history makes existing stats more useful.

## What exists today

- `data/stats/ListeningStats.kt` stores aggregate statistics and has backup support.
- `data/stats/ListeningRecorder.kt` records listening activity.
- `data/stats/ListeningSummaryCsv.kt` exports aggregate summaries.
- Plan 037 explicitly records that per-play history is absent. Inspect recorder lifecycle and event definitions before proposing persistence.

## Scope

**In scope:** session event model; start/end timestamps; listened milliseconds; outcome classification (completed, skipped, stopped, interrupted, unknown); stable track identity; paginated history; filters; per-track history; export/delete controls; deduplication across service reconnects.

**Out of scope:** server upload, social sharing, invasive behavioral profiling, precise location, schema migration without approval, treating a stream transition as proof a user listened.

## Affected files

| File | Expected change |
|------|-----------------|
| `data/stats/ListeningRecorder.kt` | Define trustworthy session boundaries and event emission |
| `data/stats/ListeningStats.kt` | Keep aggregate counters consistent with event semantics |
| `data/stats/ListeningSummaryCsv.kt` | Add a separate event export only if format remains backward-compatible |
| `ui/screens/HistoryScreen.kt` | History timeline, filters, per-track entry point |
| `MainActivity.kt` / view models | Lifecycle-safe state and navigation |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels and privacy explanations |

Persistence is gated: first inspect existing stores and repository rules. Do not add or alter Room schema in this plan. If no approved append-only storage exists, document the blocker and request approval rather than hiding a schema change in the implementation.

## Implementation

1. Trace playback callbacks and distinguish actual elapsed listening from merely queued or buffered time.
2. Define sessionization rules for pause/resume, seek, repeat, queue transition, interruption, and service recreation.
3. Implement a pure session reducer with injectable clock and deterministic IDs.
4. Add bounded local persistence only through an approved existing storage mechanism.
5. Expose paginated history, filters, per-track history, and explicit clear/export actions.
6. Reconcile aggregate stats from events only if equivalence can be proven; otherwise keep aggregate recorder behavior unchanged.

## Testing

- Unit tests for completion thresholds, skips, pauses, seeks, repeats, interruptions, duplicate callbacks, and process recreation.
- Verify listened duration never exceeds elapsed wall time and excludes paused time.
- Test pagination, date boundaries/time zones, delete/export, and no-network behavior.
- Manual test with short and long tracks and background playback.

## Performance notes

Append events off the main thread; index/filter using existing storage capabilities; paginate rather than materializing all history.

## Rollback

Disable history recording and hide the history route. Preserve aggregate statistics and existing CSV export. Do not automatically erase user history during rollback.

## Rejected alternatives

- Count every media-item transition as a play: queue skips and source retries produce false records.
- Infer completion solely from player state: use elapsed playback and explicit transition reasons.
- Upload history by default: unnecessary for a local listening-history feature.

## Implementation contract and acceptance criteria

### Event semantics
- Define a listening session as one track occurrence with start/end instants, stable media identity, source category, accumulated audible playback duration, and terminal reason. A session is not a raw player-state event.
- Paused, buffering, seeking, and stopped intervals do not accrue listened duration. Seeking must not count skipped time as listened. Repeated tracks create separate sessions.
- Establish explicit completion threshold from product behavior (for example, a percentage plus minimum duration); do not invent it during implementation. Short tracks need a duration-aware rule.
- Treat process death/service recreation as recoverable interruption; event IDs must make duplicate callbacks idempotent. Store timestamps in UTC and render in device-local time.

### Storage and privacy gate
- First produce a storage decision note: current approved store, retention cap, deletion semantics, backup/export behavior, and expected storage growth. No Room/schema change under this plan without explicit approval.
- History is local-only by default. Do not add analytics upload, identifiers unrelated to playback, or background network traffic.
- Clear-history must explain scope and require confirmation; export must be user initiated and use SAF.

### Done when
- Session reducer tests cover pause/resume, seek, repeat, queue replacement, interruptions, service restart, duplicate callbacks, clock/time-zone boundaries, and completion threshold.
- History paging and filters return deterministic results; per-track history agrees with global history.
- Aggregate stats are not silently recalculated unless event-to-aggregate equivalence is demonstrated by tests.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
