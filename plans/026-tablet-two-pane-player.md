# Plan 026 — Tablet / two-pane player layout

**Status: not started.** Verified at commit `9e594bde` — no `calculateWindowSizeClass` usage, no
side-pane player layout, the player is a bottom sheet on all screen sizes.

## Objective

On tablets (and large phones in landscape), show the queue/context alongside the player instead of
hiding it behind a bottom sheet. Same player, same queue — different layout.

## Why

- Matrix #15: BitChord v1.7 (`e96ac9b`) ships a two-pane layout on large screens.
- Modern Android UX expectation: on a 7"+ screen, a bottom sheet that covers half the screen feels
  like a phone layout stretched, not a tablet layout.
- Pure UI change — no new features, no backend, no DB. Low risk.

## What exists today

- `ui/component/BottomSheet.kt` — the player is a bottom sheet with `isExpanded` state.
- `MainActivity.kt` — reads `playerBottomSheetState.isExpanded` for some UI decisions.
- `ui/player/Player.kt` — the player COMPOSABLE; laid out inside the bottom sheet host.
- No window size class detection anywhere in the app.
- The queue is a separate screen/navigation destination today (`QueueMenu` / queue sheet).

## Scope

**In scope:**
- Detect window size class (compact / medium / expanded) via `calculateWindowSizeClass` on the activity.
- Pass the size class down to the player host.
- On medium/expanded width: render the player as a persistent side pane (right side) instead of a
  bottom sheet. The queue / now-playing context lives in the left pane.
- On compact: keep the current bottom-sheet behavior unchanged.
- The player composable itself is shared — only the host container changes.
- Mini-player at the bottom still collapses to the same compact mini-player on all sizes.

**Out of scope:**
- Restructuring the entire app into a two-pane master-detail (just the player area).
- Changing navigation routing for the queue — the queue still opens as a sheet/screen; only the
  persistent player host layout changes.
- Landscape-only behavior — use window size class, not orientation. A large portrait phone is still
  "expanded" and should get the two-pane treatment; a small landscape tablet is still "compact".
- Any change to playback logic, queue logic, or MediaSession — purely a layout refactor.

## Affected files

| File | Change |
|------|--------|
| `MainActivity.kt` | Call `calculateWindowSizeClass()`; expose `windowSizeClass` via a composition local or pass as param |
| `ui/player/PlayerHost.kt` (new, or extend existing host) | Two layouts: bottom-sheet host (compact) vs side-pane host (medium/expanded) |
| `ui/component/BottomSheet.kt` | Likely unchanged for compact; the sheet becomes the compact-only path |
| `ui/player/Player.kt` | Unchanged internals; only the outer host changes where Player is placed |
| `ui/screens/player/` (if any) | May need a small wrapper for the side-pane context |
| `sachit_strings.xml` | Any new accessibility labels if the side-pane needs a screen-reader label |

## Technical approach

1. **Size class** — Add `calculateWindowSizeClass()` in `MainActivity.onCreate` (or in the Compose
   content). Store in a `CompositionLocal` or pass as a parameter. Medium+ width = side-pane mode.
2. **Host** — Extract the player's outer container into a `PlayerHost` composable that takes the size
   class. Compact: bottom sheet. Medium/expanded: a `Row` with the queue/context pane (left) and the
   player pane (right). The player pane hosts the existing `Player` composable.
3. **Queue context pane** — On medium/expanded, the left pane shows the current queue / now-playing
   context (the same data the bottom sheet shows when expanded today). It's not a full queue editor —
   it's the "what's playing / what's next" context that the sheet currently reveals.
4. **Mini-player** — Unchanged. Always the compact bar at the bottom. Tapping it opens the player in
   whatever mode the screen supports.
5. **Animations** — The transition between compact and expanded is a window resize event, not a runtime
   animation the user triggers. Keep it simple: the layout re-composes on size class change. No shared
   element transition needed between the two modes.

## Testing

- Build: `./gradlew :app:assembleFossDebug` must pass.
- Emulator: test on a compact phone (pixel), a medium tablet (7" landscape), and an expanded tablet
  (10" landscape). Verify: compact = bottom sheet; medium/expanded = side pane; mini-player works on
  both; queue context shows in the side pane; playback works in both.
- Rotation: rotating a tablet from portrait to landscape should re-flow to side pane (or stay, depending
  on size class) without losing playback state.
- Regression: mini-player, full player, queue, lyrics sheet, all still work in compact mode.

## Performance notes

- Zero runtime cost when not on a large screen — size class is computed once per configuration change.
- No new dependencies.

## Rollback

- Remove the size-class branch in `PlayerHost` → everything goes back to bottom sheet. The size class
  computation can stay (it's harmless) or be removed.

## Anti-BitChord check

- BitChord's two-pane is the reference for the *idea*, not the implementation. Use Audify's existing
  bottom-sheet content for the side pane, not a copied layout.
- Keep the same visual language (parchment, M3 components, the "S" identity) — no BitChord colors or
  naming.
