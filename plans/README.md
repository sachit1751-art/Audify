<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Audify improvement plans

This folder is the **execution track**: one self-contained plan file per feature, written so an
executor with zero session context can implement it. Every plan states its goal, the existing
code it builds on (with line-level recon), exact in-scope files, step-by-step implementation,
testing requirements, performance notes, rollback strategy, and rejected alternatives.

**Completed plans are deleted after execution** — they are archived (summary + implementation
commit) in the table at the bottom of this file, and their full text stays in git history.

Feature-planning track started **2026-09-26**. Before executing a plan, run
`git rev-parse --short HEAD`; if the tip has moved past the commit named in the plan's header,
check whether the plan's in-scope files changed (each plan lists them) and report drift instead
of blindly applying.

**Repository context review (2026-11-02):** stable release `1.8.6` (`v1.8.6`) is publicly published. Nine production APKs are attached and signature-verified; device-install verification was unavailable because no device/emulator was connected. Plans 051–068 remain proposals, not evidence of implementation. Reconcile each plan against current source and tests before changing its status. This checkout has no root `CONTEXT.md` or `docs/adr/` directory; use `PRODUCT.md` and `DESIGN.md` as the current product/design context.

Scope rules that apply to **every** plan (from the repo's AGENTS.md and project rules):

- Never bump the app version (`versionCode` / `versionName` in `app/build.gradle.kts`).
- Never touch the database schema or Room migrations.
- String edits go only in `app/src/main/res/values/sachit_strings.xml` (English). Never
  edit translated `sachit_strings.xml` / `strings.xml` files in `values-*`.
- No commits, pushes, or merges unless a human explicitly authorized them per-plan.
- No changes to README/markdown files except inside `plans/` and the top-level planning docs (audit, matrix, ideas, design system, roadmap).
- Build/verify with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-*` is auto-provisioned if no
  standalone JDK 21 is installed):

  ```bash
  ./gradlew :app:compileFossDebugKotlin --console=plain
  ./gradlew :app:testFossDebugUnitTest --console=plain   # app JVM/Robolectric tests
  ./gradlew :app:assembleFossDebug --console=plain
  ```

## Active plans

### Selected feature expansion (plans written, not started)

The following 18 plans map one-to-one to the user's selected feature list, in its original order.
They use new plan IDs because 001–050 are already part of the repository's planning history.
Where an earlier plan already covers the same area, the new plan is an implementation-ready
reconciliation/extension, not permission to build a duplicate subsystem. Reconcile the older plan
and current source before execution.

| # | Plan | Selected item | Category | Effort | Depends on | Requires human gate | Status |
|---|------|---------------|----------|--------|------------|---------------------|--------|
| 051 | [Named queue slots](051-selected-01-named-queue-slots.md) | 1. Named queue slots | Queue | M | 031 | none | **planned — reconcile 031** |
| 052 | [Complete playlist importer](052-selected-02-playlist-importer.md) | 2. Complete playlist importer | Import | H | 038 | none | **planned — extend partial 038** |
| 053 | [Full listening history](053-selected-03-full-listening-history.md) | 3. Full listening history | Stats | H | 037 | storage approval if schema required | **planned — not started** |
| 054 | [Automatic backups](054-selected-04-automatic-backups.md) | 4. Automatic backup scheduling | Backup | M | 050 | SAF destination decision | **planned — reconcile 050** |
| 055 | [Library cleanup assistant](055-selected-05-library-cleanup-assistant.md) | 5. Library cleanup assistant | Library | M | 040 | none | **planned — extend 040** |
| 056 | [Artist release alerts](056-selected-06-artist-release-alerts.md) | 6. Artist release alerts | Discovery | M | 044 | provider capability/terms review | **planned — reconcile 044** |
| 057 | [Local music library](057-selected-07-local-music-library.md) | 7. Better local music library | Library | H | 025 | none | **planned — extend 025** |
| 058 | [Download repair manager](058-selected-08-download-repair-manager.md) | 8. Download repair manager | Downloads | M | 039 | none | **planned — not started** |
| 059 | [Smarter queue management](059-selected-09-smarter-queue-management.md) | 9. Smarter queue management | Queue | M | 020, 031 | none | **planned — not started** |
| 060 | [Audio presets per output](060-selected-10-audio-presets-per-output.md) | 10. Audio presets per output | Audio | M | 032 | hardware QA | **planned — extend EQ foundation** |
| 061 | [Playlist health checker](061-selected-11-playlist-health-checker.md) | 11. Playlist health checker | Playlists | M | 038, 040 | none | **planned — not started** |
| 062 | [Source fallback transparency](062-selected-12-source-fallback-transparency.md) | 12. Improved source fallback | Playback | M | 021 | provider behavior review | **planned — extend shipped 021** |
| 063 | [Advanced search filters](063-selected-13-advanced-search-filters.md) | 13. Advanced search filters | Search | M | none | provider capability review | **planned — not started** |
| 064 | [Automix controls](064-selected-14-automix-controls.md) | 14. Better Automix controls | Playback | M | existing Automix | real-device listening QA | **planned — not started** |
| 065 | [Tablet two-pane interface](065-selected-15-tablet-two-pane-interface.md) | 15. Tablet two-pane interface | UI | M | 026 | tablet/foldable QA | **planned — extend 026** |
| 066 | [Accessibility-first lyrics](066-selected-16-accessibility-first-lyrics.md) | 16. Accessibility-first lyrics | Accessibility | H | 045 | assistive-tech QA | **planned — reconcile 045** |
| 067 | [Statistics dashboard](067-selected-17-statistics-dashboard.md) | 17. Better statistics dashboard | Stats/UI | M | current stats | metric/data audit | **planned — known UX gap** |
| 068 | [Contextual player actions](068-selected-18-contextual-player-actions.md) | 18. Contextual player actions | Player/UI | S–M | existing song menus | none | **planned — audit for duplicates** |

## Completed and archived

Plans are removed from `plans/` once fully implemented and verified. Record kept here; the
full plan text is in git history (delete commits reference these hashes).

## How to update status

When you start a plan: mark it `in-progress`. When done criteria all pass: `done`, move the
plan's summary into the archive table above, and delete the plan file. If an escape hatch
fired: `blocked` and note why in the plan file. Executors update this file.

Before starting any plan, read `PRODUCT.md` and `DESIGN.md`, then inspect any architecture-decision
records that actually exist in the checkout. Do not assume `CONTEXT.md` or `docs/adr/` exists; as
of the 2026-11-01 review, neither path is present in this repository.
