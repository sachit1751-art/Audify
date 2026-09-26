# Plan 011 — Quick-settings tiles: Shuffle all & Recognizer

Written against commit **`192b529e`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`192b529e`, check drift in the in-scope files first.

## Goal

Two Android quick-settings tiles: **Shuffle all** (starts shuffled playback of the whole
library) and **Recognize music** (jumps into the existing recognizer flow). The recognizer
tile already exists — this plan adds the shuffle tile and extracts the shared plumbing.

## Existing infrastructure (recon at `192b529e`)

- `app/src/main/kotlin/com/sachit/music/quicksettings/MusicRecognizerTileService.kt` — the
  proven pattern: extends `TileService`, builds a `PendingIntent`.
- `AndroidManifest.xml` line 368 registers it under `.quicksettings.MusicRecognizerTileService`.
- A second manifest entry + service class is all that is needed for the new tile; the shuffle
  action itself can deep-link into the app (MainActivity intent extra) so no playback-service
  surgery is required.

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/quicksettings/ShuffleAllTileService.kt` | New tile service |
| `app/src/main/AndroidManifest.xml` | Register the new service (additive entry only) |
| `app/src/main/kotlin/com/sachit/music/MainActivity.kt` | Handle the shuffle intent extra |
| `app/src/main/res/values/sachit_strings.xml` | Tile label string |
| `app/src/main/res/drawable/` | Tile icon (reuse existing shuffle drawable if present) |

Out of scope: `MusicService.kt`, every `values-*` file, `app/build.gradle.kts`.

## Implementation steps

1. **Drift check**: confirm `MusicRecognizerTileService` still matches the manifest entry at
   `AndroidManifest.xml:368`.
2. **Create `ShuffleAllTileService`**: mirror the recognizer tile's PendingIntent structure.
   onClick → broadcast/deep-link intent with extra `EXTRA_SHUFFLE_ALL = true` targeting
   `MainActivity` with `FLAG_ACTIVITY_NEW_TASK`.
3. **MainActivity**: in `onNewIntent`/`onCreate` intent handling, when the extra is present
   and the UI is ready, invoke the same "shuffle all" path the library shuffle button uses
   (find it by searching for the existing shuffle-all click handler; reuse, don't duplicate).
   If playback isn't possible yet (service not started), defer until `playerConnection` is
   available — mirror how other deferred startup actions behave.
4. **Manifest**: add the `<service>` with `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`,
   an `intent-filter` for `android.service.quicksettings.action.QS_TILE`, and
   `android:icon` (existing shuffle drawable).
5. **Strings**: `tile_shuffle_all` (English content in `sachit_strings.xml`).
6. **Verify**:
   ```bash
   ./gradlew :app:compileFossDebugKotlin --console=plain
   ./gradlew :app:assembleFossDebug --console=plain
   ```
   Manual QA: add both tiles from the quick-settings edit sheet; tap Shuffle all with the app
   cold, warm, and in background; verify shuffled playback starts and the tile icon state
   stays passive (it is an action tile, not a toggle).

## Edge cases

- Tile tapped before first app launch completes: must not crash — defer the action.
- Doze/background restrictions: `PendingIntent` into MainActivity is the safe pattern (the
  recognizer tile proves it works on this minSdk).
- Listen Together guest: shuffle-all is a local action; keep it disabled visually if the app
  happens to be open as a guest (consistent with player gating), but never block cold-start.

## Done criteria

- Both tiles listed in the QS edit sheet and functional.
- Manifest change is purely additive; `MusicService.kt` untouched.
- Build green, `testFossDebugUnitTest` unaffected (no logic moved).

## Escape hatches

- If MainActivity's intent handling has no clean extension point at `192b529e`, report the
  found structure and propose the minimal hook before writing code.
- If a `shuffle` drawable does not exist, reuse the recognizer tile's approach of
  `Icon.createWithResource` with the closest existing material icon.
