# Plan 032 — 10-band EQ + AutoEq profiles

**Status: not started.** Verified at commit `4a3bee56` — a full parametric EQ already exists in
`com.sachit.music.eq/` with `CustomEqualizerAudioProcessor`, `ParametricEQ`, `EQState`, `EQScreen`,
`EqFrequencyResponseGraph`, and an AutoEq wizard (`WizardScreen`, `GitHubAutoEqSearch`, `ParametricEQParser`).
What's missing is the **10-band preset grid** and **AutoEq profile import UX** (parse CSV/text, apply
presets, per-output association).

## Objective

Add a 10-band preset EQ grid (the typical consumer EQ layout — 10 fixed frequency bands the user can
drag up/down) on top of the existing parametric EQ, plus a cleaner AutoEq import path: parse a CSV/text
AutoEq profile, show it as an applicable preset, and associate presets with outputs (headphones, speaker,
Bluetooth device). Reuse the existing `CustomEqualizerAudioProcessor` + `ParametricEQ` as the engine.

## Why

- Matrix #9 + #22: SimpMusic ships 10-band EQ + AutoEq profiles; Poweramp has per-output EQ. Audify has
  the DSP engine but not the consumer-facing preset grid or AutoEq import UX.
- The existing AutoEq wizard already fetches profiles from GitHub — the gap is the apply/import UX and the
  10-band preset surface.
- Power user feature — not every user wants it, but the ones who do care a lot.

## What exists today

- `com.sachit.music.eq/audio/CustomEqualizerAudioProcessor.kt` — the DSP engine.
- `com.sachit.music.eq/data/ParametricEQ.kt` + `ParametricEQParser.kt` — parametric EQ model + parser.
- `com.sachit.music.eq/data/EQProfileRepository.kt` — profile storage.
- `com.sachit.music.eq/EqualizerService.kt` — EQ service wiring.
- `ui/screens/equalizer/EQScreen.kt` + `EQState.kt` + `EQViewModel.kt` — the EQ UI.
- `ui/screens/equalizer/EqFrequencyResponseGraph.kt` — frequency response visualization.
- `ui/screens/equalizer/wizard/WizardScreen.kt` + `WizardViewModel.kt` + `WizardState.kt` — the AutoEq
  wizard that fetches GitHub AutoEq profiles.
- `EQProfileRepository`, `GitHubAutoEqSearch` — already fetch + store AutoEq profiles.

## Scope

**In scope:**
- A 10-band preset grid in the EQ screen: 10 frequency bands (e.g. 32, 64, 125, 250, 500, 1k, 2k, 4k,
  8k, 16k Hz) shown as a grid of draggable rows or a graphic EQ. Each band has a gain slider. The grid
  maps to the existing parametric EQ engine (each band → a parametric filter, or the grid is a simplified
  surface on top of the parametric engine).
- Preset management: save the current 10-band state as a named preset, load a preset, delete a preset,
  a few built-in defaults (Flat, Bass boost, Treble boost, Vocal). Presets persisted in `EQProfileRepository`
  or a new preset store.
- AutoEq import UX: a clearer path to fetch an AutoEq profile (from the existing `GitHubAutoEqSearch`),
  parse it, show it as an applicable preset, apply it. The existing wizard already fetches — the gap is the
  apply flow and the parsing/import UX.
- Per-output association (v1: simplistic) — a preset can be tagged to an output type (headphones / speaker
  / Bluetooth). When the output changes, optionally apply the matched preset. v1 can be a simple "apply to
  current output" without full per-output auto-detection.
- The existing parametric EQ is not replaced — the 10-band grid is a surface on top of (or alongside) the
  parametric engine. The user can use whichever.

**Out of scope:**
- Replacing the parametric EQ with a 10-band-only engine — keep the parametric engine, add the grid as a
  surface.
- AutoEq profile authoring / editing — import and apply, not edit the profile coefficients.
- Per-output auto-switching based on Bluetooth device MAC / audio device ID — defer; v1 is "apply to current
  output" with a manual association.
- Convolution / specialty filters beyond what the parametric engine already handles.

## Affected files

| File | Change |
|------|--------|
| `ui/screens/equalizer/EQScreen.kt` | Add the 10-band preset grid UI alongside the existing parametric controls; add preset load/save/delete UI |
| `ui/screens/equalizer/EQState.kt` | Add 10-band state, current preset, preset list, output tag |
| `ui/screens/equalizer/EQViewModel.kt` | Bridge: 10-band ↔ parametric mapping, preset save/load/delete, AutoEq import apply flow |
| `com.sachit.music.eq/data/` | Add a 10-band preset model + store (or extend `EQProfileRepository`); add CSV/text AutoEq import parser if the existing parser doesn't cover it |
| `com.sachit.music.eq/audio/CustomEqualizerAudioProcessor.kt` | May need a 10-band → parametric filter mapping if the grid is a simplified surface; check at implementation time |
| `com.sachit.music.eq/EqualizerService.kt` | Wire the 10-band surface to the engine |
| `ui/screens/equalizer/wizard/WizardScreen.kt` | Clarify the apply path: after fetching/parsing an AutoEq profile, show "Apply as preset" → saves as a named preset in the 10-band grid's preset list |
| `sachit_strings.xml` | "10-band EQ", band labels, preset names, "Save preset", "Load preset", "Delete preset", "Apply AutoEq profile", per-output labels |

## Technical approach

1. **10-band model** — A `BandPreset` with 10 gains (one per frequency). The grid UI shows the 10 bands.
   The mapping to the parametric engine: each band → a parametric filter (gain + Q + frequency), or the
   grid is a simplified surface that internally sets parametric filters. The executor decides the mapping at
   implementation time; the key is that the 10-band grid is a user-friendly surface on top of the existing
   engine.
2. **Presets** — `EQPreset`: name, bandGains (10 values), output tag (optional), built-in flag. Store in
   `EQProfileRepository` or a new `EQPresetStore`. Built-in defaults ship with the app; user presets are
   saved/loaded/deleted by the user.
3. **Grid UI** — A vertical or horizontal grid of 10 bands, each with a gain slider (or a draggable point
   on a frequency response overlay). Prefer the existing `EqFrequencyResponseGraph` as the visual base —
   extend it to show 10-band control points, or add a parallel grid. Reuse, don't copy SimpMusic's look.
4. **AutoEq import UX** — The existing wizard fetches a profile. After fetch, the flow is: parse profile →
   show as an applicable preset → "Apply" → saves as a named preset in the 10-band grid's preset list →
   applies it to the engine. The existing `ParametricEQParser` may already handle the parsing — check at
   implementation time. If it doesn't, add a CSV/text import path.
5. **Per-output association** — v1: a preset can have an output tag (headphones / speaker / Bluetooth).
   When the user applies a preset, they can tag it to the current output. A later pass can auto-apply the
   matched preset on output change. v1 is manual association, not auto-detection.
6. **Coexistence with parametric** — The 10-band grid and the parametric controls coexist. The user can use
   the grid or the parametric sliders. If both are used, decide at implementation time how they compose
   (e.g. the grid sets parametric filters, and the parametric sliders adjust the same filters). Keep it
   simple: the grid is a surface; the parametric engine is the backend.

## Testing

- Unit test for the 10-band ↔ parametric mapping (if the grid is a surface on top of the parametric
  engine): set 10 band gains, assert the parametric filters are set correctly.
- Unit test for preset save/load/delete: save a preset, load it, verify the band gains match; delete it,
  verify it's gone.
- Unit test for AutoEq import: parse a sample AutoEq CSV/text → assert the resulting band gains are
  sensible (the existing `ParametricEQParser` may already be tested).
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: open EQ screen, verify 10-band grid appears, drag bands, save preset, load preset, verify it
  restores. Fetch an AutoEq profile via the wizard, apply it, verify it loads as a preset and the EQ
  response changes. Verify per-output tag (v1).
- Regression: existing parametric EQ still works; existing AutoEq wizard still works; frequency response
  graph still works.

## Performance notes

- The DSP engine is already there — adding a 10-band surface is a UI + mapping cost, not a new DSP cost.
- Presets are tiny (10 floats each). No performance concern.

## Rollback

- Remove the 10-band grid UI, the preset store, and the AutoEq import UX → parametric EQ + existing wizard
  unchanged. The engine is untouched.

## Anti-BitChord check

- The 10-band grid is a consumer-friendly surface on top of Audify's parametric engine — not a replacement.
  Reuse the existing engine and graph. Don't copy SimpMusic's grid look — use Audify's M3 visual language.
- AutoEq import reuses the existing wizard + parser — don't invent a new fetch/auth path.
