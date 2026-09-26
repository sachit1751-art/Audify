# Plan 014 — "Play next" from search results

Written against commit **`2aaba551`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`2aaba551`, check drift in the in-scope files first.

## Goal

Let users enqueue any search result (song / video / album) without leaving the search screen —
"Play next" plus "Add to queue" reachable from the result row.

## Existing infrastructure (recon at `2aaba551`)

- `ui/screens/search/OnlineSearchResult.kt` line 500: `itemsIndexed(... itemContent = { ytItemContent(item) })`
  — `ytItemContent` is the single row renderer for summary and filtered result lists (also used
  at line ~558 for the embedded search screen).
- Queue mutations already exist as `PlayerConnection` APIs used by every menu in the app
  (search `ui/menu/QueueMenu.kt` for `playNext`/`addToQueue` call sites and reuse the exact
  same pattern — do not invent new enqueue logic).
- Song rows elsewhere open `YouTubeSongMenu`-style menus; check what menu composable
  `ytItemContent` already opens for song items — if a full menu already exists behind long-press,
  this plan reduces to: (a) verify "Play next" is inside that menu, and (b) add a fast path
  (long-press or trailing button) so the action doesn't require opening the menu.

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/ui/screens/search/OnlineSearchResult.kt` | Row affordance + menu parity |
| `app/src/main/kotlin/com/sachit/music/ui/menu/` | Reuse existing song/video menu composables; edits only if "Play next" is missing there |
| `app/src/main/res/values/sachit_strings.xml` | Only if a new label is genuinely needed (play_next likely exists) |

Out of scope: `MusicService.kt`, `PlayerConnection.kt` (reuse its public API only), local
search screen (`LocalSearchScreen.kt` already has full menus), every `values-*` file,
`app/build.gradle.kts`.

## Implementation steps

1. **Drift + parity check** (do NOT skip): read `ytItemContent` and follow the menu composable
   it opens for a `SongItem`. Inventory which of these actions the search-result menu has vs.
   the in-queue `QueueMenu`: Play next, Add to queue, Start radio, Add to playlist, Download.
2. **Fill menu gaps first**: add missing "Play next"/"Add to queue" items to the menu the
   search rows open, using the same `playerConnection.playNext(...)`/`enqueue(...)` calls the
   queue menu uses (ids differ between Song/Video/Album — handle `AlbumItem` via its songs or
   the album enqueue API the library menus use).
3. **Fast path**: add a trailing "more" affordance only if rows currently require long-press
   with no visual hint; if long-press is the app-wide convention, keep it and skip the button
   (consistency over novelty — do not introduce a divergent row pattern).
4. **Strings**: reuse existing `play_next` / `add_to_queue` strings if present; add nothing
   unless a genuinely new label is required. Any addition goes in `sachit_strings.xml`.
5. **Verify**:
   ```bash
   ./gradlew :app:compileFossDebugKotlin --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Manual QA: search a song → menu → Play next while something plays → song starts after the
   current one; same for Add to queue (appends); album row → Play next enqueues album songs in
   order; airplane mode → error toast/handled, no crash; Listen Together guest → actions
   follow the existing guest gating used by the same APIs.

## Edge cases

- Video results: enqueue path must match the song-vs-video playback handling already used by
  the player menu (verify with a video item in QA).
- Empty queue / nothing playing: "Play next" behaves as "play now" — mirror whatever the
  queue menu does today (don't invent a new rule).
- Duplicate enqueues: no dedup needed — match existing queue-menu behavior.

## Done criteria

- Search-result rows can enqueue songs/videos/albums via the same code paths as the queue menu.
- No changes to `MusicService.kt`/`PlayerConnection.kt`; no new enqueue logic invented.
- Strings: reused where they exist; new ones only in default `sachit_strings.xml`.

## Escape hatches

- If `ytItemContent` already opens a menu with both actions, the deliverable shrinks to the
  parity check + QA evidence — record that and close the plan as no-op/verified.
- If album-level enqueue has no existing API, limit the fast path to songs/videos and note
  albums as menu-only (open album → play), rather than adding new service logic.
