<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 07 — Explainable listening recommendations

**Status: proposal — audit current discovery ranking and listening signals before implementation.**

## Objective

Make Audify's existing discovery shelves more personally useful through listening-aware ranking, transparent reasons, and user-controlled familiarity/adventure balance. Improve existing Home/Explore surfaces rather than creating a second recommendation destination.

## User problem

Generic or repetitive recommendations do not help listeners decide what to play. Users need useful picks, a comprehensible reason for each pick, and a way to steer discovery without training an opaque profile.

## Existing foundation to verify

- `PRODUCT.md` records mood shortcuts and a time-aware discovery shelf based on feed content and on-device listening-hour patterns.
- Listening aggregates and recap data exist, but a complete per-play event history is not established.
- Identify current recommendation providers, ranking functions, saved/liked signals, skip signals, and available local data. Do not assume every signal exists or is reliable.

## Scope

**In scope:** a capability inventory; a deterministic first-stage ranking model; optional familiarity/adventure preference; concise reason labels; never-played discovery only if reliable history supports it; explicit feedback controls; privacy-preserving local preference storage where existing settings infrastructure is suitable.

**Out of scope:** uploading listening history, third-party behavioral profiling, opaque ML models, fabricated per-play signals, social ranking, or replacing the current discovery feed wholesale.

## Implementation

1. Map every existing discovery shelf, provider request, ranking stage, and user signal. Classify each signal as exact, aggregate, inferred, unavailable, or provider-supplied.
2. Define a deterministic scoring contract with bounded weights, neutral defaults, stable tie-breaking, and graceful behavior when signals are missing.
3. Start with existing provider results and locally available signals; do not fetch an entire catalogue to improve ranking.
4. Attach a truthful explanation to each recommendation (for example, similar artist or familiar genre) only when the underlying evidence exists.
5. Add a small preference control and feedback actions using existing settings/state ownership. Make reset and opt-out behavior explicit.
6. Instrument no new network telemetry. Any future model training or remote profile requires a separate product/privacy decision.

## Acceptance criteria

- Empty or unavailable listening data produces the same safe baseline discovery behavior as today.
- Explanations are traceable to actual ranking inputs and never claim a reason that was not used.
- Preference changes have predictable, tested effects; reset restores neutral ranking.
- Duplicate results, provider errors, pagination, and missing metadata are handled deterministically.
- No listening history is uploaded and no schema/migration is introduced.
- Ranking is tested with synthetic fixtures and manually compared against baseline results.

## Rollback

Disable the new ranking layer and preference controls; retain the existing discovery provider and feed behavior.

## Mandatory pre-implementation audit

Read relevant older discovery plans (including any retained or historical plans in the 025–068 range), inspect current Home/Explore source and tests, and identify the canonical feed/ranking owner. Record exact source files and signal availability before editing. If another plan already owns a requirement, extend that plan instead of duplicating it.

## Project constraints and handoff

Follow `plans/README.md`, `PRODUCT.md`, and `DESIGN.md`. Do not bump versions, alter database schemas/migrations, edit translated strings, or commit/push without explicit authorization. Report changed files, tests/results, device checks, limitations, privacy impact, and rollback steps.
