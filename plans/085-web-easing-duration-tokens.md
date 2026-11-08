# 085 — Normalize web UI easing and duration tokens

- **Status**: TODO
- **Commit**: ba5d080d
- **Severity**: HIGH
- **Category**: Easing & duration; cohesion
- **Estimated scope**: 4–8 files; focused token and class cleanup

## Problem

The shared token `--global-transition` in `web/app/src/styles/tokens.css:61` uses `ease-in`, and recurring UI controls also use `ease-in`. The audit playbook treats `ease-in` on UI as a finding because it delays the response at the moment the user is watching. Enter/exit should use ease-out; hover and colour changes should use ease; on-screen movement should use ease-in-out. UI animations should remain under 300ms.

Current token:

```css
--ease-out-panel: cubic-bezier(0.215, 0.61, 0.355, 1);
--duration-panel: 0.3s;
--global-transition: opacity 0.1s ease-in;
--duration-arrow: 0.25s;
```

Other confirmed call sites include `web/app/src/shell/FullScreenPlayer.tsx:57-63`, `:79`, `:121`, `web/app/src/components/music/Lockups.tsx:74,81,148,171`, `web/app/src/components/music/SearchField.tsx:166`, and `web/app/src/shell/Lcd.tsx:138,204`.

## Target

Add or reuse shared tokens matching the audit playbook exactly:

```css
--ease-out: cubic-bezier(0.23, 1, 0.32, 1);
--ease-in-out: cubic-bezier(0.77, 0, 0.175, 1);
--ease-drawer: cubic-bezier(0.32, 0.72, 0, 1);
```

- Use `ease` for hover/colour changes.
- Use `var(--ease-out)` for entrances/exits.
- Use `var(--ease-in-out)` for spatial movement/morphing.
- Keep button press feedback 100–160ms; small popovers 125–200ms; dropdowns 150–250ms.
- Replace `--global-transition`'s `ease-in` with `ease` for its opacity-only hover/state use.

## Repo conventions to follow

- Motion tokens belong in `web/app/src/styles/tokens.css`.
- Existing `--ease-out-panel` and `--duration-panel` are established conventions; preserve them unless a deliberate migration is required. Avoid duplicate near-identical curves.
- Tailwind arbitrary easing/duration classes are already used in component class strings.

## Steps

1. Search all web source for `ease-in`, `transition: all`, bare `ease` on entrances, and UI durations over 300ms. Separate continuous motion and marketing/explanatory content from interactive UI.
2. Add the three exact shared curves above only if the existing tokens do not already provide equivalent names/values; retain the existing panel token where its use is intentionally distinct.
3. Change `--global-transition` to an appropriate hover/state curve (`ease`) and migrate frequent interactive UI call sites to the correct semantic token.
4. Do not change the artist hero, artwork load or full-screen player long fades in this plan; those are handled by plan 086/087.
5. Add a source-level test or lint assertion preventing new `ease-in` in interactive UI classes and flagging UI transitions over 300ms for review.

## Boundaries

- Do not blindly replace every `ease-in`; classify the property and interaction first.
- Do not change linear progress/marquee animations.
- Do not alter marketing/explanatory animations solely to meet the UI budget.
- Do not add dependencies or change markup.

## Verification

- **Mechanical**: inspect `web/app/package.json`, run the existing lint, typecheck and test scripts, plus the new motion-token assertion.
- **Feel check**: test hover, focus, pressed, selected and menu open/close states with DevTools at 10% playback. Hover feedback should start promptly; entrances should start quickly and settle without bounce.
- Spam reversible controls and open/close menus mid-transition; CSS transitions must retarget from the current visual state.
- **Done when**: interactive web motion follows the shared curve/duration rules and no unreviewed `ease-in` remains in frequent UI.
