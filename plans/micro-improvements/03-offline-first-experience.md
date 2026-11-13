<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Micro Improvement 03 — Offline-First Library and Playback Experience

**Status: planned — not started.**  
**Execution model: eight gated stages.**  
**Canonical owner:** Plan 081 — Library Backup and Restore plus existing local-media/download/provider boundaries.  
**Baseline:** Reconcile against repository HEAD at execution time; this plan deliberately does not assume historical source paths remain valid.

## Product direction

Make locally available music discoverable and usable without a network while accurately communicating which provider features require connectivity.

## Problem being solved

Users may have local or legitimately downloaded content but still encounter online-only loading, search, or error states. Conversely, presenting streamed provider content as downloadable or offline-capable would be misleading and may violate provider rules.

## Product principles

- Fix the user problem at its actual source; do not add cosmetic controls without working behaviour.
- Reuse the canonical AUDIFY architecture and existing state/data owners.
- Evidence and correctness take priority over visual polish or feature count.
- Preserve existing playback, navigation, provider behaviour, backup/restore and user preferences unless a verified defect requires a scoped change.
- Empty, unavailable and unsupported states must be honest; never fabricate data or capabilities.
- No new database schema, Room migration, remote service, account requirement, tracking pipeline or permission is implied by this plan.
- Any change crossing a prohibited boundary requires a separate proposal and explicit human approval.

## Known foundation and dependency map (historical; verify at execution)

The canonical owner is **Plan 081 — Library Backup and Restore plus existing local-media/download/provider boundaries**. The current plans index also lists overlapping work. These are planning relationships, not proof that a particular implementation is present or absent. The executor must inspect the current source, tests and plan history before editing. This plan is an enhancement/quality track; it must not silently take ownership away from the canonical plan.

## Stage 1 audit checklist

1. Map local files, provider streams, cached metadata, artwork caches, and any existing download feature; classify ownership and persistence for each.
2. Trace library browse/search/playback requests while offline and identify unnecessary network dependencies.
3. Inspect provider terms/API capabilities and distinguish locally owned files, provider-authorized downloads, transient cache, and remote-only content.
4. Read Plan 081 and backup/restore boundaries; identify which data is portable and which is device/provider-bound.
5. Audit connectivity observation, download state, partial files, cancellation, storage errors, and retry handling.

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

1. Define a capability matrix for each content source: browse offline, search offline, play offline, metadata available, artwork cached, and download permitted.
2. Ensure local library queries and playback do not wait for remote provider calls when local data is sufficient.
3. Clearly label downloaded/local/stream-only/unavailable states using existing domain models; never treat temporary cache as a permanent download.
4. Repair download lifecycle states where the feature already exists: queued, downloading, paused, complete, failed, cancelled, and storage unavailable.
5. Add safe retry and resume behaviour only where the provider and current storage implementation support it.
6. Provide offline-aware empty/error states and reconnect reconciliation without deleting local content or duplicating library entries.
7. Document features that intentionally remain online-only rather than attempting to simulate unsupported offline access.

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

1. Run library browse, local search, metadata display and playback tests with network access disabled.
2. Test partial download, cancellation, low storage, permission loss, corrupt/incomplete files, app restart and reconnection.
3. Verify remote-only items are not falsely playable offline and local items are not hidden by provider timeouts.
4. Test duplicate reconciliation and backup/restore boundaries without adding schema changes.
5. Use provider fakes or existing test doubles; do not rely only on manual airplane-mode checks.

### Performance and resource constraints

1. Avoid repeated connectivity checks per row; use existing connectivity observation.
2. Do not eagerly download or cache entire libraries; respect user actions, storage limits and provider policy.
3. Keep local browse and search responsive on large libraries.

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
   ./gradlew :app:compileFossDebugKotlin --console=plain
   ./gradlew :app:testFossDebugUnitTest --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
4. Run additional module/platform build tasks only when the changed code requires them and the task exists.
5. For UI or device-specific work, install and verify on a real emulator/device; screenshots or compilation alone are insufficient.
6. Re-test adjacent behaviour owned by Plan 081 — Library Backup and Restore plus existing local-media/download/provider boundaries.
7. Record exact commands, exit codes, device/API levels and any unavailable checks.

**Deliverable:** Reproducible verification report.  
**Exit criteria:** All acceptance criteria have evidence and no known critical regression remains in scope.

## Stage 8 — Handoff, rollback and completion

**Rollback strategy:** Keep offline routing and status changes isolated behind existing repository/provider boundaries. Revert only the affected fallback path if it hides or misclassifies content. No new storage schema, download entitlement, or provider bypass is authorized.

**Rejected alternatives:** Do not scrape provider streams, bypass DRM, download content without provider authorization, equate cache with offline ownership, or add a background downloader without separate approval.

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

- Offline capability is documented by content source and action.
- Eligible local/downloaded content can be browsed and played without network access.
- Remote-only items are never represented as guaranteed offline content.
- Partial downloads, storage failures, cancellation and reconnect are handled correctly.
- Provider terms, DRM and licensing boundaries are respected; no new schema is introduced.


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

## Implementation tracking

| Stage | Workstream | Initial status |
|---:|---|---|
| 1 | Repository and product audit | Not started |
| 2 | Contract and acceptance design | Not started |
| 3 | Foundation and domain implementation | Not started |
| 4 | User interface and interaction | Not started |
| 5 | Platform and integration coverage | Not started |
| 6 | Reliability, performance and privacy | Not started |
| 7 | Full verification and regression pass | Not started |
| 8 | Handoff and plan status | Not started |

Progress is **0/8** until work is actually performed and evidenced. Estimates are planning ranges, not delivery commitments.

## Repository-wide execution constraints

- Follow `plans/README.md` and the repository's actual agent instructions.
- Never bump `versionCode` or `versionName`.
- Never modify database schema or Room migrations under this plan.
- String edits go only in `app/src/main/res/values/sachit_strings.xml`; do not edit translated resources.
- No commits, pushes or merges unless the user explicitly authorizes them for this work.
- Do not edit README/Markdown outside `plans/` and top-level planning docs.
- Use JDK 21 and the repository-approved verification commands:
  ```bash
  ./gradlew :app:compileFossDebugKotlin --console=plain
  ./gradlew :app:testFossDebugUnitTest --console=plain
  ./gradlew :app:assembleFossDebug --console=plain
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
