# Audify feature parity after the Audify UI migration

## Goal

Keep the new player, Home/Explore redesign, podcast integration, and migrated playback engine while restoring Audify-owned screens, settings, navigation, and behaviour that were reduced or disconnected during the UI migration. This is an additive compatibility pass, not a rollback.

## Source of truth

- Original Audify implementation and user preferences: `D:\Audify_migration_backup_20261113`.
- Migrated implementation to retain: current `D:\Audify` working tree.
- Copied Audify source: `D:\copy paste` (read-only; do not edit).

## Audited differences and status

### Restored in this pass

- **Onboarding:** restore the original four-stage Audify flow: welcome/brand introduction, profile name, optional Google sign-in, and music setup. Keep the current onboarding completion preference and use the signed-in account/nickname already available to the app.
- **Replay:** restore Audify's original Replay page, including ranked song/artist/album/genre charts, chart row actions, loading skeletons, listening habits, share action, and CSV save/share actions. Keep the newer month/year anchor navigation from the migrated implementation.
- **Replay resources:** restore the original CSV export/share strings.
- **Existing settings data:** merge `audify_settings` into `bitchord_settings` before defaults are seeded. Existing values in the destination win; missing legacy values are copied; the old preference file is not deleted.
- **Renamed preferences:** migrate known renamed keys (`font_choice`, `prefer_music_only`, `home_layout_v1`, `lyrics_liquid_flow`, `lyrics_offset_ms`, `progressive_scroll_edge_blur`, and `lightweight_liquid_glass`) to their current equivalents when the destination key is absent.
- **Startup stores:** restore initialization of `SmartPlaylistStore`, `LocalLibraryStore`, and `AutomaticBackupScheduler`, in addition to the migrated stores.
- **Settings categories:** verified against the original backup. The original 11 categories remain present and in the same order: Downloads, Playback, Content feed, Appearance, Performance, Local music, Storage, Your data, Miscellaneous, Language, and Advanced. Keep new migrated controls inside these groups rather than replacing the category model.
- **Navigation:** restore the Audify primary tabs Home, Explore, Library, Settings. Search remains accessible from the Home search action; Listen Together remains a destination, not a permanent replacement tab.
- **Audify identity:** restore the Audify wordmark in the Home/player shelves, the Explore tab label, the `audify://party/...` invite scheme, and the `Music/Audify` download destination.

### Preserved but requiring a separate UI/behaviour parity pass

The migration copies all original preference entries without deleting unknown keys. These legacy preferences are retained in the destination store, but not all currently have an active flow/control in the migrated UI:

- Background style selection (the custom background image URI and image picker are restored).
- Launcher icon selection.
- Legacy skip-segment configuration.
- Pause-on-mute and resume-on-Bluetooth preferences.
- Strict add-on stream matching and add-on quality preference.
- Weekly recap visibility/seen-week and time-aware Home shelf preferences.
- Explicit-content filtering and Home Recents view preference.
- Legacy lossless-audio preference; do not blindly convert it to USB bit-perfect mode because those controls have different semantics.

These items must be reintroduced individually with compatible defaults and validated against the current playback/theme architecture. Do not delete their old keys or reinterpret them as unrelated settings.

## Compatibility rules

1. Never clear app data, uninstall the user's app, or delete the legacy preference file as part of migration.
2. When both preference stores contain a key, the current destination value wins.
3. Preserve the original application ID, signing configuration, playlist storage, listening history, and authentication stores.
4. Do not reset onboarding for users whose old `onboarding_complete` flag is true.
5. Keep new migrated UI and playback features; restore old Audify behaviour by merging components and state, not replacing whole modules.

## Verification checklist

- [x] Original onboarding source restored and Kotlin compilation succeeds.
- [x] Original Replay source restored, including CSV actions; Kotlin compilation succeeds.
- [x] New Replay month/year anchor controls retained.
- [x] Preference migration runs before first-install defaults and leaves source preferences untouched.
- [x] Original settings category list compared with current list; 11 categories and order match.
- [x] Original startup stores and automatic backup scheduler initialized again.
- [x] Build a fresh APK from the final tree (`1.9.3`, version code `42`).
- [x] Install with `adb install -r` without uninstalling or clearing data.
- [x] Launch the app on `audify_migration`; process remains alive and no fatal exception was logged.
- [x] Inspect Home and Settings on emulator; Home shows the Audify wordmark and Home / Explore / Library / Settings navigation.
- [x] Run unit tests: 1,212 passed, 0 failed, 1 skipped.
- [ ] Verify the restored Replay page interactively on emulator.
- [ ] Verify the first-run onboarding visually on a clean test profile. The existing emulator has `onboarding_complete=true`, so onboarding correctly stayed hidden for this existing install.
- [ ] Exercise preference migration from an actual `audify_settings` file. The current emulator only has `bitchord_settings`, so the migration path is code-reviewed but not device-tested against a legacy file.
- [ ] Complete the remaining legacy settings UI/behaviour parity items above.

## Custom appearance restoration and identity audit (2026-11-14)

- [x] Add 18 built-in theme palettes and persist the selected palette.
- [x] Add user-created colour themes, persistent custom theme storage, and Audify JSON theme import.
- [x] Restore the custom background image picker, persisted URI permission, and app-root background rendering without changing player layout code.
- [x] Apply selected palette colours and surface styles through the current app-wide Material theme; Classic retains the existing accent-colour behaviour.
- [x] Rename branded theme/icon identifiers, native library targets/load names, JNI export namespace, and internal app URI identifiers to Audify.
- [x] Scan source, resources, documentation, and configuration for the removed brand token: zero matches. Generated build/cache directories are excluded from this source audit.
- [ ] Build/test/install this latest appearance + identity pass. The latest Gradle attempt stopped in Kotlin analysis of `PlaybackService.kt` because the incremental compiler could not find the generated `OutputNegotiator.class`; this is not a verified successful APK build.
- [ ] Verify custom palette switching and image backgrounds on the emulator.

## Scope guard

No broad revert, force push, signing-key replacement, app-data wipe, or modification of the copied source/backup directories.
