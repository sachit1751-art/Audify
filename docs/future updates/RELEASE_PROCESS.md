<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Production release process

**Last reviewed:** 2026-11-21
**Workflow:** [Audify Release](../../.github/workflows/android.yml)
**Latest published stable release:** [Audify 1.9.4](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.4)

This document records the published release inventory and the checks required for future production releases. The GitHub Release and its attached assets are authoritative for publication status; do not infer publication from a local build or a successful workflow run.

## Current release snapshot

- Stable release: `1.9.4`
- Tag: [`v1.9.4`](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.4)
- Publication state: **published** (2026-11-20)
- Draft: `1.9.5` is still unpublished. Its assets are uploaded to a draft release entry and are not public stable downloads.
- Download page: <https://github.com/sachit1751-art/Audify/releases/tag/v1.9.4>

## Published assets

The release currently contains the following downloadable assets:

| Platform | Asset | Purpose |
| --- | --- | --- |
| Android phone | `Audify-prod-universal-release.apk` | Universal phone/tablet APK |
| Android phone | `Audify-prod-arm64-v8a-release.apk` | ARM64 phone APK |
| Android phone | `Audify-prod-armeabi-v7a-release.apk` | ARMv7 phone APK |
| Android phone | `Audify-prod-x86_64-release.apk` | x86_64 phone APK |
| Android TV | `tv-prod-universal-release.apk` | Universal Android TV / Google TV APK |
| Android TV | `tv-prod-arm64-v8a-release.apk` | ARM64 Android TV APK |
| Android TV | `tv-prod-armeabi-v7a-release.apk` | ARMv7 Android TV APK |
| Android TV | `tv-prod-x86_64-release.apk` | x86_64 Android TV APK |
| Wear OS | `AudifyWear-prod-universal-release.apk` | Universal Wear OS companion APK |
| Wear OS | `AudifyWear-prod-arm64-v8a-release.apk` | ARM64 Wear OS companion APK |
| Wear OS | `AudifyWear-prod-armeabi-v7a-release.apk` | ARMv7 Wear OS companion APK |
| Wear OS | `AudifyWear-prod-x86_64-release.apk` | x86_64 Wear OS companion APK |

The TV APK is a separate TV-targeted application, not a phone build. No Windows, macOS, or Linux desktop installer is attached to this release.

## Release notes and download links

The published release notes must match the actual attached asset names. Do not list an asset unless its upload state is `uploaded` in the GitHub Release API. The repository [README](../../README.md) uses GitHub's `releases/latest` redirect so stable download links advance automatically. The [changelog](../../CHANGELOG.md) distinguishes published releases from drafts.

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
- Do not describe a desktop installer as available unless it is attached to a published release and its signing status has been verified.
- Do not publish a draft or create a new release tag without explicit authorization.
- Keep unrelated working-tree changes out of release commits.

## Updating this document

After each release attempt, refresh the tag, publication state, workflow run, exact asset inventory, validation results, and known limitations. Update the root README, platform guides, and version-specific release notes in the same documentation pass. Preserve historical facts; never rewrite a failed or incomplete gate as successful.
