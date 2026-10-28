# Plan 037 — Stats export (CSV)

**Status: not started.** Verified at commit `4a3bee56` — no stats export exists. The app has play history 
and recap stats (monthly/yearly/weekly), but no CSV export of play counts / history. Musicolet ships CSV 
play-count export; Audify can add the same.

## Objective

Let the user export their play stats / history to a CSV file: play counts per song, listen history 
(timestamp, song, artist, album), optionally filtered by range (this week / month / year / all time). The 
CSV is saved to a shared location (Downloads / a choosable directory) and opened in the system file manager 
/ shareable.

## Why

- Matrix #20: Musicolet ships CSV play-count export; it's a data-portability feature that's cheap to build 
on top of the existing wrapped/DB stats infra.
- Privacy-positive — the user owns their data and can take it elsewhere. No server, no upload.
- Cheap to build — it's a DB query + CSV write + share/save. Reuses the existing stats queries.

## What exists today

- Play history in the DB (the recap infra queries it — monthly/yearly top song, top artist, minutes).
- Recap screens that show stats — the queries already exist, just not the CSV export.
- Share-image infra (recap card share) — the "save to a shareable file" pattern exists in some form.
- No CSV generation, no export UI, no file save/share path for stats.

## Scope

**In scope:**
- A "Export stats" action in the stats/recap area (or a new export screen): choose what to export (play 
counts per song, listen history, or both), choose a range (this week / month / year / all time — or custom 
range), generate the CSV.
- CSV content: 
  - Play counts: songId, title, artist, album, play count, total minutes (per song).
  - Listen history: timestamp, songId, title, artist, album (one row per play).
- The CSV is saved to a writable location (Downloads folder, or a choosable directory via the system file 
picker) and the system file manager opens it (or it's shared via the share sheet).
- Private and local — no upload, no server. The CSV is the user's data, saved locally.

**Out of scope:**
- Export to other formats (Excel, JSON, etc.) — CSV is the v1 format. Musicolet does CSV; that's the bar.
- Cloud export / upload to a service — defer; this is local data portability.
- Scheduled / auto-export — defer; v1 is on-demand.
- Stats beyond play counts + history — defer; v1 is the two core exports. Don't add top-artist-over-time 
graphs etc. in v1.

## Affected files

| File | Change |
|------|--------|
| `ui/screens/stats/` or `ui/screens/library/` (new export screen or action) | "Export stats" entry point: choose export type (play counts / history / both), choose range, generate + save/share |
| `db/MusicDatabase.kt` | Add export queries: play counts per song (with range filter), listen history (with range filter). Reuse existing recap queries where possible. |
| `utils/CsvExport.kt` (new) | CSV generation helpers: build a CSV string / file from the query results; handle escaping, headers, line endings. Write to a file in a shared location. |
| `utils/FileShare.kt` (or extend existing share utils) | Save the CSV to a shared location (Downloads / MediaStore) and/or share via the system share sheet. Reuse the existing file-share pattern. |
| `sachit_strings.xml` | "Export stats", "Play counts", "Listen history", "This week", "This month", "This year", "All time", "Generate CSV", "Saving…", "Export complete", "Open in file manager", error strings |

## Technical approach

1. **Export queries** — Add DB queries (or reuse existing recap queries) for: (a) play counts per song with a 
range filter (last 7 / 30 / 365 days / all), (b) listen history with a range filter (timestamp, songId, 
title, artist, album). The executor reuses existing recap queries where possible and adds range filters.
2. **CSV generation** — Given the query results, generate a CSV: headers + one row per record, with proper 
escaping (commas, quotes in fields) and consistent line endings. Write to a string or directly to a file. 
Keep it simple — a CSV library is optional; a hand-rolled CSV writer is fine for the small data sizes 
involved.
3. **File save / share** — Write the CSV to a shared, user-accessible location. Options at implementation 
time: (a) save to Downloads and open the file manager, (b) save to a MediaStore audio export directory, 
(c) share via the system share sheet. Pick one or offer a choice. Reuse the existing file-share pattern if 
it exists (the recap share path may be a template).
4. **Range selection** — A simple range picker: this week / this month / this year / all time (pre-set ranges) 
+ optionally a custom range. The range filters the export queries. Keep it simple — pre-set ranges are enough 
for v1.
5. **Export type** — Play counts / listen history / both. If "both", generate one CSV with both sections 
(or two files — the executor decides). Keep it simple: one CSV with both is fine, or two files.

## Testing

- Unit test for the export queries: insert known play data, run the play-counts query with a range, assert the 
correct rows; run the history query with a range, assert the correct rows.
- Unit test for CSV generation: given known query results, assert the generated CSV has the correct headers, 
rows, escaping.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: open export, choose play counts + this month, generate, verify the CSV is saved and opens with the 
correct data. Test history export, test "both", test custom range. Verify the file is in the expected 
location / shared correctly. Test empty history (no plays in range) → empty CSV with headers.
- Regression: existing recap queries / screens unchanged; existing share paths unchanged.

## Performance notes

- Export queries are read-only DB queries over the play-history table — cheap for typical history sizes. If a 
user has tens of thousands of plays, the history export could be large; consider paging the query or writing 
the CSV incrementally for large exports. The executor handles large-export performance at implementation time 
if it comes up.
- CSV write is a small file write — cheap.

## Rollback

- Remove the export screen / action, the export queries, the CSV writer, and the file save/share → no export, 
stats screens unchanged. Zero impact on the existing stats/recap infra.

## Anti-BitChord check

- CSV export is a standard data-portability feature — Musicolet has it. Implement Audify-native: own queries, 
own CSV writer, own file save/share. Don't copy Musicolet's export code.
- Private and local — no upload, no server, no analytics on what's exported. The user's data, their CSV.
