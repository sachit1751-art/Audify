<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" alt="Audify icon" width="96" />

# Audify

### Stream, discover and organize YouTube Music — your way.

<br/>

[![Latest release](https://img.shields.io/github/v/release/sachit1751-art/Audify?style=for-the-badge&labelColor=0d1117)](https://github.com/sachit1751-art/Audify/releases/latest)
[![License](https://img.shields.io/github/license/sachit1751-art/Audify?style=for-the-badge&labelColor=0d1117)](https://github.com/sachit1751-art/Audify/blob/main/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/sachit1751-art/Audify/total?style=for-the-badge&labelColor=0d1117)](https://github.com/sachit1751-art/Audify/releases)

<br/>

[**Download**](#download) · [**Features**](#features) · [**Build from source**](#build-from-source) · [**FAQ**](#faq) · [**Support**](#support)

</div>

> [!WARNING]
> **Regional restriction** — if YouTube Music is unavailable in your region, the app will not work without a **VPN or proxy** connecting to a supported region.

---

<div align="center">

## Screenshots

<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_1.png" alt="Home screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_2.png" alt="Artist screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_3.png" alt="Recognize music screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_4.png" alt="Listen together screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_5.png" alt="Player screen" width="30%" />
<img src="fastlane/metadata/android/en-US/images/screenshots/screenshot_6.png" alt="Player lyrics screen" width="30%" />

</div>

---

<div align="center">

## Features

</div>

<table>
  <tr>
    <td width="50%" valign="top">

#### 🎵 Playback
- Stream any song or video from YouTube Music
- Background playback
- Download & cache for offline use
- **One-tap song radio** from the player
- **Play next / add to queue** straight from search results
- Skip silence · Sleep timer

</td>
    <td width="50%" valign="top">

#### 🔊 Audio
- Audio normalization with loudness presets
- Tempo & pitch control
- Built-in equalizer

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### 📝 Lyrics & Discovery
- Live synced lyrics with word-by-word highlighting
- AI-powered lyrics translation
- **Current lyric line in the mini player**
- Personalized quick picks
- Search songs, albums, artists, videos and playlists

</td>
    <td width="50%" valign="top">

#### 📚 Library & Account
- Full library management with local playlists & imports
- **Smart auto-playlists** — Most played this month · On repeat · Recently added · Never played
- **Monthly listening card** — minutes, top artist & top song, refreshed every month
- Reorder songs in playlists and the queue
- YouTube Music account login & sync

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### 👥 Social
- Listen together with friends in real time
- Guest-aware playback controls

</td>
    <td width="50%" valign="top">

#### 🎨 Interface
- Home screen widget & **shuffle-all quick-settings tile**
- Light / Dark / Black / Dynamic theme modes
- Dynamic color + 19 preset palettes
- Up Next peek, tap-to-pause artwork, mini-player lyric line
- Built with Material 3

</td>
  </tr>
</table>

<div align="center">
<i>Bold items are recent additions — see the <a href="https://github.com/sachit1751-art/Audify/releases/latest">latest release notes</a> for details.</i>
</div>

---

<div align="center">

## Download

### Get the latest release from GitHub, or add the repo to Obtainium for automatic updates.

<table>
  <tr>
    <th align="center">GitHub</th>
    <th align="center">Obtainium</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/sachit1751-art/Audify/releases/latest">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub" height="60">
      </a>
    </td>
    <td align="center">
      <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sachit1751-art/Audify/">
        <img src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png" alt="Download from Obtainium" height="40">
      </a>
    </td>
  </tr>
</table>

Every release ships three signed variants — pick the one that matches your device:

| Variant | Google Play Services | In-app updater | Best for |
|---------|:---:|:---:|----------|
| `Audify-foss` | ✗ | ✓ | Most users, de-Googled devices |
| `Audify-gms` | ✓ (Cast) | ✓ | Chromecast / Google Cast users |
| `Audify-izzy` | ✗ | ✗ | IzzyOnDroid repository users |

<sub>Tip: with Obtainium, track *releases* (not prereleases) to only receive stable builds.</sub>

</div>

---

<div align="center">

## Build from source

</div>

**Prerequisites:** Android Studio (or the Android SDK), JDK 21, Git.

```bash
git clone https://github.com/sachit1751-art/Audify.git
cd Audify

# Debug build (fast, debug-signed, installable)
./gradlew :app:assembleFossDebug

# Release builds of every flavor (requires signing credentials — see below)
./gradlew :app:assembleFossRelease :app:assembleGmsRelease :app:assembleIzzyRelease
```

The debug APK lands at `app/build/outputs/apk/foss/debug/app-foss-debug.apk`.

For a fully automated build + signature verification + GitHub upload, use:

```bash
scripts/build-apks.sh [--with-tests] [--upload]
```

Release signing credentials are read from `local.properties` (`AUDIFY_RELEASE_STORE_*`); the keystore itself is **not** committed — provide your own and configure the same properties. Unit tests can be run with `./gradlew :app:testFossDebugUnitTest`.

---

<div align="center">

## FAQ

### Got questions? Check out the [FAQ page](https://sachitmusic.cc/#faq) for answers to the most common ones.

</div>

---

<div align="center">

## Support

- 🐛 Found a bug? [Open an issue](https://github.com/sachit1751-art/Audify/issues/new/choose)
- 💡 Have an idea? Feature suggestions are welcome in [Issues](https://github.com/sachit1751-art/Audify/issues) too
- ❓ Usage questions — start with the [FAQ](https://sachitmusic.cc/#faq)

If Audify is useful to you, consider giving the repo a ⭐ — it helps others discover it.

</div>

---

<div align="center">

## Acknowledgements

### Audify stands on the shoulders of incredible open-source work.

**Main inspirations**

| Project | Authors |
|---------|---------|
| **[InnerTune](https://github.com/z-huang/InnerTune)** | [Zion Huang](https://github.com/z-huang) · [Malopieds](https://github.com/Malopieds) |
| **[OuterTune](https://github.com/DD3Boh/OuterTune)** | [Davide Garberi](https://github.com/DD3Boh) · [Michael Zh](https://github.com/mikooomich) |
| **Sachit** | [Sachit](https://github.com/sachit1751-art) |

**Libraries & integrations**

| Project | Contribution |
|---------|--------------|
| **[Better Lyrics](https://better-lyrics.boidu.dev)** | Time-synced lyrics with word-by-word highlighting & YouTube Music integration |
| **[MusicRecognizer](https://github.com/aleksey-saenko/MusicRecognizer)** | Music recognition feature & Shazam API integration |
| **[zemer-cipher](https://github.com/ZemerTeam/zemer-cipher)** | YouTube cipher deobfuscation and PoToken generation |

Thanks to the entire open-source community — every library, tool and API that powers this project.

</div>

---

<div align="center">

## Disclaimer

This project is **not affiliated with, funded, authorized, endorsed by, or in any way associated** with YouTube, Google LLC, or any of their affiliates and subsidiaries.

All trademarks, service marks, and intellectual property rights referenced in this project belong to their respective owners.

Audify is licensed under **GPL-3.0** — see [LICENSE](https://github.com/sachit1751-art/Audify/blob/main/LICENSE).

<br/>

**Made with ❤️ by [Sachit](https://github.com/sachit1751-art)**

</div>
