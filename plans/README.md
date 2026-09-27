# Audify improvement plans

Feature-planning track started **2026-09-26**, written against commit **`192b529e`**.
Each plan is self-contained for an executor with zero session context. Before executing a
plan, run `git rev-parse --short HEAD`; if the tip has moved past `192b529e`, check whether
the plan's in-scope files changed (each plan lists them) and report drift instead of blindly
applying.

Scope rules that apply to **every** plan (from the repo's AGENTS.md and project rules):

- Never bump the app version (`versionCode` / `versionName` in `app/build.gradle.kts`).
- Never touch the database schema or Room migrations.
- String edits go only in `app/src/main/res/values/sachit_strings.xml` (English). Never
  edit translated `sachit_strings.xml` / `strings.xml` files in `values-*`.
- No commits, pushes, or merges unless a human explicitly authorized them per-plan.
- No changes to README/markdown files **except** inside `plans/`.
- Build/verify with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-*` is auto-provisioned if no
  standalone JDK 21 is installed):

  ```bash
  ./gradlew :app:compileFossDebugKotlin --console=plain
  ./gradlew :app:testFossDebugUnitTest --console=plain   # app JVM/Robolectric tests
  ./gradlew :app:assembleFossDebug --console=plain
  ```

## Index

| # | Plan | Category | Effort | Depends on | Requires human gate | Status |
|---|------|----------|--------|------------|---------------------|--------|
| 009 | Smart auto-playlists (no schema change) | Feature | M | — | none | done (implemented 41420e57, 2026-09-27) |
| 010 | One-tap song radio from the player | Feature | S | — | none | done (implemented 2cc93084, 2026-09-26) |
| 011 | Quick-settings tiles (shuffle all) | Feature | S | — | Manifest addition | done (implemented 5c7e60a5, 2026-09-26) |
| 012 | Monthly mini-Wrapped card on Home | Feature | S–M | — | none | done (implemented a01085da, 2026-09-26) |
| 013 | Synced lyric line in the mini player | Feature | M | — | none | done (implemented fbdfd244, 2026-09-27) |
| 014 | "Play next" from search results | Feature | S | — | none | done (parity verified, no-op 2026-09-26: search rows open YouTubeSongMenu/YouTubeAlbumMenu which already ship Play next + Add to queue, guest-gated, behind a visible trailing button) |

## Recommended order

1. **010** (song radio) — smallest, user-visible immediately, zero service surgery.
2. **014** (play next from search) — small, mostly a parity check on existing menus.
3. **011** (tiles) — small, independent, touches only the manifest + MainActivity hook.
4. **012** (monthly card) — reuses Wrapped data; keeps diffs isolated from 013.
5. **013** (mini-player lyric line) — the lyrics plumbing needs care; isolated from 012.
6. **009** (smart playlists) — largest surface (DAO + library UI); run last.

Plans 010/011/014 are fully disjoint and could run in parallel branches; 012 and 013 both
touch the mini player / Home area but not the same files — sequence them. 009 touches
`DatabaseDao.kt`, so keep it sequential regardless.

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

## How to update status

When you start a plan: mark it `in-progress`. When done criteria all pass: `done`. If an
escape hatch fired: `blocked` and note why in the plan file. Executors update this table.
