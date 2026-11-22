<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Micro Improvement 09 — User-Controlled Listening Intent and Context

**Status: in progress — the existing AutoPlay/radio refill owner now accepts session-scoped Familiar, Discovery, Energetic and Calm intent with an Off state, equal-score artist diversity and per-track explanations derived from the actual ranking signal. Automated coverage has been added but is intentionally pending the later verification pass.**
**Execution model: eight gated stages.**  
**Canonical owner:** Plan 07 — Explainable Listening Recommendations and Plan 079 — Smart Music Radio.  
**Baseline:** Reconcile against repository HEAD at execution time; this plan deliberately does not assume historical source paths remain valid.

## Product direction

Let users steer an existing recommendation/radio session toward a stated listening intent without introducing a second ranking engine or inferring sensitive traits.

## Problem being solved

A recommendation may be technically personalized yet mismatched to what the user wants now. Automatically inferring mood or personal circumstances can be intrusive and unreliable; explicit lightweight controls are safer and easier to explain.

## Product principles

- Fix the user problem at its actual source; do not add cosmetic controls without working behaviour.
- Reuse the canonical AUDIFY architecture and existing state/data owners.
- Evidence and correctness take priority over visual polish or feature count.
- Preserve existing playback, navigation, provider behaviour, backup/restore and user preferences unless a verified defect requires a scoped change.
- Empty, unavailable and unsupported states must be honest; never fabricate data or capabilities.
- No new database schema, Room migration, remote service, account requirement, tracking pipeline or permission is implied by this plan.
- Any change crossing a prohibited boundary requires a separate proposal and explicit human approval.

## Known foundation and dependency map (historical; verify at execution)

The canonical owner is **Plan 07 — Explainable Listening Recommendations and Plan 079 — Smart Music Radio**. The current plans index also lists overlapping work. These are planning relationships, not proof that a particular implementation is present or absent. The executor must inspect the current source, tests and plan history before editing. This plan is an enhancement/quality track; it must not silently take ownership away from the canonical plan.

## Stage 1 audit checklist

1. Trace current recommendation ranking, seed selection, radio generation, exclusions and provider fallback behaviour.
2. Read Plans 07 and 079 and identify the single ranking owner and extension points.
3. Inventory available signals and classify them as explicit user input, local listening aggregates, provider metadata or unavailable.
4. Inspect privacy controls, recommendation explanations, preference persistence and opt-out behaviour.
5. Record baseline relevance/diversity and current user control surfaces.

**Deliverable:** A source-backed capability matrix and exact file/symbol inventory.  
**Exit criteria:** The executor can name the canonical state/data owner, real UI entry points, relevant tests, dependencies and prohibited boundaries without relying on guesses.

## Stage 2 — Behaviour contract and scope

1. Translate the product goal into user stories and observable behaviour.
2. Define the primary flow and all relevant edge cases, including empty, offline, unavailable, partial, interrupted and restored states.
3. Define stable identity, state ownership and data provenance where applicable.
4. Define platform/provider capability differences explicitly.
5. Write acceptance criteria and a test matrix before UI implementation.
6. List non-goals and rejected approaches so the implementation cannot expand into an unrelated rewrite.
7. Stop for human approval if the required solution needs a schema change, migration, new backend, credential handling, provider-policy exception, new permission, destructive operation or release action.

**Deliverable:** A concise behaviour contract, acceptance matrix and confirmed file-level implementation scope.  
**Exit criteria:** Every requirement has a real owner, implementation location, test and observable outcome.

## Stage 3 — Domain and foundation implementation

1. Define a small set of explicit session intents (for example familiar, discover, energetic or calm) only where metadata and ranking can support them.
2. Treat intent as a user-selected session parameter, not a permanent identity label; allow change, clear and disable actions.
3. Pass intent into the existing ranking/radio pipeline through a typed parameter; do not create another recommendation service.
4. Define how each intent affects ranking and diversity, with deterministic fallbacks when metadata is missing.
5. Explain why a recommendation appears using real contributing signals; do not invent explanations or claim AI understanding without evidence.
6. Keep sensitive inference off by default; do not infer health, emotional state, age, identity or other sensitive attributes.
7. Ensure explicit user actions, blocked tracks and selected seeds take precedence over inferred or generic ranking signals.

**Deliverable:** Working domain behaviour integrated with the existing owner.  
**Exit criteria:** Core invariants are covered by focused automated tests; no parallel architecture or prohibited storage boundary has been introduced.

## Stage 4 — UI and interaction implementation

1. Implement the smallest complete user journey using existing AUDIFY components, typography, spacing, colours and interaction conventions.
2. Connect every control to real domain behaviour. No decorative toggle, placeholder button or fake success state is acceptable.
3. Provide relevant loading, empty, partial, unavailable, offline, error and success states.
4. Preserve navigation and playback continuity; avoid unexpected screen resets.
5. Support large text, accessible semantics, keyboard/remote/touch navigation and reduced motion on applicable platforms.
6. Verify responsive behaviour on phone, tablet, desktop and TV/Wear only where the repository actually supports the surface.
7. Do not copy a design from another app blindly; translate the intended behaviour into AUDIFY's established design system.

**Deliverable:** A complete user-facing flow wired to production domain logic.  
**Exit criteria:** No mock-only behaviour, dead control, fabricated result or inaccessible primary action remains.

## Stage 5 — Integration and platform coverage

1. Identify every module/client consuming the changed contract from the current source graph.
2. Update only clients that actually support the feature; document unsupported platforms rather than claiming parity.
3. Verify integration with the canonical owner, navigation, settings, playback/session and provider adapters as applicable.
4. Exercise lifecycle recreation, connectivity changes, permission changes and process restart where relevant.
5. Verify compatibility with existing settings and backup/restore boundaries.
6. Ensure external controls and system integrations remain consistent where the feature affects them.

**Deliverable:** Platform integration matrix with evidence for each supported target.  
**Exit criteria:** In-scope clients compile and relevant integration tests pass; unavailable device testing is recorded explicitly.

## Stage 6 — Feature-specific test plan

### Required automated coverage

1. Test each intent against deterministic fixture catalogues and verify expected ranking effects.
2. Test sparse metadata, conflicting signals, provider failures, empty results and repeated seeds.
3. Test switching, clearing and disabling intent mid-session.
4. Verify diversity guardrails and ensure one intent does not collapse results to a narrow repeated set.
5. Test privacy defaults and confirm no sensitive inference or unexpected remote upload occurs.

### Performance and resource constraints

1. Reuse existing ranking inputs and cached metadata; do not add per-track remote calls.
2. Keep session intent lightweight and scoped to the active session unless the user explicitly saves a preference.
3. Avoid model inference or background processing unless separately approved and justified.

### Privacy, compatibility and safety review

- Inspect data collection, permissions, logs, provider requests and user-visible disclosures touched by the change.
- Do not add tracking, analytics, account sync or remote upload unless separately approved.
- Do not expose credentials, tokens, private listening history or personal data in logs or error messages.
- Confirm existing preferences and user data remain readable; no migration is authorized.

**Deliverable:** Automated test evidence plus a short performance/privacy review.  
**Exit criteria:** Edge cases, concurrency, lifecycle, performance and privacy risks are either tested and resolved or explicitly blocked for human decision.

## Stage 7 — Verification and regression

1. Run the focused tests for every changed module.
2. Run relevant integration tests and static analysis configured by the repository.
3. Run the repository-approved JDK 21 commands:
   ```bash
   ./gradlew :app:compileDevDebugKotlin --console=plain
   ./gradlew :app:testDevDebugUnitTest --console=plain
   ./gradlew :app:assembleDevDebug --console=plain
   ```
4. Run additional module/platform build tasks only when the changed code requires them and the task exists.
5. For UI or device-specific work, install and verify on a real emulator/device; screenshots or compilation alone are insufficient.
6. Re-test adjacent behaviour owned by Plan 07 — Explainable Listening Recommendations and Plan 079 — Smart Music Radio.
7. Record exact commands, exit codes, device/API levels and any unavailable checks.

**Deliverable:** Reproducible verification report.  
**Exit criteria:** All acceptance criteria have evidence and no known critical regression remains in scope.

## Stage 8 — Handoff, rollback and completion

**Rollback strategy:** Make intent an optional parameter with the current ranking behaviour as the null/default path. Remove the parameter integration if it degrades relevance or diversity; do not alter stored listening history.

**Rejected alternatives:** Do not build a second ranking engine, infer mood from microphone/location/sensitive data, or present arbitrary genre labels as reliable emotional understanding.

1. Report changed files, key decisions and any deviation from this plan.
2. Report tests/builds actually executed and their real outcomes; never infer success from a started process.
3. List manual/device checks, supported platform coverage and unavailable validation.
4. State persistence, compatibility and migration impact (or explicitly state none when verified).
5. List known limitations, follow-up issues and the exact rollback path.
6. Update plan status only when work actually starts or all completion criteria pass.
7. Do not commit, push, merge, publish or change app versions without explicit user authorization.

**Deliverable:** A complete handoff that another agent can continue without prior chat context.  
**Exit criteria:** Every acceptance item is evidenced, limitations are transparent and rollback is understood.

## Feature-specific acceptance criteria

- Session intent is an optional input to the canonical recommendation/radio owner.
- Users can change, clear or disable intent at any time.
- Explanations are backed by actual ranking inputs.
- Explicit user choices take precedence; diversity is measured and protected.
- Sensitive inference and unapproved data collection are absent.


## Execution model: eight gated stages

Stages are sequential. The executor must report evidence at each gate and must not mark a stage complete because a screen compiles or looks correct.

### Stage 1 — Repository and product audit
1. Run `git rev-parse --short HEAD` and record the exact baseline.
2. Read `plans/README.md`, `docs/PRODUCT.md`, `docs/DESIGN.md`, repository instructions that actually exist, and this plan in full.
3. Inspect the canonical owner plan named above and all adjacent plans discovered from the index.
4. Locate the real implementation, state owner, data model, persistence boundary, routes, UI entry points and tests. Historical path guesses are not authoritative.
5. Build a capability matrix: shipped, partial, absent, obsolete, blocked, or not verifiable. Attach exact source paths and symbol names.
6. Record current behaviour and baseline test/build results before editing.

**Deliverable:** Evidence-backed audit matrix, dependency map, baseline commit and proposed exact file-level change list.

**Exit gate:** No duplicate ownership, unknown persistence boundary or unreviewed provider/platform constraint remains.

### Stage 2 — Contract and acceptance design
1. Define user-visible behaviour and non-goals.
2. Specify state/data contracts, invariants, edge cases and platform/provider capability differences.
3. Define empty, loading, partial, error, offline, permission and unsupported states where applicable.
4. Define accessibility and responsive behaviour before UI implementation.
5. Define measurable acceptance criteria and test fixtures.
6. Identify any decision requiring human approval; stop before crossing that boundary.

**Deliverable:** A concise implementation contract and test matrix.

**Exit gate:** Every proposed change maps to a user problem, owning module, test and acceptance criterion.

### Stage 3 — Foundation and domain implementation
1. Extend the existing canonical owner; do not create a parallel manager, repository, service or ranking pipeline.
2. Keep domain rules out of composables/UI components.
3. Preserve stable identity and deterministic behaviour.
4. Reuse existing dependency injection, coroutine, state, persistence and error conventions.
5. Keep changes narrow and backward-compatible.
6. Add focused unit tests alongside each domain change.

**Deliverable:** Tested domain behaviour with no unnecessary UI or storage expansion.

**Exit gate:** Core invariants pass focused tests and no prohibited architectural boundary was crossed.

### Stage 4 — User interface and interaction
1. Implement the smallest coherent user journey using existing AUDIFY design tokens and components.
2. Add complete loading, empty, error, unavailable and success states relevant to the feature.
3. Support keyboard, touch, remote/rotary and assistive technology on applicable platforms.
4. Preserve navigation, playback and existing user preferences.
5. Avoid decorative complexity that obscures the primary action.
6. Verify narrow and wide layouts, large text and reduced motion.

**Deliverable:** End-to-end user flow connected to the real domain implementation.

**Exit gate:** No mock-only control, dead button, fabricated content or UI-only toggle remains.

### Stage 5 — Platform and integration coverage
1. Identify which modules/platforms actually consume the changed contract.
2. Update only supported clients and keep capability differences explicit.
3. Verify integration with playback/session, providers, settings and navigation as applicable.
4. Test lifecycle, process recreation, connectivity and permission changes relevant to the feature.
5. Check compatibility with existing backup/restore and provider data boundaries.
6. Do not claim a platform is complete without build or runtime evidence.

**Deliverable:** Platform integration matrix with verified and unverified targets clearly separated.

**Exit gate:** All in-scope clients compile and their relevant integration tests pass; unavailable device testing is disclosed.

### Stage 6 — Reliability, performance and privacy
1. Test sparse, large, malformed, duplicate, stale and unavailable data.
2. Check cancellation, concurrency, lifecycle disposal and repeated user actions.
3. Measure hot-path performance and avoid unnecessary recomposition, polling, network calls or disk writes.
4. Review permissions, data collection, logging, sharing and provider policy.
5. Confirm no secret, credential or private user data enters logs or diagnostics.
6. Document fallback and rollback behaviour.

**Deliverable:** Reliability/performance/privacy review with evidence.

**Exit gate:** No known critical data-loss, privacy, performance or lifecycle defect remains in scope.

### Stage 7 — Full verification and regression pass
1. Run focused unit tests and module tests.
2. Run the repository-approved compile/test/build commands with JDK 21.
3. Run lint/static analysis if configured for the touched modules.
4. Test the actual application on emulator/device for UI or platform work; screenshots alone are insufficient.
5. Re-test adjacent features identified during the audit.
6. Inspect build output and record exact commands, exit codes and relevant logs.

**Deliverable:** Verification report, platform matrix and regression results.

**Exit gate:** Every acceptance criterion has evidence; failures and unavailable checks are explicitly listed.

### Stage 8 — Handoff and plan status
1. Summarize changed files and architectural decisions.
2. List tests/builds with actual results; never report an unrun command as passed.
3. Record manual/device checks and remaining limitations.
4. State compatibility, persistence and migration impact, including explicit no-impact where verified.
5. Provide a scoped rollback procedure.
6. Update this plan's status only when the corresponding work genuinely starts or all acceptance gates pass.
7. Do not commit, push, merge, publish, bump app versions or modify protected storage boundaries without explicit human authorization.

**Deliverable:** Complete implementation handoff with evidence and remaining work.

**Exit gate:** A new agent can understand exactly what changed, what passed, what did not, and how to safely continue.

## Execution audit — 2026-11-22, baseline `b49d62b7`

The current source exposes explicit mood choices through `TopPickMood`, `TopPicks.moodStation(...)`, the Find Your Mood surface in `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/HomeScreen.kt` and `TopPicksShelf.kt`, and mood queue construction in `shared/src/jvmSharedMain/kotlin/com/music/audify/data/TopPicks.kt:540-591`. Mood queues are fetched when the user selects a mood. `TopPicks.moodFor(...)` also selects a suggested mood using local time; it does not inspect microphone, location, health, identity or other sensitive signals.

The player already distinguishes player-origin actions from list-origin actions in `core/app/src/main/java/com/music/audify/MainActivity.kt:4847-5019` and `core/app/src/main/java/com/music/audify/ui/components/SongActionsMenu.kt`. Player-only options include sleep timer, playback-version controls, source rollback/upgrade, lyrics offset, spatial audio and track-log access; playlist-row actions are withheld when the menu is opened from the player. This contextual menu behaviour is already implemented in the baseline.

The existing `core/app/src/main/java/com/music/audify/playback/Autoplay.kt` `loadAutoplayTracks`/`rankForAutomix` path is the current canonical radio refill owner: it already fetches provider radio candidates, preserves queue metadata, de-duplicates through `QueueBuilder`, and optionally applies `orderForMixing`. This pass extends that same path rather than creating a parallel recommender. `RadioListeningIntentState` is an in-memory session control exposed from the player-origin `SongActionsMenu`; it cycles Off, Familiar artists, Discover new artists, More energetic and Calmer. It is hidden in Listen Together parties, resets on a non-radio queue transition and when the playback service is destroyed, and is deliberately not persisted as a permanent taste profile.

`orderForListeningIntent` applies deterministic stable ranking using only existing queue/session artist names or finite measured `TrackAnalysis.energyCurve` values. Missing evidence leaves provider order intact for those candidates. Within equal intent-score groups, a stable two-artist recency guard prefers an alternative artist when available; it never moves a lower intent score ahead of a higher one. The existing provider order remains the tie-breaker, the `QueueBuilder` still owns de-duplication and queue boundaries, and already-queued items are not silently reordered: a changed intent is labelled as applying to the next radio refill. The player menu explains each intent and, for the currently playing AutoPlay item, displays a per-track explanation derived from the signal actually available for that candidate. Explanations are session-only, bounded to 100 entries and cleared with the radio intent. No microphone, location, health, account identity or unapproved history signal is inferred. This remains an enhancement to the existing ranking engine, not a claim that the full Plan 07/079 recommendation system is complete.

## Implementation tracking

| Stage | Workstream | Current status |
|---:|---|---|
| 1 | Repository and product audit | Complete |
| 2 | Contract and acceptance design | Partial — five-state session intent and signal-backed per-track explanation contract implemented; full Plan 07/079 acceptance remains |
| 3 | Foundation and domain implementation | Partial — existing radio refill owner extended with deterministic artist/energy ranking, equal-score artist diversity and missing-evidence fallback |
| 4 | User interface and interaction | Partial — player menu cycles intent, describes its real ranking input and shows the current AutoPlay track's factual reason; effect is explicitly next-batch only |
| 5 | Platform and integration coverage | Android phone player/radio owner only; party UI hides the control; no device acceptance |
| 6 | Reliability, performance and privacy | Partial — no new network/data source; session state is ephemeral and reset at queue/service boundaries |
| 7 | Full verification and regression pass | In progress — focused ranking tests and cross-platform source tests/compilation are being rerun |
| 8 | Handoff and plan status | In progress |

Progress is tracked by the current stage statuses above; Plan 09 is no longer blocked on owner discovery. Equal-score artist diversity and per-track explanations are implemented in the existing ranking owner and player song menu. Automated tests have been added but not run by request; device-level radio-session validation remains deferred. Estimates are planning ranges, not delivery commitments.

## Repository-wide execution constraints

- Follow `plans/README.md` and the repository's actual agent instructions.
- Never bump `versionCode` or `versionName`.
- Never modify database schema or Room migrations under this plan.
- String edits go only in `core/app/src/main/res/values/sachit_strings.xml`; do not edit translated resources.
- No commits, pushes or merges unless the user explicitly authorizes them for this work.
- Do not edit README/Markdown outside `plans/` and top-level planning docs.
- Use JDK 21 and the repository-approved verification commands:
  ```bash
  ./gradlew :app:compileDevDebugKotlin --console=plain
  ./gradlew :app:testDevDebugUnitTest --console=plain
  ./gradlew :app:assembleDevDebug --console=plain
  ```
- Preserve unrelated working-tree changes. Never reset, clean, stash, or overwrite user work without approval.
- Do not assume a path, class, API, platform target or feature exists; prove it from the current checkout.

## Mandatory pre-edit checklist

Before the first code edit, the executor must:
1. Record repository HEAD and working-tree status.
2. Read the canonical owner plan and reconcile overlapping plans.
3. Locate actual source owners and tests; replace all investigation leads with verified paths/symbols.
4. Write the exact intended file-change list and test list.
5. Identify prohibited changes and any human approval gate.
6. Establish baseline build/test status.
7. Stop and report if the actual architecture contradicts this plan materially.

## Final handoff checklist

The final report must include:
- [ ] Baseline commit and working-tree preservation confirmation.
- [ ] Audit matrix with source references.
- [ ] Changed files and rationale.
- [ ] Tests/build commands and actual outcomes.
- [ ] Platform/device coverage and unavailable checks.
- [ ] Performance, privacy and accessibility findings.
- [ ] Persistence/compatibility/migration impact.
- [ ] Known limitations and follow-up work.
- [ ] Rollback procedure.
- [ ] Evidence for each acceptance criterion.

A plan is not implementation evidence. Do not mark the feature done until the acceptance criteria below are demonstrated.
