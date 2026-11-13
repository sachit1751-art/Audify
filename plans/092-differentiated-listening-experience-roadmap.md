<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 092 — Audify Differentiated Listening Experience Roadmap

**Status: roadmap proposal — reconcile linked plans and current source before implementation.**  
**Purpose:** Coordinate five product investments that improve the overall listening experience and give Audify a clear identity. This is an umbrella roadmap; it does not duplicate or replace the detailed implementation plans linked below.

## Product direction

Audify should feel like a music companion that helps listeners discover music, connect with other listeners, move between devices, and understand their own taste. The goal is not to copy Spotify or YouTube Music screen-for-screen. Build on Audify's existing multi-platform client, playback, discovery, statistics, and social foundations.

## Priority and implementation difficulty

| Workstream | Outcome | Existing plan ownership | Relative difficulty |
|---|---|---|---|
| Personal music identity | Useful listening insights and shareable, privacy-safe taste summaries | [06 — Statistics](06-selected-17-statistics-dashboard.md), [083 — Shareable Music Cards](083-shareable-music-cards.md), [082 — Listening Challenges](082-listening-challenges.md) | Low–medium for a first share-card slice; medium–high for full historical insights |
| Intelligent discovery | Recommendations that adapt to available listening signals and explain why an item appears | [07 — Explainable Listening Recommendations](07-explainable-listening-recommendations.md), [079 — Smart Music Radio](079-smart-music-radio.md) | Medium |
| Social music ecosystem | First-party profiles, username search, follows, and later shared activity | [091 — AUDIFY Identity and Lightweight Social Layer](091-audify-identity-and-social-layer.md), [075 — Collaborative Playlists](075-collaborative-playlists.md) | High; backend, identity, privacy and durable storage |
| Deeper music exploration | Better paths from a song or artist into related artists, genres, releases and radio | [07 — Explainable Listening Recommendations](07-explainable-listening-recommendations.md), [079 — Smart Music Radio](079-smart-music-radio.md), [05 — Advanced Search Filters](05-selected-13-advanced-search-filters.md) | Medium; depends on provider capabilities |
| Cross-device continuity | Continue and control playback across phone, desktop, TV and watch | New architecture proposal in this roadmap; reconcile with MediaSession, existing playback and device integrations first | Very high; synchronization service and conflict handling |

Difficulty is a planning estimate, not a promise. It must be revised after source, provider, backend and deployment audits.

## Recommended first implementation

**Start with the small Personal Music Identity slice: Plan 083 — Shareable Music Cards.**

Why this is the easiest useful starting point:
- It can build on existing track, album, playlist and artwork metadata.
- It can use local card rendering and the platform share sheet.
- It does not require a new account system or always-on backend.
- It creates a visible, shareable Audify artifact without pretending that a complete taste model already exists.

Before implementation, audit current deep links, artwork permissions/attribution, sharing APIs and existing recap/statistics data. Keep private listening history opt-in; never expose library names or personal activity by default. Plan 06 remains the owner of trustworthy statistics and insights. Do not implement a second statistics pipeline inside Plan 083.

## Workstream briefs

### A. Personal music identity

**User value:** Give listeners a useful view of their music taste and a way to share selected parts of it.

1. Start with Plan 083 share cards for a track, album, playlist, or explicitly selected listening milestone.
2. Reuse verified aggregate metrics from Plan 06 for any listening summaries.
3. Add taste evolution only when the underlying history supports it; do not infer exact play history from aggregate counters.
4. Keep every share action user-initiated, previewable and reversible before sharing.
5. Make private-by-default behavior explicit and provide a clear opt-in for listening statistics.

**Success measures:** card generation success, share-sheet completion, deep-link open rate where measurable without invasive tracking, and zero unintended private-data exposure.

### B. Intelligent discovery

**User value:** Help listeners find relevant music without getting trapped in repetitive recommendations.

1. Plan 07 owns the single recommendation/ranking contract.
2. Use only signals verified in the current repository; label each signal exact, aggregate, inferred, provider-supplied or unavailable.
3. Add a small, understandable familiarity-versus-exploration control.
4. Explain recommendations only when the explanation matches the actual ranking input.
5. Extend the same ranking into Plan 079 radio rather than creating a competing recommendation engine.

**Success measures:** recommendation diversity, duplicate rate, meaningful plays/saves where existing product signals support measurement, and user feedback. Do not upload listening history as part of this roadmap.

### C. Social music ecosystem

**User value:** Let listeners find people with similar interests and build music experiences together.

1. Plan 091 is the canonical first-party identity and social-graph plan and retains its P0 execution priority.
2. Ship verified identity, profile, username search and follow relationships before any social feed.
3. Define privacy controls before exposing listening activity.
4. Only after identity and durable storage are verified, proceed to Plan 075 Shared Playlists.
5. Treat provider accounts as optional integrations, never as proof of Audify identity.

**Success measures:** successful account/profile creation, search success, follow/unfollow reliability, abuse-report handling, and authorization tests. Do not begin a social activity feed or collaborative playlist backend ahead of Plan 091's gates.

### D. Deeper music exploration

**User value:** Make it easy to move from one interesting item to meaningful related music.

1. Audit provider support for related artists, artist discography, albums, genres, release dates and radio seeds.
2. Improve existing Explore/Search surfaces rather than adding another disconnected destination.
3. Reuse Plan 07's ranking and Plan 079's radio session.
4. Provide useful empty/error states when a provider does not expose a relation.
5. Avoid fabricated artist relationships, genre labels, release metadata or availability.

**Success measures:** successful discovery-to-play flows, fewer dead ends, result diversity, and provider-error recovery.

### E. Cross-device continuity

**User value:** Start playback on one Audify client and continue or control it from another.

This is the most technically demanding workstream and must not be presented as a simple UI feature.

1. Audit the current playback owner and capabilities on Android, Android TV, Wear OS and desktop.
2. Define a device/session identity model and a canonical playback-session protocol before choosing backend technology.
3. Specify ownership and conflict rules for play, pause, seek, queue edits, track transitions, reconnects and simultaneous commands.
4. Separate remote-control capability from true playback handoff; a device must not claim it can transfer a stream unless the target can resolve and play the same media.
5. Prototype with two clients first. Add persistence, authentication, reconnect behavior, observability and abuse protection before broad rollout.
6. Keep local playback fully functional when the network or sync service is unavailable.

**Success measures:** correct state convergence, command latency, reconnect recovery, no duplicate/phantom playback, and no loss of the local queue. No device should silently take playback ownership.

## Dependency and execution order

1. **Plan 091 remains P0** as already established in plans/README.md; complete its identity/backend decision gates before social features or Plan 075.
2. **Plan 083 is the easiest independent first delivery** and can proceed after a focused rights/deep-link/sharing audit.
3. Reconcile Plans 06, 07, 079 and 05 against current source; avoid duplicate metrics, ranking and discovery surfaces.
4. Treat cross-device continuity as a separate architecture/research phase after playback ownership is mapped on every platform.
5. Do not run all workstreams concurrently. Finish one bounded vertical slice, verify it, then expand.

## Repository audit required before implementation

The plans linked above contain historical source notes. They are not proof that current code still matches those notes.

Before starting any workstream:
- Read docs/PRODUCT.md, docs/DESIGN.md, plans/README.md and applicable repository instructions.
- Inspect current source, tests, provider capabilities, backend deployment and data ownership relevant to that workstream.
- Record the current HEAD and identify plan drift.
- Reuse existing feature owners; update their plan rather than creating duplicate implementations.
- Follow all repository constraints, including no version bump, no database schema/migration changes without explicit approval, no translated-string edits, and no commit/push without explicit authorization.

## Global acceptance criteria

- Existing playback, library, search, offline use and navigation remain functional.
- Each platform receives an experience appropriate to its input model and screen size; do not force phone UI onto TV, watch or desktop.
- Network-dependent features fail gracefully and do not block local playback.
- Privacy settings and data ownership are explicit; no undisclosed listening-history upload.
- Automated tests cover domain rules, failure paths, privacy and cross-platform contracts where applicable.
- Report actual build/test/device results; never describe untested behavior as verified.

## Rollback

Each workstream must be independently feature-gated where practical. Disable the new surface or service without deleting existing user data, breaking local playback, or changing the behavior of unrelated features. Cross-device sync must always allow a client to return to independent local playback.
