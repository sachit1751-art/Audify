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
| 009 | Smart auto-playlists (no schema change) | Feature | M | — | none | proposed |
| 010 | One-tap song radio from the player | Feature | S | — | none | proposed |
| 011 | Quick-settings tiles (shuffle all) | Feature | S | — | Manifest addition | proposed |
| 012 | Monthly mini-Wrapped card on Home | Feature | S–M | — | none | proposed |

## Recommended order

1. **010** (song radio) — smallest, user-visible immediately, zero service surgery.
2. **011** (tiles) — small, independent, touches only the manifest + MainActivity hook.
3. **012** (monthly card) — reuses Wrapped data; do after 010/011 to keep diffs isolated.
4. **009** (smart playlists) — largest surface (DAO + library UI); run last.

Plans 010/011/012 touch disjoint files and could run in parallel branches; 009 shares the
library screen with none of them but touches `DatabaseDao.kt`, so keep it sequential.

## Considered and rejected for this round

- **Lyrics translation on-tap** — already shipped (`LyricsTranslationHelper` + DeepL in
  `ui/menu/LyricsMenu.kt`).
- **Playlist folders** — needs a DB schema change (AGENTS.md forbids); revisit if the core
  team approves a migration.
- **Wear OS companion** — high value but a multi-week effort; deserves its own plan round.
- **Volume-normalization settings surface** — normalization already runs with
  `loudnessDb`/`perceptualLoudnessDb`; the remaining work is settings UX, parked until a
  human confirms the target-LUFS choices.

## How to update status

When you start a plan: mark it `in-progress`. When done criteria all pass: `done`. If an
escape hatch fired: `blocked` and note why in the plan file. Executors update this table.
