# Plan 036 — Tag editor for downloads

**Status: blocked — unsafe to expose an editor with the current storage/tag APIs.** Re-audited on 2026-10-31.
This pass adds regression tests that pin the current destructive/append-only writer behavior. No editor UI or
song-menu action was added: the current writers cannot safely replace metadata while preserving unrelated
comments and artwork, and there is no transactional write-back for all supported download locations.

## Objective

A post-download tag editor: for a downloaded song, let the user edit the embedded metadata (title, artist, 
album, genre, year) and embedded artwork, writing the changes back into the downloaded file's tags. Useful 
when the auto-embedded metadata is wrong or the user wants to correct it.

## Why

- Matrix #18: OuterTune has a custom tag extractor; Musicolet has a full tag editor. Audify embeds metadata 
on download but doesn't let the user edit it afterwards.
- Downloads that get the metadata wrong are a real pain point — a tag editor is the fix.
- Medium effort — a tag-writing library + a small edit UI + write-back to the downloaded file.

## What exists today

- Download flow embeds metadata + artwork on download (`AudioExporter.kt` writes to MediaStore, and the 
download process embeds tags — check the actual download path at implementation time to confirm where tags 
are written).
- No post-download tag reading/writing UI.
- Artwork embedding on download exists (the exported file gets album art) — the primitives for writing 
artwork to a file exist.

### Implementation audit (2026-10-31)

- `MediaTagger.carriesTags` supports exactly `m4a`, `webm`, and `flac`. WAV is deliberately excluded.
- `Mp4Tagger.tag`, `WebmTagger.tag`, and `FlacTagger.tag` are write-only. They accept title, artist, album,
  lyrics and cover bytes; none accepts genre or year. `EmbeddedLyrics` is a narrow lyrics extractor, not a
  metadata reader: it cannot prefill title, artist, album, genre, year, or artwork.
- `FlacTagger` replaces its existing `VORBIS_COMMENT` and `PICTURE` blocks, but emits only the title,
  artist, album and lyrics fields supplied to it. It drops arbitrary comments (including genre/date) and
  drops an existing picture when no replacement cover is supplied.
- `Mp4Tagger` appends a new `udta/meta/ilst` tree. It does not inspect or replace an existing metadata tree,
  so repeated edits can leave competing values. Its current cover writer appends a cover atom.
- `WebmTagger` appends a new `Tags` element and an attachment. It does not replace earlier tags or attachments.
- `DownloadStore` can save to app-private files, legacy filesystem paths, or MediaStore content URIs.
  `Downloads.saved` maps video IDs to URI strings, and `getDownloadedSongs` exposes the URI as `Song.localUri`.
  It has no replace/transaction API. Existing `MediaTagger` write-back truncates a file or opens a content URI
  with `wt` and copies bytes directly; a failure after truncation can leave the original download damaged.
  A safe editor needs a staged replacement and a commit/rollback strategy for each storage type, including
  provider behavior for MediaStore URIs.
- The song menu is assembled in `MainActivity.kt` (`songMenuBody`, around line 4038) and calls
  `SongActionsSheet` (around line 4198). `SongActionsSheet` already identifies offline songs through
  `song.localUri`, but it has no tag-editor callback. This remains an integration point only; adding it now
  would expose an unsafe writer.

### Safe implementation boundary

Do not expose an editor for any of the three formats yet. Genre/year are unsupported; metadata and artwork
readers are absent; MP4 and WebM append duplicate tags/attachments instead of replacing prior values; FLAC
discards unrelated comments and existing artwork; and direct `wt` write-back is not rollback-safe. A smaller
title/artist/album editor would still risk stale competing values and metadata loss. The new tests below
reproduce the MP4/WebM duplicate-title behavior and FLAC comment/artwork loss. Existing `EmbeddedLyricsTest`
round-trips lyrics only and does not satisfy tag-editor read/write requirements.

The implementation gate is: (1) format readers for every editable field and artwork, (2) replace-in-place
writers that preserve every unedited field and attachment, with round-trip tests for existing and absent
metadata, and (3) staged, recoverable write-back for app-private files, legacy paths, and MediaStore URIs.
Only after all three gates pass should the menu callback and editor UI be added.

The eventual UI integration point is an optional `onEditTags: (() -> Unit)?` parameter on
`SongActionsSheet`, rendered only when `song.localUri != null` and the extension is one of `m4a`, `webm`,
or `flac`. `MainActivity.songMenuBody` must pass a callback that opens the editor with `song.localUri` and
closes the menu. This is the only required `MainActivity.kt` hook; shared strings should be added separately
to `app/src/main/res/values/sachit_strings.xml` under the project’s string-resource convention.

## Scope

**In scope:**
- A tag editor UI for a downloaded song: edit title, artist, album, genre, year; set/replace artwork 
(from gallery or camera). Write the changes back into the downloaded file's tags.
- Read current tags from the downloaded file to pre-populate the editor (so the user sees what's there 
before editing).
- Write-back: update the file's embedded tags (title, artist, album, genre, year, artwork). For MediaStore 
exported files, this means updating the MediaStore columns + re-embedding artwork; for direct files, update 
the file's tags. The executor checks the actual download storage path at implementation time and implements 
the write-back accordingly.
- Apply to a single downloaded song at a time (v1). Not batch editing.

**Out of scope:**
- Batch tag editing — v1 is single-song.
- Tag editor for remote/non-downloaded songs — only downloaded files (the ones Audify manages directly).
- CD ripping / tag lookup from musicbrainz-style databases — defer; this plan is local tag edit, not lookup.
- Format conversion — the editor edits tags on the existing file, doesn't convert formats.
- Deleting artwork only (without re-uploading) — can be part of v1 if the write-back supports "clear 
artwork"; otherwise defer.

## Affected files

| File | Change |
|------|--------|
| `ui/screens/downloads/DownloadTagEditor.kt` (new) | Tag editor screen: read current tags from the downloaded file, edit fields, pick/set artwork, write back |
| `utils/TagWriter.kt` (new) | Tag read + write helpers: read title/artist/album/genre/year/artwork from a downloaded file; write them back. Handles the actual storage path (MediaStore export vs direct file) — check at implementation time |
| `utils/AudioExporter.kt` (or the download tag path) | May reuse the existing artwork-embedding code for the write-back; check at implementation time |
| `db/MusicDatabase.kt` | Optionally refresh the DB metadata after a tag edit (so the library shows the corrected title/artist) — the executor decides if DB sync is in scope for v1; at minimum the file tags are updated |
| `ui/menu/SongMenu.kt` (or the download actions menu) | Add "Edit tags" action on downloaded songs |
| `constants/PreferenceKeys.kt` | Nothing required for v1 (no feature flag needed — the editor is a normal action) |
| `sachit_strings.xml` | "Edit tags", "Title", "Artist", "Album", "Genre", "Year", "Artwork", "Pick artwork", "Save tags", "Tags updated", error strings |

## Technical approach

1. **Read current tags** — On opening the editor for a downloaded song, read the file's current embedded 
tags (title, artist, album, genre, year, artwork). Pre-populate the editor fields. If a field is missing, 
show an empty field. The executor checks the actual file format / storage path at implementation time and uses 
the appropriate tag reading library.
2. **Edit fields** — The user edits title, artist, album, genre, year in text fields; picks/replaces artwork 
from gallery or camera. Standard edit UI — text fields + an artwork picker.
3. **Write back** — On "Save tags", write the edited values back into the downloaded file's tags. For 
MediaStore-exported files, update the MediaStore columns + re-embed artwork. For direct files, write the 
tags directly into the file. The executor implements the write-back for the actual storage path.
4. **DB sync (v1 decision)** — At implementation time, decide whether a tag edit also updates the DB record 
(the library shows the corrected title/artist). If yes, update the Song entity after a successful write-back. 
If no, the file tags are updated but the library may show stale metadata until re-scan — document the choice. 
Prefer DB sync for v1 so the library stays consistent.
5. **Artwork** — The user can pick a new artwork (from gallery) or remove the existing one. Write the new 
artwork (or no artwork) back into the file. Reuse the existing artwork-embedding code if it supports write-
back; otherwise implement the write path.
6. **Error handling** — If the tag write fails (bad file, permission, etc.), show an error and don't lose the 
user's edits — keep the edits in the UI so the user can retry. Standard error handling; no crash.

## Testing

- Unit test for `TagWriter` read: given a test file with known tags, assert the read returns the correct 
title/artist/album/genre/year/artwork. (May require a test file — the executor sets this up.)
- Unit test for `TagWriter` write: write tags to a test file, re-read, assert the written values are correct.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: download a song, open "Edit tags", change title, save, verify the file's tag + the library shows 
the new title. Test artwork replace (pick a new image, save, verify the file's artwork is updated). Test 
empty fields, test write failure (e.g. a read-only file if that's possible) → error, no crash.
- Regression: existing download flow unchanged; existing library metadata unchanged for non-edited songs.

## Performance notes

- Tag read/write is a small file operation per edit — cheap.
- Artwork write is a small image write — cheap.

## Rollback

- Remove the tag editor screen, the "Edit tags" action, and the `TagWriter` → no tag editing, downloads 
unchanged. The file tags are whatever they were on download.

## Anti-BitChord check

- A local tag editor is a standard feature — Musicolet and OuterTune have it. Implement Audify-native: own 
editor UI, own tag read/write for the actual storage path. Don't copy OuterTune's extractor or Musicolet's 
editor code.
- Single-song v1 — keep it focused; don't scope-creep into batch editing or CD ripping.
## Implementation audit (2026-10-31)

The existing `MediaTagger`, `Mp4Tagger`, `WebmTagger`, and `FlacTagger` are write-only. They do not expose a tag reader or an in-place replacement operation. Existing supported tag fields are title, artist, album, lyrics, and container-dependent cover art; genre and year are not supported. Re-appending metadata on repeated edits is not a safe substitute for replacing tags, particularly for MP4/WebM where duplicate atoms/elements can accumulate. WAV remains intentionally unsupported.

A safe editor is therefore blocked until format-specific read/replace operations and round-trip tests exist. The eventual UI entry point is the downloaded-song action in `SongActionsSheet`, wired from `MainActivity.songMenuBody`; that hook must be added only when the tag-write path is safe. No database schema change is needed for a file-only editor.
