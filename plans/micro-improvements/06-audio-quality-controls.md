<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Micro Improvement 06 — Audio Quality, Output and Playback Enhancement Controls

**Status: planned — not started.**  
**Execution model: eight gated stages.**  
**Canonical owner:** Plan 11 — Headphone Audio Profiles and the existing audio pipeline.  
**Baseline:** Reconcile against repository HEAD at execution time; this plan deliberately does not assume historical source paths remain valid.

## Product direction

Make supported audio-quality and playback-enhancement behaviour understandable, consistent and safe across sources and output routes.

## Problem being solved

Settings that do not match actual pipeline behaviour undermine trust. Streaming quality, normalization, gapless playback, crossfade and device profiles depend on provider, decoder, output route and platform support; a UI toggle alone is not implementation.

## Product principles

- Fix the user problem at its actual source; do not add cosmetic controls without working behaviour.
- Reuse the canonical AUDIFY architecture and existing state/data owners.
- Evidence and correctness take priority over visual polish or feature count.
- Preserve existing playback, navigation, provider behaviour, backup/restore and user preferences unless a verified defect requires a scoped change.
- Empty, unavailable and unsupported states must be honest; never fabricate data or capabilities.
- No new database schema, Room migration, remote service, account requirement, tracking pipeline or permission is implied by this plan.
- Any change crossing a prohibited boundary requires a separate proposal and explicit human approval.

## Known foundation and dependency map (historical; verify at execution)

The canonical owner is **Plan 11 — Headphone Audio Profiles and the existing audio pipeline**. The current plans index also lists overlapping work. These are planning relationships, not proof that a particular implementation is present or absent. The executor must inspect the current source, tests and plan history before editing. This plan is an enhancement/quality track; it must not silently take ownership away from the canonical plan.

## Stage 1 audit checklist

1. Trace the audio pipeline from provider/source through decoder, Media3/player, audio processing and output route.
2. Inventory existing quality, normalization, equalizer, crossfade, gapless, loudness and device-profile controls and verify whether each is wired to runtime behaviour.
3. Read Plan 11 and relevant player/provider plans; document platform/provider support and conflicts.
4. Inspect audio focus, Bluetooth/wired/USB route changes, sample-rate/format reporting and lifecycle behaviour.
5. Record baseline CPU, memory, battery and playback stability with current effects enabled/disabled.

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

1. Create a capability matrix for each proposed control by platform, provider/source and output route; expose only verified capabilities.
2. Correct existing controls whose labels, defaults or persistence do not match actual runtime behaviour.
3. If streaming quality selection already exists, map choices to documented provider-supported values and explain data-use implications.
4. Implement or refine normalization, gapless or crossfade only when the current pipeline supports it and tests prove no clipping, abrupt transitions or route regressions.
5. Make device-specific profiles opt-in, scoped to supported output identities, and robust to devices being disconnected or renamed.
6. Ensure controls have clear defaults, reset behaviour and conflict handling; avoid stacking multiple gain processors unintentionally.
7. Report actual effective state where possible; do not claim bit-perfect output or codec quality from metadata alone.

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

1. Test each control enabled/disabled and at boundary values using deterministic audio/pipeline tests where available.
2. Test route changes, Bluetooth reconnect, wired/USB output, interruptions, focus changes and source/provider capability differences.
3. Verify no clipping or unexpected volume jumps; test normalization interaction with user volume.
4. Measure CPU, memory and battery impact; ensure disabling an effect restores baseline behaviour.
5. Test preference persistence and safe fallback when a previously selected output device is unavailable.

### Performance and resource constraints

1. Avoid expensive DSP on the main thread; use existing audio processing lifecycle.
2. Do not keep processing active when disabled or when the pipeline cannot apply it.
3. Measure battery and thermal impact on representative hardware.

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
6. Re-test adjacent behaviour owned by Plan 11 — Headphone Audio Profiles and the existing audio pipeline.
7. Record exact commands, exit codes, device/API levels and any unavailable checks.

**Deliverable:** Reproducible verification report.  
**Exit criteria:** All acceptance criteria have evidence and no known critical regression remains in scope.

## Stage 8 — Handoff, rollback and completion

**Rollback strategy:** Gate each enhancement independently and preserve a reliable default path. If an effect causes instability, disable that effect without breaking core playback. No native audio engine replacement is authorized.

**Rejected alternatives:** Do not add cosmetic toggles, claim unsupported formats, stack duplicate DSP, force device-specific settings globally, or promise provider-controlled bitrate selection where APIs do not expose it.

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

- Every exposed control is connected to verified runtime pipeline behaviour.
- Capability differences by provider, platform and output route are explicit.
- Route changes do not cause clipping, unexpected volume jumps or stuck effects.
- CPU, memory and battery impact are measured for enabled processing.
- No unsupported codec, bitrate or bit-perfect guarantee is displayed.


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
