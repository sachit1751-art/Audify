<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 068 — Selected feature 18: Contextual player actions

**Status: planned — audit existing player/song menus before adding actions; avoid duplicate shortcuts.**

## Objective

Make common actions available from the now-playing context: add to playlist, open artist/album, find another version, share, and manage offline downloads. Keep the primary playback controls uncluttered and show only actions valid for the current item/source.

## Why

Users should not need to leave the player and search for the same track to perform common library actions. Contextual actions must reflect the actual source and permissions.

## What exists today

- `MainActivity.kt` contains song menu assembly (`songMenuBody`) and calls `SongActionsSheet`.
- `ui/components/PlaylistPickerSheet.kt`, `DownloadManagerSheet.kt`, and existing artist/album menus provide reusable flows.
- `ui/player/PlayerMenu.kt` and shared player components expose player-level actions. Inspect current menu contents before adding entries.

## Scope

**In scope:** action availability matrix by local/remote/downloaded/guest state; add to playlist; open artist/album; find alternate version through existing search; share supported metadata/deep link; download/remove-download actions with truthful status; accessible labels and feedback.

**Out of scope:** duplicate actions already visible; new sharing backend; silent downloads; unsafe tag editing (plan 036 remains blocked); actions unavailable to Listen Together guests.

## Affected files

| File | Expected change |
|------|-----------------|
| `MainActivity.kt` (`songMenuBody` / `SongActionsSheet` integration) | Reuse existing action callbacks |
| `ui/player/PlayerMenu.kt` | Player-context entry points only where missing |
| `ui/components/PlaylistPickerSheet.kt` | Reuse playlist selection flow |
| `ui/components/DownloadManagerSheet.kt` | Reuse download state/actions |
| player view model/state owner | Provide current item/source capabilities |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels and action feedback |

## Implementation

1. Audit all existing player, song, album, artist, and mini-player menus; create a duplicate-action matrix.
2. Define a typed capability model from current item/source/guest state.
3. Reuse existing callbacks and sheets rather than reimplementing operations.
4. Group secondary actions in a predictable overflow/context sheet; keep play/pause/seek dominant.
5. Provide success/failure feedback and disable actions while operations are pending.
6. Verify deep links, guest restrictions, local-file permissions, and download state transitions.

## Testing

- Capability matrix tests for local, remote, downloaded, unavailable, and guest states.
- UI tests for action visibility, loading, failure, and repeated taps.
- Regression for playlist add, share/deep link, artist/album navigation, and download cancellation.
- Manual TalkBack and compact-screen checks.

## Performance notes

Menu opening should use already available player metadata; do not trigger network lookups just to render action labels.

## Rollback

Remove newly added menu entries and capability wiring; existing song menus and action implementations remain intact.

## Rejected alternatives

- Put every action beside play/pause: overcrowds the primary player surface.
- Duplicate playlist/download workflows inside the player: creates inconsistent behavior.
- Show actions that silently fail due to guest or URI permissions: hide or explain unavailable actions.

## Implementation contract and acceptance criteria

### Capability matrix
- Build a single capability resolver from current item identity, source type (remote/local/downloaded), URI permission, availability, guest/Listen Together role, and operation-in-progress state.
- For each action, define visible/enabled/disabled-with-reason behavior. Never show an enabled action that is known to fail for the current source or user role.
- Add-to-playlist, artist/album navigation, alternate-version search, share, download, and remove-download must call existing domain actions/sheets. No duplicate business logic or parallel persistence.
- Share payload must contain only user-visible metadata and supported links; never expose local filesystem paths, signed URLs, or internal provider identifiers.

### Interaction behavior
- Keep play/pause/seek and primary transport dominant. Group secondary actions in existing overflow/context surface; maintain consistent ordering and labels across player, mini-player, and song menus.
- Pending operations disable repeat taps and show progress; success/failure feedback must reflect actual operation result. Cancellation returns to a stable state.
- Guest restrictions and local URI permission failures are handled before dispatch and revalidated at action time.

### Done when
- Capability matrix tests cover remote, local, downloaded, unavailable, permission-revoked, guest, and pending states.
- UI tests cover action visibility, repeated taps, errors, cancellation, and navigation destinations.
- Existing song menu actions still work and there are no duplicate entries across player/mini-player menus.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
