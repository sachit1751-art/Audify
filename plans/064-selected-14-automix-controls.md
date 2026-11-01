# Plan 064 — Selected feature 14: Better Automix controls

**Status: planned — extend existing Automix planner and analysis; do not rebuild the engine.**

## Objective

Expose understandable transition controls: per-playlist preference where supported, transition compatibility feedback, a safe preview mechanism, and clearer explanation when Automix chooses or declines a transition.

## Why

Automix is technically complex. Controls should expose meaningful choices without requiring users to understand signal-processing internals or causing audio gaps during preview.

## What exists today

- `shared/.../playback/smart/AdvancedAutomixPlanner.kt` and tests implement planning logic.
- `shared/.../playback/smart/PhraseBarAutomix.kt` and tests cover phrase-aware transitions.
- `playback/TransitionFilterProcessor.kt`, `TrackAnalyzer.kt`, and `AudioCache.kt` support processing/analysis.
- `SettingsSheet.kt` exposes Automix and performance settings.

## Scope

**In scope:** explainable compatibility states; user-selectable conservative/balanced/creative behavior if planner can support them; per-playlist preference only if playlist identity/persistence supports it without schema change; preview through a non-disruptive sample/visual timeline; reset and fallback behavior.

**Out of scope:** a second mixing engine; guaranteed preview playback without an isolated audio path; user controls that bypass clipping/headroom protections; schema changes.

## Affected files

| File | Expected change |
|------|-----------------|
| `shared/.../playback/smart/AdvancedAutomixPlanner.kt` | Typed transition explanation and policy knobs |
| `shared/.../playback/smart/PhraseBarAutomix.kt` | Reuse phrase analysis where relevant |
| `playback/TransitionFilterProcessor.kt` | Only if transition parameter application needs safe ramping |
| `ui/screens/SettingsSheet.kt` / player settings | Controls and explanation |
| Automix common/JVM tests | Planner behavior and compatibility |

## Implementation

1. Audit planner inputs/outputs and current user settings; identify controls that have real engine support.
2. Define a compact transition result explaining duration, alignment, and fallback reason.
3. Add bounded preference modes and map them to tested planner constraints.
4. Add preview as a visualization or isolated preview only if it cannot interrupt live playback; otherwise explicitly defer audio preview.
5. Add playlist-level preference only through existing safe preference storage; do not introduce schema changes.
6. Verify transition behavior on real audio hardware before calling the feature complete.

## Testing

- Planner tests for mode mapping, incompatible tracks, insufficient analysis, and fallback.
- Audio processor tests for clipping/headroom and parameter changes.
- Manual listening tests for audible transition quality, gaps, clicks, and CPU load.
- Regression for crossfade disabled/enabled and low-performance mode.

## Performance notes

Reuse cached analysis; do not run expensive analysis solely to render a settings preview. Respect the existing performance mode and thermal constraints.

## Rollback

Remove new controls and revert to existing Automix defaults; preserve the current planner and analysis pipeline.

## Rejected alternatives

- Expose dozens of DSP sliders: complexity without user value.
- Promise a live audio preview that hijacks playback: unacceptable interaction.
- Allow per-playlist storage by adding an unapproved schema migration.

## Implementation contract and acceptance criteria

### Control semantics
- Inventory actual planner inputs before exposing controls. Every UI option must map to a deterministic planner parameter and have a documented effect; do not ship cosmetic toggles.
- Compatibility result must distinguish supported transition, reduced-quality transition, and no safe transition, with reasons such as missing analysis, tempo/key mismatch, insufficient overlap, or processor limitation.
- User preference precedence is explicit: playlist override only when a stable playlist identity and approved persistence exist; otherwise global mode. Reset returns to global default.
- Preview is non-audible timeline/visualization in v1 unless an isolated preview path is proven not to interrupt the live player. Never simulate audio preview by seeking or replacing the active queue.

### Audio safety
- Existing clipping/headroom, CPU/performance, and low-power safeguards remain authoritative over creative settings.
- Parameter changes apply at safe transition boundaries or via tested ramps; mid-transition changes must not cause clicks or discontinuities.
- Unsupported/incompatible tracks fall back to existing crossfade/transition behavior with an explanation.

### Done when
- Each control has planner-level tests proving distinct behavior and safe bounds.
- Manual listening QA covers compatible/incompatible pairs, low-performance mode, crossfade off/on, and processor load on real hardware.
- No second mixing engine or unapproved schema is introduced.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
