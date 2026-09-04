# Sachit improvement plans

Audit run by an advisory pass on **2026-09-04**, written against commit **`7ca7a9653`**.
Each plan below is self-contained for an executor with zero session context. Before
executing a plan, run `git rev-parse --short HEAD`; if the tip has moved past `7ca7a9653`,
check whether the plan's in-scope files changed (each plan lists them) and report drift
instead of blindly applying.

Scope rules that apply to **every** plan (from the repo's AGENTS.md and project rules):

- Never bump the app version (`versionCode` / `versionName` in `app/build.gradle.kts`).
- Never touch the database schema or Room migrations.
- String edits go only in `app/src/main/res/values/sachit_strings.xml` (English). Never
  edit translated `sachit_strings.xml` / `strings.xml` files in `values-*`.
- No commits, pushes, or merges unless a human explicitly authorized them per-plan.
- No changes to README/markdown files **except** inside `plans/` (the advisory directory
  created for this workflow).
- Verification commands assume the dev machine from the session handoff:

  ```bash
  export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"
  ./gradlew :app:assembleFossDebug --console=plain
  ./gradlew :app:testFossDebugUnitTest --console=plain   # app JVM/Robolectric tests
  ./gradlew :innertube:testDebugUnitTest --console=plain # innertube JVM tests
  ```

## Index

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 001 | Fix Listen Together invite deep links | Bug | S | — | Choose canonical domain | skipped (user choice, 2026-09-04) |
| 002 | QA + land the uncommitted player visualizer | Feature | S–M | — | Final commit | done (QA pass 2026-09-04; no fixes needed; commit included in feature branch) |
| 003 | Remove `200B` watermark comments (513 files) | Hygiene | M | — | none | done (executed 2026-09-04) |
| 004 | Scope or document cleartext network config | Security | S | 001 (same feature area) | none | done (comment + non-wss warning added 2026-09-04) |
| 005 | Characterization tests for YouTube.kt + LT state | Tests | M | — | none | blocked (escape hatch: InnerTubeX URL allowlist + no safe LT seam; see file) |
| 006 | Fix dev-guide + CI drift | Docs/DX | S | — | Markdown track needs human | done (dev-guide text fixed during rebrand; CI flavor matrix left for human decision) |
| 007 | Localization pipeline spike (sachit_strings.xml via Crowdin) | Direction | S (spike) | — | Feature choice | done → results/007-localization-findings.md |
| 008 | Audio/queue power-up spike (pick one) | Direction | S (spike) | — | Feature choice | done → results/008-feature-spec.md |

## Dependency notes

- 001 should land before 004 only because both touch the Listen Together surface; they are
  otherwise file-independent.
- 003 (watermark removal) is safe to run at any time and makes every later diff cleaner —
  recommended second, after 001.
- 002 works on already-written but uncommitted code (`PlayerArtworkVisualizer.kt`,
  `VisualizerLevels.kt`, `VisualizerLevelsTest.kt` + wiring). Do not run it concurrently
  with 003: 002's files have no watermark lines, but both plans move the same working tree.
- 005 is characterization-only (no behavior change) and is a prerequisite for any future
  refactor of the areas it covers.

## How to update status

When you start a plan: mark it `in-progress`. When done criteria all pass: `done`. If an
escape hatch fired: `blocked` and note why in the plan file. Executors update this table.

## Considered and rejected (do not re-audit)

- Hardcoded `GOOGLE_API_KEY` in `PoTokenWebView.kt` — public web-client key, shipped in
  every fork of this codebase; no rotation possible.
- `MusicService` exported without permission — required for Android Auto / media buttons.
- `runBlocking` DataStore reads in `MusicService.onCreate` — deliberate single batch read.
- Exact-alarm permission handling — correctly gated (`MusicAlarmScheduler.kt:31`).
- 5s Discord presence poll loop — foreground-service only, flag-guarded.
- Room `newFixedThreadPool(4)` executors — deliberate.
- No Compose UI/preview tests at all — acknowledged gap, too large to plan usefully now.
