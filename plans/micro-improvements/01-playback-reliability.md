# 01 — Playback Reliability

**Status:** Proposal — reconcile with existing playback-state and Media3 work before implementation.

## Problem
Playback can feel unreliable when startup is slow, the app is resumed, the network changes, or a provider temporarily fails.

## Brief solution
Audit the playback lifecycle and add targeted fixes for fast start, state restoration, network recovery, clear retry behaviour, and prevention of duplicate playback commands. Reuse the existing Media3 session and playback state rather than creating another playback layer.

## Expected outcome
Playback starts and resumes predictably, recovers gracefully from temporary failures, and gives users clear feedback when recovery is impossible.

**Acceptance signal:** lifecycle, network-switch, process-recreation, and provider-error tests pass without duplicate playback or lost queue state.
