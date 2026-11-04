<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 08 — Foreground music recognition

**Status: proposal — provider feasibility and privacy review required.**

## Objective

Let a user deliberately identify music currently playing nearby, review the match, and optionally save it to an Audify playlist or recognition history.

## Product boundary

Start with a user-initiated foreground action. Background/always-listening recognition is explicitly excluded from this plan because it changes microphone, battery, privacy, and notification expectations.

## Scope

**In scope:** provider/SDK feasibility research; explicit microphone permission flow; foreground capture lifecycle; recognition result with confidence/uncertainty; retry and no-match states; optional local recognition history; add-to-playlist action after user confirmation.

**Out of scope:** passive/background listening, recording retention, uploading raw audio except the minimum transient data required by an approved provider, automatic playlist mutation, or bypassing provider terms.

## Implementation

1. Compare viable recognition providers, Android support, licensing, costs, rate limits, regional availability, data handling, and terms. Do not select a provider based only on a competitor's feature list.
2. Present the provider and data flow for approval before integrating any external service that transmits audio or identifiers.
3. Request microphone permission only after the user taps Recognize; explain the purpose in context and handle denial without blocking playback.
4. Capture only while the foreground recognition UI is active. Stop on completion, cancellation, navigation away, app backgrounding, or lifecycle loss.
5. Show matched title/artist and confidence where the provider supplies it; distinguish no match, timeout, permission denial, and provider outage.
6. Save recognition history locally only after a clear product decision. Provide clear-history behavior and avoid storing audio samples.
7. Offer add-to-playlist as an explicit follow-up action; preserve playlist order and report partial failures honestly.

## Acceptance criteria

- No microphone access occurs before explicit user action and permission grant.
- Capture always stops on all terminal/lifecycle paths; tests cover cancellation and errors.
- No raw audio is persisted by Audify.
- Provider terms, privacy disclosure, licensing, and regional behavior are documented before release.
- No-match and uncertain matches are not presented as confirmed identification.
- Recognition history is opt-in or clearly disclosed, local-first, and user-clearable if implemented.
- Real-device microphone and battery behavior is verified.

## Rollback

Remove the recognition entry point and provider adapter; do not affect existing playback or library data.

## Mandatory pre-implementation audit

Search for existing audio-capture, microphone-permission, external-provider, playlist-add, and history abstractions. Check older plans and dependencies for overlap. Record provider decision, exact files, data flow, permission UX, and tests. Stop for explicit approval before any audio-transmitting provider integration.

## Project constraints and handoff

Follow `plans/README.md`, `docs/PRODUCT.md`, and `docs/DESIGN.md`. No background capture, schema/migration, version bump, translated-string edits, or commits/pushes without authorization. Report privacy/data-flow impact, provider terms, device tests, and rollback steps.
