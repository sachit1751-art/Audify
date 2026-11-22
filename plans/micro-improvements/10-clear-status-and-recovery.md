<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Micro Improvement 10 — Consistent Status, Error and Recovery UX

**Status: in progress — local-library/download state is preserved on incomplete operations; shared UI errors now carry backward-compatible retryability metadata, and empty/permanent states suppress unsafe retry actions. Offline/permission-specific guidance and broader workflow coverage remain.**
**Execution model: eight gated stages.**  
**Canonical owner:** Existing shared UI state patterns plus playback, search, provider, artwork, lyrics, sync and download owners.  
**Baseline:** Reconcile against repository HEAD at execution time; this plan deliberately does not assume historical source paths remain valid.

## Product direction

Make asynchronous operations understandable and recoverable by distinguishing loading, empty, unavailable, offline, permission-required and failed states.

## Problem being solved

Generic spinners and vague errors force users to guess whether to wait, retry, change settings or leave the screen. Inconsistent status handling also hides real defects and makes support reports difficult to diagnose.

## Product principles

- Fix the user problem at its actual source; do not add cosmetic controls without working behaviour.
- Reuse the canonical AUDIFY architecture and existing state/data owners.
- Evidence and correctness take priority over visual polish or feature count.
- Preserve existing playback, navigation, provider behaviour, backup/restore and user preferences unless a verified defect requires a scoped change.
- Empty, unavailable and unsupported states must be honest; never fabricate data or capabilities.
- No new database schema, Room migration, remote service, account requirement, tracking pipeline or permission is implied by this plan.
- Any change crossing a prohibited boundary requires a separate proposal and explicit human approval.

## Known foundation and dependency map (historical; verify at execution)

The canonical owner is **Existing shared UI state patterns plus playback, search, provider, artwork, lyrics, sync and download owners**. The current plans index also lists overlapping work. These are planning relationships, not proof that a particular implementation is present or absent. The executor must inspect the current source, tests and plan history before editing. This plan is an enhancement/quality track; it must not silently take ownership away from the canonical plan.

## Stage 1 audit checklist

1. Inventory asynchronous workflows and their current UI states across playback, search, artwork, lyrics, provider connection, sync, backup, downloads and podcast/feed features where present.
2. Trace domain error types and determine which layer owns retryability, cancellation, authentication and user-facing messages.
3. Inspect existing design-system components, dialogs, snackbars, banners, empty states and loading skeletons.
4. Audit logging/diagnostics for secrets, tokens, personal data, provider URLs and listening history.
5. Identify duplicate error mapping and inconsistent status terminology; choose existing shared patterns where possible.

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

1. Define a shared state vocabulary while preserving domain-specific error detail: idle, loading, partial, empty, offline, permission-required, unavailable, failed and success as applicable.
2. Map errors at the owning feature boundary into concise user-facing explanations and a specific next action.
3. Offer retry only for operations known to be safe and retryable; use bounded retries and respect cancellation.
4. Ensure every loading state has a terminal path for success, failure, cancellation, timeout or lifecycle disposal.
5. Distinguish no results from provider failure, no permission from unavailable capability, and offline from authentication failure.
6. Use consistent accessible announcements and avoid repeatedly announcing transient progress.
7. Add privacy-safe diagnostic context such as operation category, app version and non-sensitive error code; exclude credentials and private listening details.

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

1. Test every state transition and terminal path for each audited workflow.
2. Test retryable versus permanent errors, repeated retry, cancellation, timeout, offline, authentication expiry and partial provider failure.
3. Verify loading indicators cannot remain indefinitely after navigation or lifecycle cancellation.
4. Test screen-reader announcements, focus handling and action labels for retry/settings actions.
5. Review diagnostic output with explicit secret/PII assertions.

### Performance and resource constraints

1. Do not add global polling or a central error service that every feature must synchronously call.
2. Avoid logging large payloads or repeatedly rendering progress updates.
3. Use existing lifecycle-aware coroutine/state patterns and cancel work when no longer relevant.

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
6. Re-test adjacent behaviour owned by Existing shared UI state patterns plus playback, search, provider, artwork, lyrics, sync and download owners.
7. Record exact commands, exit codes, device/API levels and any unavailable checks.

**Deliverable:** Reproducible verification report.  
**Exit criteria:** All acceptance criteria have evidence and no known critical regression remains in scope.

## Stage 8 — Handoff, rollback and completion

**Rollback strategy:** Adopt shared components incrementally and keep feature-specific fallback rendering available. Revert only mappings/components that hide actionable domain information. Do not suppress exceptions to make the UI appear successful.

**Rejected alternatives:** Do not show a generic retry button for permanent failures, hide errors behind endless spinners, expose raw stack traces to users, or log tokens/provider credentials.

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

- Audited workflows distinguish loading, empty, offline, unavailable, permission-required and failed states as applicable.
- Every loading path has a terminal success, failure, cancellation or timeout path.
- Retry controls are offered only for safe retryable operations.
- Errors provide a specific next action without exposing raw stack traces.
- Diagnostics are privacy-reviewed and contain no credentials or private listening data.


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

The shared UI model at `shared/src/commonMain/kotlin/com/music/audify/data/model/Models.kt:767-771` originally exposed only `Loading`, `Success(data, warning)` and `Error(message)`. `MessageState` in `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/components/Common.kt:1474-1497` accepts an optional action, but the error model did not say whether retry was safe. Home, Explore and Library attached Retry to generic errors, while some producers also represented empty results as errors.

The implementation adds backward-compatible `UiState.Error.retryable` metadata (defaulting to true so existing callers retain their behavior), makes Home/Explore/Library/History retry affordances conditional on that metadata, and adds common tests for default and explicit non-retryable states. Empty Explore charts/mood shelves, empty Library/History, signed-out History, completed empty local scans, empty remote listings, empty add-on discovery, empty cached/local detail pages and missing offline playlist records are marked non-retryable; provider/query/connection failures remain retryable. Permission-required local-library states are non-retryable because retry alone cannot grant access. `RemoteListing.state` now maps raw remote exceptions to a safe user-facing message instead of exposing UNC paths/server details; its regression test asserts that raw path text is not surfaced. `Downloads.delete` preserves metadata when storage deletion fails, and incomplete MediaStore verification does not prune offline playlist rows. More explicit offline/permission guidance and broader workflow coverage remain open.

## Implementation tracking

| Stage | Workstream | Current status |
|---:|---|---|
| 1 | Repository and product audit | Complete |
| 2 | Contract and acceptance design | Partial — retryability metadata is implemented; a broader typed status vocabulary remains |
| 3 | Foundation and domain implementation | Partial — empty/permanent producers annotated; remote exceptions sanitized |
| 4 | User interface and interaction | Partial — Home/Explore/Library/History retry actions are metadata-gated |
| 5 | Platform and integration coverage | Partial — phone/Wear/TV source compile and unit tests; no device checks |
| 6 | Reliability, performance and privacy | Partial — permanent retry cases and raw remote path exposure addressed; diagnostic log review remains |
| 7 | Full verification and regression pass | In progress — serialized shared/app/Wear/TV tests and compilation are being rerun after the final code changes |
| 8 | Handoff and plan status | In progress |

Progress is tracked by the current stage statuses above; broader acceptance gates remain open. Estimates are planning ranges, not delivery commitments.

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
