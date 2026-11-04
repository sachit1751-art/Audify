# Audify 1.8.9

**Version code:** 37 (phone, Android TV, and Wear OS)

> [!CAUTION]
> **Existing Audify users on v1.8.7 or older must perform a manual reinstall.** The Android signing certificate has changed, so Android will reject v1.8.9 as an in-place update over those versions.
>
> **Before uninstalling:** download the correct APK to your device's Downloads folder. For ordinary Android phones, use [Audify 1.8.9 — Universal APK](https://github.com/sachit1751-art/Audify/releases/download/v1.8.9/Audify-1.8.9.apk) or [Audify 1.8.9 — ARM64 APK](https://github.com/sachit1751-art/Audify/releases/download/v1.8.9/Audify-1.8.9_arm64-v8a.apk). You can also use the [v1.8.9 release page](https://github.com/sachit1751-art/Audify/releases/tag/v1.8.9). Then:
> 1. Back up anything important that is stored only on this device. Uninstalling Audify can remove its local app data.
> 2. Uninstall the existing Audify v1.8.7 or older.
> 3. Open the downloaded APK from Downloads and install it. Allow installation from that browser/file manager if Android asks.
>
> **Do not press the update dialog's normal Download Now action and attempt to install over the old app.** Download the APK first, uninstall the old app, then install the downloaded file. The TV and Wear OS builds are separate APKs and are not for phones.
>
> We apologise for the disruption. This signing-certificate change is a one-time migration requirement.

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

These notes describe the release candidate, not proof of publication. The 1.8.9 dry-run must pass unit tests, production lint, all three production APK builds, package/version checks, signing-certificate comparison with the approved new Audify signing identity, and universal native-payload checks. Check [GitHub Actions](https://github.com/sachit1751-art/Audify/actions) and [GitHub Releases](https://github.com/sachit1751-art/Audify/releases) for authoritative status. Do not distribute this candidate as a published stable release until its release page exists.
