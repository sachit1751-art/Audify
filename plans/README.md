# Audify improvement plans

This folder is the **execution track**: one self-contained plan file per feature, written so an
executor with zero session context can implement it. Every plan states its goal, the existing
code it builds on (with line-level recon), exact in-scope files, step-by-step implementation,
testing requirements, performance notes, rollback strategy, and rejected alternatives.

**Completed plans are deleted after execution** — they are archived (summary + implementation
commit) in the table at the bottom of this file, and their full text stays in git history.

Related reference docs (top level, context only — not executed): `BITCHORD_AUDIFY_FEATURE_AUDIT.md`
(ecosystem audit), `COMPETITOR_FEATURE_MATRIX.md` (feature selection + sources),
`AUDIFY_INNOVATION_IDEAS.md` (original feature concepts), `AUDIFY_DESIGN_SYSTEM.md` (UI rules),
`AUDIFY_ROADMAP.md` (multi-phase ordering). A local read-only checkout of the reference project
lives at `reference-player-sdk/` (gitignored).

Feature-planning track started **2026-09-26**. Before executing a plan, run
`git rev-parse --short HEAD`; if the tip has moved past the commit named in the plan's header,
check whether the plan's in-scope files changed (each plan lists them) and report drift instead
of blindly applying.

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

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 017 | Artwork-mesh player backdrop (smooth animated background) | UI/Feature | M | — | none | planned (port of BitChord v1.7 `ArtworkMeshBackdrop.kt` as an opt-in player backdrop style; player UI only) |
| 019 | "Signal" in-player diagnostics sheet | Feature | S | — | none | planned (live codec/client/buffer/network/loudness sheet from the player menu; 1 Hz ticker only while open) |
| 020 | Queue skip marks + "Earlier" boundary | Feature | M | — | none | planned (mark rows jumped over by forward queue jumps; divider inside the active row; session-scoped, no persistence) |
| 021 | Stream source health + fresh-resolution fallback | Feature | M | 019 (surface) | none | planned (per-mediaId failure memory with backoff; FALLBACK state bypasses the URL cache; surfaced in the Signal sheet) |

## Recommended order (round 3, updated after 018 shipped)

1. **019** (Signal sheet) — build the diagnostics surface before 021 needs it; zero service
   surgery, all read-only.
2. **020** (skip marks) — service hook + queue UI; independent of 019, but do it after
   019 so manual verification can use the Signal sheet if needed.
3. **021** (source health) — last: hooks the error path, benefits from 019's surface and from
   confidence built by the first two.
4. **017** (artwork backdrop) — anytime; pure UI, no interaction with 019–021.

(018, per-network quality ceilings, shipped 2026-09-30 and was the smallest blast radius of
the round; see the archive below.)

## Considered and rejected for this round

- **Lyrics translation on-tap** — already shipped (`LyricsTranslationHelper` + DeepL in
  `ui/menu/LyricsMenu.kt`).
- **Volume-normalization settings** — already shipped end to end: `AudioNormalizationKey`,
  `LoudnessLevelKey` (QUIET/BALANCED/LOUD/AGGRESSIVE) with settings UI in
  `PlayerSettings.kt` and a per-track info display in `ShowMediaInfo.kt`. Nothing left to plan.
- **Playlist folders** — needs a DB schema change (AGENTS.md forbids); revisit if the core
  team approves a migration.
- **Wear OS companion** — high value but a multi-week effort; deserves its own plan round.
- **Wear/Android Auto/external-display audits** — Auto is already covered
  (`MediaLibrarySessionCallback` + settings); nothing actionable without hardware QA.

## Completed and archived

Plans are removed from `plans/` once fully implemented and verified. Record kept here; the
full plan text is in git history (delete commits reference these hashes).

| # | Plan | Implemented | Implementation |
|---|------|-------------|----------------|
| 009 | Smart auto-playlists (no schema change) | 2026-09-27 | commit `41420e57` |
| 010 | One-tap song radio from the player | 2026-09-26 | commit `2cc93084` |
| 011 | Quick-settings tiles (shuffle all) | 2026-09-26 | commit `5c7e60a5` |
| 012 | Monthly mini-Wrapped card on Home | 2026-09-26 | commit `a01085da` |
| 013 | Synced lyric line in the mini player | 2026-09-27 | commit `fbdfd244` |
| 014 | "Play next" from search results | 2026-09-26 | parity verified, no-op: search rows open `YouTubeSongMenu`/`YouTubeAlbumMenu` which already ship Play next + Add to queue, guest-gated, behind a visible trailing button |
| 015 | Monthly & yearly recap (Wrapped with range) | 2026-09-29 | commit `adaad9f5` (`wrapped/{range}` route, range chips on intro, empty state, Stats recap entry; verified on emulator with real DB data) |
| 016 | Lossless / highest-quality audio option | 2026-09-29 | commit `adaad9f5`; fork extended with `AudioQuality.LOSSLESS` + FLAC/ALAC-preferred selection (sachit1751-art/innertubex `0.5.2-lossless.3` via JitPack); app wiring, settings UI, honest ShowMediaInfo badge. Real FLAC still requires a premium-serving client/account; graceful fallback verified on emulator |
| 018 | Per-network audio quality ceilings | 2026-09-30 | commit `4a54b391`: `playback/NetworkQuality.kt` pure resolver + `NetworkClass` mapping, `WifiAudioQualityKey`/`MeteredAudioQualityKey` (unset = global fallback, `VERY_HIGH` legacy alias kept), MusicService factory wiring with resolved-quality log, "Audio quality per network" settings group with "Same as general setting" unset option, `NetworkQualityTest` (6 tests). Optional network-flip cache invalidation skipped — `NetworkConnectivityObserver` has no metered awareness; ceilings apply at next stream resolution. Builds + tests green; emulator manual checks open |

## How to update status

When you start a plan: mark it `in-progress`. When done criteria all pass: `done`, move the
plan's summary into the archive table above, and delete the plan file. If an escape hatch
fired: `blocked` and note why in the plan file. Executors update this file.
