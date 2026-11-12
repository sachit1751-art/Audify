# Audify 1.9.3

Audify 1.9.3 focuses on a more responsive, personal playback experience, with Home customization and controls adapted to the capabilities of each supported Android form factor.

## What's changed

- **Play Next by swiping:** Move a track directly after the currently playing track from supported touch-based track lists.
- **Elastic refresh feedback:** Home, Explore and Library refresh interactions provide more responsive pull feedback.
- **Customizable Home:** Reorder, hide and restore Home rows on Android phone/tablet and Android TV. Hidden rows are excluded from client-side processing and presentation, and eligible dedicated shelf requests are skipped. The provider's aggregated personalized Home response can still contain hidden shelf data.
- **Smoother browsing:** Improve list stability and reduce unnecessary row work during scrolling.
- **Artwork preference:** Prefer static cover artwork over motion artwork where the player supports animated artwork.
- **Lyrics navigation:** Selecting a different track from the lyrics view returns to Now Playing for the selected track.
- **Playback feedback:** Previous and Next controls use short press feedback animations, respecting reduced-motion settings.
- **Theme Studio:** Customize the player palette with a live preview.
- **Android TV:** Add a D-pad-accessible Home layout editor and retain the last successful Home shelves while a refresh is loading.
- **Wear OS:** Add animated press feedback to the watch's skip controls while respecting the system animator setting.

## Android downloads

The following production APKs will be attached after the release workflow builds them and verifies their package IDs, version metadata, native payloads and approved signing certificate:

- **Phone / tablet (universal):** `Audify-1.9.3.apk`.
- **Phone / tablet (ARM64):** `Audify-1.9.3_arm64-v8a.apk`.
- **Android TV / Google TV (universal):** `AudifyTV-1.9.3.apk`.
- **Wear OS (universal):** `AudifyWear-1.9.3.apk`.
- **Checksums:** `SHA256SUMS.txt` will contain SHA-256 hashes for the attached APKs.

## Compatibility and verification

- Android version: **1.9.3** (version code **42**).
- Production assets must use the existing approved Audify signing identity (certificate SHA-256: `4bde8155328a9a8659ce6b75d1a0df2e4811c0ccdc555a52ccb3df2a4be94e21`).
- The universal phone and TV packages must contain the supported ARMv7, ARM64 and x86_64 native-library payloads.
- Package IDs, version metadata, signatures and APK integrity are checked by the release workflow before assets are attached.
- This is an **unpublished draft**. It will remain a draft for manual review; creating it does not publish the release.

## Platform scope

- **Android Auto:** Retains its driver-safe MediaLibrary browse and playback model. Phone-only gestures, Home editing and decorative interactions are not exposed in the car surface.
- **Windows, macOS and Linux desktop:** No desktop installer is included in this Android release. Desktop parity work is tracked separately and is not claimed as complete here.
- **Device validation:** Successful CI builds and unit tests do not replace D-pad, watch-device, Android Auto host or cross-device visual verification.

## Feedback and bug reports

- **Bug reports:** https://github.com/sachit1751-art/Audify/issues/new?template=bug_report.yml
- **General tester feedback:** https://github.com/sachit1751-art/Audify/issues/new?template=tester-feedback.yml
- **All releases:** https://github.com/sachit1751-art/Audify/releases
