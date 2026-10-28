# Plan 028 — Week-shape recap card

**Status: not started.** Verified at commit `9e594bde` — the app has a monthly/yearly recap (plan 015,
shipped) but no week-shaped recap card. `ShowWrappedCardKey` and `WrappedSeenKey` exist for the
existing recap.

## Objective

A small, private, offline Home card that shows the past 7 days of listening as a simple shape — play
count, top song, top artist, minutes — refreshed weekly. Complements the existing monthly/yearly recap
with a lighter, more frequent cadence.

## Why

- Innovation #12: week-shape recap is a companion to the monthly/yearly recap, not a replacement.
- A weekly card is "did I listen to anything good this week?" — more casual, more frequent, more
  shareable-without-being-shared.
- Reuses the existing recap infra (wrapped screen, DB queries, share-image path) — low implementation
  cost relative to the value.

## What exists today

- Recap/wrapped screen already exists (plan 015) — monthly and yearly views with minutes, top artist,
  top song.
- `ShowWrappedCardKey` / `WrappedSeenKey` — a recap card on the Home screen already exists for the
  monthly/yearly recap.
- DB has play history — `HistoryViewModel`, play-count queries already exist for the recap.
- Share-image infra exists (the recap can be shared as an image today).

## Scope

**In scope:**
- A new "This week" Home card, separate from the monthly/yearly recap card, shown when the weekly
  recap is due (once per week, or on open after the week rolls over).
- Week query: plays in the last 7 days → count, top song, top artist, total minutes.
- Card content: week label ("This week"), minutes, top song, top artist, play count. Same visual family
  as the existing recap card (consistent identity).
- Private and offline — computed from local DB only, no network, no upload.
- Shown on Home, below or near the existing recap card. Not a full screen — it's a card.
- Tap the card → opens the full recap screen (reuse the existing recap screen; the weekly data is just
  the initial view, or a new "This week" tab in the recap screen if the screen supports range tabs).

**Out of scope:**
- Daily recap — too much for now; week is the right granularity.
- Sharing the week card as a separate image — reuse the existing share path if it supports range;
  otherwise defer.
- Leaderboards, social comparison, any "your week vs others" — private only.
- Changing the monthly/yearly recap — leave it as-is; add week alongside it.

## Affected files

| File | Change |
|------|--------|
| `db/MusicDatabase.kt` | Add a `weeklyRecap()` query: last 7 days → (minutes, topSongId, topArtistId, playCount) |
| `viewmodels/HomeViewModel.kt` (or a new recap ViewModel) | Expose `weeklyRecap` flow; compute on a weekly schedule or on demand |
| `ui/screens/HomeScreen.kt` | Show the "This week" card below the existing recap card; respect `ShowWrappedCardKey`-style toggle |
| `constants/PreferenceKeys.kt` | Add `ShowWeeklyRecapCardKey` (toggle, default on) and `WeeklyRecapSeenKey` (date of last shown week) |
| `sachit_strings.xml` | "This week", "minutes this week", empty state ("Not much this week" / "Start listening to see your week"), top song/artist labels |
| `ui/component/` (recap card component) | Reuse or extend the existing recap card to accept a range (week / month / year) |

## Technical approach

1. **Query** — One DB query for the last 7 days: aggregate play count per song, pick the top, aggregate
   total minutes, pick top artist. Keep it read-only and cached — recompute once per day or on card open
   after the week rolls over.
2. **Card** — Reuse the existing recap card component if it supports a range parameter; otherwise extend
   it. The week card looks like a smaller sibling of the monthly/yearly card: same identity, same visual
   language, smaller headline.
3. **Cadence** — Show the card when `WeeklyRecapSeenKey` is older than 7 days or missing. When shown,
   set the key to today. This makes it a "once a week" card without a background worker.
4. **Tap** — Opens the recap screen. If the recap screen supports range tabs (week / month / year),
   land on the week tab. If not, the tap can just open the recap screen with the existing monthly view
   (week card is the casual entry point; the full recap screen is the deep view).
5. **Toggle** — `ShowWeeklyRecapCardKey` controls visibility. Defaults on. Off = no week card.

## Testing

- Unit test for `weeklyRecap()` query: insert 7 days of play data with known top song/artist/minutes,
  assert the query returns the right top song, top artist, minutes, count.
- Unit test for the "week rolls over" logic: set `WeeklyRecapSeenKey` to 8 days ago, assert the card
  should show; set to yesterday, assert it shouldn't.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: on a device with play history, open Home, verify the week card appears with correct data,
  tap to open recap, verify empty state with no recent plays.
- Regression: monthly/yearly recap card still works; existing recap screen still works.

## Performance notes

- One read-only aggregate query, cached. Cheap.
- No background work — computed on card open or once per day at most.

## Rollback

- Set `ShowWeeklyRecapCardKey` default off and remove the card from Home → zero change.
- Revert the `weeklyRecap()` query and the card — no impact on monthly/yearly recap.

## Anti-BitChord check

- Private and offline — no social feed, no leaderboard across users. This is "your week", not "your
  week vs the world". That's the Audify identity.
- Reuse the existing recap visual language — don't invent a new card style.
