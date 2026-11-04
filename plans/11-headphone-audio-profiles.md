<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 11 — Headphone audio profiles

**Status: proposal — reconcile with output-specific preset work and inspect the audio engine.**

## Objective

Evaluate and, if technically and legally viable, add optional headphone correction profiles and output-aware audio preferences without misrepresenting Android system effects as Audify-controlled processing.

## Existing foundation to verify

`docs/PRODUCT.md` records audio normalization, playback quality controls, output selection, and audio diagnostics. The roadmap already includes output-specific EQ presets. This plan is specifically about measured headphone profiles/AutoEQ-style correction, not a duplicate EQ or preset implementation.

## Scope

**In scope:** audio-effect capability audit; profile format/licensing review; profile search/select; safe preamp/headroom handling; output association where Android exposes a stable identifier; clear active/inactive/unsupported status; reset and bypass.

**Out of scope:** claiming universal headphone detection, bypassing OEM/system effects, modifying audio at the system level, bundled copyrighted profile databases without permission, or replacing existing EQ controls.

## Implementation

1. Reconcile the existing output-preset plan and inspect the current audio session/effect ownership on each supported device class.
2. Determine which effects can be applied reliably to Audify's playback session and which are owned by Android, OEM software, Bluetooth devices, or Cast receivers.
3. Review candidate profile data sources for license, attribution, redistribution, update, and offline-use rights before bundling anything.
4. Define profile schema and correction semantics without changing persistent database schema; use existing settings only if suitable.
5. Apply correction only when the target output/session supports it. Include headroom/preamp safeguards and a one-tap bypass.
6. Display truthful state: selected profile, applied, unavailable, unsupported, or external-system-managed.
7. Validate with representative wired, Bluetooth, and unsupported outputs; compare frequency-response behavior only with appropriate test equipment/data.

## Acceptance criteria

- Existing EQ and normalization behavior remains unchanged when no profile is selected.
- No profile is claimed active unless the audio session confirms the effect is attached.
- Profile licenses and provenance are documented; no unlicensed database is bundled.
- Headroom safeguards prevent profile gain from causing avoidable clipping.
- Unsupported outputs degrade gracefully and retain normal playback.
- Real-device testing covers lifecycle, route changes, Bluetooth reconnect, and effect release.

## Rollback

Disable profile application and retain existing EQ/output settings; no media or playlist data is affected.

## Mandatory pre-implementation audit

Read the output-specific EQ plan and inspect the current audio pipeline/effect classes. Identify device/API constraints, profile data licensing, and test equipment availability. Stop if the feature requires unsupported system-level control or unapproved data redistribution.

## Project constraints and handoff

Follow `plans/README.md`, `docs/PRODUCT.md`, and `docs/DESIGN.md`. No schema/migration, version bump, translated-string edits, or unauthorized commits/pushes. Report which outputs were genuinely tested and which are unsupported.
