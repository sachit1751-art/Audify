# 008 Spike findings + chosen feature — EQ profile export/import

Written 2026-09-04 against commit `7ca7a9653`. Spike deliverable; no production code changed.

## Candidate scoring (recon-grounded)

| Candidate | User value | Effort | Core-player risk | Existing infra | Score |
|-----------|-----------|--------|------------------|----------------|-------|
| A. EQ preset export/import | High (profiles are per-device today; sharing/reinstall is painful) | S–M | None (eq data layer + settings UI only) | `SavedEQProfile` is `@Serializable`; `EQProfileRepository.saveProfiles/importCustomProfile` exist; `PlaylistExporter` shows the file-share pattern | **1st** |
| B. Smart offline (Wi-Fi+charging next-N auto-download) | Med-High | M–L | High — touches `MusicService` download triggers & battery policy | `AutoDownloadOnLikeKey`, `NetworkConnectivityObserver`, `DownloadCache` | 3rd |
| C. Radio refinements (why-picked/pre-fetch) | Med | M | High — `MusicService` queue surgery | `automixItems`, `startRadioSeamlessly` | 4th |
| D. Queue sharing via LT rooms | Med | M | Medium — reuses `ListenTogetherManager.createRoom/joinRoom` but needs non-playback room mode + server changes | `ListenTogetherManager` (94KB, I/O-heavy, hard to test) | 2nd |

Decision: **A — EQ preset export & import as JSON files.**

Why: no `MusicService` risk (biggest win), the data model is already serializable, and the
repo has an exemplar for file export/sharing (`PlaylistExporter.kt` uses
`getExternalFilesDir` + share intents). Note: `EQProfileRepository.importCustomProfile(name,
parametricEQ)` already covers *ParametricEQ-text* import (AutoEQ); the missing piece is
*whole-profile* JSON export/import for user-created profiles.

## Feature spec (to be approved by a human before implementation)

**Goal:** from the EQ screen's profile list, export one or all custom profiles to a JSON file
(share sheet), and import a JSON file shared into the app (ACTION_SEND / file picker),
merging or replacing by profile id.

### Files it will touch (implementation phase, not done here)
- `app/src/main/kotlin/com/sachit/music/eq/data/Entry.kt` — verify `SavedEQProfile`
  serializes cleanly (add `@Serializable` wrapper if needed; it already is).
- `app/src/main/kotlin/com/sachit/music/eq/data/EQProfileRepository.kt` — add
  `exportProfiles(profiles): String` (Json encode) and `importProfilesJson(json): Int`
  (decode + dedupe by id + `saveProfiles`).
- `app/src/main/kotlin/com/sachit/music/ui/screens/equalizer/EQViewModel.kt` +
  `EQScreen.kt` — Export / Import actions in the profile area (follow the existing
  overflow-menu pattern used elsewhere).
- File write/share via `context.getExternalFilesDir` + `FileProvider`
  (authorities `${applicationId}.FileProvider` already declared) or `ACTION_CREATE_DOCUMENT` /
  `ACTION_OPEN_DOCUMENT` (preferred: storage-safer). Pattern source: `PlaylistExporter.kt`.
- Strings (names only, English values added later in `values/sachit_strings.xml`):
  `eq_export_profiles`, `eq_import_profiles`, `eq_exported_success`,
  `eq_import_success`, `eq_import_invalid_file`.

### Edge cases & failure handling
- Import file missing/empty/invalid JSON → toast `eq_import_invalid_file`, no crash
  (runCatching, like `importCustomProfile` callers).
- Profile id collision → overwrite on import (same id) — document in UI copy.
- Export with zero custom profiles → disable action.
- Non-custom (AutoEQ/GitHub-sourced) profiles: include in export only if explicitly
  selected; default export = custom profiles only.

### Out of scope
- Backup/restore integration (exists separately), parametric-text AutoEQ import changes,
- any DB schema change, EQ DSP changes.

### Verification (implementation phase)
- Unit test for round-trip: build profiles → `exportProfiles` → `importProfilesJson` → equal
  set (pure JVM test next to `eq` tests if any exist, else under
  `app/src/test/.../eq/`); Robolectric not required.
- Manual QA: export → clear profile → import → identical bands/preamp/name; share export to
  a second install; import a corrupt file → error toast, no crash.
- `./gradlew :app:testFossDebugUnitTest` and `:app:assembleFossDebug` green.
