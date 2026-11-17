<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

<p align="center">
  <img src="audify_banner.svg" alt="Audify — discover, listen and make music yours" width="100%">
</p>

<h1 align="center">Your music. Your space. Your way.</h1>

<p align="center">
  An open-source music player for Android, Android TV, Wear OS and desktop.<br>
  Discover music, build your library, and make every listening session your own.
</p>

<p align="center">
  <a href="https://github.com/sachit1751-art/Audify/releases/latest"><img src="https://img.shields.io/github/v/release/sachit1751-art/Audify?label=latest%20stable" alt="Latest stable release"></a>
  <a href="https://github.com/sachit1751-art/Audify/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sachit1751-art/Audify" alt="GPL-3.0 license"></a>
  <a href="https://github.com/sachit1751-art/Audify/releases"><img src="https://img.shields.io/github/downloads/sachit1751-art/Audify/total?label=downloads" alt="GitHub release downloads"></a>
</p>

<p align="center">
  <a href="#downloads"><strong>↓ Download Audify 1.9.3</strong></a>
</p>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fgithub.com%2Fsachit1751-art%2FAudify"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="44"></a>
  &nbsp;
  <a href="https://rookieenough.github.io/Orion-Data/app/?id=audify"><img src="https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png" alt="Get it on Orion Store" height="44"></a>
  &nbsp;
  <a href="https://github.com/sachit1751-art/Audify/releases/latest"><img src="https://img.shields.io/badge/Get%20it%20on-GitHub-181717?logo=github&logoColor=white&style=for-the-badge" alt="Get the latest release on GitHub" height="44"></a>
</p>

<p align="center">
  <a href="https://audify-music.vercel.app">Website</a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="#features">Features</a> ·
  <a href="#platforms">Platforms</a> ·
  <a href="#build-from-source">Build</a> ·
  <a href="#community">Community</a>
</p>

> **Independent project:** Audify is a third-party client. It is not affiliated with, endorsed by, or sponsored by Google or YouTube.

## Screenshots

See Audify as it actually looks. These are captures from the app, not concept mockups.

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/audit-artifacts/phone/screenshots/audify-lyrics-expanded.png" alt="Audify expanded synchronized lyrics screen" width="100%">
      <strong>Now playing</strong><br>
      <sub>Follow along with synchronized lyrics.</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/audit-artifacts/phone/screenshots/audify-lyrics-seek.png" alt="Audify lyric-based playback seeking screen" width="100%">
      <strong>Lyrics navigation</strong><br>
      <sub>Move through a track using its timed lyrics.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="docs/audit-artifacts/phone/screenshots/audify-tab-explore.png" alt="Audify Explore screen showing music discovery" width="100%">
      <strong>Explore</strong><br>
      <sub>Discover music and browse recommendations.</sub>
    </td>
    <td align="center" width="50%">
      <img src="docs/audit-artifacts/phone/screenshots/audify-tab-library.png" alt="Audify Library screen showing saved music" width="100%">
      <strong>Your library</strong><br>
      <sub>Keep saved music and playlists organised.</sub>
    </td>
  </tr>
</table>

## Features

<table>
  <tr>
    <td width="50%" valign="top">

### 🎧 Playback & audio

- Background and gapless playback.
- Adjustable crossfade and Automix (Beta), including beat matching and tempo stretching.
- Playback speed, skip silence and sleep timer.
- System equalizer support, streaming quality controls and audio-output selection.

    </td>
    <td width="50%" valign="top">

### 🔎 Music discovery

- Search songs, albums, artists and playlists through YouTube Music.
- Sign in with a Google account for supported personalized content.
- Browse recommendations and explore music.

    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">

### 📚 Library & playlists

- Create and organise playlists and edit supported playlist details.
- Manage the listening queue.
- Browse and play audio stored on your device, including selected folders.
- Import supported Spotify playlist exports and access supported Spotify library features.

    </td>
    <td width="50%" valign="top">

### ✨ Lyrics & personalisation

- Synchronized line- and word-level lyrics from supported providers.
- Seek through tracks using timed lyrics.
- Animated artwork and dynamic colour theming.
- Material 3 interface and optional progressive scroll-edge blur.

    </td>
  </tr>
  <tr>
    <td colspan="2" valign="top">

### ☁️ Offline listening & connected services

- Download supported tracks with metadata for offline listening.
- Discord Rich Presence for currently playing tracks and playback progress.
- Last.fm and ListenBrainz scrobbling.
- Configurable external source modules, including optional high-resolution audio sources.

    </td>
  </tr>
</table>
## Platforms

| Platform | Experience |
| --- | --- |
| **Android phones and tablets** | Full music discovery, library management, playback, lyrics and downloads. |
| **Android TV** | A dedicated TV experience for browsing and listening on a larger screen. |
| **Wear OS** | A companion experience for supported watch-based playback controls. |
| **Desktop (Windows)** | Native Rust desktop client. No desktop installer is attached to the latest stable Android release; see the [desktop project guide](desktop/README.md) for its current build status. |

## Downloads

The latest published stable release is **Audify 1.9.3**. The [1.9.4 release](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.4) is still a draft and is not a public download. Use the [complete 1.9.3 release page](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.3) to view all assets and their checksum metadata.

| Platform | Download | Notes |
| --- | --- | --- |
| Android phone / tablet | [Universal APK](https://github.com/sachit1751-art/Audify/releases/download/v1.9.3/Audify-1.9.3.apk) | Recommended for most devices. |
| Android phone / tablet | [ARM64 APK](https://github.com/sachit1751-art/Audify/releases/download/v1.9.3/Audify-1.9.3_arm64-v8a.apk) | Architecture-specific package for ARM64 devices. |
| Android TV / Google TV | [AudifyTV APK](https://github.com/sachit1751-art/Audify/releases/download/v1.9.3/AudifyTV-1.9.3.apk) | TV-specific interface; not the phone app. |
| Wear OS | [Universal Wear OS APK](https://github.com/sachit1751-art/Audify/releases/download/v1.9.3/AudifyWear-1.9.3.apk) | Companion app; requires a compatible phone app. |
| Checksums | [SHA256SUMS.txt](https://github.com/sachit1751-art/Audify/releases/download/v1.9.3/SHA256SUMS.txt) | SHA-256 checksums for the four published APKs. |

For installation notes and platform-specific requirements, see the [Android TV guide](tv/README.md), [Wear OS guide](wearos/README.md), and [Desktop guide](desktop/README.md).
## Community

- **Questions and ideas:** [GitHub Discussions](https://github.com/sachit1751-art/Audify/discussions)
- **Report a bug:** [Open a bug report](https://github.com/sachit1751-art/Audify/issues/new?template=bug_report.yml)
- **Suggest a feature:** [Submit a feature suggestion](https://github.com/sachit1751-art/Audify/issues/new?template=feature_request.yml)
- **Tester feedback:** [Share feedback](https://github.com/sachit1751-art/Audify/issues/new?template=tester-feedback.yml)
- **Contribute code:** Read the [Contributing Guide](CONTRIBUTING.md), [Code of Conduct](CODE_OF_CONDUCT.md), and [Pull Request template](.github/PULL_REQUEST_TEMPLATE.md).
- **Security:** See [SECURITY.md](SECURITY.md) for responsible vulnerability reporting.
- **Changelog:** Browse the [release history](CHANGELOG.md).
- **More project docs:** [Documentation index](docs/ADDITIONAL.md)

When reporting an issue, include clear reproduction steps and relevant app details. Remove personal information from screenshots and logs. Never share account credentials, tokens, or private listening data.

The Android app also supports direct anonymous bug reports and feature suggestions from Settings → About. These are published as public GitHub issues. The in-app submission service requires the one-time Worker secret described in [the feedback setup guide](backend/cloudflare/FEEDBACK_SETUP.md).

## Build from source

<details>
<summary><strong>Requirements and development setup</strong></summary>

Audify uses the Gradle wrapper; a separate Gradle installation is not required. Android Studio is optional, but the Android SDK is required.

For development requirements and test commands, follow the [Contributing Guide](CONTRIBUTING.md). On Windows, `scripts/first-run.ps1 -AcceptLicenses` checks the JDK and Android SDK, runs the phone and shared UI unit-test tasks, and builds the phone `prodDebug` APK. Build TV and Wear OS separately with the Gradle tasks below.

- **JDK 21**
- **Android SDK Platform 37** and the Android build tools required by the project
- **Android NDK 28.2.13676358**
- Internet access for first-time Gradle and dependency downloads

Current Android toolchain: **Gradle 9.7.0**, **Android Gradle Plugin 9.3.1**, **Kotlin 2.4.21**, and **Compose Multiplatform 1.12.2**. Phone and TV builds compile against API 37, target API 36 and support API 26+. Wear OS compiles against API 37, targets API 36 and supports API 30+.

Desktop is a separate native Rust/GPUI application. Its toolchain, platform dependencies and build steps are documented in [`desktop/README.md`](desktop/README.md).

</details>

<details>
<summary><strong>Build APKs</strong></summary>

On Windows, build the production release variants with:

```bat
build-apks.bat
```

Or use the Gradle wrapper for an individual target:

```bash
# Android phone
./gradlew :app:assembleProdRelease

# Android TV
./gradlew :tv:assembleProdRelease

# Wear OS
./gradlew :wearos:assembleProdRelease
```

For a development build, use the corresponding `assembleDevDebug` task, for example:

```bash
./gradlew :app:assembleDevDebug
```

</details>

<details>
<summary><strong>Build the desktop app</strong></summary>

The desktop client lives in `desktop/` and uses the Rust toolchain pinned in `desktop/rust-toolchain.toml`.

```bash
cd desktop
cargo build --release --locked --package audify
```

This builds the native desktop executable for the host platform. For required system libraries, Windows installer creation, packaging and platform-specific instructions, follow the [desktop build guide](desktop/README.md).

</details>

<details>
<summary><strong>Local release signing</strong></summary>

Copy `keystore.properties.example` to `keystore.properties` and add your own signing configuration. Keep your keystore and credentials private; the local properties file is gitignored. CI release signing requires the repository's configured secrets.

</details>

## About and legal

Audify is free and open-source software distributed under the [GNU General Public License v3.0](LICENSE). Product names and trademarks belong to their respective owners.

Audify does not host or upload music. It accesses local media and third-party services through their available interfaces. External services may change, restrict access or vary by region; features that depend on them may consequently change or stop working. You are responsible for following applicable laws and service terms.

Audify is an independent third-party client and is not associated with or endorsed by Google LLC, YouTube, YouTube Music, Deezer, Telegram or their parent companies.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=sachit1751-art/Audify&type=Date)](https://www.star-history.com/#sachit1751-art/Audify&Date)

---

<p align="center">
  <a href="https://github.com/sachit1751-art/Audify/releases/latest"><strong>Get Audify</strong></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/sachit1751-art/Audify/discussions">Join the discussion</a>
  &nbsp;·&nbsp;
  <a href="LICENSE">GPL-3.0</a>
</p>
