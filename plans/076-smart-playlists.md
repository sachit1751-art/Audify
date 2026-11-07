<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 076 — Smart Playlists

**Status: implemented in v1.9.0.**

Implementation is present in `app/src/main/java/com/music/audify/data/playlist/SmartPlaylistEngine.kt`, `SmartPlaylistRepository.kt`, `SmartPlaylistStore.kt`, and `ui/screens/SmartPlaylistsScreen.kt`. Unit coverage is in `app/src/test/java/com/music/audify/SmartPlaylistEngineTest.kt`.

## Outcome

Users create playlists that update automatically from simple rules such as recently added, most played, not played recently, selected artists, or liked tracks. Rules remain understandable and users can preview the result before saving.

## Implementation

1. Audit local/provider playlist models, available metadata, listening aggregates, and persistence. Do not assume per-play history exists.
2. Define a small rule model with explicit limits, stable sorting, duplicate handling, and empty-data behavior.
3. Recompute results from existing local data when the library changes or when the user opens the playlist; avoid continuous background scans.
4. Add a rule builder, preview/count, save, edit, pause, and delete actions using existing playlist UI patterns.
5. Persist rules only through existing storage. Any new schema or migration requires separate approval.

## Verification

- Unit-test every rule, combined filters, missing metadata, duplicate tracks, empty libraries, and deterministic ordering.
- Verify edits and deletion do not alter source playlists or playback queues.
- Manually test with local and provider-backed tracks, offline state, and large libraries.
- Confirm ordinary playlists remain unchanged and rule evaluation does not block the UI.

## Rollback

Hide Smart Playlists entry points and stop rule evaluation; preserve existing playlists and user data.

## Guardrails

Do not invent listening signals, create a second playlist engine, or silently modify ordinary playlists. Reconcile with existing playlist/import plans before implementation.
