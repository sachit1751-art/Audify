<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 077 — Android Auto

**Status: Media3 media-library integration implemented in v1.9.0; end-to-end vehicle validation remains.**

The phone app declares the automotive application descriptor and exports its Media3 `MediaLibraryService` through `PlaybackService`. The browse tree exposes Quick Picks, Recents, Playlists, Liked, Downloads, Local Music, and Albums. Instrumentation coverage is in `app/src/androidTest/java/com/music/audify/AndroidAutoMediaLibraryTest.kt`. Desktop Head Unit and a real compatible vehicle validation are still required before claiming full automotive certification.

## Outcome

Users can safely browse and control Audify while driving through Android Auto: recent items, favourites, playlists, albums, playback controls, and supported voice actions. The phone UI must not be mirrored onto the car display.

## Implementation

1. Audit the current Media3 session, playback service, media tree, notification controls, and Android Auto declarations.
2. Use the supported Media3/Android for Cars app-library integration and expose a compact, capability-aware browse tree.
3. Reuse the existing playback session and queue; do not create a second player or duplicate playback state.
4. Add automotive-safe search and voice query handling only where provider capabilities support it.
5. Handle connection lifecycle, unavailable content, authentication expiry, and offline browsing without crashes.

## Verification

- Run manifest, media-session, and automotive host validation required by the current Android Auto SDK.
- Test browse, play, pause, next/previous, search, reconnect, and phone-to-car handoff using Desktop Head Unit and a real compatible vehicle when available.
- Verify no distracting custom visual content, touch-only dependency, or playback duplication.
- Run existing playback/service regression tests and Foss debug build.

## Rollback

Disable the automotive browse integration while retaining the existing phone playback service and controls.

## Guardrails

No new playback engine, no unsafe interaction while driving, no version bump, and no release claim until automotive validation passes.
