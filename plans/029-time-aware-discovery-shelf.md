# Plan 029 — Time-aware discovery shelf

**Status: not started.** Verified at commit `9e594bde` — the Home screen has a time-aware greeting
("Good morning/afternoon/evening") but no time-blocked discovery shelf. No hour-block play-count logic
exists.

## Objective

A small Home shelf — private, offline — that surfaces discovery picks shaped by the time of day: morning
commute picks, late-night chill picks, etc. Not a new content source — a re-cut of existing discovery
content (auto-playlists, daily discover, community playlists) into time-aware buckets.

## Why

- Innovation #6: hour-block play counts → "Morning / Late night" shelf.
- The existing Home already has a greeting by time of day — a time-aware shelf is the natural companion:
  "Good morning" + "here's a morning pick" feels coherent.
- Uses existing discovery content (no new content pipeline) — the shelf is a re-cut, not a new feed.

## What exists today

- `ui/screens/HomeScreen.kt` — greeting by time of day already exists (`R.string.greeting_morning`/etc.).
- Daily discover card exists (plan in the roadmap / shipped context).
- Community playlists / auto-playlists exist as discovery content.
- No hour-block play counting, no time-bucket logic.

## Scope

**In scope:**
- Compute the user's dominant listening hour block over the last N days (e.g. last 30 days): morning
  (6-11), afternoon (11-17), evening (17-22), late night (22-6). Pick the block that matches the current
  time of day.
- A Home shelf ("Good morning — morning picks") that shows 1-3 discovery picks aligned to that block.
- Picks come from existing discovery content: e.g. a community playlist tagged "morning", the daily
  discover card's picks, an auto-playlist, or a quick pick. The shelf is a curated re-cut, not new content.
- Private and offline — the hour-block computation is from local play history; the picks are existing
  content rendered in a new arrangement.
- Shown on Home, near the greeting and daily discover. Small shelf, not a full section.

**Out of scope:**
- Viral/algorithmic discovery — this is a time-of-day re-cut of existing picks, not a new recommendation
  engine.
- Hour-block play counts exposed to the user as a stat — defer; the shelf is the feature, the counts are
  the implementation detail.
- Multiple shelves per day — one shelf per current block is enough for the v1.
- Changing the greeting — leave the greeting as-is; add the shelf beside it.

## Affected files

| File | Change |
|------|--------|
| `db/MusicDatabase.kt` | Add an `hourBlockPlays()` query: last N days → play count per hour block (4 buckets) |
| `viewmodels/HomeViewModel.kt` | Expose `currentHourBlock` and `timeAwarePicks` flows; compute the block from local history, pick picks from existing discovery content |
| `ui/screens/HomeScreen.kt` | Show the time-aware shelf below the greeting; shelf content = 1-3 picks with a "Good morning — morning picks" label |
| `constants/PreferenceKeys.kt` | Add `ShowTimeAwareShelfKey` (toggle, default on) |
| `sachit_strings.xml` | Shelf labels per block ("Morning picks", "Afternoon picks", "Evening picks", "Late night picks"), empty state |
| `ui/component/` | Reuse existing discovery card/pick components for the shelf items |

## Technical approach

1. **Hour-block query** — One read-only query: for each play in the last N days, bucket by hour-of-day
   (0-23) into 4 blocks, count plays per block. Pick the block with the most plays *that matches the
   current time of day*. If the current block has no history, fall back to the block with the most plays
   overall, or skip the shelf.
2. **Picks** — The shelf shows 1-3 items from existing discovery sources. The executor picks the sources
   at implementation time (e.g. daily discover top picks, a community playlist, an auto-playlist). The
   plan deliberately does not hard-code the source list — it says "picks come from existing discovery
   content" and the implementation chooses which ones and how many.
3. **Shelf UI** — A small horizontal or vertical list of 1-3 cards, with a time-of-day label
   ("Morning picks"). Reuses existing card/pick components. Not a full discovery section — a shelf.
4. **Toggle** — `ShowTimeAwareShelfKey` controls visibility. Defaults on.

## Testing

- Unit test for the hour-block query: insert plays at known hours, assert the dominant block is correct
  and the current-block match works.
- Unit test for the "current block has no history → fallback" logic.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: on a device with play history, open Home, verify the shelf appears with the right label for the
  current time, picks look like discovery content. Verify empty state when there's no history.
- Regression: existing Home sections (greeting, daily discover, recap card) still work.

## Performance notes

- One read-only aggregate query, cached. Cheap.
- No background work — computed on Home open or once per day.

## Rollback

- Set `ShowTimeAwareShelfKey` default off and remove the shelf from Home → zero change.
- Revert the hour-block query and the shelf — no impact on greeting or other Home sections.

## Anti-BitChord check

- Private and offline — no telemetry about when the user listens, no cross-user comparison. The hour
  block is local play history, used only to pick the shelf label.
- Reuse existing discovery content — don't invent a new content pipeline. The shelf is curation, not
  creation.
