# Audify 1.8.9

**Version code:** 37 (phone, Android TV, and Wear OS)

This release candidate focuses on reliability, playback/library interface polish, and production build validation.

## Highlights

- Fixed layout and scrolling issues across Statistics, playlists, and bottom controls.
- Improved track artwork consistency across Home and Search.
- Improved Home recommendation diversity and fallback continuation.
- Hardened the translation-language dialog for constrained screens, landscape, and keyboard layouts.
- Improved playlist creation reliability with durable local persistence and remote-failure fallback.
- Improved long-title and subtitle handling in Keep Listening.
- Restored the previous Audify app icon and matching brand marks.

## Production APKs

The intended production asset set contains four APKs: phone universal, phone ARM64, Android TV universal, and Wear OS universal. The phone and TV universal APKs are checked for required native libraries across `armeabi-v7a`, `arm64-v8a`, and `x86_64`.

## Verification status

These notes describe the release candidate, not proof of publication. The 1.8.9 dry-run must pass unit tests, production lint, all three production APK builds, package/version checks, signing-certificate comparison with the published 1.8.7 release, and universal native-payload checks. Check [GitHub Actions](https://github.com/sachit1751-art/Audify/actions) and [GitHub Releases](https://github.com/sachit1751-art/Audify/releases) for authoritative status. Do not distribute this candidate as a published stable release until its release page exists.
