<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 057 — Selected feature 07: Better local music library

**Status: planned — extends existing plan 025 (Local file library beside YTM).**

## Objective

Make local music a first-class library alongside streaming results: reliable folder selection, incremental indexing, metadata/artwork presentation, local playback, and explicit source distinction.

## Why

Local files should be discoverable and playable without being confused with remote catalogue entries or requiring broad storage permissions.

## What exists today

- Plan 025 is the existing local-library proposal.
- Local playback primitives exist under `playback/smart/LocalAudioSource.kt` and the audio pipeline.
- Downloaded media is represented separately under `download/`; do not conflate user-owned local files with app-managed downloads.

## Scope

**In scope:** SAF folder grants and persisted permissions; incremental scan; metadata extraction with malformed-file handling; embedded artwork with bounded decode; album/artist/track grouping; sort/search; local playback and queue integration; unavailable-file state; explicit rescan and revoke handling.

**Out of scope:** arbitrary filesystem access, copying user files into app storage by default, tag editing, automatic metadata rewriting, cloud sync, schema changes without approval.

## Affected files

| File | Expected change |
|------|-----------------|
| `playback/smart/LocalAudioSource.kt` | Reuse and harden local playback source contract |
| `ui/screens/LibraryPhoneParts.kt` / `sharedUi/.../LibraryScreen.kt` | Local library entry and source-aware presentation |
| new `data/library/LocalLibraryScanner.kt` | SAF-backed scan and incremental index abstraction |
| `MainActivity.kt` | Folder picker and persisted URI permission lifecycle |
| existing song/album/artist models | Extend only without breaking remote identity assumptions |
| `app/src/main/res/values/sachit_strings.xml` | English-only local-library copy |

## Implementation

1. Reconcile plan 025 and map existing local playback, download, and library models.
2. Define a distinct local media identity and source type; avoid collisions with remote video IDs.
3. Implement SAF folder selection and permission persistence; surface revoked access clearly.
4. Build an incremental scanner with file fingerprinting and cancellation.
5. Extract metadata/artwork off the main thread with size limits and fallback behavior.
6. Integrate local tracks into search, album/artist grouping, queue, and playback without changing remote flows.

## Testing

- Scanner tests for nested folders, duplicate names, unsupported/corrupt files, and changed files.
- URI permission grant/revocation and reboot persistence tests.
- Playback regression for local-to-remote and remote-to-local queue transitions.
- Large-library performance and memory test with artwork decode limits.

## Performance notes

Use incremental scans, bounded metadata concurrency, thumbnail caching, and paginated UI. Never decode full-resolution album art in list rows.

## Rollback

Hide local-library navigation and stop scans; do not revoke user folder permissions or delete user files.

## Rejected alternatives

- `MANAGE_EXTERNAL_STORAGE`: unnecessary and incompatible with least-privilege design.
- Treat every local file as a downloaded stream: storage ownership and lifecycle differ.
- Full rescans on every screen open: wastes battery and causes slow startup.

## Implementation contract and acceptance criteria

### Identity, scan, and permissions
- Local track identity must be distinct from remote IDs and stable across rescans where possible (persisted document URI + provider metadata/fingerprint); do not assume URI is immutable across providers.
- Persist SAF tree grants and verify them at scan/use time. Revoke/removal is surfaced as unavailable; never request broad storage access as a shortcut.
- Define supported audio MIME types/extensions and metadata precedence: embedded tags, MediaStore metadata, then filename fallback. Malformed tags must not abort the scan.
- Incremental scan compares stable URI, size, modified time, and optional content fingerprint; avoid full hashing every scan. Handle renamed/moved files as potential remove+add until identity can be proven.

### Playback and UI behavior
- Local media must carry source type and URI permission context through queue, player, search, and album/artist grouping. Never pass local URIs into remote resolvers.
- Artwork decode has byte/pixel limits, orientation handling, cancellation, and placeholder fallback. Metadata/artwork extraction stays off main thread.
- Distinguish local user files from app-managed downloads in filters, deletion actions, and storage labels.

### Done when
- Tests cover permission persistence/revocation, nested folders, duplicate filenames, corrupt metadata, unsupported formats, changed/moved files, and mixed local/remote queues.
- Manual tests cover reboot, provider cloud document, large library, local playback offline, and permission loss during playback.
- Startup does not trigger a full library scan; scan progress and cancellation are visible.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
