# Audify 1.8.8

**Version code:** 36 (phone and Wear OS) · 34 (Android TV)

This update focuses on interface reliability, more predictable playlist creation, and better adaptive layouts across device sizes.

## Highlights

- Improved bottom-bar measurement and content insets so scrollable content remains accessible above floating player controls.
- Refined artwork thumbnail presentation and playlist-header fade behaviour.
- Improved time-aware Home recommendations while preserving the Recently Played experience.
- Made dialogs adapt to available screen height and scroll when their content exceeds the viewport.
- Added a local playlist fallback when remote YouTube Music playlist creation is unavailable, with clear feedback about where the playlist was saved.
- Improved responsive Home shelf layout for wider screens.
- Added regression coverage for recommendation behaviour, playlist creation fallback, and adaptive layout calculations.

## Verification

The Android GitHub Actions workflows run the app and shared test suites, Android lint, and phone, Android TV, and Wear OS APK builds. The release workflow additionally builds the `prodRelease` variants and publishes them as workflow artifacts. See the workflow run for the authoritative CI result.

Playlist creation may require a working YouTube Music connection for remote account creation. If that operation fails, Audify now keeps the playlist on the device and reports that it is local-only.
