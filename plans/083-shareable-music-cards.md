<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 083 — Shareable Music Cards

**Status: proposal — implement with platform sharing and rights-safe content.**

## Outcome

Users can create attractive share cards for a track, album, playlist, or listening milestone and share them outside Audify. Each card should make it easy for a recipient to open the corresponding item in Audify when installed.

## Implementation

1. Audit current artwork access, deep links, Android Sharesheet integration, and provider attribution requirements.
2. Create a small set of branded card templates using track/album metadata and artwork only when sharing is permitted.
3. Generate cards locally, with a clear fallback when artwork is missing or cannot be redistributed.
4. Include a stable Audify deep link and a web/store fallback; never expose private library names or listening history without explicit selection.
5. Add preview, save/share, and error states. Keep generation off the playback-critical path.

## Verification

- Test tracks, albums, playlists, missing artwork, long titles, Unicode, and unsupported providers.
- Verify deep links open the correct destination and fall back cleanly when Audify is absent.
- Confirm no copyrighted lyrics or unlicensed artwork are embedded in generated cards.
- Test Android Sharesheet, image save permissions, and TalkBack descriptions.

## Rollback

Remove card-generation entry points and deep-link promotion while preserving ordinary Android sharing.

## Guardrails

Use only content the user is authorized to share. Do not scrape artwork, reproduce lyrics, or expose private activity by default.
