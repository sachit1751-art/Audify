<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Audify 1.8.4 — Release notes

> **Stable release.** This release removes the unfinished alternate launcher-icon picker and its unverified variant artwork. Audify uses one consistent default launcher icon until a complete, high-resolution icon set is ready.

## What's new

- Added a saved app-font preference with Audify, System and Serif choices.
- Improved adaptive typography and window-width handling so the interface responds to the actual app window.
- Refined Home discovery with compact mood shortcuts, clearer Quick Picks layouts, profile artwork, and Listen Together entry points.
- Improved Replay cards and responsive presentation across available window widths.
- Made the On Device library section visible even when it is empty, with explanatory copy.
- Improved library, search, settings and shared loading-state presentation.
- Improved release update selection so the updater prefers the device's supported ABI APK and uses the universal APK as a fallback.
- Added regression tests for ABI selection and version comparison in the in-app updater.
- Removed the unfinished 13-option launcher icon picker, its manifest aliases, unused artwork and translated labels. The default launcher icon remains unchanged.
- Updated distribution, contribution and product documentation.

## Release verification

Verified on 2026-11-02 with Temurin JDK 21:

- `:app:testDevDebugUnitTest` — passed.
- `:sharedUi:jvmTest` — passed.
- `:shared:jvmTest` — passed.
- `:app:lintProdRelease`, `:tv:lintProdRelease`, `:wearos:lintProdRelease` — zero errors. Existing non-blocking warnings remain (11 phone, 25 TV, 12 Wear OS).
- `:app:assembleProdRelease`, `:tv:assembleProdRelease`, `:wearos:assembleProdRelease` — passed.
- All nine APKs were checked for package/version metadata and verified signatures. All use the same release signing certificate (SHA-256 `3901ab0e231ff1369f42373891d63893b224b87ff8bdc12638873acaec1ff130`).

## Compatibility and release integrity

- Phone and Wear OS version: **1.8.4 (version code 32)**.
- Android TV version: **1.8.4 (version code 30)**.
- Stable release; no beta suffix.
- No Room schema or migration changes are included.
- The app uses its default launcher icon; alternate icon selection is no longer exposed.
- Signing credentials are not included in the repository.

## Known limitations

- Lint reports zero errors, but non-blocking warnings remain in the phone, TV and Wear OS modules.
- Availability of online catalog, lyrics and external-source behavior depends on upstream services and configured providers.
- A successful build does not replace physical-device, TalkBack and long-duration playback testing.
