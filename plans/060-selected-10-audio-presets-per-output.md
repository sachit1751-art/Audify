<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 060 — Selected feature 10: Audio presets per output

**Status: planned — extends EQ foundation in plan 032; output routing capability must be audited first.**

## Objective

Allow users to associate an EQ preset and supported audio preferences with output categories (built-in speaker, wired, Bluetooth, USB, Cast). Apply only when the active route is reliably identified and the audio engine supports the setting.

## Why

Headphones and speakers can benefit from different tuning, but automatic switching must not claim control over settings owned by Android, a DAC, or a Cast receiver.

## What exists today

- `shared/.../playback/EqualizerPreset.kt` and `EqualizerCurve.kt` define preset data.
- `app/.../playback/EqualizerProcessor.kt`, `AudioRouting.kt`, and `AudioOutputStatus.kt` implement audio processing/routing.
- `ui/screens/EqualizerScreen.kt` and `ui/player/AudioOutput.kt` expose current controls.
- Plan 032 covers 10-band EQ/AutoEq; this plan adds route association only.

## Scope

**In scope:** route classification using supported Android APIs; per-route preset selection; fallback default; explicit manual override; route-change application with smoothing; unsupported-route messaging; reset controls.

**Out of scope:** claim to control external receiver EQ; automatic volume changes; per-device fingerprinting; changing the audio output architecture; new schema without approval.

## Affected files

| File | Expected change |
|------|-----------------|
| `playback/AudioRouting.kt` / `AudioOutputStatus.kt` | Stable route category and capability model |
| `playback/EqualizerProcessor.kt` | Safe preset switch without audio discontinuity |
| `shared/.../EqualizerPreset.kt` | Reuse preset model |
| `ui/screens/EqualizerScreen.kt` | Route preset selector and default behavior |
| settings persistence | Per-route preference storage using approved existing mechanism |

## Implementation

1. Audit route APIs and identify which routes can be distinguished reliably on supported Android versions.
2. Define a conservative route enum; unknown/external routes use the default preset.
3. Add per-route preset mapping and manual override semantics.
4. Apply EQ changes with a short ramp/crossfade to avoid clicks; do not reinitialize unrelated output components.
5. Surface “managed by external device” where Audify cannot apply processing.

## Testing

- Pure route-to-preset resolution tests, including unknown route and disconnect/reconnect.
- EQ processor tests for transitions and clipping/headroom.
- Manual built-in, wired, Bluetooth, USB, and Cast checks on available hardware.
- Confirm no changes to system volume or receiver-owned settings.

## Performance notes

Route changes are infrequent; avoid polling. Keep preference lookup cheap and do not rebuild the full player pipeline for a preset switch.

## Rollback

Disable automatic route mapping and retain the existing global EQ preset selection.

## Rejected alternatives

- Device-name keyed settings: names are unstable and can expose unnecessary device identifiers.
- Claim per-route control for Cast: processing may occur remotely.
- Apply large EQ jumps instantly: can produce audible discontinuities.

## Implementation contract and acceptance criteria

### Route capability model
- Persist preferences by coarse route category, not device name/MAC/address. Categories must include unknown; route detection must be based on supported platform signals and expose uncertainty.
- Distinguish Audify-controlled processing from system/receiver processing. Cast and external DSP paths default to not-controlled unless the app can prove its processor is in the active audio path.
- Define precedence: explicit session override, saved route preset, then global default. Specify whether manual override survives disconnect/reconnect; do not infer silently.

### Audio transition behavior
- Switching presets must update the active processor without rebuilding player/session unnecessarily. Smooth parameter changes and retain headroom/anti-clipping constraints.
- If EQ is unavailable or processor is bypassed, keep playback unchanged and explain why route preset is inactive.
- Route flapping must be debounced; unknown route uses safe global default.

### Done when
- Unit tests cover route mapping, precedence, reconnect, unknown routes, bypass, and rapid route changes.
- Processor tests verify no clipping regression, discontinuity/clicks, or unexpected volume changes.
- Manual checks use real wired/Bluetooth/USB hardware; Cast behavior is marked unsupported unless verified end-to-end.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
