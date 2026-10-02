# ADR-0001: Resolve artist aliases at ingest, not at every read

**Status:** Accepted
**Date:** 2026-10-02
**Context:** `CONTEXT.md` — *Artist alias*

---

## Context

An **artist alias** is a user-chosen display name for an artist, keyed by channel id rather than by name (see `CONTEXT.md`). The rule is simple: *display name = the user's override if one exists, else the name YouTube supplied*. Aliases chain — one alias may point at a name that is itself aliased — so resolution follows the chain to its end and must terminate even on cyclic data.

The rule is implemented in `utils/ArtistNameAliases.kt` and is expressed at **51 call sites across 14 files**, in **two incompatible shapes**:

```kotlin
ArtistNameAliases.resolve(it.id, it.name)             // 30 sites — reads a process-global singleton
ArtistNameAliases.resolve(aliasMap, it.id, it.name)   // 21 sites — reads an explicitly passed map
```

Both shapes exist for a structural reason: the alias store is a process-global object that needs a `Context` to initialize, but Compose needs synchronous reads during composition, which a suspend-backed store cannot provide. `MainActivity` bridges the gap by mirroring the alias map into a `CompositionLocal` (14 consumers). Each new call site therefore has roughly a coin-flip chance of picking the wrong shape.

The two shapes are not merely redundant — they can **disagree**. The singleton form returns the *unresolved* name if `initialize()` has not yet run, while the map form works regardless. That failure is invisible in development (the app is warm) and surfaces as wrong artist names on cold start, in tests, and anywhere the `App` initializer has not run.

Separately, some paths resolve **on write** rather than on read: `utils/SyncUtils.kt` and `viewmodels/ArtistViewModel.kt` resolve names as they persist entities, so that stored rows carry display-ready names.

## Decision

**Resolve once, upstream, at the boundary where a track's metadata enters the system — and on write for anything persisted. Never at the point of display.**

`models/MediaMetadata.kt` already touches aliases in three places when constructing metadata. That becomes the only read-path seam. The 48 downstream read-path call sites and the `CompositionLocal` are deleted.

Concretely:

- Display name becomes a **property of the model**, not a step each reader must remember.
- Persistence keeps resolving on write, unchanged — stored rows stay display-ready.
- `ArtistNameAliases` exposes exactly one resolution entry point taking an explicit alias map. The implicit-singleton overload is removed, so the cold-start hazard is unrepresentable rather than merely documented.

## Alternatives considered

**Keep both APIs, add a lint rule forbidding the singleton form.** Rejected: it preserves the disagreement between the shapes and relies on static analysis to prevent the exact class of bug that motivated this decision. It also keeps a `CompositionLocal` whose only purpose is to work around a global's initialization.

**Resolve at read time only, dropping write-time resolution.** Rejected: it forces every reader to resolve, which is the status quo, and makes stored rows hold names that may become stale or incorrect after an alias change.

**Resolve on read, and re-resolve everything on alias change.** Rejected: a full re-resolution sweep touches persisted rows and every cached metadata object at once, for a rule that is cheaper to apply once on the way in.

## Consequences

**Good.** One rule, one owner, one place to test. New surfaces — widgets, notifications, the media session — display correct names without knowing aliases exist. The cold-start hazard disappears because the implicit global is gone. `ArtistNameAliasesTest` shifts from testing the alias *store* to testing the resolution *rule*, which is the part that actually breaks.

**Bad.** Requires a careful pass over all 51 sites, and it is a genuine behaviour change: any display path that currently forgets to resolve will start showing resolved names, which is the point but will look like a regression in review.

**Neutral.** Paths that already resolve on write (`SyncUtils`, `ArtistViewModel`, `DatabaseDao`) are unaffected and keep doing so.

## Follow-up

The 51-site sweep is mechanical but must be done in one pass — a partial migration leaves two live seams, which is worse than either end state. Land it as a single commit.