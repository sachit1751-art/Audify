<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 12 — Lyrics quality and language tools

**Status: proposal — reconcile with plans 02 and existing lyrics translation work.**

## Objective

Improve lyric match quality and provider resilience, then add user-controlled correction/import and language presentation features (translation and romanization) only where reliable sources and licensing permit.

## Existing foundation to verify

Audify already supports synchronized line-/word-level lyrics, provider selection, offsets, lyric navigation, and a translation foundation. Plan 02 covers accessibility-first lyrics. This plan must not duplicate accessibility layout/preferences or existing translation functionality.

## Scope

**In scope:** provider capability/terms matrix; match confidence and mismatch recovery; bounded provider fallback; manual search and user correction; import of supported local lyric formats; translation/romanization feasibility; clear provenance and reset behavior.

**Out of scope:** scraping unsupported services, claiming perfect synchronization, silently replacing a user's selected lyrics, automatic upload of local files, or adding AI translation without a separately approved provider/data-flow decision.

## Implementation

1. Reconcile Plan 02, `LYRICS_TRANSLATION.md`, and older lyrics-related plans. Separate accessibility requirements from provider/matching requirements.
2. Inventory current lyric providers, matching keys, confidence signals, cache rules, format parsers, translation flow, and license/attribution obligations.
3. Define a match-quality contract using available metadata (title, artist, duration, album/version) and preserve an explicit no-match/ambiguous state.
4. Add bounded provider fallback with cancellation, timeouts, deduplication, and clear source attribution; never use hidden APIs or unbounded retries.
5. Let users search/select/correct lyrics explicitly. Preserve the current selection until the replacement is confirmed.
6. Support local import only for formats the parser can validate; preview content and timing before applying it.
7. Evaluate translation and romanization separately. Document language coverage, provider terms, network/data flow, and uncertainty; do not label machine output as verified translation.

## Acceptance criteria

- Wrong-version matches are less likely and ambiguous results are surfaced rather than silently accepted.
- Provider fallback is bounded, cancellable, deduplicated, and tested under timeout/outage conditions.
- User-selected lyrics are not silently overwritten by background refresh.
- Imported content is validated and previewed; malformed files fail safely.
- Translation/romanization labels disclose machine/provider origin and supported languages.
- Existing synchronization, seeking, accessibility, and manual-focus behavior remains intact.
- Provider terms, attribution, and privacy implications are documented.

## Rollback

Disable new provider/matching paths and restore the previous provider selection flow without deleting cached/user-selected lyric content.

## Mandatory pre-implementation audit

Inspect Plan 02, `LYRICS_TRANSLATION.md`, all existing lyrics providers/parsers, and relevant historical plans. Map exact ownership and supported formats before editing. Any external AI/translation service or new upload flow requires explicit approval.

## Project constraints and handoff

Follow `plans/README.md`, `docs/PRODUCT.md`, and `docs/DESIGN.md`. No schema/migration, version bump, translated-string edits, or unauthorized commits/pushes. Report provider limitations and unverified lyric timing explicitly.
