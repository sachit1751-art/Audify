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

**Repository context review (2026-11-04, HEAD `a135811c`):** latest published stable release is `1.8.7` (`v1.8.7`). Audify `1.8.9` (versionCode 37) is being validated by dry-run workflow [41835700383](https://github.com/sachit1751-art/Audify/actions/runs/41835700383); it is not published and must not be treated as a stable release. The production release build uses a 6 GiB Gradle heap and `--max-workers=1` for APK assembly after an R8 Java-heap OOM. Plans 01–14 and 069–074 remain planned/TODO unless their individual status is explicitly changed after source and test verification. Reconcile each plan against current source and tests before changing its status. This checkout has no root `CONTEXT.md` or `docs/adr/` directory; use `PRODUCT.md` and `DESIGN.md` as the current product/design context.

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

Only plan files currently present in this directory are listed below. Plans removed from this directory remain recoverable in git history; their removal is preserved as part of the current working-tree changes. Do not recreate removed plans unless explicitly requested. Where two retained plans cover the same feature area, reconcile them before implementation.

| Plan | Feature | Status / relationship |
|---|---|---|
| [01 - Artist new-release notifications](01-artist-new-release-notifications.md) | Followed-artist release alerts | **Not started - earlier baseline plan; reconcile with 04** |
| [02 - Accessibility-first lyrics](02-accessibility-first-lyrics.md) | Accessible lyrics presentation | **Not started - retained plan; reconcile against current lyrics implementation** |
| [03 - Complete playlist importer](03-selected-02-playlist-importer.md) | Playlist import and portability | **Planned - reconcile with existing importer and partial historical plan 038** |
| [04 - Artist release alerts](04-selected-06-artist-release-alerts.md) | Robust followed-artist release alerts | **Planned - newer hardened extension of 01; use one unified implementation scope** |
| [05 - Advanced search filters](05-selected-13-advanced-search-filters.md) | Capability-aware search filters | **Planned - provider capability audit required** |
| [06 - Statistics dashboard](06-selected-17-statistics-dashboard.md) | Improved statistics dashboard | **Planned - build on current aggregate statistics; no false per-play precision** |
| [07 - Explainable listening recommendations](07-explainable-listening-recommendations.md) | Listening-aware recommendations and discovery controls | **Proposal - audit existing Home shelves and available signals first** |
| [08 - Music recognition](08-music-recognition.md) | Foreground music recognition and recognition history | **Proposal - external provider, privacy, permission, and battery review required** |
| [09 - Player Studio](09-player-studio.md) | Player layouts, backgrounds, gestures, and motion controls | **Proposal - extend existing player components; no parallel design system** |
| [10 - Local metadata intelligence](10-local-metadata-intelligence.md) | Rich local tags, extraction, and library reconciliation | **Proposal - reconcile with older local-library plans before implementation** |
| [11 - Headphone audio profiles](11-headphone-audio-profiles.md) | AutoEQ-style headphone profiles and output-aware tuning | **Proposal - reconcile with existing output preset plan and audio pipeline** |
| [12 - Lyrics quality and language tools](12-lyrics-quality-language-tools.md) | Provider fallback, correction/import, translation, and romanization | **Proposal - reconcile with lyrics plans and existing translation foundation** |
| [13 - Casting protocol expansion](13-casting-protocol-expansion.md) | Evaluate FCast and DLNA support | **Research proposal - architecture and maintenance gate before implementation** |
| [14 - Podcast library](14-podcast-library.md) | Podcast subscriptions, episodes, and playback workflows | **Proposal - separate product surface; explicit scope decision required** |
| [069 - Artwork-to-detail continuity](069-artwork-to-detail-continuity.md) | Spatial continuity from album/playlist cover to detail hero | **TODO - high visual impact; implement before player micro-polish** |
| [070 - Playback-state transitions](070-playback-state-transitions.md) | Play/pause, buffering and track identity feedback | **TODO - high-frequency interaction; use authoritative playback state** |
| [071 - Queue interaction identity](071-queue-interaction-identity.md) | Stable queue occurrence identity and edit feedback | **TODO - high priority; duplicate-track correctness prerequisite** |
| [072 - Settings and expandable continuity](072-settings-expandable-continuity.md) | Interruptible expansion and settings state feedback | **TODO - medium priority; preserve immediate controls** |
| [073 - Android TV reduced motion](073-android-tv-reduced-motion.md) | Apply Reduce Motion consistently across TV transitions | **TODO - accessibility fix; TV preference currently misses hard-coded transitions** |
| [074 - Wear OS accessibility](074-wearos-accessibility-review.md) | TalkBack, hit bounds, rotary, state semantics and ambient behavior | **TODO - high priority; device verification required** |

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
