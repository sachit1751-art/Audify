# Audify 1.8.6

## What's new

- Added progressive top and bottom scroll-edge blur, with an independent Settings toggle and a longer feathering region.
- Refined Home mood-card illustrations and touch feedback animations.
- Added subtle directional fade-and-drift transitions when switching primary tabs.
- Preserved the existing reduced-motion and reduced-dynamic-blur preferences.

## Compatibility

- Phone and Wear OS: **1.8.6 (version code 34)**.
- Android TV: **1.8.6 (version code 32)**.
- Stable release; no beta suffix.
- No database schema or migration changes.

## Verification

- `:app:compileDevDebugKotlin` and `:app:testDevDebugUnitTest` passed.
- Phone, Android TV, and Wear OS production release APKs built successfully.
- All nine production APKs verified with APK Signature Scheme v2 and the same Audify Release certificate (SHA-256: `3901ab0e231ff1369f42373891d63893b224b87ff8bdc12638873acaec1ff130`).
- `git diff --check` passed.
- Device-install verification was unavailable because no Android device/emulator was connected during release preparation.

## Publication

- Published as the stable (non-prerelease) GitHub release on 2026-11-02: https://github.com/sachit1751-art/Audify/releases/tag/v1.8.6.
- All 11 release assets are uploaded: nine production APKs, BUILD-MANIFEST.txt, and SHA256SUMS.txt.

## Downloads

- **Phone:** universal APK or matching `arm64-v8a`, `armeabi-v7a`, or `x86_64` APK.
- **Android TV:** universal APK or matching architecture-specific APK.
- **Wear OS:** `AudifyWear-1.8.6.apk`.
- `BUILD-MANIFEST.txt` and `SHA256SUMS.txt` are attached for build provenance and integrity checks.
