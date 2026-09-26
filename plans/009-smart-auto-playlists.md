# Plan 009 — Smart auto-playlists (no DB schema change)

Written against commit **`192b529e`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`192b529e`, check whether the in-scope files changed and report drift instead of blindly applying.

## Goal

Auto-updating "smart" playlists computed from existing Room tables — e.g. **Most played this
month**, **On repeat**, **Recently added**, **Never played** — surfaced in the library without
adding any column or table.

## Why this is safe under the project constraints

- **No DB schema change**: all data already exists. `playCount` (see
  `app/src/main/kotlin/com/sachit/music/db/entities/PlayCountEntity.kt`: `song`, `year`,
  `month`, `count`) is exactly what `WrappedManager` uses for year stats, and
  `DatabaseDao.kt` lines 659–662 already aggregate per-song and per-month counts.
- **No version bump** (`app/build.gradle.kts` is out of scope entirely).
- **Strings only** in `app/src/main/res/values/sachit_strings.xml` (English default).

## In-scope files (recon at `192b529e`)

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/db/DatabaseDao.kt` | Add read-only `@Query` methods (additive; no entity changes) |
| `app/src/main/kotlin/com/sachit/music/viewmodels/` | One new ViewModel (or extend the library VM) exposing the 4 lists |
| `app/src/main/kotlin/com/sachit/music/ui/screens/` | UI: a "Smart" shelf/section in the Library screen |
| `app/src/main/res/values/sachit_strings.xml` | New strings (names listed below) |
| `app/src/test/kotlin/com/sachit/music/` | JVM test for the list-computation logic |

Out of scope: `MusicService.kt`, `MusicDatabase.kt`, every `values-*` file, `app/build.gradle.kts`.

## Implementation steps

1. **Drift check**: `git status` clean-ish; confirm `DatabaseDao.kt` still has the
   `playCount` aggregation queries at ~line 659.
2. **Add DAO queries** (READ-ONLY, additive):
   - top songs by summed `count` within the current `(year, month)` window,
   - songs with `count` above a threshold in the last N months ("On repeat"),
   - recently inserted songs (existing `song` table already has a rowid/ordering),
   - songs present in `song` but absent from `playCount` ("Never played" — `LEFT JOIN` or `NOT EXISTS`).
3. **Define the four lists as pure functions** over the query results (thresholds, limits,
   month-window computation) in a small class so they are unit-testable without Robolectric.
   Follow the `DevicePerformanceTest.kt` style for the pure tests.
4. **UI**: add a horizontally scrolling "Smart playlists" shelf to the Library screen that
   opens a standard song-list screen (reuse the existing local playlist detail screen with a
   synthetic playlist object — do NOT create new Room entities for the lists).
5. **Strings** to add (names only; write the English content in `sachit_strings.xml`):
   `smart_playlists_title`, `smart_most_played_month`, `smart_on_repeat`,
   `smart_recently_added`, `smart_never_played`.
6. **Verify**:
   ```bash
   export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"   # fallback: ~/.gradle/jdks toolchain
   ./gradlew :app:compileFossDebugKotlin --console=plain
   ./gradlew :app:testFossDebugUnitTest --console=plain
   ```
   then `./gradlew :app:assembleFossDebug` and manual QA: install the APK from
   `app/build/outputs/apk/foss/debug/app-foss-debug.apk`, open Library, tap each smart list,
   play a song, confirm "Most played this month" updates after the play count increments.

## Edge cases

- Empty library / no play counts: every list must render an empty state, never crash.
- Month rollover mid-session: compute the window at query time, don't cache it in a field.
- Guest (signed-out) mode: play counts are local, so all four lists must still work.

## Done criteria

- Four smart lists visible in Library, backed only by additive DAO queries and pure logic.
- New unit tests pass; full `testFossDebugUnitTest` green.
- `git diff` shows zero changes to `MusicDatabase.kt`, schemas/, `app/build.gradle.kts`.

## Escape hatches

- If the DAO additions force an entity change → STOP, report, and re-scope (constraint).
- If the library screen structure changed drastically since `192b529e`, adapt the UI step and
  note the drift in the final report.
