# Plan 063 — Selected feature 13: Advanced search filters

**Status: planned — extend current search behavior only after auditing provider query capabilities.**

## Objective

Add optional filters for artist, album, duration, source, downloaded/local availability, and content type while keeping ordinary search fast and uncluttered.

## Why

Filters reduce repeated query refinement for large libraries and mixed local/streaming results. They must reflect actual provider capabilities rather than pretend every backend supports the same filters.

## What exists today

- Search UI is in `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/SearchScreen.kt` and is integrated from `MainActivity.kt`.
- Search history exists in `app/.../data/settings/SearchHistory.kt`.
- Local and download source types are distinct; inspect search repository and model contracts before adding filters.

## Scope

**In scope:** filter state model; artist/album/duration filters where data is available; source and downloaded/local toggles; active-filter chips; clear-all; persistence for current session or user preference only if justified; unsupported-filter messaging.

**Out of scope:** complex query language, provider-specific hidden APIs, server-side filtering claims when filtering is client-side, changing default search ranking.

## Affected files

| File | Expected change |
|------|-----------------|
| `sharedUi/.../ui/screens/SearchScreen.kt` | Filter controls and result presentation |
| search repository/provider layer | Capability-aware filter mapping |
| `data/settings/SearchHistory.kt` | Only if filter persistence is explicitly desired and compatible |
| `MainActivity.kt` | Search state ownership/navigation as required |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels |

## Implementation

1. Map all search providers and determine which fields are queryable versus locally filterable.
2. Define a platform-neutral filter model with neutral defaults.
3. Apply filters after merging results only where field semantics are consistent; avoid dropping unknown metadata unexpectedly.
4. Add a compact filter sheet and active chips with clear-all.
5. Keep filter state scoped to a search session unless persistent behavior is approved.

## Testing

- Filter combination and reset tests, missing metadata, duration boundaries, source type, and downloaded state.
- Provider capability tests to ensure unsupported filters are not sent as malformed query syntax.
- UI tests for active chips, empty filtered results, and returning to prior query.

## Performance notes

Filter in-memory result pages cheaply; avoid fetching the entire remote catalogue to apply a client-side filter.

## Rollback

Remove filter controls and reset filter state to neutral; default search query/ranking remains unchanged.

## Rejected alternatives

- Add every filter to every provider request: providers differ in supported query syntax.
- Persistent filters by default: can make later searches appear mysteriously incomplete.
- Hide results with unknown metadata: missing data is not a failed match.

## Implementation contract and acceptance criteria

### Filter model and semantics
- Define a platform-neutral immutable filter model with neutral defaults: artist, album, min/max duration, source categories, downloaded/local flags, and content type only where supported.
- Document whether each filter is provider-side or client-side per provider. Never send unsupported query syntax or imply remote catalogue completeness when filtering only the loaded page.
- Missing metadata is unknown, not a failed match. Define whether unknown values remain visible (default: retain unless the user explicitly chooses an exclude-unknown option).
- Duration boundaries are inclusive and validated (minimum <= maximum; non-negative). Empty filters must produce exactly the existing search behavior.

### UI and state
- Filter sheet shows only applicable controls or marks unsupported controls clearly. Active filters appear as removable chips; clear-all restores neutral state.
- Filter state follows the search session and query. Define reset behavior when query/provider changes; do not persist across app launches unless separately justified.
- Empty filtered results explain that filters removed all loaded matches and provide one-step clear filters.

### Done when
- Tests cover every filter alone/in combination, missing metadata, boundaries, provider capability mapping, pagination, query changes, and reset.
- Existing search ranking and unfiltered result set remain unchanged.
- Performance tests confirm client filtering touches only loaded pages and does not trigger full-catalogue fetches.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
