# Plan 021 — Stream source health + fresh-resolution fallback

Written against commit **`605975d5`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`605975d5`, check drift in the in-scope files first.

**Order note:** implement after plan 019 (Signal sheet) so the health state has a ready-made
surface. It is otherwise independent.

## Goal

Stop re-trying a broken stream the same way. Today every playback error path re-resolves
through the same cache/client chain, so a poisoned URL or a throttled client can burn the full
retry budget identically each time. This plan adds **per-mediaId failure memory with backoff**:
the second failure within a session bypasses the URL cache and forces a genuinely fresh
resolution (different generation + excluded-client hint), and the current health state is
visible in the Signal sheet ("Healthy / Recovering (attempt 2) / Fallback active").

## Existing infrastructure (recon at `605975d5`)

- `playback/StreamUrlCache.kt`: in-memory URL cache with **generations** — `invalidate(mediaId)`
  bumps the generation so stale `put`s are rejected (`expectedGeneration`). `clientName(mediaId)`
  reports which client served the failed URL (read in `onPlayerError` line 3017 as
  `failedStreamClient`). **The generation mechanism is exactly the hook for "fresh resolution":
  invalidate + remember the failed client.**
- `MusicService.onPlayerError` (line 3008): rich error classification (audio-renderer, 416,
  page-reload, expired-URL 403/410, ENOENT, remote, network-related) already routes to
  dedicated handlers; `performAggressiveCacheClear` (line ~3094) already calls
  `songUrlCache.invalidate(mediaId)` on every error. Retry budget: `currentMediaIdRetryCount` +
  `MAX_RETRY_PER_SONG` (`hasExceededRetryLimit`, line ~3113) → `markSongAsFailed` +
  `handleFinalFailure`.
- `utils/InnerTubeXPlayer.kt`: already has a **per-mediaId failure-exclusion precedent** —
  `webRemixFailures` map + `hasRecentWebRemixFailure(videoId)` + `markWebRemixFailed(videoId)`
  exclude `WEB_REMIX` from extraction after a failure (TTL-based). The plan generalizes this
  pattern at the service layer rather than inside InnerTubeX (client-agnostic).
- The resolution choke point: `createDataSourceFactory` → `ResolvingDataSource.Factory` block
  (~line 3737): cached-stream branch (`songUrlCache[mediaId]?.let { ... }`) and fresh-fetch
  branch (`playerResponseForPlayback` with `excludedClients` built inside InnerTubeXPlayer).
- `music/` package exposes what flows to UI via `PlayerConnection.currentStreamClient` (line 193).

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/playback/StreamSourceHealth.kt` | **New.** Pure health tracker (counts, backoff, state enum) |
| `app/src/main/kotlin/com/sachit/music/playback/MusicService.kt` | Hook error paths + consult health in the resolution block; expose health StateFlow |
| `app/src/main/kotlin/com/sachit/music/playback/PlayerConnection.kt` | Expose `streamHealth` StateFlow (same delegation pattern) |
| `app/src/main/kotlin/com/sachit/music/ui/player/PlaybackStatsSheet.kt` | One new row: source health (only if plan 019 landed; otherwise add the row in 019's follow-up) |
| `app/src/main/res/values/sachit_strings.xml` | Health strings (names below) |
| `app/src/test/java/com/sachit/music/StreamSourceHealthTest.kt` | **New.** Tracker tests |

Out of scope: `InnerTubeXPlayer.kt` internals (no fork changes — the excluded-client hint rides
the existing `webRemixFailures`-style mechanism or arrives as a parameter), `DownloadUtil.kt`
(download-side failure memory is a later plan), persistence (session-scoped only), `values-*`.

## Design

**`StreamSourceHealth`** (pure):

```kotlin
enum class StreamHealth { HEALTHY, RETRYING, FALLBACK }

class StreamSourceHealth(
    private val fallbackThreshold: Int = 2,      // failures before fresh-resolution mode
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // per mediaId: failure count, first-failure timestamp, last failed client
    fun onFailure(mediaId: String, clientName: String?)
    fun onSuccess(mediaId: String)                  // clears the entry
    fun state(mediaId: String): StreamHealth
    fun failedClient(mediaId: String): String?      // for client exclusion hint
    fun clear(mediaId: String); fun clearAll()      // queue change / dispose
}
```

`RETRYING` after 1 failure, `FALLBACK` at ≥ `fallbackThreshold` (2). Entries expire after
5 min without events (TTL, same spirit as InnerTubeX's `WEB_REMIX_FAILURE_TTL_MS`).

**Service behavior:**

1. In `onPlayerError` (top, next to `performAggressiveCacheClear`): `health.onFailure(mediaId,
   failedStreamClient)`.
2. In the resolution block's **cached-stream branch**: only take the cached URL if
   `health.state(mediaId) != FALLBACK` — otherwise skip the cache (the generation bump from
   the existing invalidate already forces a fresh fetch) **and** remember `failedClient` so
   the retry avoids it (pass-through: if InnerTubeXPlayer grows an `excludeClient: String?`
   parameter, pass it; otherwise log-only in v1 — the fresh generation alone usually fixes
   URL-class poisoning).
3. On successful stream start (`onMediaItemTransition` to a new mediaId, or first READY for
   that id): `health.onSuccess(mediaId)`.
4. `clearAll()` in `playQueue` alongside plan 020's clear (a new queue is a new session).
5. Expose `state(mediaId)` for the current mediaId as a `StateFlow<StreamHealth>` updated on
   every mutation (map from the tracker to keep UI dumb).

Failure-type filtering: only count failures the retry path can actually influence — the I/O
family (expired-URL, remote, generic-IO, page-reload, ENOENT). Do **not** count
audio-renderer errors (device-side) or offline waits (network-absent) — those would poison
the health state with non-source failures.

## Implementation steps

1. **Drift check**: read `onPlayerError` handlers end-to-end to confirm which paths re-resolve
   (handleExpiredUrlError / handleGenericIOError / handlePageReloadError /
   handleFileNotFoundError / handleRangeNotSatisfiableError) and where
   `performAggressiveCacheClear` sits relative to them; confirm the cached-stream branch
   layout; check InnerTubeXPlayer for an exclusion parameter (recon says `excludedClients` is
   built internally from `webRemixFailures` — if there's no external parameter, v1 is
   generation-bump only and the plan still stands).
2. **`StreamSourceHealth.kt`** + tests (thresholds, TTL expiry, client tracking, clear paths).
3. **Service wiring**: onFailure hook (filtered failure classes), cache-skip in FALLBACK state,
   onSuccess on transition/READY, clearAll on new queue, StateFlow exposure.
4. **PlayerConnection** delegation.
5. **Signal sheet row** (plan 019 surface): `playback_stats_source_health` with the three
   states — Healthy (default, maybe hidden to reduce noise: show only when not HEALTHY —
   decide in review, default show-always for honesty).
6. **Strings** (exact names): `playback_stats_source_health`,
   `playback_stats_source_health_ok` ("Healthy"), `playback_stats_source_health_retrying`
   ("Recovering (attempt %1$d)"), `playback_stats_source_health_fallback` ("Fallback active —
   fresh source next track").
7. **Build + tests + fault-injection manual pass** (below).

## Testing

- **Unit** (`StreamSourceHealthTest`): 1 failure → RETRYING; 2 → FALLBACK; success clears;
  TTL expiry; clear/clearAll; failedClient tracking; non-counted classes are the service's
  job (tracker stays pure).
- **Manual fault injection**: enable airplane mode mid-song → error → disable → observe
  Recovering state in the Signal sheet and successful fresh resolution; simulate a poisoned
  URL by pausing + clearing app network permission (or `adb shell cmd network` tricks) —
  confirm second failure takes the no-cache path (visible via the `FETCHING STREAM` /
  `BYPASSING CACHE` log lines); regression: normal playback never logs fallback; expired-URL
  single-failure recovery unchanged; auto-skip-on-error still honored after retry limit.

## Performance notes

One small map, mutated only on errors/successes (rare), TTL-swept lazily on access. UI row
reads a StateFlow that changes at most a few times per incident. No I/O anywhere; session-only
memory (a few hundred bytes worst case).

## Rollback

Single revert: new file + hooks. The cached-branch guard is one boolean expression; removing
it restores today's behavior exactly. `webRemixFailures` (InnerTubeX's own) is untouched.

## Anti-clone / visual identity

BitChord's `PlaybackFallback` is a URI-marker scheme for its addon-source system and is
invisible to users; Audify's version is client-agnostic (works for plain YouTube streams),
uses the existing generation-cache mechanism instead of URI markers, and **surfaces its state
honestly in the Signal sheet** — capability parity, different engineering and different UX.

## Considered and rejected for this plan

- **Download-side failure memory** (sidecar JSON for re-resolution on retry) — worth doing,
  but downloads are a different pipeline (`DownloadUtil`); separate plan after this one proves
  the tracker.
- **Global (not per-mediaId) client blacklisting** — too blunt: one bad video would degrade
  the client for everything.
- **Persisting health across process death** — stale by definition; session scope is correct.
