# Plan 015 — Monthly & Yearly Recap (Spotify-Wrapped-style, working end to end)

Written against the repo state after `41420e57`. Self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` first; if the tip has moved, re-verify the
in-scope seams below before editing.

## Goal

A recap experience the user can open any time for **this month** or **this year**: minutes
listened, top songs, top artists, top albums, totals — reusing the existing Wrapped
slideshow with a selectable time range. Must actually work: real DB data, no placeholder
screens, and entry points wired.

## Existing infrastructure (recon, verified at 41420e57)

- **Slideshow exists and works**: `ui/screens/wrapped/` — `WrappedScreen.kt` (full-screen
  pager), `WrappedManager.kt` (prepare/dispose + `createPlaylist(imageResName)`),
  `pages/` (Intro, Minutes + Tease, TopArtist, Top5Artists, TopSong, Top5Songs,
  TotalSongs, TotalArtists, AlbumPages, Conclusion), `components/` (AnimatedBackground,
  AutoResizingText), `WrappedData.kt`, `WrappedState.kt`, `WrappedConstants.kt`,
  `WrappedAudioService.kt`, `WrappedEntryPoint.kt`.
- **Yearly data layer already exists** (used by WrappedManager):
  - `DatabaseDao.mostPlayedSongs(fromTimeStamp, limit, offset, toTimeStamp): Flow<List<Song>>` @ ~529
  - `DatabaseDao.mostPlayedArtists(fromTimeStamp, limit, toTimeStamp)` @ ~567
  - `DatabaseDao.mostPlayedSongsStats(...)` @ ~438 (playTime per song)
  - `DatabaseDao.getTotalPlayTimeInRange(from, to): Flow<Long?>` @ ~616
  - albums/unique-count queries also exist (`getUniqueAlbumCountInRange` etc.).
- **Monthly wiring already exists**: `HomeViewModel.monthlyCardState` +
  `dismissMonthlyCard()` (plan 012), `MonthlyStatsProvider` (pure, tested), the monthly
  card on `HomeScreen.kt` (~line 1445) with an **Open** button that navigates
  `"wrapped"`.
- Navigation: `"wrapped"` composable route in `ui/screens/NavigationBuilder.kt`
  (WrappedScreen has no arguments today).

## In-scope files

| File | Change |
|---|---|
| `ui/screens/wrapped/WrappedManager.kt` | Accept a `(from, to)` window (yearly = today's behavior; monthly = Jan 1 replaced by month start) |
| `ui/screens/wrapped/WrappedScreen.kt` | Read the range from the VM/manager; keep pages identical |
| `viewmodels/WrappedViewModel.kt` (or wherever `prepare()` is driven — check `WrappedEntryPoint.kt`) | Expose `mode: MONTHLY | YEARLY`, pass LocalDateTimes |
| `ui/screens/wrapped/pages/*` | Only if a hardcoded "year"/"2026" string exists — parametrize it |
| `ui/screens/HomeScreen.kt` | Monthly card "Open" → `wrapped/monthly`; new yearly entry |
| `ui/screens/settings/` or Stats screen | Yearly recap entry point (Stats screen already exists: `ui/screens/StatsScreen.kt`) |
| `ui/screens/NavigationBuilder.kt` | Route `wrapped/{range}` with `range ∈ {monthly, yearly}` (default yearly for back-compat) |
| `res/values/sachit_strings.xml` | Strings listed below (default English only) |

Out of scope: DB schema changes (FORBIDDEN), `MusicService.kt`, new Room entities. All
recap math must come from the DAO queries above, which already take arbitrary
`(fromTimeStamp, toTimeStamp)` windows.

## Implementation steps

1. **Range plumbing**: add `wrapped/{range}` route. In the Wrapped VM, map
   `monthly → Pair(firstOfCurrentMonth.atStartOfDay(), nextMonthStart)` (reuse
   `MonthlyStatsProvider.monthWindow(today)` — it already returns exactly this pair),
   `yearly → Pair(Jan 1 → now)` (today's Wrapped behavior). Pass into WrappedManager's
   data loading; verify every DAO call site in WrappedManager receives the window (no
   hardcoded year boundaries remain).
2. **Time-range picker**: on the monthly card's "Open" there is nothing to pick (it IS
   monthly). Add the picker at the Wrapped intro page: two chips "This month" / "This
   year" (default: follow the route arg). Keep it simple — no custom date ranges.
3. **Headline strings** (add to `sachit_strings.xml`):
   `recap_title_month` ("Your month in music"), `recap_title_year` ("Your year in
   music"), `recap_range_month` ("This month"), `recap_range_year` ("This year"),
   `recap_minutes_month` ("%1$d minutes this month"), `recap_minutes_year`
   ("%1$d minutes this year"), `recap_share` ("Share"), `recap_empty` ("Play something
   first — your recap appears once you've listened to a few songs.").
4. **Empty state**: if `getTotalPlayTimeInRange(...).first() == null || minutes <= 0`,
   show a single friendly page (`recap_empty`) instead of zeros — never crash, never
   show "0 minutes" slideshow.
5. **Entry points** (all three, cheap):
   - Monthly card Open → `wrapped/monthly` (currently navigates `"wrapped"` — change it),
   - Stats screen: add a "Recap" card/button → `wrapped/yearly`,
   - Keep any existing yearly entry working (`"wrapped"` route stays valid, defaults to yearly).
6. **Verify it works** (do all):
   ```bash
   export JAVA_HOME="C:/Users/HP/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
   ./gradlew :app:testFossDebugUnitTest --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Then install on the emulator (`audify_shots` AVD exists) and drive with adb:
   play 1–2 songs for a few minutes → Home → monthly card shows minutes → tap Open →
   monthly slideshow plays with the real numbers → back → Stats → Recap → yearly
   slideshow plays. Confirm the monthly and yearly numbers differ.
7. **Unit test**: extend `MonthlyStatsProviderTest.kt` (or add `RecapRangeTest.kt`)
   covering the window mapping (`monthly`/`yearly` → correct start/end, leap-year
   February, December).

## Edge cases

- Month/year rollover mid-session: windows are computed at query time (both
  `MonthlyStatsProvider.monthWindow` and the yearly `LocalDate.now()` calls) — do not
  cache them in fields.
- New user, zero plays: empty state page (step 4).
- Wrapped audio (WrappedAudioService) must keep working for both ranges — it is
  range-independent (background music), verify `prepare()` still succeeds.
- Guest mode: all recap queries are local (playCount/event tables) — must work
  signed-out. Test once logged out.

## Done criteria

- Both ranges show real, correct, different numbers from the local DB.
- All three entry points work; `adb`-driven manual pass on the emulator documented in
  the final report.
- Zero DB schema changes (`git diff` on `MusicDatabase.kt`, `entities/`, `schemas/` is empty).
- Unit tests green + `assembleFossDebug` green.

## Escape hatches

- If WrappedManager hard-codes year boundaries deeper than expected, parametrize the
  constructor/window and note the diff in the report — do NOT fork a second slideshow.
- If the pager pages are pure composables over `WrappedState`, add `rangeLabel` to the
  state instead of threading parameters page-by-page.
