<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Audify TV

Android TV / Google TV build of Audify: a remote-first Compose-for-TV interface
(home, library, search, settings, a full-screen now-playing view with synced
lyrics, display refresh-rate switching, spatial audio, and QR-code sign-in).

This module holds only what differs from the phone app. Everything else — the
data and playback layers, resources, native code — is built straight from
[`app/`](../app), together with the shared `:shared` and `:sharedUi` modules.

| Path | What it is |
| --- | --- |
| `src/main/java/.../ui/tv/**`, `TvActivity.kt` | The TV interface |
| `src/main/java/...` (same path as a file under `app/`) | A phone-app class TV patches; it replaces the `app/` one at build time |
| `src/main/res` | TV-only resources: banner, fonts, TV theme |
| `src/main/AndroidManifest.xml` | TV's own manifest (`LEANBACK_LAUNCHER`, `TvActivity`) |

## Download and install

Download the **AudifyTV 1.9.0 universal APK** from the [GitHub release](https://github.com/sachit1751-art/Audify/releases/download/v1.9.0/AudifyTV-1.9.0.apk). This is the Android TV / Google TV package; do not install the phone APK on a TV device. The [release page](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.0) contains the complete release inventory.

Install the APK using your preferred Android TV sideloading method. The device must allow installation from the source you use.
## Build

```bash
./gradlew :tv:assembleProdDebug
```

The module is only included when an Android SDK is available, like `:app`.
---

> **Audify** — made by [Sachit](https://github.com/sachit1751-art).
