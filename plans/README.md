<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Audify improvement plans

This folder is the **execution track**: one self-contained plan file per feature, written so an
executor with zero session context can implement it. Every plan states its goal, the existing
code it builds on (with line-level recon), exact in-scope files, step-by-step implementation,
testing requirements, performance notes, rollback strategy, and rejected alternatives.

**Completed plans are deleted after execution** â€” they are archived (summary + implementation
commit) in the table at the bottom of this file, and their full text stays in git history.

Feature-planning track started **2026-09-26**. Before executing a plan, run
`git rev-parse --short HEAD`; if the tip has moved past the commit named in the plan's header,
check whether the plan's in-scope files changed (each plan lists them) and report drift instead
of blindly applying.

**Repository context review (2026-11-04, HEAD `a135811c`):** latest published stable release is `1.8.7` (`v1.8.7`). Audify `1.8.9` (versionCode 37) is being validated by dry-run workflow [41835700383](https://github.com/sachit1751-art/Audify/actions/runs/41835700383); it is not published and must not be treated as a stable release. The production release build uses a 6 GiB Gradle heap and `--max-workers=1` for APK assembly after an R8 Java-heap OOM. Plans 01â€“14 and 069â€“074 remain planned/TODO unless their individual status is explicitly changed after source and test verification. Reconcile each plan against current source and tests before changing its status. This checkout has no root `CONTEXT.md` or `docs/adr/` directory; use `docs/PRODUCT.md` and `docs/DESIGN.md` as the current product/design context.

**Feature audit (2026-11-06, baseline `2ac1c21eff38f718ab5127b6e99a42e4656d68ca`):** Plan 075 adds AUDIFY-owned Shared Playlists. The source audit found existing device-local and YouTube Music playlist paths, but no persistent collaborative playlist service. The Listen Together Go service uses an in-memory PartyStore and accepts client-submitted user identity that it does not independently verify; its party token is scoped to a transient party/device membership. Plan 075 therefore blocks implementation on verified AUDIFY identity, durable transactional storage, and portable provider-aware track references. Audit was read-only through GitHub; local uncommitted changes, deployed infrastructure, and runtime/device behavior were not verified.

Scope rules that apply to **every** plan (from the repo's AGENTS.md and project rules):

- Never bump the app version (`versionCode` / `versionName` in `app/build.gradle.kts`).
- Never touch the database schema or Room migrations.
- String edits go only in `app/src/main/res/values/sachit_strings.xml` (English). Never edit translated resources in `values-*`.
- No commits, pushes, or merges unless a human explicitly authorized them per-plan.
- No changes to README/markdown files except inside `plans/` and the top-level planning docs.
- Build/verify with JDK 21:
  ```bash
  ./gradlew :app:compileFossDebugKotlin --console=plain
  ./gradlew :app:testFossDebugUnitTest --console=plain
  ./gradlew :app:assembleFossDebug --console=plain
  ```

## Active plans

Only plan files currently present in this directory are listed below. Plans removed from this directory remain recoverable in git history; reconcile overlapping plans before implementation.

| Plan | Feature | Status / relationship |
|---|---|---|
| [01 - Artist new-release notifications](01-artist-new-release-notifications.md) | Followed-artist release alerts | **Not started - reconcile with 04** |
| [02 - Accessibility-first lyrics](02-accessibility-first-lyrics.md) | Accessible lyrics presentation | **Not started - reconcile against current lyrics implementation** |
| [03 - Complete playlist importer](03-selected-02-playlist-importer.md) | Playlist import and portability | **Planned - canonical import pipeline; reconcile with 078** |
| [04 - Artist release alerts](04-selected-06-artist-release-alerts.md) | Robust followed-artist release alerts | **Planned - unified scope with 01** |
| [05 - Advanced search filters](05-selected-13-advanced-search-filters.md) | Capability-aware search filters | **Planned - provider audit required** |
| [06 - AUDIFY Statistics](06-selected-17-statistics-dashboard.md) | Personal music intelligence | **Planned - aggregate-first; schema changes require approval** |
| [07 - Explainable listening recommendations](07-explainable-listening-recommendations.md) | Listening-aware recommendations | **Proposal - ranking owner; radio extension in 079** |
| [08 - Music recognition](08-music-recognition.md) | Foreground recognition and history | **Proposal - provider/privacy/battery review required** |
| [09 - Player Studio](09-player-studio.md) | Player layouts, backgrounds, gestures, motion | **Proposal - extend existing player components** |
| [10 - Local metadata intelligence](10-local-metadata-intelligence.md) | Rich local tags and reconciliation | **Proposal - reconcile older plans** |
| [11 - Headphone audio profiles](11-headphone-audio-profiles.md) | Output-aware tuning | **Proposal - reconcile audio pipeline** |
| [12 - Lyrics quality and language tools](12-lyrics-quality-language-tools.md) | Lyrics fallback, correction, translation | **Proposal - reconcile lyrics foundation** |
| [13 - Casting protocol expansion](13-casting-protocol-expansion.md) | FCast and DLNA research | **Research proposal** |
| [14 - Podcast library](14-podcast-library.md) | Podcast subscriptions and playback | **Blocked - Plan 15 prerequisite** |
| [15 - Typed playable-media boundary](15-typed-playable-media.md) | Multi-type Media3 identity foundation | **Designed - implementation not started** |
| [069 - Artwork-to-detail continuity](069-artwork-to-detail-continuity.md) | Artwork-to-detail transition | **TODO - existing Android artwork continuity proposal; separate from page navigation** |
| [070 - Playback-state transitions](070-playback-state-transitions.md) | Playback feedback | **TODO** |
| [071 - Queue interaction identity](071-queue-interaction-identity.md) | Stable queue occurrence identity | **TODO** |
| [072 - Settings and expandable continuity](072-settings-expandable-continuity.md) | Settings state feedback | **TODO** |
| [073 - Android TV reduced motion](073-android-tv-reduced-motion.md) | TV motion accessibility | **RECONCILE - current TvApp already wires reduced-motion transitions; inspect remaining TV call sites before changing** |
| [074 - Wear OS accessibility](074-wearos-accessibility-review.md) | Wear OS accessibility | **TODO - include reduced-motion audit for infinite buffering spinner and press feedback** |
| [075 - Collaborative Playlists](075-collaborative-playlists.md) | Persistent shared playlists | **Planned - blocked on verified identity, durable storage and portable track IDs** |
| [076 - Smart Playlists](076-smart-playlists.md) | Rule-based auto-updating playlists | **Implemented in v1.9.0** |
| [077 - Android Auto](077-android-auto.md) | Car-safe browse and playback | **Media3 browse/playback integration in v1.9.0; real vehicle validation remains** |
| [078 - Cross-service Music Migration](078-cross-service-music-migration.md) | Import supported service exports | **Proposal - companion to 03; 03 owns import pipeline** |
| [079 - Smart Music Radio](079-smart-music-radio.md) | Endless seed-based listening | **Proposal - companion to 07; 07 owns ranking** |
| [080 - Home Screen Widgets and Quick Settings](080-home-widgets-quick-settings.md) | External playback controls | **Proposal - reuse MediaSession state** |
| [081 - Library Backup and Restore](081-library-backup-restore.md) | Portable Audify-owned data | **Proposal - data boundary audit required** |
| [082 - Listening Challenges](082-listening-challenges.md) | Optional private discovery prompts | **Proposal - verified local signals only** |
| [083 - Shareable Music Cards](083-shareable-music-cards.md) | Shareable music artwork cards | **Proposal - rights/deep-link audit required** |
| [084 - Web reduced-motion feedback](084-web-reduced-motion-feedback.md) | Preserve useful feedback while suppressing nonessential motion | **TODO - HIGH; accessibility foundation** |
| [085 - Web easing and duration tokens](085-web-easing-duration-tokens.md) | Normalize interactive web easing and timing | **TODO - HIGH; depends on 084 policy and shared tokens** |
| [086 - Artwork loading fades](086-artwork-loading-fades.md) | Responsive artwork and artist hero fades | **TODO - MEDIUM; depends on 085 tokens** |
| [087 - Full-screen player motion](087-fullscreen-player-motion.md) | Refine artwork, motion-artwork and control transitions | **TODO - MEDIUM; depends on 084/085** |
| [088 - Desktop popover entrance](088-desktop-popover-entrance.md) | Trigger-origin-aware popover entrance | **TODO - LOW; independent** |
| [089 - Android micro-interactions](089-android-micro-interactions.md) | Balanced-premium touch, icon, slider and feedback motion | **Implemented locally; device QA pending** |

## Recommended motion execution order

1. 084 — Web reduced-motion feedback (accessibility prerequisite).
2. 085 — Web easing and duration tokens (shared motion vocabulary).
3. 086 — Artwork loading fades and 087 — full-screen player motion (can proceed after 085).
4. Android navigation continuity is already present in `MainActivity.kt` page `AnimatedContent`; preserve it. Reconcile 069, 073 and 074 against current code before implementation; do not duplicate work.
5. 088 — Desktop popover entrance (independent, low priority).
6. 089 - Android micro-interactions are implemented locally; finish phone-device feel and accessibility QA before archiving.

## Completed and archived

Plans are removed from `plans/` once fully implemented and verified. Record kept here; the
full plan text is in git history.

## How to update status

When a plan starts, mark it `in-progress`. When all done criteria pass, mark it `done`, move
its summary into the archive table, and delete the plan file. If an escape hatch fires, mark it
`blocked` and explain why. Executors update this file.

Before starting a plan, read `docs/PRODUCT.md` and `docs/DESIGN.md`, then inspect any architecture-decision
records that actually exist. Do not assume `CONTEXT.md` or `docs/adr/` exists.
