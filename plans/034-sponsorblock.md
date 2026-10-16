# Plan 034 — SponsorBlock integration

**Status: not started.** Verified at commit `4a3bee56` — no SponsorBlock integration exists. The app
handles video songs but has no sponsor-segment skipping. SponsorBlock is a community-driven API that
returns timestamp segments to skip in sponsored YouTube videos.

## Objective

Add SponsorBlock integration: fetch segment markings for video songs from the SponsorBlock API, let the
user select which categories to skip (default: off), and skip those segments during playback. Only for video
song content (not audio-only). Off by default.

## Why

- Matrix #7: SimpMusic ships SponsorBlock; no other YTM client does. Audify plays video songs — adding
  sponsor skipping is a genuine value-add for video song listeners.
- The SponsorBlock API is public and community-driven — no auth, no tokens for the basic usage. Low backend
  cost.
- Off-by-default + category selection respects users who don't want it.

## What exists today

- Video song playback exists (`MusicService`, `PlaybackIntent.StartRadio` for video mediaIds, etc.).
- No segment-skipping logic, no SponsorBlock client, no category settings.
- The Media3 player supports seeking + position tracking — the primitives for segment skipping exist at the
  player level (seek to a position, detect when a segment starts).

## Scope

**In scope:**
- A SponsorBlock REST client: fetch segments for a videoId from the SponsorBlock API (the public API).
  Cache segments per videoId (with TTL — segments can be updated by the community).
- Category selection: a settings UI where the user picks which categories to skip (e.g. Sponsorship,
  Self-Promotion, Interaction, Mini-Video, Intermission, Pedvertisement, Selection — check the current SB
  API category list at implementation time). Off by default; user opts into categories.
- Segment skipping at playback: when playing a video song with fetched segments, at playback time detect
  when the player enters a skip-able segment and seek to its end. Implement at the player/MusicService level
  using position tracking + seek.
- Only for video songs — audio-only songs have no segments. The feature is gated on the media being a video
  song with a videoId.
- Off by default — the feature must be explicitly enabled + categories selected before any skipping happens.

**Out of scope:**
- Submitting segment markings back to SponsorBlock (users can't submit from the app in v1).
- A segment timeline UI (showing the skip markers on a timeline) — defer; v1 is silent skipping, not a
  visual timeline.
- Category-specific skip behavior beyond "skip the segment" — v1 skips the selected categories, no per-category
  custom behavior.
- Sponsorship detection for audio-only content — not applicable.

## Affected files

| File | Change |
|------|--------|
| `playback/SponsorBlockClient.kt` (new) | REST client: fetch segments for a videoId, parse the SB response, cache with TTL |
| `playback/SponsorBlockSegmentCache.kt` (new) | In-memory + optional disk cache of segments per videoId, with TTL refresh |
| `constants/PreferenceKeys.kt` | Add `SponsorBlockEnabledKey`, per-category enabled keys (e.g. `SB_SkipSponsorshipKey`, etc.) |
| `ui/screens/settings/integrations/SponsorBlockSettings.kt` (new) | Settings UI: enable/disable, category toggles, "last fetched" status, manual refresh |
| `ui/screens/settings/integrations/IntegrationsSettings.kt` (or the host) | Add SponsorBlock section |
| `playback/MusicService.kt` (or the video playback path) | When playing a video song with segments, at position update time detect segment entry and seek to segment end for enabled categories |
| `sachit_strings.xml` | SponsorBlock labels, category names, "Skip sponsorships", "Off by default", error strings |

## Technical approach

1. **SponsorBlock API** — At implementation time, check the current SB API (API root, fetch endpoint,
   response format, category list, rate limits). The plan deliberately doesn't hard-code endpoints — the
   executor checks the API and implements the client against the current spec. The key is: fetch segments
   per videoId, cache them, skip enabled categories.
2. **Segment cache** — Fetch segments for a videoId when the video starts playing (or when the cache is
   stale). Cache in-memory (and optionally disk) with a TTL — segments can be updated by the community, so
   re-fetch after a reasonable window (e.g. 24h) or on manual refresh. Don't refetch on every play.
3. **Category selection** — A settings grid of SB categories with toggles. Off by default — the user opts
   into categories. The default-off + per-category model respects users who don't want any skipping.
4. **Segment skipping** — At playback time, on position updates, check if the current position is inside a
   cached segment for an enabled category. If so, seek to the segment's end. Implement at the
   `MusicService`/player level using the position stream. Watch for edge cases: segment end beyond video
   duration, rapid re-entry, segments that overlap. Keep it simple — skip to end, don't try to be clever.
5. **Gating** — Only for video songs with a videoId + fetched segments + SB enabled + at least one category
   enabled. If any of those is missing, no skipping. Audio-only songs → no segments → no skipping.
6. **Manual refresh** — A "Refresh segments" action in the settings (re-fetches segments for the current
   video or clears the cache). Primarily for debugging / stale cache recovery.

## Testing

- Unit test for `SponsorBlockClient` (mock HTTP): fetch segments for a videoId → assert the parsed segments
  have correct categories + timestamps. Don't hit the real API in unit tests.
- Unit test for segment skipping logic (pure function): given a list of segments, current position, and
  enabled categories, assert the correct seek-to-end decision.
- Unit test for the cache: fetch → cache → re-fetch after TTL → refresh.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: play a video song that has SB segments (use a known sponsored video), enable SB + a category,
  verify the player skips the segment. Test with SB off (no skipping), with a category off (no skip for that
  category), with no segments cached (no skip). Test audio-only songs → no skipping.
- Regression: audio-only playback unchanged; video playback without SB unchanged; seeking still works.

## Performance notes

- Segment fetch is a small REST call on video start (cached after). Cheap.
- Position-check on each position update is a small loop over cached segments — cheap for typical segment
  counts (a few segments per video).

## Rollback

- Remove the SB client, cache, settings UI, and the segment-skipping logic → no SB, video playback unchanged.
- The feature is additive and off by default — rollback is just removing the code path.

## Anti-BitChord check

- SponsorBlock is a public, community-driven API — using it is standard, not a copy of SimpMusic's
  integration. Implement Audify-native: own client, own cache, own settings UI. Don't copy SimpMusic's SB
  code.
- Off-by-default + per-category selection respects users who don't want sponsor skipping. The feature is
  opt-in.
