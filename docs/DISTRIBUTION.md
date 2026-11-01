<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Alternative Android distribution

## Obtainium

Obtainium can track the Audify GitHub Releases page and notify users when a new release is published. The README includes an `obtainium://` deep link that pre-fills the official repository URL. Users must review and confirm the app in Obtainium before adding it.

Audify's GitHub release currently contains multiple APKs. Users must select the phone APK matching their device ABI; the universal APK is substantially larger. TV and Wear OS packages are separate apps and are not the phone package.

## IzzyOnDroid

**Not listed yet.** Fastlane metadata and screenshots are prepared in `fastlane/metadata/android/en-US/` for a future inclusion request.

The current 1.8.3 phone APKs exceed IzzyOnDroid's usual 30 MB per-APK limit: even the smallest ABI-specific phone APK is approximately 55 MiB. The current release therefore cannot be submitted as-is. A smaller, properly signed release artifact is required first. All bundled fonts, model assets, native libraries and runtime dependencies also need a documented license/compliance review before requesting inclusion.

IzzyOnDroid inclusion policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
New app inclusion guidance: https://izzyondroid.org/contributing/NewAppInclusions/

## Official F-Droid

Official F-Droid is a separate future track. It requires an FOSS-only build variant, dependency and asset compliance, and a reproducible build recipe. The normal production build must remain unaffected.
