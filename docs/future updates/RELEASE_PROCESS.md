<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Production release process

**Last reviewed:** 2026-11-07
**Workflow:** [Audify Release](../../.github/workflows/android.yml)
**Latest published release:** [Audify 1.9.0](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.0)

This document records the published release inventory and the checks required for future production releases. The GitHub Release and its attached assets are authoritative for publication status; do not infer publication from a local build or a successful workflow run.

## Current release snapshot

- Release: `1.9.0`
- Tag: [`v1.9.0`](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.0)
- Publication state: **published** (2026-11-07)
- Target branch recorded by GitHub: `main`
- Download page: <https://github.com/sachit1751-art/Audify/releases/tag/v1.9.0>

## Published assets

The release currently contains the following downloadable assets:

| Platform | Asset | Purpose |
| --- | --- | --- |
| Android phone | `Audify-prod-universal-release.apk` | Universal phone/tablet APK |
| Android phone | `Audify-prod-arm64-v8a-release.apk` | ARM64 phone APK |
| Android phone | `Audify-prod-armeabi-v7a-release.apk` | ARMv7 phone APK |
| Android phone | `Audify-prod-x86_64-release.apk` | x86_64 phone APK |
| Android TV | `AudifyTV-1.9.0.apk` | Universal Android TV / Google TV APK |
| Android TV | `tv-prod-universal-release.apk` | Existing universal TV asset; retained on the release |
| Wear OS | `AudifyWear-prod-universal-release.apk` | Universal Wear OS companion APK |
| Windows | `Audify-Setup.exe` | Windows x64 desktop installer |

The TV APK is a separate TV-targeted application, not a phone build. The two TV-named assets are both attached; use the versioned `AudifyTV-1.9.0.apk` link in user-facing installation instructions. The Windows installer is intentionally unsigned; do not describe it as Authenticode-signed or trusted by Windows publisher verification. No macOS or Linux desktop installer is included in this release.

## Release notes and download links

The published release notes must match the actual attached asset names. Keep the download section explicit about phone universal and ABI-specific APKs, the TV APK, Wear OS APK, and Windows installer. Do not list an asset unless its upload state is `uploaded` in the GitHub Release API. The repository [README](../../README.md) and platform guides link to the versioned 1.9.0 assets.

## Validation requirements for future releases

Before publishing a future production release:

1. Confirm the release tag matches the version configured in the Android and desktop projects.
2. Run the applicable unit/JVM tests, lint, and production builds for phone, TV, and Wear OS; record skipped or unavailable gates rather than implying they passed.
3. Verify APK package IDs, version names/codes, signatures, and required native libraries. Verify desktop installer metadata and launch behavior separately.
4. Upload the intended artifacts, then query the exact GitHub Release and confirm every expected asset has state `uploaded`, the expected size, and the expected filename.
5. Review the published release body, README links, and platform guides against the final asset inventory.

A draft is not a published release. A successful build is not proof of an uploaded asset. An uploaded asset is not proof of a successful real-device installation. Record each state separately.

## Signing and publication safeguards

- Keep keystore files, signing credentials, and CI secret values private; never print or commit them.
- Verify Android APK signing identity against the intended distribution identity before claiming a build is suitable for public distribution.
- Windows Authenticode signing is not required for the current 1.9.0 installer. Do not substitute a self-signed certificate and call it trusted.
- Do not publish a draft or create a new release tag without explicit authorization.
- Keep unrelated working-tree changes out of release commits.

## Updating this document

After each release attempt, refresh the tag, publication state, workflow run, exact asset inventory, validation results, and known limitations. Update the root README, platform guides, and version-specific release notes in the same documentation pass. Preserve historical facts; never rewrite a failed or incomplete gate as successful.
