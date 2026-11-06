<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 079 — Smart Music Radio

**Status: proposal — extend Plan 07's existing discovery and recommendation pipeline.**

## Outcome

A listener can start an endless, continuously replenished queue from a song, artist, album, or genre. Results balance familiar music with discovery and explain why each recommendation appears.

## Implementation

1. Audit Plan 07's ranking owner, provider capabilities, available signals, queue insertion APIs, and current duplicate rules.
2. Add a radio session seeded by a supported item; reuse Plan 07's deterministic ranking and explanation model.
3. Add simple controls for more familiar, balanced, or more exploratory results, plus exclude/less-like-this feedback where supported.
4. Replenish the existing queue in bounded batches; deduplicate by stable track identity and avoid interrupting the current track.
5. Handle provider limits, unavailable tracks, network loss, and empty results with clear recovery actions.

## Verification

- Unit-test seed handling, ranking determinism, deduplication, missing metadata, provider failures, and queue continuation.
- Verify starting radio never clears or unexpectedly reorders the user's existing queue.
- Manually test song, artist, album, and genre seeds, plus offline/reconnect behavior.
- Confirm every explanation reflects an actual ranking input and no unavailable listening signal is fabricated.

## Rollback

Remove the radio entry point and stop replenishment; preserve the existing discovery shelves and manual queue.

## Guardrails

Plan 07 remains the recommendation engine owner. Do not create a parallel recommendation system, upload listening history, or claim personalization unsupported by available data.
