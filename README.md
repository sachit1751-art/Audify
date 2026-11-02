## Download and install

**[Get the latest stable release](https://github.com/sachit1751-art/Audify/releases/latest)** · [Browse every release](https://github.com/sachit1751-art/Audify/releases)

### Choose the right APK

Open the latest release and download the phone APK that matches your device:

- **Most Android phones:** `Audify-1.8.6_arm64-v8a.apk` (64-bit ARM).
- **Older 32-bit ARM phones:** `Audify-1.8.6_armeabi-v7a.apk`.
- **x86_64 devices and emulators:** `Audify-1.8.6_x86_64.apk`.
- **Not sure?** The universal `Audify-1.8.6.apk` supports multiple architectures but is much larger.

Android TV and Wear OS have separate packages. Do not install the TV or watch build on a phone.

### Install manually

1. Download the matching phone APK from the [latest release](https://github.com/sachit1751-art/Audify/releases/latest).
2. Open the downloaded APK from your browser's downloads or your file manager.
3. If Android asks, allow that app to install unknown apps, then return to the installer.
4. Confirm the installation and open Audify.

Only install APKs from the official repository. The release includes `SHA256SUMS.txt` for checksum verification and `BUILD-MANIFEST.txt` for build details.

### Keep Audify updated

You can download each update from [GitHub Releases](https://github.com/sachit1751-art/Audify/releases), or use [Obtainium](https://github.com/ImranR98/Obtainium) to track the official repository:

[Add Audify to Obtainium](obtainium://add/https%3A%2F%2Fgithub.com%2Fsachit1751-art%2FAudify)

Review the selected package in Obtainium and choose the correct phone variant. Obtainium is an independent app; it is not an app store operated by Audify.

### Current stable release

Audify **1.8.6** (`v1.8.6`) was published on 2026-11-02. See the [release notes](RELEASE_NOTES_1.8.6.md) for changes and verification details. The release contains phone, Android TV and Wear OS builds.

**Need help?** [Open an issue](https://github.com/sachit1751-art/Audify/issues/new/choose) with your Audify version, device model, Android version and reproduction steps. Remove personal information from logs before attaching them.
<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
<p align="center">
  <img src="audify_banner.svg" alt="Audify — discover, listen and make music yours" width="100%">
</p>

<p align="center">
  <strong>An open-source music player for Android, Android TV and Wear OS.</strong>
  <br>
  Discover music, manage your library, download for offline listening, and shape the way you listen.
</p>

<p align="center">
  <a href="https://github.com/sachit1751-art/Audify/releases"><img src="https://img.shields.io/github/v/release/sachit1751-art/Audify?label=latest%20stable" alt="Latest stable release"></a>
  <a href="https://github.com/sachit1751-art/Audify/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sachit1751-art/Audify" alt="GPL-3.0 license"></a>
  <a href="https://github.com/sachit1751-art/Audify/releases"><img src="https://img.shields.io/github/downloads/sachit1751-art/Audify/total?label=downloads" alt="GitHub downloads"></a>
</p>

<p align="center">
  <strong><a href="https://github.com/sachit1751-art/Audify/releases/latest">Download the latest stable APK</a></strong>
  &nbsp;·&nbsp;
  <a href="https://github.com/sachit1751-art/Audify/releases">View all builds</a>
</p>

<p align="center">
  <a href="#screenshots">Screenshots</a> &middot;
  <a href="#features">Features</a> &middot;
  <a href="#download">Download</a> &middot;
  <a href="#build-from-source">Build from source</a> &middot;
  <a href="#roadmap">Roadmap</a> &middot;
  <a href="#contributing">Contributing</a>
</p>

Audify brings music discovery, library management, local playback, offline downloads, synchronized lyrics, and detailed playback controls into one customizable app. Listen on your phone, use the dedicated Android TV experience, or control playback from the Wear OS companion.

Audify is free and open-source software distributed under the GNU General Public License v3.0.

> **Independent project:** Audify is a third-party client. It is not affiliated with, endorsed by, or sponsored by Google or YouTube.

## Screenshots

These are captures from the app — not concept mockups.

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/audit-artifacts/screenshots/audify-tab-explore.png" alt="Audify music discovery and Explore screen" width="220">
      <br><strong>Explore</strong>
      <br><sub>Find music and browse recommendations.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/audit-artifacts/screenshots/audify-tab-library.png" alt="Audify music library screen" width="220">
      <br><strong>Library</strong>
      <br><sub>Keep saved music and playlists organised.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/audit-artifacts/screenshots/audify-lyrics-expanded.png" alt="Audify expanded synchronized lyrics screen" width="220">
      <br><strong>Now playing</strong>
      <br><sub>Follow along with timed lyrics.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/audit-artifacts/screenshots/audify-lyrics-seek.png" alt="Audify lyric-based playback seeking screen" width="220">
      <br><strong>Lyric seek</strong>
      <br><sub>Jump to a moment through the lyrics.</sub>
    </td>
  </tr>
</table>

