<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 06 — Selected feature 17: Better statistics dashboard

**Status: planned — known UX gap; builds on current aggregate stats and recap features.**

## Objective

Rework the Stats screen into a scannable dashboard for listening trends, top tracks/artists/albums, time-of-day patterns, and date-range comparisons. Clearly label which metrics are aggregate versus event-derived.

## Why

The repository records listening aggregates and recap cards, but the Stats screen is already documented as needing UX work. A better dashboard should improve comprehension, not invent precision unsupported by stored data.

## What exists today

- `data/stats/ListeningStats.kt` stores aggregate statistics and exposes backup data.
- `data/stats/ListeningRecorder.kt` records activity.
- `data/stats/ListeningSummaryCsv.kt` exports aggregate CSV.
- Existing monthly/yearly recap work is archived in plans README; per-play history is not yet present (plan 053).

## Scope

**In scope:** clear overview hierarchy; date range; top tracks/artists/albums; listening-time trends; time-of-day/week patterns where available; empty states; data freshness and definitions; links to recap and history; accessible charts/tables.

**Out of scope:** pretend per-play event data exists; online leaderboards; social comparisons; new tracking/upload; schema changes; charts without a decision or insight.

## Affected files

| File | Expected change |
|------|-----------------|
| Stats screen implementation (locate exact route/source before editing) | Dashboard composition and range controls |
| `data/stats/ListeningStats.kt` | Read-only aggregate queries or derived summaries |
| `data/stats/ListeningRecorder.kt` | Only if metric definitions require a correctness fix |
| recap UI/routes | Reuse existing recap components, avoid duplication |
| `app/src/main/res/values/sachit_strings.xml` | Metric names, definitions, empty states |

## Implementation

1. Audit current Stats route, all available fields, and README-documented known gaps.
2. Write metric definitions and data availability matrix before designing charts.
3. Define dashboard information hierarchy and date-range semantics.
4. Implement overview and top lists using existing aggregates; label estimates/unknown values honestly.
5. Add accessible chart descriptions and tabular alternatives.
6. Integrate per-play history only after plan 053 ships; do not block the aggregate dashboard on it.

## Testing

- Pure aggregation tests for date ranges, time zones, empty data, and ties.
- Verify chart totals reconcile with underlying aggregate values.
- Accessibility semantics and large-font layout tests.
- Manual test with empty, sparse, and large listening datasets.

## Performance notes

Compute summaries off the main thread and cache by range/data revision. Avoid chart recomputation on every player position tick.

## Rollback

Restore the existing Stats screen route while retaining all stored listening data and recap features.

## Rejected alternatives

- Wrapped-style animation on every chart: motion is not the goal of a utility dashboard.
- Invent per-play precision from aggregate buckets: data cannot support it.
- Put every metric on the first screen: prioritize actionable summaries and progressive detail.

## Implementation contract and acceptance criteria

### Metric contract
- Before UI work, create a metric dictionary: display name, definition, source field, unit, aggregation window, timezone rule, known limitations, and whether the value is exact/estimated/unknown.
- Separate aggregate counters from per-play event metrics. Until plan 053 exists, do not show per-session timelines, completion rates, or exact last-played data unless a current source proves it.
- Define date range presets and custom range boundaries using device-local calendar days, converted consistently for storage/query. State whether comparisons use equal-length prior periods.
- Top lists need deterministic tie-breaking and a clear sample/window label. Empty, sparse, and newly reset data have distinct states.

### Dashboard hierarchy
- First viewport prioritizes total listening for selected range, trend over time, and a small set of top items. Secondary patterns (day/hour, artist/album breakdown) are progressively disclosed only if the data supports them.
- Every chart has a text summary and accessible tabular alternative. Avoid decorative charts without a decision or insight.
- Data freshness and reset/backup implications are visible; dashboard reads must not trigger network requests or mutate recorder state.

### Done when
- Metric calculations reconcile exactly to source aggregates with tests for timezone, daylight-saving changes, empty data, ties, and range boundaries.
- Large-font, TalkBack, and narrow-screen layouts are verified.
- No chart presents false precision or event-level claims from aggregate-only data; performance is measured on large histories.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
