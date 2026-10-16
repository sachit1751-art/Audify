# Plan 038 — Playlist import expansion

**Status: not started.** Verified at commit `4a3bee56` — the app has CSV and M3U playlist import. The plan 
is to expand import beyond CSV/M3U to handle Spotify-converted lists (a common request — users export from 
Spotify to CSV and want to import into Audify). The core import engine (parse a file → list of song 
identifiers → match to Audify songs → create a playlist) already exists; the plan is to add new source 
parsers.

## Objective

Expand playlist import to handle additional source formats beyond CSV/M3U — starting with Spotify-converted 
lists (CSV/TSV/JSON exports from Spotify's "export your data" or third-party Spotify export tools). The 
existing import engine (parse → match → create playlist) is reused; new source parsers are added for the 
additional formats.

## Why

- Matrix #14: SimpMusic has Spotify-converted imports; Metrolist has playlist import. Audify has CSV/M3U 
but not Spotify-converted lists. Closing the gap lets users move playlists from Spotify to Audify.
- The existing import engine already does parse → match → create playlist — the work is adding new parsers 
for the additional formats, not rebuilding the engine.
- Low-to-medium cost — new parsers + a file-type auto-detect + the existing import flow.

## What exists today

- Playlist import (CSV, M3U) exists — there's an import flow that parses a file, matches songs to Audify's 
library (by title/artist/YouTube ID), and creates a playlist.
- CSV column mapping dialog exists (the user maps columns to title/artist/URL etc.).
- The existing import engine handles CSV and M3U. The executor checks the actual import code at implementation 
time to see how to extend it.
- Spotify-converted lists come in various formats (CSV, TSV, JSON) depending on the export tool — the plan is 
to support the common ones.

## Scope

**In scope:**
- New import parsers for Spotify-converted formats: at minimum, a Spotify CSV/TSV parser (common columns: 
Track Name, Artist, Album, Album Artist, Duration, Track Number, etc.) and, if the executor chooses, a 
Spotify JSON parser (for Spotify's "your data" export or third-party tool JSON exports).
- File-type auto-detect: when the user picks a file to import, detect the format (CSV/TSV/JSON/M3U) and route 
to the right parser. For unknown formats, fall back to "unknown format" error.
- The existing column-mapping UX for CSV/TSV (the user maps columns to song fields) is reused for the 
Spotify CSV/TSV parser — the columns are different but the mapping UX is the same.
- Match logic reuse: the existing match (title/artist/YouTube ID lookup) is reused for the new parsers — the 
parser outputs a list of "candidate songs" (title + artist + optional URL/ID), and the existing match creates 
the playlist.
- Create a playlist from the import (as today) — the new parsers feed into the existing "create playlist from 
import" flow.

**Out of scope:**
- Direct Spotify API import (logging into Spotify, fetching a user's playlists via the Spotify API) — that's 
a different, larger feature (auth + OAuth + API + matching). This plan is for *converted* lists (files the 
user exports from Spotify and imports into Audify), not live Spotify connection.
- Other services' exports (Apple Music, YouTube Music exports, etc.) — defer; start with Spotify-converted 
lists as the common case.
- Batch import of many playlists at once — v1 is one playlist per import file.
- Re-matching / fuzzy matching improvements — reuse the existing match; don't rebuild matching in this plan.

## Affected files

| File | Change |
|------|--------|
| `ui/menu/ImportPlaylistDialog.kt` (or the import host) | Add file-type auto-detect; route to the right parser (CSV/TSV/JSON/M3U/Spotify CSV/TSV). For new formats, show the right mapping UI (e.g. Spotify CSV column mapping). |
| `utils/ImportParser.kt` (or a new parsers package) | Add parsers: `SpotifyCsvParser`, `SpotifyTsvParser`, `SpotifyJsonParser`, plus a parser registry / auto-detect. Each parser outputs a list of candidate songs (title, artist, album, url/id) + a column mapping for CSV/TSV. |
| `utils/CsvColumnMappingDialog.kt` (or the mapping UI) | Reuse for Spotify CSV/TSV: the columns are different but the mapping UX is the same; the dialog takes a column list and lets the user map to title/artist/album/etc. |
| `ui/screens/settings/` or the import result screen | Add format info / "imported from Spotify" label for Spotify imports. |
| `sachit_strings.xml` | New format labels ("Spotify CSV", "Spotify TSV", "Spotify JSON"), "Unknown format", parser-specific column labels, import result strings. |
| `db/MusicDatabase.kt` / import engine | No change if the existing match + playlist creation is reused. The new parsers feed candidate songs into the existing engine. |

## Technical approach

1. **Parser interface** — Define a common parser interface: `parse(fileContent) → ImportCandidate list`, where 
`ImportCandidate` has title, artist, album (optional), url/id (optional), duration (optional). The existing 
CSV and M3U parsers implement this interface (or are adapted to). New parsers (Spotify CSV/TSV/JSON) 
implement the same interface.
2. **Auto-detect** — When the user picks a file, detect the format: by extension ( .csv / .tsv / .json / .m3u 
) and/or by content (spot the signature of a Spotify CSV — column headers like "Track Name", "Artist"). 
Route to the right parser. Unknown format → error.
3. **Spotify CSV/TSV parser** — Parse the file, map columns. The common Spotify export columns are: Track 
Name, Artist, Album, Album Artist, Duration, Track Number, Genre, etc. The parser outputs candidates with 
title + artist (required for matching) + album/url/duration (optional, for better matching / display). The 
column-mapping dialog lets the user map columns if the auto-detect guess is wrong.
4. **Spotify JSON parser** — If the executor chooses to support Spotify's "your data" JSON export (or a 
third-party tool's JSON), add a JSON parser for that format. Same interface: parse → candidates. The JSON 
structure depends on the export tool — check at implementation time.
5. **Match + create playlist** — The existing import flow: candidates → match to Audify songs (by title + 
artist, optionally YouTube URL/ID) → create a playlist from the matched songs. The new parsers feed 
candidates into this flow. Unmatched candidates are shown as "not found" (as today).
6. **Column mapping reuse** — For Spotify CSV/TSV, reuse the existing column-mapping dialog. The columns are 
different (Spotify's columns vs the generic CSV columns today) but the UX is the same: list the columns, let 
the user map to song fields. Pre-map the common ones (Track Name → title, Artist → artist) and let the user 
adjust.

## Testing

- Unit test for each new parser: given a sample file content (Spotify CSV/TSV/JSON), assert the parser outputs 
the correct candidates (title, artist, etc.).
- Unit test for auto-detect: given file content/extensions, assert the correct parser is selected; unknown 
format → error.
- Unit test for the import flow with a new parser: candidates → match → playlist creation, assert the 
playlist has the matched songs.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: export a playlist from Spotify (or create a sample Spotify CSV), import it into Audify, verify the 
parser detects the format, the column mapping works, the playlist is created with the matched songs, 
unmatched songs are shown as not found. Test M3U and generic CSV still work (regression).
- Regression: existing CSV/M3U import unchanged; existing column mapping unchanged.

## Performance notes

- Parsing a playlist file is a small in-memory operation — cheap. Spotify playlists are typically < 10k songs; 
parsing is fast.
- Matching is the expensive part (title + artist lookup) — that's the same cost as today's CSV/M3U import. No 
new cost.

## Rollback

- Remove the new parsers, the auto-detect routing, and the Spotify format strings → import falls back to CSV/
M3U only, as before. Zero impact on existing import.

## Anti-BitChord check

- Spotify-converted list import is a standard feature — SimpMusic has it. Implement Audify-native: own parsers, 
own auto-detect, own column-mapping reuse. Don't copy SimpMusic's parser code.
- The import engine (parse → match → create playlist) is reused — the plan adds parsers, not a new engine.
