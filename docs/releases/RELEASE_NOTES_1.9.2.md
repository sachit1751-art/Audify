# Audify 1.9.2

Audify 1.9.2 is an Android-focused maintenance release that improves adaptive navigation and layout behavior across phones, tablets, and landscape windows, while strengthening the Listen Together service path.

## What's changed

- **Adaptive navigation:** Kept the floating bottom navigation anchored and usable across portrait, landscape, tablet, and short or wide window configurations.
- **Window-aware player settings:** Made the full-bleed artwork option available only when the current player layout can apply it, avoiding a setting that has no effect on tablet or landscape layouts.
- **Explore layout:** Adjusted mood-card column calculation to respond to available width on narrow screens.
- **Scrollable settings pages:** Corrected content-inset and scrolling order across Settings, Account & Scrobbling, Discord, Equalizer, Listen Together, Sources, and Wear OS settings so the inset stays with the viewport while page content scrolls.
- **Listen Together reliability:** Added the Cloudflare Durable Objects backend, moved client service configuration to the deployed Cloudflare endpoint, and restored invite-code and participant-presence behavior with integration coverage.

## Android downloads

- **Phone / tablet (universal):** `Audify-1.9.2.apk`.
- **Phone / tablet (ARM64):** `Audify-1.9.2_arm64-v8a.apk`.
- **Phone / tablet (ARMv7):** `Audify-1.9.2_armeabi-v7a.apk`.
- **Phone / tablet (x86_64):** `Audify-1.9.2_x86_64.apk`.
- **Android TV / Google TV (universal):** `AudifyTV-1.9.2.apk`.
- **Wear OS (universal):** `AudifyWear-1.9.2.apk`.
- **Checksums:** `SHA256SUMS.txt` contains SHA-256 hashes for all six APKs.

## Compatibility and verification

- Android version: **1.9.2** (version code **41**).
- Phone, Android TV, and Wear OS APKs are signed with the approved Audify Android release identity (certificate SHA-256: `4bde8155328a9a8659ce6b75d1a0df2e4811c0ccdc555a52ccb3df2a4be94e21`).
- The universal phone and TV packages include the supported ARMv7, ARM64, and x86_64 native-library payloads.
- Package IDs, version metadata, signatures, and APK integrity are verified before upload.
- This is an **unpublished draft**. Downloads will remain unavailable until the release is explicitly published.

## Desktop

No Windows, macOS, or Linux installer is included in this Android release draft.

## Feedback and bug reports

- **Bug reports:** https://github.com/sachit1751-art/Audify/issues/new?template=bug_report.yml
- **General tester feedback:** https://github.com/sachit1751-art/Audify/issues/new?template=tester-feedback.yml
- **All releases:** https://github.com/sachit1751-art/Audify/releases
