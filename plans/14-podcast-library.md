<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 14 — Podcast library and episode discovery

**Status: proposal — requires an explicit product-scope decision before implementation.**

## Objective

Evaluate a focused podcast experience that lets users discover or add podcast feeds, browse episodes, and listen with reliable resume/progress behavior without destabilizing Audify's music-first library and player.

## Product decision required

Podcasts are a distinct content type with feed refresh, episode metadata, progress, artwork, and enclosure-download semantics. This is not a small extension to song search. Decide whether Audify should support podcasts at all before building a new navigation or persistence surface.

## Scope

**In scope if approved:** RSS/Atom feed feasibility; explicit add/search flow; show and episode browsing; episode queue/playback; resume position and played state; optional download controls; clear source and refresh status.

**Out of scope:** proprietary podcast catalogue scraping, automatic subscriptions, background feed polling by default, advertising insertion, social/community feeds, or changing music queue semantics without a clear content-type boundary.

## Research and implementation

1. Compare user value and maintenance cost against Audify's core music roadmap. Obtain explicit approval for a podcast product surface.
2. Define supported feed formats, enclosure media types, redirects, authentication limitations, feed size limits, and malformed-feed behavior.
3. Inspect current playback abstractions to determine whether episode media can be represented without weakening track identity, queue occurrence, or source semantics.
4. Design subscription refresh as user-controlled and bounded; no unbounded background polling. Document network and battery behavior.
5. Define episode progress/resume semantics and storage ownership. Any schema or migration requirement is a hard stop requiring explicit approval.
6. Add feed and enclosure validation, safe download handling, and clear unsupported-format states.
7. Test real feeds from multiple publishers, long episodes, interrupted playback, redirects, offline behavior, and feed changes.

## Acceptance criteria

- Product-scope approval is recorded before implementation.
- Feed parsing is bounded and robust to malformed or oversized documents.
- Episode identity and resume position remain stable across refreshes.
- Podcast items are clearly distinguished from music tracks in search, queue, and library.
- Refresh/download work is user-controlled, cancellable, and transparent.
- No feed or enclosure is treated as trusted input; network and media handling are validated.
- Any required schema change is separately approved before work proceeds.

## Rollback

Remove podcast navigation and feed integration while preserving existing music library/playback data. Do not delete downloaded user media as incidental cleanup.

## Mandatory pre-implementation audit

Inspect current search, playback, queue, downloads, source resolution, persistence, and any historical podcast/content-type plans. Document architecture fit, schema impact, threat model, and exact tests. Stop for explicit product and schema approval where required.

## Project constraints and handoff

Follow `plans/README.md`, `PRODUCT.md`, and `DESIGN.md`. No version bump, schema/migration, translated-string edits, or unauthorized commits/pushes. A plan or prototype does not imply public podcast support.
