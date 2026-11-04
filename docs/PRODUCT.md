<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Product context — Audify

> **Purpose:** durable product brief for contributors and implementation agents. This document describes the product evidenced by the current repository and distinguishes shipped behavior from planned work. It is not a promise that every upstream service or optional integration is always available.
>
> **Context snapshot:** reviewed 2026-11-04 against repository `main` at `a135811c` (Audify `1.8.9`, versionCode `37`) and GitHub Releases. The latest published stable release remains `1.8.7` (`v1.8.7`). The `1.8.9` release workflow is a dry run and is currently in progress; it has not yet produced verified APK artifacts or a published release. The production build now gives Gradle a 6 GiB heap and serializes APK assembly workers to reduce R8 memory pressure. Do not describe `1.8.9` as released until CI completes package, signature, and native-payload verification and the release is explicitly published.

## Product summary

Audify is an open-source, third-party music player and discovery client built primarily for Android, with dedicated Android TV and Wear OS variants. It combines remote music discovery, personal-library access, playlists, local and downloaded audio, background playback, synchronized lyrics, and detailed playback/audio controls.

Audify is independent of Google, YouTube, YouTube Music, Deezer, Telegram, and other third-party services. It does not host or upload music. External service capabilities, availability, authentication, and regional behavior can change independently of Audify.

## Users and core jobs

1. **Discover and identify music:** search and browse songs, albums, artists, playlists, recommendations, and supported personalized content.
2. **Organize a listening collection:** manage playlists, saved library content, local files, downloads, and the playback queue.
3. **Listen reliably:** continue playback in the background, restore listening state, select available outputs, and control quality and transitions.
4. **Follow and control playback:** use synchronized lyrics, lyric seeking, queue controls, playback diagnostics, and audio settings.
5. **Use the right device surface:** listen on Android, use the TV-specific experience, and control supported playback from Wear OS.

## Product surfaces

- **Android phone:** primary full-featured application; Explore/Home, Search, Library, player, lyrics, queue, settings, account/source management, and downloads.
- **Android TV:** separate TV application/build with remote-oriented navigation and shared playback/download infrastructure where supported.
- **Wear OS:** companion build for supported watch workflows; not a replacement for the phone application.
- **Web:** a separate Vite/React web-player and browser-extension workstream lives in `web/`. Treat its feature set and deployment state independently; do not imply Android feature parity or infer that a local build is publicly deployed.
- **Shared UI and domain modules:** Compose Multiplatform code is shared where the architecture allows; Android-only integrations remain in platform source sets.

## Current capability baseline

The following is a repository-backed snapshot, not a guarantee that each third-party provider is currently reachable:

### Discovery and library
- YouTube Music-oriented search and discovery for songs, albums, artists, and playlists.
- Google-account sign-in and supported personalized library content.
- Playlist creation and supported playlist editing/management.
- Local audio browsing/playback and selected-folder access.
- Home discovery includes mood shortcuts and a time-aware shelf based on existing feed content and on-device listening-hour patterns.

### Playback and audio
- Background playback and playback-session restoration.
- Queue management, queue skip-history markers, and an "Earlier" boundary.
- Gapless playback and configurable crossfade; crossfade audible quality still requires real-device listening QA.
- Automix is explicitly beta; transition quality and compatibility need real-device listening validation.
- Playback speed, silence skipping, sleep timer, audio quality controls including per-network ceilings, output selection, and playback diagnostics (Signal sheet).
- Optional pause-on-mute and resume-on-Bluetooth behaviors are disabled by default.
- Audio normalization and loudness preferences are implemented; do not plan a duplicate implementation.

### Lyrics and presentation
- Synchronized line-/word-level lyrics from supported providers, lyric navigation/seeking, provider selection, and offset controls.
- Artwork-driven theming/backdrop effects, progressive top/bottom scroll-edge blur with an independent Settings toggle, and a shared Compose motion-token foundation.
- Profile listening aggregates and a weekly recap; monthly/time-aware discovery surfaces exist.

### Downloads and integrations
- Offline downloads with metadata and storage-budget controls.
- Download checks establish file accessibility/readable-header evidence, **not proof of successful playback**.
- Spotify playlist CSV/TSV import is wired for exports with Track Name and Artist columns. JSON, M3U, and richer column mapping are not yet supported.
- Statistics CSV export contains retained monthly per-track aggregates; it is **not** a complete per-play listening history and does not fabricate per-play timestamps.
- Last.fm and ListenBrainz scrobbling and Discord Rich Presence are listed as supported integrations; actual provider availability/configuration can vary.
- Optional external source modules may depend on third-party clients, accounts, or provider terms.

## Product principles

1. **Truthful state:** distinguish confirmed, inferred, stale, unavailable, and unknown states. Do not label a file playable based only on existence or readability.
2. **User intent is authoritative:** never silently change the requested recording, playlist order, queue occurrence, or selected output to make an operation appear successful.
3. **Least privilege:** use scoped platform permissions and explicit user consent. Do not request broad storage access or collect credentials unnecessarily.
4. **Safe mutation:** preview consequential bulk changes, preserve recoverability where feasible, and never delete user-owned files as incidental cleanup.
5. **Graceful provider failure:** explain external-service limitations; avoid unsupported scraping, hidden APIs, unbounded retries, or fabricated availability.
6. **Local-first privacy:** do not add analytics upload, background network work, or cross-device synchronization without an explicit product decision.
7. **Incremental architecture:** reuse existing playback, queue, persistence, source-health, and UI ownership rather than introducing parallel subsystems.
8. **Accessibility and responsiveness:** support system text scaling, assistive technology, reduced motion, touch/remote/keyboard input, and constrained devices.

## Explicitly not current capabilities

Do not describe these as shipped merely because a plan exists:

- Complete M3U/JSON/Spotify mapping importer; only the documented CSV/TSV subset is implemented.
- Per-play listening history with timestamps, skips, and durations.
- Scheduled automatic backups with verified restore.
- Library cleanup assistant or download repair manager.
- Artist release alerts.
- Full local-library indexing beyond existing local playback/browsing capabilities.
- Advanced search filters.
- Output-specific EQ presets.
- New queue bulk-selection behavior beyond existing queue functionality.
- Playlist health scanning/repair.
- New transparent same-recording source fallback policy beyond existing source-health/cache behavior.
- Expanded Automix controls, tablet two-pane redesign, accessibility-first lyrics preference suite, redesigned statistics dashboard, or unified contextual player actions.

The selected feature expansions are tracked in `plans/README.md` as plans 051–068. They are planned, not shipped. Older plans 025–050 remain relevant and must be reconciled before overlapping work.

## Architecture and implementation constraints

- Main Android application code: `app/src/main/java/com/music/audify/`.
- Shared domain logic: `shared/`.
- Shared Compose UI: `sharedUi/`, with `jvmSharedMain`, platform-specific source sets, and tests.
- Dedicated device applications: `tv/` and `wearos/`.
- Playback ownership is centered on the Android playback service and shared playback abstractions; inspect current owners before changing state flow.
- Existing queue/transport abstractions include `PlaybackIntent`, `PlaybackTarget`, and `StreamResolver`; existing source health is in `StreamSourceHealth`.
- Existing persistence is distributed across current settings/data stores and download/playlist implementations. Do not assume a new database is available.

### Hard project rules

- Never change app `versionCode` or `versionName` unless specifically authorized.
- Never change Room/database schemas or migrations without explicit human approval.
- English Android string edits belong only in `app/src/main/res/values/sachit_strings.xml`; do not edit translated resource files unless specifically authorized.
- Do not commit, push, merge, or rewrite history unless explicitly authorized.
- Preserve unrelated working-tree changes. Inspect `git status` and current HEAD before implementation.
- Build with JDK 21. Use the current repository task names and verify the actual variant/module affected.
- The current Gradle wrapper is 9.7.0, Android Gradle Plugin is 9.3.1, Kotlin is 2.4.21, and Compose Multiplatform is 1.12.2. The Android phone/TV modules compile against API 37 and target API 36; Wear OS has min API 30 while phone/TV have min API 26. Verify these values in build files before changing them.
- The Android app targets JVM 17 bytecode while the build itself requires JDK 21. Do not confuse `sourceCompatibility`/`jvmTarget` with the Gradle runtime JDK.
- Read `PRODUCT.md`, `DESIGN.md`, `plans/README.md`, and relevant architecture decisions before implementing a plan. This checkout has no root `CONTEXT.md` or `docs/adr/` directory; do not assume either exists or invent their contents.

## Planning and status source of truth

`plans/README.md` is the implementation-plan index and status ledger. It distinguishes completed, partial, blocked, in-progress, and planned work. `docs/future%20updates/RELEASE_PROCESS.md` is the CI/release status and artifact contract. Versioned release notes live in `docs/releases/` and may contain release-candidate limitations. When documents disagree, verify current source, tests, and GitHub release state before asserting a feature is shipped.

## Definition of product completion

A feature is not complete because it compiles or has a screen. It must preserve existing behavior, satisfy its plan's acceptance criteria, have focused tests, be checked on relevant form factors/devices when needed, communicate unsupported states honestly, and document any unverified limitations. Audio transitions, external integrations, notifications, and adaptive UI require the appropriate real-device/provider validation.
