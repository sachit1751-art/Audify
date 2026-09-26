# Plan 013 — Synced lyric line in the mini player

Written against commit **`2aaba551`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`2aaba551`, check drift in the in-scope files first.

## Goal

Show the current (or next) synced lyric line under the song title in the mini player; tapping
the lyric area opens the full player with the lyrics page. Gives constant "karaoke awareness"
without opening the player.

## Existing infrastructure (recon at `2aaba551`)

- `ui/player/MiniPlayer.kt` line 151: `fun MiniPlayer(...)` dispatches to `NewMiniPlayer`
  (line ~187) or `LegacyMiniPlayer`, gated by `UseNewMiniPlayerDesignKey`. Implement against
  **both** variants, or New only with the toggle documented (decide after reading the file).
- Lyrics pipeline: `lyrics/LyricsHelper.getLyrics(mediaMetadata): LyricsWithProvider` is the
  single entry point used by the player screen; synced formats (`LrcLib`, `BetterLyrics`,
  KuGou, ...) all return `syncedLyrics` LRC text that `LyricsUtils.kt` already parses
  (including word-by-word karaoke timing at line 464).
- The mini player already recomposes on playback state via `playerConnection`; the lyric
  lookup must NOT add network calls per-frame — fetch once per mediaId change.

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/ui/player/MiniPlayer.kt` | Lyric line composable + wiring |
| `app/src/main/kotlin/com/sachit/music/lyrics/LyricsHelper.kt` or a small wrapper VM | Reuse; only touch if a cached-per-song accessor is missing |
| `app/src/main/kotlin/com/sachit/music/utils/LyricsUtils.kt` | Reuse the LRC parser — no edits expected |
| `app/src/main/kotlin/com/sachit/music/constants/PreferenceKeys.kt` | `MiniPlayerLyricsKey` (additive toggle) |
| `app/src/main/res/values/sachit_strings.xml` | Strings (names below) |
| `app/src/test/kotlin/com/sachit/music/` | Test for the "which line is active at t" pure function |

Out of scope: `MusicService.kt`, the full player lyrics screens, every `values-*` file,
`app/build.gradle.kts`.

## Implementation steps

1. **Drift check**: confirm `MiniPlayer.kt:151` and the two variants; confirm
   `LyricsHelper.getLyrics` signature.
2. **Active-line resolver**: pure function `(syncedLyrics: List<LrcLine>, positionMs: Long) -> Int`
   returning the active line index (last line with `time <= position`), or `-1` before the
   first line. Unit-test boundaries (0ms, exact timestamps, past end).
3. **Mini player wiring**:
   - `LaunchedEffect(mediaId)` fetch lyrics via the helper (reuse whatever caching the player
     screen already does — check `ui/player/Player.kt` lyrics collection; if a shared
     `LyricsCache`/flow exists, subscribe instead of refetching),
   - collect position (the mini player already receives `positionState`),
   - render the active line (or the next line if between lines) in a single-line,
     `basicMarquee()`-free Text below the title — truncated, animated alpha on change,
   - onClick → the same action the mini player's existing lyric affordance/expand uses, or
     open player + navigate to lyrics page if such a route exists.
4. **Gating**:
   - new toggle `MiniPlayerLyricsKey` (default ON; respect "show lyrics" permission-free —
     no extra permission is needed),
   - hide when: no synced lyrics (plain lyrics are NOT shown — avoid a wall of text),
     instrumental flag set, or Listen Together guest (session lyrics follow the host's song;
     the same mechanism works, keep it simple and let it display).
5. **Strings**: `mini_player_lyrics` (settings toggle title),
   `mini_player_lyrics_desc` (settings description), `mini_player_lyrics_instrumental`
   (optional placeholder shown for instrumentals). English content in `sachit_strings.xml`.
6. **Settings entry**: add the toggle next to the existing mini-player settings in
   `AppearanceSettings.kt` (find the `UseNewMiniPlayerDesignKey` usage and mirror its row).
7. **Verify**:
   ```bash
   ./gradlew :app:testFossDebugUnitTest --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Manual QA: play a song with LRCLIB synced lyrics → line advances with playback; play an
   instrumental → placeholder; toggle off → line hidden; plain-lyrics-only song → nothing shown.

## Edge cases

- Unsynchronized position state (seeking): clamp, never index out of bounds.
- Long lines: single line + ellipsis; no marquee (battery/perf).
- No network: fetch fails → feature silently hidden (no retry loop; retry on next mediaId).
- The `Up Next peek` card (Appearance toggle) overlapping: lyric line lives inside the mini
  player row, peek card overlays above — verify both together.

## Done criteria

- Lyric line visible on both mini player variants (or New only, with the decision recorded),
  synced within ~200ms perceptually, toggleable in settings.
- Zero network calls added per position tick; lyrics fetched once per mediaId.
- `MusicService.kt` untouched; strings only in default `sachit_strings.xml`.

## Escape hatches

- If lyrics are already exposed as a shared flow from a ViewModel that the mini player can
  collect cheaply, use it instead of calling `LyricsHelper` directly — note it in the report.
- If word-by-word karaoke timing complicates line-level extraction, degrade to line-level
  timestamps only (strip word timing) and note the limitation.
