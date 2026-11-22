# AUDIFY Micro Improvements

Small, focused improvements to everyday usability, reliability, and polish. Each file is a self-contained, agent-executable implementation plan modelled on the depth and gating structure of Plan 06. Before starting any item, inspect the current source and reconcile it with the existing numbered plans linked below.

## Plan format
Each plan defines the product problem and outcome, canonical owner/dependencies, repository reconnaissance, staged implementation, feature-specific tests, performance/privacy constraints, acceptance criteria, rollback, repository-wide guardrails, and an evidence-based handoff. Paths and capabilities must be re-verified against the live checkout before implementation.

## Plans

| Plan | Improvement | Relationship / notes |
|---|---|---|
| [01 — playback reliability](01-playback-reliability.md) | Playback reliability | **Occurrence-aware recovery and per-occurrence retry budgets; automated source checks passed; playback lifecycle/device acceptance open** |
| [02 — better queue experience](02-better-queue-experience.md) | Queue experience | **Stable occurrence identity, reduced-motion feedback and stale Wear snapshot protection; device acceptance open** |
| [03 — offline first experience](03-offline-first-experience.md) | Offline-first experience | **Partial-scan trust and failed-delete preservation implemented; offline browse/play/reconnect acceptance open** |
| [04 — intent aware search](04-intent-aware-search.md) | Intent-aware search | **Conservative intent inference and explicit/manual filter precedence tested; provider boundaries preserved** |
| [05 — personalized home](05-personalized-home.md) | Personalized home | **Pin/reorder/visibility/reset slice and dedicated checkbox semantics implemented; form-factor/device acceptance open** |
| [06 — audio quality controls](06-audio-quality-controls.md) | Audio quality controls | **Session/route lifecycle policies now tested; measured per-headphone correction remains blocked by licensing and verified output identity/attachment prerequisites** |
| [07 — accessibility polish](07-accessibility-polish.md) | Accessibility polish | **Home/Library non-drag reorder, Home pin controls and dedicated visibility checkbox; manual assistive-technology acceptance open** |
| [08 — guided onboarding](08-guided-onboarding.md) | Guided onboarding | **Existing skippable flow retained; Back navigation regression tests included; process recreation/device acceptance open** |
| [09 — context aware listening](09-context-aware-listening.md) | Context-aware listening | **Existing radio refill owner extended with session intent, artist/energy ranking and real-input descriptions; per-track explanation/diversity and device acceptance open** |
| [10 — clear status and recovery](10-clear-status-and-recovery.md) | Clear status and recovery | **Retryability-aware empty/error states and safe remote failure messaging; broader workflow acceptance open** |

## Consolidated verification — 2026-11-22

The following serialized Gradle run completed successfully after the implementation changes (JDK 21, Android SDK configured, one Gradle worker, no parallel project execution):

```text
:shared:jvmTest                         PASS
:sharedUi:jvmTest                       PASS
:app:compileDevDebugKotlin              PASS
:app:testDevDebugUnitTest               PASS
:wearos:compileDevDebugKotlin           PASS
:wearos:testDevDebugUnitTest            PASS
:tv:compileDevDebugKotlin               PASS
:tv:testDevDebugUnitTest                PASS
Test totals: 1,527 passed, 0 failed, 1 skipped across shared JVM, shared UI JVM, Android phone DevDebug, Wear OS DevDebug and Android TV DevDebug reports.
:app:lintDevDebug                       PASS — 0 errors, 12 warnings
:wearos:lintDevDebug                    PASS — 0 errors, 5 warnings
:tv:lintDevDebug                        PASS — 0 errors, 11 warnings
git diff --check                        PASS
```

Warnings are reported by the existing lint baselines; no lint errors were reported in the last completed lint run before this final pass. The latest UI/string edits still require a lint rerun. No dedicated formatter task/plugin (ktlint, Spotless, ktfmt or Detekt) is configured in the inspected Gradle build, so formatting validation is limited to `git diff --check` and lint. No APK packaging, emulator, physical-device, accessibility-service, audio-route, offline reconnect or lifecycle acceptance test was run. Those checks remain explicitly unverified. Plan 06 remains partially blocked by the external headphone-profile prerequisites; Plan 09 is no longer blocked on owner discovery but still has per-track explanation/diversity and device acceptance gaps.

## Execution rules
- These proposals do not override existing P0 priorities or active bug fixes.
- Reconcile overlapping plans before implementation; extend the existing owner plan instead of duplicating architecture.
- Verify every claimed capability against source code and tests.
- Follow the repository-wide planning constraints in `plans/README.md`.
