<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 080 — Home Screen Widgets and Quick Settings

**Status: proposal — reuse the existing playback service and state.**

## Outcome

Users can see the current track and control playback from Android home-screen widgets or a Quick Settings tile without opening Audify.

## Implementation

1. Audit current MediaSession, playback state flow, artwork cache, app-widget infrastructure, and Quick Settings declarations.
2. Build compact and expanded widget layouts for artwork, title/artist, play/pause, and previous/next.
3. Add a Quick Settings tile for play/pause and app launch; reflect authoritative session state.
4. Reuse cached artwork and existing service commands. Handle no active session, stale metadata, and process recreation.
5. Keep layouts glanceable, accessible, and adaptive to light/dark system surfaces.

## Verification

- Test widget refresh, controls, artwork fallback, resizing, multiple widget instances, and process recreation.
- Test tile add/remove and state updates across playback start/stop and service restart.
- Verify controls work while the app is backgrounded and do not create a second playback session.
- Check TalkBack labels, contrast, and battery/network behavior.

## Rollback

Remove widget/tile entry points and receivers without changing the playback service or in-app player.

## Guardrails

No polling loop, no independent playback state, and no network artwork fetch initiated solely by widget rendering.
