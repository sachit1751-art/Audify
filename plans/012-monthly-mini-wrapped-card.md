# Plan 012 — Monthly mini-Wrapped card on Home

Written against commit **`192b529e`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`192b529e`, check drift in the in-scope files first.

## Goal

A compact, dismissible Home card — "You listened 12h this month · top artist X" — reusing the
existing Wrapped statistics engine instead of building new analytics.

## Existing infrastructure (recon at `192b529e`)

- `ui/screens/wrapped/WrappedManager.kt` builds the year's stats from the same
  `playCount` data this plan needs; it already aggregates minutes, top artists, top songs.
- `ui/screens/wrapped/WrappedState.kt` (`topArtists`, minutes fields) and
  `ui/screens/wrapped/WrappedData.kt` (`WrappedRepository`) are the seams to reuse or generalize.
- Home already integrates Wrapped: `ui/screens/HomeScreen.kt` line 749 collects
  `showWrappedCard` and gates a card render; `constants/PreferenceKeys.kt` lines 265–266
  (`ShowWrappedCardKey`, `WrappedSeenKey`) show the dismiss/persist pattern to copy.
- A settings toggle already exists (`ContentSettings.kt` line 988 area) — mirror it.

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/ui/screens/wrapped/MonthlyStatsProvider.kt` | New: month-window stats computation (pure logic) |
| `app/src/main/kotlin/com/sachit/music/ui/screens/HomeScreen.kt` | Card UI + visibility gating |
| `app/src/main/kotlin/com/sachit/music/viewmodels/HomeViewModel.kt` | Expose monthly stats state |
| `app/src/main/kotlin/com/sachit/music/constants/PreferenceKeys.kt` | `MonthlyCardSeenKey` (additive) |
| `app/src/main/res/values/sachit_strings.xml` | Card strings |
| `app/src/test/kotlin/com/sachit/music/` | Unit tests for `MonthlyStatsProvider` |

Out of scope: `WrappedManager.kt` internals (read-only reuse), `MusicService.kt`, every
`values-*` file, `app/build.gradle.kts`.

## Implementation steps

1. **Drift check**: confirm `HomeScreen.kt:749` still gates the Wrapped card and
   `WrappedManager` still reads `playCount` via `WrappedRepository`.
2. **Generalize the window**: extract (or duplicate-small) the aggregation so the window is
   "current calendar month" instead of Wrapped's fixed year — into a new pure
   `MonthlyStatsProvider(minutes: Long, topArtist: Artist?, topSong: SongWithStats?)`.
   Do not modify `WrappedManager.kt` behavior for the year view.
3. **Card UI**: a Material 3 card under the greeting header, content:
   minutes listened this month + top artist name, tap → opens Wrapped screen (existing route),
   dismiss (X) → sets `MonthlyCardSeenKey`, never shown again for the month (store
   `year-month` string, not a boolean, so it re-appears next month).
4. **Preference key**: `MonthlyCardSeenKey = stringPreferencesKey("monthly_card_seen")`
   storing `yyyy-MM`.
5. **Strings**: `monthly_card_minutes` (with `%d` placeholder), `monthly_card_top_artist`,
   `monthly_card_dismiss` (content description). English content in `sachit_strings.xml`.
6. **Tests**: `MonthlyStatsProviderTest.kt` — month window boundaries (Jan 1, Dec 31,
   leap-day), zero plays, null top artist.
7. **Verify**:
   ```bash
   ./gradlew :app:testFossDebugUnitTest --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Manual QA: with some plays recorded this month → card shows real numbers; dismiss →
   gone until next month; toggle in settings hides it app-wide.

## Edge cases

- No plays this month: hide the card entirely (don't render "0 minutes").
- Top artist with a tie: pick deterministically (alphabetical) — document in the test.
- Wrapped card and monthly card coexisting: stack them; monthly card sits below.

## Done criteria

- Monthly card on Home with real stats, dismiss persists across restarts, re-appears next month.
- `MonthlyStatsProvider` unit-tested; `WrappedManager.kt` untouched (read-only reuse).
- Strings only in the default `sachit_strings.xml`.

## Escape hatches

- If `WrappedRepository`/`WrappedManager` cannot be reused without modification, implement the
  month aggregation directly over `DatabaseDao`'s existing `playCount` queries (lines ~659–662)
  and note it in the report — still zero schema impact.
- If the Home layout changed since `192b529e`, place the card after the greeting header and
  note the drift.
