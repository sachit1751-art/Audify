<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Alternative Android distribution

> **Status snapshot: 2026-11-04.** `1.8.7` (`v1.8.7`) is the latest published stable release, available on [GitHub Releases](https://github.com/sachit1751-art/Audify/releases/tag/v1.8.7). `1.8.9` is undergoing CI dry-run validation and is not yet published. This page records alternative-distribution readiness, not a store submission or approval.

## Obtainium

Obtainium can track the Audify GitHub Releases page and notify users when a new release is published. The README includes an `obtainium://` deep link that pre-fills the official repository URL. Users must review and confirm the app in Obtainium before adding it.

The release workflow is being standardized on four production APKs: phone universal, phone ARM64, Android TV universal, and Wear OS universal. The universal phone and TV APKs must contain the required native libraries for `armeabi-v7a`, `arm64-v8a`, and `x86_64`. Users should install the phone APK, not the TV or Wear OS package, on a phone. Confirm the actual assets on the published release page before updating installation instructions.

## IzzyOnDroid

**Not listed yet.** Fastlane metadata and screenshots are prepared in `fastlane/metadata/android/en-US/` for a future inclusion request.

The previously measured 1.8.6 phone APKs exceeded IzzyOnDroid's usual 30 MB per-APK limit. Re-measure the current release APKs before making a new eligibility claim. All bundled fonts, model assets, native libraries and runtime dependencies also need a documented license/compliance review before requesting inclusion.

IzzyOnDroid inclusion policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
New app inclusion guidance: https://izzyondroid.org/contributing/NewAppInclusions/

## Official F-Droid

Official F-Droid is a separate future track. It requires an FOSS-only build variant, dependency and asset compliance, and a reproducible build recipe. The normal production build must remain unaffected.
