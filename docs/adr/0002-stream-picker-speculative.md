# ADR-0002: StreamPicker stays speculative until the extractor surfaces candidates

**Status:** Accepted
**Date:** 2026-10-02
**Context:** plan 016 (lossless audio)

---

## Context

Lossless playback (FLAC / ALAC) requires choosing between audio variants. The upstream extractor this fork depends on (`InnerTubeXPlayer`) currently resolves playback to a **single** stream — it returns one `PlaybackData`, not a list of candidates. There is therefore nothing for a selection algorithm to select between.

`utils/StreamPicker.kt` was written anyway, and this is a deliberate choice rather than an oversight. Its own doc comment states the situation plainly:

> The innertubex extractor currently returns a single resolved stream, so the candidate-based picker is exercised by unit tests and ready for the moment the fork surfaces multiple audio variants.

Two functions exist:

| Function | Status |
|---|---|
| `isLossless(candidate)` | **Live.** Called from `ui/utils/ShowMediaInfo.kt` and `ui/player/PlaybackStatsSheet.kt` to drive the "Lossless" badge. |
| `pickBestStream(streams, wantLossless)` | **Speculative.** Zero call sites in main or test sources outside its own definition. |

`pickBestStream` ranks lossless candidates first (highest bitrate), then falls back to highest-bitrate lossy. Its unit test (`utils/StreamPickerTest.kt`) passes, which is precisely the point: the ranking policy is settled before it is needed.

## Decision

**Keep both functions in the tree. Keep the honest doc comment. Do not wire `pickBestStream` to anything until the extractor returns multiple candidates.**

The **badge** is the load-bearing half and must stay honest. `isLossless` reports only whether the format *currently playing* is genuinely FLAC or ALAC. It must never be derived from a user preference or an availability flag — the badge describes reality, not capability.

When the extractor does surface candidates, the integration is expected to route through `pickBestStream` rather than growing a second ranking implementation at the call site.

## Alternatives considered

**Delete `pickBestStream` until it is needed.** Rejected: the ranking policy is the part that benefits from being settled in advance and covered by tests while it is still cheap to change. Reconstructing it later means re-deciding lossless-vs-bitrate precedence under time pressure, which is how you end up promising lossless and delivering 320kbps.

**Delete the whole module; inline `isLossless` at its two call sites.** Rejected: the badge logic would then be free to drift between the media-info sheet and the playback-stats sheet, and the lossless marker list would exist in two places.

**Wire `pickBestStream` to a fabricated single-element candidate list today.** Rejected: it manufactures a call site that looks live while testing nothing. That is strictly worse than an obviously-unused function, because it makes the gap invisible.

## Consequences

**Good.** The lossless badge cannot over-promise. When multi-variant extraction lands, the selection policy and its tests already exist.

**Bad.** A function with no call sites will read as dead code to any future maintainer or automated dead-code pass. **This ADR is the mitigation** — the reason it is written down is that the alternative explanations ("forgot to delete", "half-finished") are both wrong.

**Neutral.** `isLossless` callers are unaffected.