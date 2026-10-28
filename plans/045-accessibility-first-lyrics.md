# Plan 045 — Accessibility-first lyrics

**Status: not started.** Verified at commit `61778ab4` — lyrics exist (portrait `LyricsLine`, 
`OriginalLyrics`, `ExperimentalLyrics`, landscape plan 027 pending) but there's no accessibility-focused 
lyrics mode: font scale to 2×, steady mode (no sync animation for users who prefer stable text), 
reduced-motion compliance, high-contrast text option.

## Objective

Make lyrics accessible: support larger font sizes (up to 2× system font scale), a steady mode that shows 
lyrics without the word-by-word highlight animation (for users who find the animation distracting or 
hard-to-read), reduced-motion compliance (respect Android's `prefers-reduced-motion`), and a high-contrast 
text option. The sync data still drives the highlighting for users who want it — accessibility mode is an 
option, not a replacement for sync.

## Why

- Innovation #8: fontScale to 2×, steady mode, reduced-motion compliance, high-contrast.
- Lyrics sync (word-by-word highlight) is visually rich but can be a barrier for some users — the animation 
can be distracting, the text can be too small, and the motion may violate reduced-motion preferences.
- Accessibility is a quality feature — it makes the app usable for more people and is a marker of polish.

## What exists today

- `ui/component/LyricsLine.kt` (or the core lyrics renderer) — word-by-word sync highlighting.
- `ui/component/OriginalLyrics.kt` + `ExperimentalLyrics.kt` — other lyrics UI paths.
- The app may or may not already read `Configuration.fontScale` / `prefers-reduced-motion` — the executor 
checks at implementation time and adds the accessibility features on top.
- Landscape lyrics (plan 027) is pending — accessibility should apply to all lyrics UI paths (portrait, 
landscape, mini-player lyric line).

## Scope

**In scope:**
- **Font scale to 2×**: a settings option (or a lyrics accessibility setting) that scales lyrics text up to 
2× the system font scale (or a fixed large size). The user can set a lyrics font size multiplier (1× / 1.5× 
/ 2×, or similar). Applies to all lyrics UI paths.
- **Steady mode**: a toggle that disables the word-by-word highlight animation and shows the current line in a 
stable, full-color (no animation, no dimming of the rest — or a static current-line emphasis). The lyric 
timing still drives which line is "current" — the current line is shown statically, not animated. The user 
chooses steady mode; sync timing still drives line selection.
- **Reduced-motion compliance**: read `androidx.core.provider.Settings` / `Configuration` / the Compose 
`ReduceMotion` API and, when reduced motion is preferred, disable the word-by-word animation globally (or offer 
steady mode as the default when reduced motion is on). The executor implements reduced-motion detection at 
implementation time.
- **High-contrast text**: a toggle for high-contrast lyrics text (e.g. white text on a dark background, or 
black on parchment with stronger contrast). The executor picks the high-contrast variant at implementation 
time — it should be a clear contrast improvement, not just a color swap.
- Applies to all lyrics UI paths: portrait player lyrics, landscape lyrics (plan 027), mini-player lyric line.

**Out of scope:**
- Audio description / voiceover of lyrics — defer; v1 is text accessibility, not audio.
- Braille / TalkBack-specific lyric UI — TalkBack should already read the lyrics text (the executor verifies 
at implementation time that TalkBack can read the lyric lines); v1 focuses on visual accessibility.
- Custom lyric font family — defer; v1 uses the system font at larger sizes / high contrast.
- Captioning-style positioning ( Lyrics as closed captions overlaid on video ) — that's a different feature 
(defer; v1 is lyrics screen/strip accessibility).

## Affected files

| File | Change |
|------|--------|
| `ui/component/LyricsLine.kt` (or the core lyrics renderer) | Add accessibility params: fontScale multiplier, steady mode (disable animation), high-contrast. The renderer applies these to the lyric display. |
| `ui/component/OriginalLyrics.kt` + `ExperimentalLyrics.kt` | Same accessibility params applied to these paths — consistent across all lyrics UI. |
| `ui/player/Player.kt` + landscape lyrics (plan 027) + mini-player | Pass accessibility params through to the lyrics render — all lyrics paths honor the settings. |
| `constants/PreferenceKeys.kt` | Add lyrics accessibility preferences: `LyricsFontScaleKey`, `LyricsSteadyModeKey`, `LyricsHighContrastKey`. |
| `ui/screens/settings/PlayerSettings.kt` (or a lyrics accessibility section) | Settings UI: font scale selector, steady mode toggle, high-contrast toggle. |
| `sachit_strings.xml` | Accessibility labels: "Lyrics font size", "Steady lyrics", "High-contrast lyrics", "Respects reduced motion", etc. |

## Technical approach

1. **Font scale** — A lyrics font scale preference (multiplier 1× / 1.5× / 2×, or a fixed set of sizes). The 
lyrics renderer applies the multiplier to the lyric text size. The executor picks the size options at 
implementation time; up to 2× is the requirement. The scale is applied on top of the system font scale (so 
it respects the user's system font settings too).
2. **Steady mode** — A toggle that changes how the current line is emphasized. In normal mode, the word-by-word 
highlight animation runs (current word highlighted as it's sung). In steady mode, the animation is disabled and 
the current line is shown in a stable emphasis (e.g. the current line is bolder / different color, no word-level 
animation, no dimming of past/future lines — or a static current-line color). Sync timing still drives which 
line is current — the current line is determined by timing, displayed statically.
3. **Reduced-motion** — At implementation time, read the system reduced-motion preference (e.g. 
`Settings.Provider.getSettings("", Settings.Global.REDUCED_MOTION)` or the Compose `LocalInspectionMode` / 
`ReduceMotion` APIs). When reduced motion is preferred, steady mode is on by default (or the animation is 
disabled). The user can still toggle steady mode off if they want (the kill switch is the setting, not a hard 
force).
4. **High-contrast text** — A toggle for high-contrast lyrics text. The executor picks the variant at 
implementation time: e.g. white lyrics on a near-black background, or black on parchment with boosted contrast. 
The key is a clear contrast improvement for users who need it. Apply to all lyrics paths.
5. **Cross-cutting** — The accessibility params are passed to all lyrics renderers (portrait, landscape, 
mini-player). The settings are in one place (PlayerSettings or a lyrics accessibility section) and the renderers 
read them (via state / ViewModel / composition local). The executor picks the wiring at implementation time.

## Testing

- Unit test for the accessibility params: given font scale / steady / high-contrast settings, assert the renderer 
outputs the right text size / no animation / right color. The animation test is a behavior test (no animation 
when steady) — the executor implements it as a state check.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: set lyrics font scale to 2×, verify lyrics text is larger across all lyrics paths. Enable steady mode, 
verify the word-by-word animation is gone and the current line is shown statically. Enable reduced-motion system 
setting, verify lyrics animation is disabled (or steady mode is on). Enable high-contrast, verify the text 
contrast is clearly improved. Verify all lyrics paths honor the settings (portrait, landscape if plan 027 exists, 
mini-player lyric line).
- Regression: normal lyrics mode (animation, default size, default contrast) still works when accessibility 
settings are off. Existing lyrics sync timing still drives line selection.

## Performance notes

- Accessibility params are state — cheap to apply. No new work per frame beyond the existing lyric render.
- Font scaling and high contrast are layout/text changes — cheap.

## Rollback

- Remove the accessibility preferences and the params from the lyrics renderers → lyrics revert to default 
(animations on, default size, default contrast). Zero impact on sync timing or the lyric data.

## Anti-BitChord check

- Accessibility is an Audify quality feature — implement it as Audify: font scale, steady mode, reduced-motion, 
high-contrast, applied across all lyrics paths. Don't copy a competitor's a11y approach — use standard Android 
accessibility patterns.
- Steady mode keeps sync timing (line selection) — it only disables the animation. Don't remove sync; make it 
static for users who prefer no motion.
