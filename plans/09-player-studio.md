<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 09 — Player Studio

**Status: proposal — implement incrementally within the existing player architecture.**

## Objective

Give listeners meaningful control over the now-playing presentation: a small set of player layouts and background treatments, configurable gestures, and motion intensity/reduced-motion behavior.

## Existing foundation to verify

`DESIGN.md` documents decomposed shared player components, artwork-aware theming, backdrop effects, and shared motion tokens. Reuse those owners and tokens. Do not create a parallel player or design system.

## Scope

**In scope:** inventory of current player variants/settings; a curated layout selector; background treatments that preserve contrast; a small gesture-action map; motion intensity choices that never override system reduced-motion; live preview where feasible; reset-to-default.

**Out of scope:** arbitrary user scripting, unrestricted gesture remapping, replacing navigation, per-screen theme engines, or decorative animation that competes with transport controls/lyrics.

## Implementation

1. Audit compact/expanded/landscape player variants and current preferences. Identify which customization already exists.
2. Define a small set of distinct layouts with stable control placement and parity for essential playback actions.
3. Add background choices using existing artwork palette/backdrop components and enforce measured text/control contrast.
4. Add only gestures that do not conflict with scrolling, lyric seeking, accessibility actions, or system navigation. Provide visible alternatives for every gesture.
5. Reuse `MotionTokens`; a system reduced-motion preference always wins over app-level animation intensity.
6. Persist preferences using existing settings mechanisms only; avoid new storage/schema.
7. Verify phone portrait/landscape, large font, TalkBack, and relevant TV/remote behavior before considering cross-device parity.

## Acceptance criteria

- Every layout retains accessible title, artist, artwork description, transport, queue, and output access.
- Layout changes do not alter playback state or queue occurrence identity.
- Gesture actions are discoverable, reversible, and have non-gesture alternatives.
- Reduced motion removes non-essential movement regardless of selected preference.
- Contrast and system text scaling pass on light/dark and artwork-derived surfaces.
- Existing defaults remain unchanged for users who never open Player Studio.

## Rollback

Restore the existing player presentation as the default and ignore the new preferences without deleting user media or playback data.

## Mandatory pre-implementation audit

Inspect all player components, settings models, navigation/gesture modifiers, and screenshots at supported breakpoints. Reconcile any older player customization or tablet plans. Record exact owners and tests; do not implement based on the affected-area description alone.

## Project constraints and handoff

Follow `plans/README.md`, `PRODUCT.md`, and `DESIGN.md`. Do not introduce a second design system, schema/migration, version bump, translated-string edits, or unauthorized commits/pushes. Include accessibility and device verification in the handoff.
