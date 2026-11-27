<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 080 — Home Screen Widgets and Quick Settings

**Status: implementation in progress — home-screen widgets are implemented; Quick Settings tile is implemented and under verification.**

## Outcome

Users can see the current track and control playback from Android home-screen widgets or a Quick Settings tile without opening Audify.

## Implementation

1. Audited the Media3 MediaLibrarySession, service-published widget snapshot, artwork cache, three widget providers, and manifest. No independent playback engine is introduced.
2. Existing 2×2/4×2 resizable artwork widgets and the 4×1 pill provide track/artist metadata and play/pause/previous/next; the pill also exposes Like and Shuffle.
3. Added an exported-only-to-system Quick Settings TileService protected by BIND_QUICK_SETTINGS_TILE. It binds the existing Media3 session, toggles play/pause, and opens Audify when there is no active queue.
4. The tile listens to Player.Listener events and releases its MediaController binding when Quick Settings stops listening. It uses the persisted widget snapshot only for the first frame while the authoritative session connection is pending; it does not poll or fetch artwork.
5. Tile presentation uses system tile surfaces, an adaptive state/subtitle, and a monochrome vector icon. Existing widget layouts remain host-resizable and use cached artwork with a no-artwork fallback.

## Verification

- Added JVM coverage for tile presentation: no active track, playing, paused, and blank metadata.
- Verify widget refresh, controls, artwork fallback, resizing, multiple instances, and process recreation on-device.
- Verify tile add/remove and live state updates across playback start/stop and service restart on-device.
- Verify background controls do not create a second playback session; inspect TalkBack output and OEM tile rendering on-device.
- No polling loop or widget-only network artwork fetch is used.

## Rollback

Remove widget/tile entry points and receivers without changing the playback service or in-app player.

## Guardrails

No polling loop, no independent playback state, and no network artwork fetch initiated solely by widget rendering.
