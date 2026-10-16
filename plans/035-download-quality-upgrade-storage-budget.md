# Plan 035 — Download quality upgrade + storage budget

**Status: not started.** Verified at commit `4a3bee56` — downloads exist with a quality setting, but there's
no "re-download at higher quality when it improved" logic and no storage budget cap with LRU eviction prompt.

## Objective

Two related features: (1) if a downloaded song's available quality has improved since download (e.g. the
song was downloaded at LOW but now HIGH is available), offer to re-download at the better quality; (2) a
storage budget — a cap on download storage with an LRU eviction prompt when the cap is reached. Both are
about managing downloads smarter, not just downloading.

## Why

- Matrix #19: BitChord has download tier work + storage budgeting; Musicolet has auto backups. Audify has
  downloads but not smart quality upgrade or storage budget.
- Storage budget is a real concern on devices with limited storage — a cap + LRU prompt is better than the
  user running out of space unexpectedly.
- Quality upgrade is a nice-to-have: if the user's quality setting was LOW when they downloaded but now it's
  HIGH, the downloaded file is worse than what they'd get today. Letting them upgrade is a small quality-of-
  life improvement.

## What exists today

- Download flow exists (`DownloadUtil`, `DownloadService`, etc.) — songs are downloaded at the user's current
  quality setting.
- No "download quality changed since download" tracking — a song downloaded at LOW is just a LOW file; the
  app doesn't check if a better quality is now available.
- No storage budget — downloads accumulate until the user removes them or runs out of space.
- Existing download removal UI (remove from cache, remove download playlist).

## Scope

**In scope:**
- **Quality upgrade**: on the download list / song download status, detect songs where the current quality
  setting is higher than the download's recorded quality (or where a better quality is now available). Offer
  "Upgrade" on those songs — re-download at the better quality, replacing the old file. Track per-song the
  quality it was downloaded at (store the itag/quality alongside the download record).
- **Storage budget**: a settings cap (e.g. 500 MB / 1 GB / 2 GB / 5 GB, or "unlimited"). When the cap is
  reached, show an LRU eviction prompt: "Storage full — remove the oldest downloaded songs?" with a list of
  the LRU candidates. The user can prune or increase the budget. Downloads beyond the cap are blocked until
  space is freed.
- LRU tracking: track per-download the last-played or last-added time (whichever the executor picks as the
  LRU metric). Use it for eviction ordering.

**Out of scope:**
- Automatic eviction without user prompt — defer; v1 is a prompt, not silent eviction.
- Per-song quality upgrade on every app open — defer; v1 is an on-demand "Upgrade" action on the download
  list, not an auto-upgrade background process.
- Quality downgrade — not a feature; if the user lowers their quality setting, existing downloads stay as-is
  (they don't get worse). Only upgrade when a better quality is available.
- Cloud storage / remote backup of downloads — defer.

## Affected files

| File | Change |
|------|--------|
| `db/entities/DownloadEntity.kt` (or the download record) | Add `downloadQuality` / `itag` field tracking what quality the file was downloaded at |
| `db/MusicDatabase.kt` | Migration to add the quality field; query to find songs where current quality setting > download quality |
| `playback/DownloadUtil.kt` (or the download service) | On download completion, record the quality; on re-download/upgrade, replace the file at the better quality |
| `ui/component/DownloadList.kt` (or the download UI) | Show "Upgrade available" on songs where a better quality is available; "Upgrade" action |
| `ui/screens/settings/StorageSettings.kt` (or a new download settings screen) | Storage budget cap setting; LRU eviction prompt UI; download size summary |
| `constants/PreferenceKeys.kt` | Add `DownloadStorageBudgetKey` (cap in bytes or a preset enum), `DownloadUpgradeEnabledKey` if the upgrade is gated |
| `sachit_strings.xml` | "Upgrade available", "Upgrade to better quality", "Storage full", "Remove oldest downloads", budget labels, size summaries |

## Technical approach

1. **Quality tracking** — When a download completes, record the quality (itag / quality enum) alongside the
   download record. When the user's quality setting changes, the existing downloads don't auto-upgrade — but
   the app can now detect "this song was downloaded at LOW, but HIGH is now available per the current setting
   + the song's available qualities".
2. **Upgrade detection** — A query: for each downloaded song, compare the download's recorded quality to the
   best available quality for that song (per the current quality setting + the song's available itags). If a
   better quality is available, mark it as "upgrade available". Show "Upgrade" on those songs in the download
   list.
3. **Upgrade action** — "Upgrade" re-downloads the song at the better quality, replacing the old file. The
   user can do this per-song or there can be a "Upgrade all" action. On upgrade completion, update the
   download record to the new quality.
4. **Storage budget** — A settings cap. Track total download size (sum of download file sizes, or an estimate
   from the download records). When the total exceeds the cap, block new downloads and show an LRU eviction
   prompt: list the LRU-downloaded songs (by last-played or last-added, executor's choice), let the user prune
   some, then resume downloads.
5. **LRU metric** — The executor picks the LRU metric at implementation time: last-played time (from play
   history) or last-added time (from the download record). Last-played is more user-friendly ("remove the
   songs you haven't listened to in a while") but requires play-history linkage; last-added is simpler. Pick
   one — last-played is preferred if the linkage is clean.

## Testing

- Unit test for upgrade detection: given a download record at LOW quality + a song whose best available
  quality is HIGH + a current setting of HIGH → assert "upgrade available" is true. Given a download at HIGH
  + setting HIGH → assert no upgrade.
- Unit test for storage budget: total size > cap → assert block + LRU list is returned; prune some → assert
  total size < cap → assert downloads resume.
- Unit test for LRU ordering: given downloads with different last-played times, assert the LRU list is
  correctly ordered.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: download a song at LOW, change setting to HIGH, verify "Upgrade available" appears, tap upgrade,
  verify re-download at HIGH. Set a small storage budget, download enough to exceed it, verify the eviction
  prompt appears with LRU list, prune some, verify downloads resume.
- Regression: existing download flow unchanged; existing download removal unchanged; existing quality setting
  unchanged.

## Performance notes

- Upgrade detection is a query on the download list — cheap.
- Storage budget total is a sum over download records — cheap.
- LRU ordering is a sort on the download list — cheap.

## Rollback

- Remove the quality field, upgrade detection, upgrade action, storage budget cap, and eviction prompt →
  downloads work as before (no quality tracking, no budget). Zero impact on the core download flow.

## Anti-BitChord check

- Storage budget is a user-facing cap with a prompt — not silent eviction. The user always knows and chooses.
- Quality upgrade is on-demand — not auto-upgrading in the background. The user chooses when to upgrade.
- Both features use Audify's existing download UI language — not copied from BitChord or Musicolet.
