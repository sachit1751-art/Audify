# Plan 010 — One-tap "Song radio" from the player screen

Written against commit **`192b529e`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`192b529e`, check drift in the in-scope files first.

## Goal

A single tap on the player screen that seamlessly extends the queue with radio/related tracks
starting from the current song — the same behavior `PlayerMenu.kt` already offers behind a
menu, promoted to a first-class button.

## Why this is low-risk

The entire mechanism already exists and is battle-tested:

- `PlayerConnection.startRadioSeamlessly()` (`playback/PlayerConnection.kt` line 271) —
  already blocks the action for Listen Together guests (line 274) and delegates to the service.
- `MusicService.startRadioSeamlessly()` (`playback/MusicService.kt` line 1821) — populates
  `automixItems` (line 468), which the Player and Queue screens already render
  (`ui/player/Player.kt` line 326, `ui/player/Queue.kt` line 659).
- `ui/menu/PlayerMenu.kt` line 337 is the existing menu-item call site to copy from.

This plan adds **zero** logic to `MusicService.kt`.

## In-scope files (recon at `192b529e`)

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/ui/player/Player.kt` | Add the radio button to the player's action row |
| `app/src/main/kotlin/com/sachit/music/ui/player/Queue.kt` | Optional: same affordance on the empty-automix state |
| `app/src/main/res/values/sachit_strings.xml` | New strings (names below) |
| `app/src/test/kotlin/com/sachit/music/` | Optional pure test for any new gating logic |

Out of scope: `MusicService.kt`, `PlayerConnection.kt`, every `values-*` file, `app/build.gradle.kts`.

## Implementation steps

1. **Drift check**: confirm `PlayerConnection.startRadioSeamlessly()` is still at
   `PlayerConnection.kt:271` and `PlayerMenu.kt:337` still calls it.
2. **Add the button** to the player's action row (next to like / download / share):
   - icon: `R.drawable.radio` (verify it exists; otherwise pick the closest existing material icon),
   - onClick: call `playerConnection.startRadioSeamlessly()`,
   - content description from a new string (name: `song_radio`),
   - hide it when Listen Together guest mode is active — copy the same gating used at
     `PlayerConnection.kt:274` so the UI never offers a no-op.
3. **Feedback**: after tapping, `automixItems` becomes non-empty and the queue screen already
   shows the incoming tracks. If the automix stays empty for >5s (network failure), show a
   snackbar using a new string `song_radio_failed`.
4. **Strings**: `song_radio`, `song_radio_failed` (English content in `sachit_strings.xml`).
5. **Verify**:
   ```bash
   ./gradlew :app:compileFossDebugKotlin --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Manual QA: play a song → tap radio button → queue shows incoming related tracks; join a
   Listen Together room as guest → button hidden; airplane-mode tap → snackbar, no crash.

## Edge cases

- No network: `startRadioSeamlessly` fails silently upstream — the snackbar covers UX.
- Automix disabled by user setting: respect it; the button should still work by explicitly
  calling the same code path as the menu item (verify parity with `PlayerMenu.kt`).
- Guest mode: hide the button (already the service-side rule).

## Done criteria

- Radio button visible on the player, functional, hidden for LT guests.
- Zero diffs in `MusicService.kt` / `PlayerConnection.kt`.
- Strings only in the default `sachit_strings.xml`.

## Escape hatches

- If the player action-row layout changed since `192b529e`, adapt placement and note drift.
- If `R.drawable.radio` does not exist and no suitable icon is present, reuse the exact icon
  the `PlayerMenu.kt` menu item uses for the same action (parity by construction).
