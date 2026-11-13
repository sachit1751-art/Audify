# 06 — Audio Quality Controls

**Status:** Proposal — reconcile with Plan 11 and the existing audio pipeline.

## Problem
Audio settings can be difficult to understand or inconsistent across output devices and playback sources.

## Brief solution
Audit existing controls first, then make supported quality options explicit and predictable. Consider per-network streaming quality, output-device behaviour, volume normalization, gapless playback, and crossfade only where the current pipeline and provider permit them.

## Expected outcome
Users understand the active audio behaviour and can configure supported quality features without unexpected volume or transition changes.

**Acceptance signal:** settings are accurately reflected in playback, persist as intended, and are tested across supported output routes.
