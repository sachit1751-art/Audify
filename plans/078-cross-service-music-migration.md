<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 078 — Cross-service Music Migration

**Status: proposal — companion scope for Plan 03; Plan 03 remains the canonical import pipeline.**

## Outcome

A user can bring a playlist from a supported music service into Audify using a user-authorized export or documented provider flow. Track order is preserved, matches are reviewed, and unmatched songs are clearly reported.

## Implementation

1. Extend Plan 03's format-neutral import model; do not create a second parser, matcher, or playlist-creation path.
2. Start with user-provided exports and documented APIs. Never request passwords, scrape private sessions, or bypass provider restrictions.
3. Normalize source title, artist, album, duration, and legitimate provider IDs; match against Audify's available catalogue.
4. Show matched, ambiguous, duplicate, and unavailable tracks with reasons; let users review before creating the playlist.
5. Preserve order and duplicate occurrences. Make create-new the safe default; add merge only if existing playlist semantics support it.

## Verification

- Test fixtures for each supported source/export format, malformed rows, Unicode, duplicates, missing metadata, and large files.
- Assert every source row has a visible outcome and ambiguous matches are never silently selected.
- Test cancellation and failures leave existing playlists unchanged.
- Manually import a real user-owned export and compare source order/count with the preview and result.

## Rollback

Disable newly added source adapters while retaining the existing Plan 03 Spotify CSV/TSV importer.

## Guardrails

Plan 03 owns parsing, matching, preview, and atomic playlist creation. This plan adds supported sources only; no credential collection, scraping, or unsupported provider promises.
