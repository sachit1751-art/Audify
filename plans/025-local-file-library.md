# Plan 025 — Local file library beside YouTube Music

**Status: existing implementation found; physical-device verification pending.** The original
reconnaissance predates the current implementation. Plan 025 is already substantially present;
do not add a second local-media stack or change the Room schema to satisfy this stale plan.

Current implementation found during the 2026-10-31 audit:
- `app/src/main/java/com/music/audify/data/LocalMediaRepository.kt` scans local audio through
  MediaStore and maps it into the app's local-media model.
- `ui/screens/DetailScreen.kt` and `MainActivity.kt` expose local music browsing, folder/group
  navigation, filtering, and sorting.
- Local playback is integrated with the existing player/queue path, and local media is included
  in the search/navigation flow through `local:all`.
- Storage permission handling and the local-library empty/permission states already exist.
- No Room schema or migration change is needed; preserve the project-wide no-schema-change rule.

Verification on 2026-10-31:
- Focused tests `LocalMediaFilterTest`, `LocalGroupsTest`, and
  `LocalAlbumTrackNumberTest` — passed.
- `:app:assembleDevDebug` — passed.
- Still required: manual playback of a local file and permission-denial behavior on a physical
  device. Do not mark the plan fully verified until that device check is completed.

## Objective

Let users browse and play their local audio files alongside YouTube Music content — one library,
one search, one player. Not a separate app, not a folder maze.

## Why

- Matrix #5: Audify has `LOCAL_ALBUM_RADIO` plumbing but no UI to surface local files.
- OuterTune died but its identity was "local + YTM in one library" — Audify can own this.
- Musicolet proves local-only players have an audience; Audify's angle is the combined view.

## What exists today

- `playback/PersistQueue.LOCAL_ALBUM_RADIO` — queue type already exists.
- `playback/MediaLibrarySessionCallback.kt` — mentions "local library" in comments but no scanning.
- `utils/AudioExporter.kt` — uses MediaStore for export; proves the API surface is understood.
- No `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` permission declared.
- No "On device" or "Local" navigation tab, screen, or ViewModel.

## Scope

**In scope:**
- `READ_MEDIA_AUDIO` permission (API 33+) / `READ_EXTERNAL_STORAGE` (API 32-) declarable.
- MediaStore audio query → in-memory list of local files (title, artist, duration, album, path, date added).
- "On device" tab in the library bottom nav (alongside Songs / Artists / Albums / Podcasts).
- LocalSongsScreen: list of local files with the same `SongListItem` / `SongGridItem` components.
- LocalAlbumRadio wrap: a local file can be queued/played like any other song.
- Search includes local files when the "On device" tab is active.
- Mini-player and full player treat local songs as normal media items (no special path).

**Out of scope:**
- Folder tree browsing (Musicolet-style) — rejected, against "one tab, no folder maze".
- Tag editing for local files (that's plan 036).
- Move/copy local files into the app's storage (AudioExporter already handles export to MediaStore).
- Local artwork extraction from files (defer; most local files have no embedded art or it's not worth the complexity now).
- Scan scheduling / "refresh" button beyond an initial scan on first open.

## Affected files

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add `READ_MEDIA_AUDIO` (API 33+) permission |
| `constants/PreferenceKeys.kt` | Add `ShowLocalLibraryKey` (opt-in toggle, defaults off) |
| `db/MusicDatabase.kt` | Add `localSongs()` query returning a local-only Song-like projection; add `insertLocalSong()` / `deleteLocalSong()` |
| `db/entities/SongEntity.kt` | Add `isLocal: Boolean` column + migration (or a separate lightweight local-file table — TBD by executor, prefer column if migration is clean) |
| `di/AppModule.kt` | Provide `LocalFileScanner` (MediaStore query, coroutine-based, paginated) |
| `viewmodels/LibraryViewModels.kt` | Add `localSongs` flow; merge local + remote counts for the library tab badge |
| `ui/screens/library/LibrarySongsScreen.kt` | Filter to show local-only when "On device" tab selected; add tab to LibraryPodsScreen nav |
| `ui/screens/library/LocalSongsScreen.kt` (new) | List/grid of local files, same style as other screens |
| `ui/screens/library/LocalAlbumScreen.kt` (new, optional) | Group local files by album if enough have album metadata |
| `ui/component/SongListItem.kt` / `SongGridItem.kt` | Show a small "local" chip or different thumbnail placeholder for local files |
| `playback/MusicService.kt` | Wire `LocalAlbumRadio` for local files (likely already works if `toMediaItem()` handles local paths) |
| `playback/PlayerConnection.kt` | Ensure local songs flow through the same play/queue path |
| `sachit_strings.xml` | "On device", "Local files", "No local files found", permission rationales |
| `ui/screens/NavigationBuilder.kt` | Add "On device" tab to library destination |

## Technical approach

1. **Permission** — declare `READ_MEDIA_AUDIO`; on first open of the "On device" tab, request it with a short rationale. If denied, show a message pointing to Settings. No fallback to legacy `READ_EXTERNAL_STORAGE` needed unless minSdk enforcement requires it.
2. **Scan** — on first enable, query `MediaStore.Audio.Media` for audio files, insert into a local Songs projection (with `isLocal = true`), store the MediaStore ID so future refreshes can diff. One-time scan; a "Refresh" action re-runs the diff.
3. **Display** — reuse `SongListItem`/`SongGridItem`. Local files get a small "On device" label or a different thumbnail placeholder (no artwork). Duration and title are the primary identifiers.
4. **Playback** — `LocalAlbumRadio` already exists; a single local file plays via the same `ListQueue` path. Verify the player can resolve a local URI (File/Uri → MediaItem) without a network call.
5. **Search** — when "On device" tab is selected, search filters the local table. Global search across local + remote is out of scope for this plan (defer to a later pass).
6. **Settings toggle** — `ShowLocalLibraryKey` defaults off. When off, the "On device" tab is hidden from the nav. When on, tab appears and scan runs on first open.

## Testing

- Unit test for the MediaStore query helper (mock a cursor with known rows; assert mapping to domain model).
- Unit test for the diff logic (new vs existing local songs).
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: install on a device with local audio files, open "On device" tab, verify list, tap play, verify in player. Verify permission denial path.
- Regression: existing library tabs still work; remote songs still play; queue still works with mixed local + remote.

## Performance notes

- MediaStore query is I/O but on a cold cache it's fast for <10k audio files. Paginate or cursor-window if a device has an extreme number of files (defer until observed).
- Don't block first frame on the scan — show an empty state with a progress indicator while scanning.

## Rollback

- Set `ShowLocalLibraryKey` default to off and remove the tab from nav → zero user-visible change.
- Revert the MediaStore scan code and the local Songs projection → no data loss for remote songs.

## Anti-BitChord check

- DO NOT copy OuterTune's folder-tree approach — Audify's identity is one flat library view.
- DO NOT copy Musicolet's tag editor here — that's plan 036.
- Use Audify's existing `SongListItem`/`SongGridItem` for visual consistency.

## Rejected alternatives

- Separate "Local" app module — over-engineering; one tab in the existing library is enough.
- Copy files into app storage on import — AudioExporter already writes to MediaStore for export; scanning in place is simpler and respects the user's file ownership.
- Full local artwork extraction — deferred; most local files are played for audio, and artwork extraction from arbitrary files is a rabbit hole.
