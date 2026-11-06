<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 06 — AUDIFY Statistics: Personal Music Intelligence

**Status: planned — not started.**  
**Execution model: eight gated stages.**  
**Baseline:** Reconcile against the repository HEAD at implementation time; do not assume the historical source descriptions below are still current.

## Product direction

Make AUDIFY Statistics a continuously useful personal music-intelligence dashboard. It should answer three questions:

1. **Statistics — What happened?** Listening time, activity, top music, and trends.
2. **Intelligence — What is interesting?** Evidence-based observations about discovery, repetition, and listening habits.
3. **Evolution — How is my taste changing?** Historical shifts in artists, genres, and listening behaviour.

This is not a Spotify Wrapped clone. Monthly and annual recap experiences are supplementary storytelling features; the permanent Statistics experience remains the core product.

## Product principles

- Trustworthy metrics take priority over visual richness.
- Never invent per-play precision from aggregate counters or infer missing history.
- Every insight must identify its supporting data and disclose limitations.
- Progressive disclosure: prioritize a useful first viewport; do not put every metric on one screen.
- Reuse AUDIFY's existing architecture, design system, recap components, and persistence paths where appropriate.
- No new tracking, upload, database schema, or Room migration is implied by this plan. Any such requirement is a separate human approval gate and must reconcile with repository-wide rules.
- Preserve playback, navigation, backup/restore, and existing Statistics behaviour unless a verified defect requires a scoped correction.

## Current known foundation (historical; verify at execution)

The prior plan audit identified:
- `data/stats/ListeningStats.kt` for aggregate statistics and backup data.
- `data/stats/ListeningRecorder.kt` for activity recording.
- `data/stats/ListeningSummaryCsv.kt` for aggregate CSV export.
- Existing monthly/yearly recap work.
- Per-play history was not present in the historical audit; plan 053 was identified as the relevant dependency.

These are investigation leads, not guarantees that paths or ownership remain unchanged. The mandatory source audit below supersedes this snapshot.

## Eight-stage execution plan

Stages must be completed in order. Each stage has an exit gate. Do not advance because a screen looks finished or a build happens to pass.

### Stage 1 — Repository and architecture audit

**Objective:** Establish the real current implementation before changing code.

Tasks:
1. Inspect the Statistics screen, navigation route, state holders, and reusable UI.
2. Trace playback events through the player, recorder, aggregation, and persistence lifecycle.
3. Inspect listening-history models, local storage, backup/restore, and CSV export.
4. Inventory existing metrics, formulas, date filters, recap features, and tests.
5. Identify correctness defects, missing signals, performance risks, and architectural constraints.
6. Reconcile plan 053 and any other relevant statistics/recap plans against current source and tests.
7. Read `docs/PRODUCT.md`, `docs/DESIGN.md`, and applicable repository instructions before implementation.

**Deliverable:** An evidence-backed audit matrix marking each capability shipped, partial, absent, obsolete, or blocked, with exact source/test references and a proposed file-level change list.

**Exit criteria:** No unresolved assumptions about data ownership, metric availability, navigation ownership, or the allowed implementation scope.

**Estimate:** 1–2 development sessions.

### Stage 2 — Listening data foundation

**Objective:** Make the existing listening data trustworthy enough to power the dashboard.

Tasks:
1. Verify when and how the current recorder counts listening activity.
2. Validate actual duration accounting against pause, resume, seek, repeat, interruption, and track-change behaviour.
3. Check duplicate event handling, process death, app restart, and persistence consistency.
4. Verify offline playback accounting and backup/restore behaviour where supported.
5. Define and test the current aggregate metric semantics; correct proven defects in the existing implementation.
6. Document which metrics are exact, estimated, unavailable, or aggregate-only.

**Scope gate:** This stage does not authorize a new per-play event store, schema change, Room migration, upload, or new tracking pipeline. If accurate duration or event-level insights require one, stop and prepare a separate proposal for explicit approval. Reconcile with plan 053 before depending on per-play history.

**Deliverable:** A tested metric/data contract and a documented list of reliable signals available to the dashboard.

**Exit criteria:** Existing supported aggregates reconcile with recorder/persistence behaviour and focused automated tests. Unsupported event-level metrics are explicitly excluded.

**Estimate:** 2–4 development sessions.

### Stage 3 — Core Statistics dashboard

**Objective:** Deliver a clear and useful dashboard using verified data already available.

Build in this order:
1. Listening overview — listening time, tracks played, and active days where supported.
2. Activity heatmap — daily activity only at the granularity supported by stored data.
3. Listening patterns — hourly and weekly trends only where source data supports them.
4. Top tracks — deterministic ranking with a clear period/sample label.
5. Top artists and albums/genres — only where reliable aggregate fields exist.

Tasks:
- Define date-range presets and custom-range semantics.
- Make empty, sparse, reset, and insufficient-history states distinct.
- Show data freshness and explain important metric definitions.
- Give every chart a text summary and accessible tabular alternative.
- Reuse existing recap/history entry points instead of duplicating them.

**Deliverable:** A functional, accessible Statistics dashboard with honest data coverage and range filtering.

**Exit criteria:** Every visible number reconciles to its source; range boundaries and tie-breaking are tested; unsupported charts are omitted rather than fabricated.

**Estimate:** 2–3 development sessions.

### Stage 4 — AUDIFY Music Intelligence

**Objective:** Add distinctive insights that explain listening behaviour, not just rankings.

Candidate features (each is conditional on data availability):

| Feature | Intended behaviour | Minimum evidence gate |
|---|---|---|
| Music DNA | Summarize recurring listening preferences | Sufficient artist/genre/track distribution |
| Taste Evolution | Show preference changes over time | Comparable historical windows |
| Discovery Ratio | Compare new-to-listener music with familiar music | Reliable first-seen identity/history |
| Forgotten Favourites | Surface formerly frequent tracks that have declined | Historical per-track or equivalent trend data |
| Listening Personality | Describe stable patterns such as album focus or repeat tendency | Multiple reliable behavioural signals |
| Track Intelligence | Show replay and listening-history details | Verified per-play history; aggregate counts are insufficient for session claims |

Tasks:
1. Define each insight's formula, evidence threshold, time window, and caveats before UI work.
2. Prefer deterministic, explainable rules over opaque generated claims.
3. Suppress insights when history is insufficient.
4. Avoid sensitive or judgemental labels; present observations neutrally.
5. Add tests for thresholds, sparse data, ties, and contradictory signals.

**Deliverable:** A small set of explainable, genuinely useful AUDIFY-specific insights.

**Exit criteria:** Every insight is reproducible from documented inputs, has tests, and is hidden when its evidence threshold is not met.

**Estimate:** 3–5 development sessions.

### Stage 5 — Historical intelligence

**Objective:** Make changes over time understandable and comparable.

Tasks:
- Week-over-week comparisons.
- Month-over-month comparisons.
- Year-over-year comparisons.
- Artist and genre growth/decline.
- Listening-time trends.
- Discovery and replay trends where supported.

Requirements:
- Compare equivalent periods and state the comparison basis.
- Use consistent device-local calendar boundaries and storage/query timezone conversion.
- Handle partial periods, daylight-saving changes, missing history, resets, and newly created accounts.
- Do not interpret missing data as zero activity unless that is demonstrably correct.

**Deliverable:** Historical comparison views and trend summaries backed by comparable data.

**Exit criteria:** Comparison calculations pass date-boundary, timezone, daylight-saving, partial-period, and missing-data tests.

**Estimate:** 1–3 development sessions.

### Stage 6 — Personal listening reports

**Objective:** Make listening history enjoyable to revisit and selectively share.

Candidate features:
- Monthly recap.
- Annual recap.
- Personal milestones.
- Listening timeline.
- Shareable statistics cards.
- Taste-evolution story.

Tasks:
1. Audit and reuse existing recap work before adding new flows.
2. Build reports only from verified metrics and eligible insights.
3. Make the reporting period and data coverage clear.
4. Keep sharing opt-in and avoid exposing private listening details unintentionally.
5. Provide graceful states for incomplete history.

**Deliverable:** Optional monthly/annual storytelling that complements, rather than replaces, the permanent dashboard.

**Exit criteria:** Report values reconcile with Statistics; privacy-sensitive sharing is user-initiated; sparse-history states are handled.

**Estimate:** 2–3 development sessions.

### Stage 7 — UI/UX refinement

**Objective:** Make Statistics feel native to AUDIFY rather than like a generic analytics template.

Tasks:
1. Audit visual hierarchy, first-viewport usefulness, and information density.
2. Refine chart readability, labels, legends, and interactions.
3. Apply existing AUDIFY typography, spacing, colour, surfaces, and motion rules.
4. Add meaningful loading, empty, error, reset, and insufficient-data states.
5. Verify scrolling, narrow screens, large fonts, TalkBack, and accessibility semantics.
6. Test the actual Android application on a device/emulator; screenshots alone are insufficient.
7. Check that charts do not recompute on every playback-position tick.

**Deliverable:** A cohesive and usable Statistics experience across supported Android form factors.

**Exit criteria:** UI/accessibility checks pass and real rendered/device behaviour is verified or explicitly recorded as unavailable.

**Estimate:** 2–3 development sessions.

### Stage 8 — Final verification and release readiness

Tasks:
1. Run focused unit and UI tests.
2. Test recorder/history edge cases and date-range calculations.
3. Reconcile displayed metrics against known source fixtures.
4. Check performance with sparse and large histories.
5. Run the repository-approved production/debug build and inspect actual results.
6. Inspect the built APK and verify Statistics on a real device/emulator.
7. Check for regressions in playback, navigation, recap, backup/restore, and accessibility.
8. Document completed work, known limitations, and rollback steps.

**Deliverable:** A verification report with exact commands, results, device coverage, and remaining limitations.

**Exit criteria:** No known critical Statistics defects, no fabricated metrics, no unverified event-level claims, and no regressions to existing functionality.

**Estimate:** 1–2 development sessions.

## Implementation tracking

| Stage | Workstream | Estimate | Initial status |
|---:|---|---:|---|
| 1 | Repository and architecture audit | 1–2 sessions | Not started |
| 2 | Listening data foundation | 2–4 sessions | Not started |
| 3 | Core Statistics dashboard | 2–3 sessions | Not started |
| 4 | AUDIFY Music Intelligence | 3–5 sessions | Not started |
| 5 | Historical intelligence | 1–3 sessions | Not started |
| 6 | Personal listening reports | 2–3 sessions | Not started |
| 7 | UI/UX refinement | 2–3 sessions | Not started |
| 8 | Final verification and release readiness | 1–2 sessions | Not started |

Estimates are rough development-session estimates, not delivery commitments. Overall progress remains **0/8** until work is actually completed and its exit criteria are verified.

## Mandatory implementation rules

- Before starting, run `git rev-parse --short HEAD`. If the repository tip differs from the baseline used during planning, inspect the in-scope files for drift and update this plan before editing.
- Follow repository-wide instructions in `AGENTS.md` and `plans/README.md`.
- Never bump `versionCode` or `versionName`.
- Do not modify database schema or Room migrations under this plan.
- String edits go only in `app/src/main/res/values/sachit_strings.xml`; do not edit translated resource files.
- No commits, pushes, or merges unless explicitly authorized by the user for that work.
- Preserve unrelated working-tree changes.
- Use JDK 21 and repository-approved verification commands.
- Do not mark any stage complete until its exit criteria have evidence.

## Mandatory pre-implementation source audit

Before changing code:
1. Read every relevant existing plan and classify its requirements as shipped, partial, absent, obsolete, or blocked with source/test evidence.
2. Locate actual owning types, state holders, persistence APIs, routes, and UI actions. The paths above are leads, not permission to create duplicate classes.
3. Record exact files/functions to change and exact tests to add; replace placeholder paths with verified paths.
4. Document unresolved product decisions and platform/provider limitations. Stop for approval at any schema/storage, new tracking, external-provider, destructive, or other human approval gate.
5. Establish baseline tests/build and current rendered/device behaviour where applicable.
6. Do not overwrite or discard unrelated local changes.

## Implementation handoff checklist

For each completed stage, report:
- Changed files and key decisions.
- Tests/build commands executed and actual results.
- Manual/device checks completed or unavailable.
- Metric/data limitations and known defects.
- Compatibility, backup/restore, and migration impact (or explicit none).
- Rollback procedure.
- Evidence that every stage exit criterion is satisfied.

A task is not complete merely because it compiles. The plan remains planned until implementation is explicitly started; each stage is verified independently.