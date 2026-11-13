# AUDIFY Micro Improvements

Small, focused improvements to everyday usability, reliability, and polish. These are brief proposals, not implementation specifications. Before starting any item, inspect the current source and reconcile it with the existing numbered plans linked below.

## Plan format
Each plan states the user problem, a brief implementation direction, and the expected outcome. Acceptance signals are suggested verification targets, not claims that the feature already exists.

## Plans

| Plan | Improvement | Relationship / notes |
|---|---|---|
| [01 — playback reliability](01-playback-reliability.md) | Playback reliability | Reconcile with Plan 070 and Media3 playback lifecycle |
| [02 — better queue experience](02-better-queue-experience.md) | Queue experience | Reconcile with Plan 071 |
| [03 — offline first experience](03-offline-first-experience.md) | Offline-first experience | Reconcile with Plan 081 and provider/download limits |
| [04 — intent aware search](04-intent-aware-search.md) | Intent-aware search | Extend Plan 05 |
| [05 — personalized home](05-personalized-home.md) | Personalized home | Reconcile with Plan 090 |
| [06 — audio quality controls](06-audio-quality-controls.md) | Audio quality controls | Reconcile with Plan 11 |
| [07 — accessibility polish](07-accessibility-polish.md) | Accessibility polish | Consolidate with Plans 02, 073, 074 and 084 |
| [08 — guided onboarding](08-guided-onboarding.md) | Guided onboarding | Audit current onboarding first |
| [09 — context aware listening](09-context-aware-listening.md) | Context-aware listening | Extend Plans 07 and 079 |
| [10 — clear status and recovery](10-clear-status-and-recovery.md) | Clear status and recovery | Audit existing error and diagnostics patterns |

## Execution rules
- These proposals do not override existing P0 priorities or active bug fixes.
- Reconcile overlapping plans before implementation; extend the existing owner plan instead of duplicating architecture.
- Verify every claimed capability against source code and tests.
- Follow the repository-wide planning constraints in `plans/README.md`.
