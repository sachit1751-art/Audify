# Plan 027 — Landscape fullscreen lyrics

**Status: verified on Android emulator.** The plan's original
reconnaissance predates a substantial player/UI restructure (current HEAD: `559b8c3e`). The
landscape lyrics experience is already implemented in the current tree; do not recreate the old
`ui/player/Player.kt` or `ui/component/*Lyrics*` paths named in the original audit.

Current implementation:
- `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/player/LandscapePlayer.kt` provides
  `LandscapePlayerLayout` and `LandscapeLyricsPane`. The landscape player uses a persistent
  artwork/actions column and a right-hand content pane; lyrics occupy the full available height.
- `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/player/NowPlayingScreen.kt` owns the
  shared lyrics, playhead, translation, selection and panel state used by both orientations.
- `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/player/PlayerLyrics.kt` and the lyric
  panel renderer provide the synced lyric presentation and current-line emphasis.
- `LANDSCAPE_PLAYER_MIN_WIDTH` and the landscape layout's compact-height handling are present.

Verification on 2026-10-31:
- `:sharedUi:compileKotlinJvm` — passed.
- `:app:assembleDevDebug` — passed.
- The plan's historical `:app:assembleFossDebug` task is absent from the current Gradle project;
  `DevDebug` is the available app variant used for build verification.
- Android emulator `audify_shots` (`emulator-5554`, x86_64): installed the current
  `app-dev-x86_64-debug.apk`, opened a playlist, started playback, and opened the synced-lyrics
  player. Verified portrait -> landscape -> portrait rotation; artwork/actions and the lyrics pane
  reflowed correctly, the current lyric remained visible, and playback stayed `PLAYING` with the
  position advancing across both rotations. No `FATAL EXCEPTION` or app-process crash appeared in
  the inspected logcat window.
- Not separately exercised: translation toggling, no-lyrics fallback, and lyric scroll/history
  interactions. These are outside the core rotation acceptance check and remain useful follow-up
  manual coverage, not blockers to the landscape layout verification.

## Objective

When the player is in landscape (tablet or large phone), show lyrics in a fullscreen-adjacent layout
that uses the extra width — two-line sync display, larger text, scrollable history — instead of the
portrait single-column lyric strip.
Completes the lyrics story: portrait mini-player strip → portrait player → landscape full lyrics.

## Why

- Matrix #16: Audify has portrait lyrics; SimpMusic and OuterTune ship landscape fullscreen lyrics.
- The user already sees lyrics in the player; on a wide screen that real estate is wasted as a thin strip.
- Pure UI reuse — `LyricsLine` (the sync engine) already exists and works. No new sync logic.

## What exists today

- `ui/component/LyricsComponents.kt` — `LyricsLine`, the core synced-lyrics renderer with word-by-word
  highlighting and timing.
- `ui/component/OriginalLyrics.kt` — the original-language lyrics display.
- `ui/component/ExperimentalLyrics.kt` — experimental lyrics UI paths.
- `ui/player/Player.kt` — currently includes a lyrics area (portrait).
- `LocalConfiguration.current.orientation` is already read in `MiniPlayer.kt` and `Player.kt` for some
  layout decisions — the pattern exists.
- `Thumbnail.kt` already branches on orientation for some layout math.

## Scope

**In scope:**
- In the player, when width >= height (landscape): replace the portrait lyrics strip with a landscape
  lyrics layout that uses `LyricsLine` in a wider, taller arrangement.
- The landscape lyrics area is a separate composable, e.g. `LandscapeLyrics`, that reuses `LyricsLine`
  or the existing lyric rendering but lays it out for width: larger font, two columns if text is long,
  scrollable past lines, current line visually emphasized.
- Portrait: unchanged — keep the existing lyrics strip.
- The lyric content (source, timing, word-by-word data) is shared — only the layout changes.
- Mini-player lyrics strip: unchanged (it's a strip by design; don't expand it).

**Out of scope:**
- A separate "lyrics screen" navigation destination — this is about the player's lyric area, not a new
  screen.
- Changing how lyrics are fetched, synced, or translated — those are done.
- Auto-scrolling behavior changes beyond what already exists — keep the current sync behavior; just lay
  it out for landscape.
- Android TV lyrics (that's a different form factor, plan 055 area).

## Affected files

| File | Change |
|------|--------|
| `ui/player/Player.kt` | Branch on orientation/size: portrait → existing lyrics strip; landscape → new `LandscapeLyrics` |
| `ui/player/LandscapeLyrics.kt` (new) | Landscape lyrics layout: reuses `LyricsLine` / lyric data, wider layout, larger text, scrollable past lines |
| `ui/component/LyricsLine.kt` (or wherever the core renderer is) | Possibly expose a param for font scale / column count if the landscape layout needs it; otherwise reuse as-is |
| `ui/component/OriginalLyrics.kt` | Same — reuse; only the host layout changes |
| `sachit_strings.xml` | Any new labels if the landscape layout needs a title/empty state |
| `ui/player/Thumbnail.kt` | May need to re-balance the player layout when lyrics take more space in landscape |

## Technical approach

1. **Detect landscape in the player** — reuse the pattern already in `MiniPlayer.kt`/`Player.kt`:
   `LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE`, or better, use the
   window size class from plan 026 if that lands first. If 026 hasn't landed, use orientation as a
   simple proxy — landscape = wide enough for the full lyrics layout.
2. **LandscapeLyrics composable** — takes the same lyric data the portrait strip takes. Lays it out as:
   - Current line large, centered or left, with word-by-word highlight.
   - Past lines above, dimmed, scrollable.
   - Future lines below (if pre-fetched), dimmer.
   - The whole thing in a `Column` that fills the player's lyric area height in landscape.
3. **Reuse, don't rewrite** — `LyricsLine` already handles the sync + highlight. `LandscapeLyrics`
   arranges multiple `LyricsLine`-like rows or reuses the same component in a list. Prefer reusing the
   existing renderer over copying its internals.
4. **Player layout rebalance** — In landscape, the player artwork + controls + lyrics share the width.
   Make sure the layout doesn't break: artwork on one side, lyrics on the other, or artwork on top and
   lyrics below, depending on aspect ratio. Test on a 16:9 landscape and a wider tablet landscape.
5. **Mini-player stays a strip** — don't touch the mini-player lyric line. It's intentionally compact.

## Testing

- Build: `./gradlew :app:assembleFossDebug` must pass.
- Emulator/device: open a song with synced lyrics, rotate to landscape, verify the landscape lyrics
  layout appears, words highlight in sync, past lines are visible/scrollable. Rotate back to portrait —
  verify the portrait strip returns.
- No-lyrics song: verify the landscape area shows the same empty state the portrait strip shows.
- Regression: portrait player still works, mini-player still works, lyrics translation still works.

## Performance notes

- No new work per frame — it's a layout change. The existing lyric sync ticker continues at its current
  rate.
- If the landscape layout renders many past lines, cap the visible list to a reasonable window (e.g. 20
  past + current + 10 future) to avoid a giant column on long songs.

## Rollback

- Remove the landscape branch in `Player.kt` → everything goes back to the portrait strip. Delete
  `LandscapeLyrics.kt`.

## Anti-BitChord check

- This is a layout completion, not a feature copy. SimpMusic and OuterTune do landscape lyrics — the
  pattern is industry-standard, not a BitChord-specific trick. Use Audify's existing lyric components.
