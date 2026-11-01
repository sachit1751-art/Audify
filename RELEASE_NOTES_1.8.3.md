# Audify 1.8.3 — Release notes

> **Stable release.** This release has completed the project's release verification. The notes below document shipped behavior, validation evidence, and known limitations; limitations are disclosed rather than represented as completed features.

## Fixes and improvements

- Tightened lyrics lookup matching so recording-version qualifiers such as Live, Remix, Acoustic and Remastered are retained during LRCLIB lookup instead of being stripped from the search identity.
- Added metadata checks before accepting Bini lyric-identification candidates: contradictory title, artist or duration candidates are rejected rather than blindly trusting the first ranked result.
- Expanded lyric-clock regression coverage around sampled playback timing and synchronization state.
- Added a configurable download-storage budget with measured existing-file sizes, bounded concurrent reservations, an explicit unknown-size block, oldest-first review, and a separate confirmation before any selected file is deleted. No automatic download eviction is performed.
- Added downloaded-file access checks that distinguish file readability from actual playback verification.
- Added a listening-summary CSV export based on retained monthly per-track aggregates. The export does not fabricate per-play timestamps and documents retention limits.
- Added Spotify CSV/TSV playlist-file parsing and import flow for exports containing Track Name and Artist columns.
- Added a weekly Home listening recap with a visibility preference and dismissal behavior.
- Improved onboarding, search, Replay and settings interactions, and corrected the DevDebug instrumentation package assertion.
- Refreshed the Home experience with compact mood shortcuts, working search/cast/profile actions, and clearer personalized discovery entry points.
- Added a time-aware Home discovery shelf that selects from existing feed content using local listening-hour patterns, with an on-device visibility preference.
- Added a Profile screen with all-time listening time, total plays, distinct tracks and distinct artists.
- Added optional Pause on mute and Resume on Bluetooth playback behaviors, both disabled by default and controlled from settings.
- Replaced the legacy Audify logo with the seven-bar Frequency Mark across app branding, launcher icons, notifications and Wear OS assets.
- Refined player, library and Home presentation, including compact mood chips, empty-state spacing and shared Compose motion tokens.
- Added the WorkManager dependency required by the TV module's shared download worker source.
- Added narrowly scoped AndroidX Media3 `UnstableApi` opt-ins, eliminating the 112 Media3 opt-in lint errors without project-wide suppression.
- Deferred InnerTubeX/EJS warm-up until after the first rendered frame, with a one-shot gate and a 2-second delay; the resolver work runs on its IO scope.
- Annotated the backdrop runtime-shader API boundary for Android 13, removing the corresponding API-level lint error.
- Replaced 10 StateFlow reads performed during Compose composition with lifecycle-aware collection and removed one Flow transformation from composition.
- Replaced 12 additional Compose-time resource lookups in account alerts, source settings and party-server editing with composition-safe resource resolution.
- Fixed the remaining time-aware Home shelf Compose resource lookups by resolving its labels with `stringResource` in composition before the producer coroutine uses them.
- Marked eleven strings with no definitions in any localized resource directory as explicitly non-translatable in the default resource files; no translated resource files were edited.

## Validation evidence

- DevDebug Kotlin compilation and APK assembly succeeded with the installed JDK 21.
- Fresh `:app:lintDevDebug` passed: 0 errors, 634 warnings and 5 hints.
- Fresh `:app:testDevDebugUnitTest`: 1,132 tests, 0 failures, 0 errors, 1 skipped.
- Fresh `:app:assembleDevDebug` succeeded after the remaining lint fixes.
- Latest `:sharedUi:jvmTest`: 43 tests, 0 failures.
- Clean `:shared:jvmTest --rerun-tasks`: 98 tests, 0 failures.
- `:app:assembleDevDebug` succeeded after the Media3 and first-draw changes.
- Fresh Android 16 emulator run of `:app:connectedDevDebugAndroidTest`: 6 tests, 0 failures.
- Three cold app launches on the Android 16 emulator completed without ANR or fatal exception. `am start -W` reported 6.1–7.2 seconds to activity launch; ActivityTaskManager reported 8.4–9.4 seconds to fully drawn. `dumpsys activity lastanr` reported no ANR since emulator boot.
- Earlier targeted lyric, download-store, storage-budget, offline-verification, CSV and instrumentation tests passed in their respective runs.
- Emulator smoke testing exercised onboarding, Home, Explore, Library, Search, Settings/Sources, download-quality selection, Adele search, playback and the lyrics panel. Media3 reached `PLAYING` at 1× speed.
- `git diff --check` passed after the latest source edits.
- Latest logo follow-up: `:app:compileDevDebugKotlin :wearos:compileDevDebugKotlin` succeeded after updating the phone and Wear OS Frequency Mark assets.
- Production release build: `:app:assembleProdRelease :wearos:assembleProdRelease :tv:assembleProdRelease` succeeded.
- Packaged nine production APKs under `dist/v1.8.3/`: phone universal + arm64-v8a + armeabi-v7a + x86_64; Wear OS companion; Android TV universal + arm64-v8a + armeabi-v7a + x86_64.
- All nine APKs passed `apksigner verify`; manifest metadata reports phone/Wear OS 1.8.3 (code 31) and TV 1.8.3 (code 29).
- All nine APKs use the same SHA-256 signing-certificate fingerprint as the existing 1.8.2 release artifacts. The phone universal APK also contains the separately built Wear OS companion at `assets/wear/AudifyWear.apk`.
- Generated `dist/v1.8.3/SHA256SUMS.txt` for the nine APKs.
- Release builds emit existing R8 Kotlin-metadata parsing warnings because some dependencies were compiled with newer Kotlin metadata than the bundled R8 understands; Gradle nevertheless completed successfully. This warning remains a release-toolchain follow-up.

## Compatibility and release integrity

- No Room schema or migration changes are included.
- Phone / Wear OS `versionCode` / `versionName`: 31 / 1.8.3.
- Android TV `versionCode` / `versionName`: 29 / 1.8.3.
- No translated resource files were edited.
- No signing credentials are included in this change set.
