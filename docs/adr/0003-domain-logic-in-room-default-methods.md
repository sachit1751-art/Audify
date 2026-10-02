# ADR-0003: Domain logic sits in Room DAO default methods (accepted as a platform constraint)

**Status:** Accepted, with a known cost
**Date:** 2026-10-02
**Context:** `CONTEXT.md` — *Library sort*

---

## Context

`db/DatabaseDao.kt` declares **238 functions** against **176 `@Query` annotations**. The ~60-function gap is Kotlin **default methods** with real bodies, and that is where a meaningful amount of domain logic currently lives:

- **20 sites** construct `Collator.getInstance(Locale.getDefault())` inline, inside `map { }` operators on Flows, to implement locale-sensitive **Library sort** by title.
- **13 sites** are `when (sortType)` dispatchers — the same four-way shape repeated for songs, liked songs, artist songs, artists, bookmarked artists, albums, liked albums, uploaded albums, playlists, downloaded songs, uploaded songs, podcast episodes, saved episodes.
- `songs(sortType, descending)` carries a hand-rolled album-grouping algorithm (`groupBy { it.album?.title }.flatMap { … sortedBy … }`) that exists because SQLite cannot express it.
- `replaceSongArtists`, `insert`, and several `update` overloads perform entity merge and artist-alias resolution inline (see [ADR-0001](0001-alias-resolution-seam.md)).

Room permits default methods **only** on the `@Dao` interface — there is no other place to put them. This is a platform constraint, not a style choice.

The cost is real and worth stating plainly: a file that imports `Collator`, `Locale`, `MediaMetadata`, `ArtistNameAliases`, and `ui.utils.resize` is a persistence adapter that has absorbed domain rules and reached into the UI package. Sorting and grouping rules are now only testable by standing up Room.

## Decision

**Accepted for now, deliberately, because the alternative is worse today.**

The constraint is real and the countervailing cost is currently lower than moving the logic. But the decision is **not** "domain logic belongs in DAOs" — it is "Room forces this particular placement, so we take it knowingly."

Two boundaries are drawn and must be honoured:

1. **Pure rules must be extractable, not tangled.** Any sorting, grouping, or merge rule that can be written as a function over data is written so that a later extraction is mechanical. No new rule may depend on Room types, `Flow`, or `Context`.
2. **The DAO must not reach outward.** No further imports from `ui.*`. The existing `ui.utils.resize` import is tech debt to be removed, not a precedent.

Sorting and grouping are the named candidates for extraction, because they are pure over collections and account for most of the default-method bulk.

## Alternatives considered

**Extract the rules now, before anything else.** Rejected as sequencing, not merit — the extraction is worth doing, but it competes with higher-value work, and doing it opportunistically while touching `DatabaseDao` is how a 2,115-line file grows a second seam. Deferred, not rejected.

**Move logic into `MusicDatabase` (the wrapper) instead.** Rejected: `MusicDatabase` is a thin delegate over `InternalDatabase`; putting transformation logic there hides it from Room's codegen path and makes call sites inconsistent — some queries transformed, some not.

**Drop the locale-sensitive sort and rely on SQL `ORDER BY title`.** Rejected: SQL collation does not follow the reader's language conventions. `Collator` with `PRIMARY` strength is the correct behaviour; it is only its *location* that is wrong.

## Consequences

**Good.** No forced churn, no schema risk, and the sorting behaviour that users actually see is correct today.

**Bad.** `DatabaseDao.kt` remains 2,115 lines mixing three concerns. Sorting rules are tested only via Room-backed fixtures, which is why the `db/` test suite needs comparatively heavy scaffolding for comparatively little coverage. Adding a sort option means touching up to thirteen dispatch sites.

**Neutral.** The 176 `@Query` functions are correctly placed and are not in question. This ADR concerns the ~60 that are not.

## Follow-up

When `DatabaseDao` is next touched for reasons unrelated to sorting, extract the pure rules in the same pass. The intended shape: a locale-aware sort module holding the `Collator` comparators, the sort-enum dispatch, and the album-grouping algorithm — capturing `Locale.getDefault()` once at construction rather than 20 times per Flow emission, which is a small but real per-emission cost today.

**Do not edit the database schema to accomplish this.** The extraction must be pure Kotlin over data already returned by queries.