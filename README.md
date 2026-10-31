# Audify

An open-source YouTube Music client for Android, Android TV, and Wear OS.

<p>
  <a href="https://github.com/sachit1751-art/Audify/releases"><img src="https://img.shields.io/github/v/release/sachit1751-art/Audify" alt="Latest release"></a>
  <a href="https://github.com/sachit1751-art/Audify/blob/main/LICENSE"><img src="https://img.shields.io/github/license/sachit1751-art/Audify" alt="License"></a>
  <a href="https://github.com/sachit1751-art/Audify/releases"><img src="https://img.shields.io/github/downloads/sachit1751-art/Audify/total" alt="GitHub downloads"></a>
</p>

Audify lets you search, stream, queue, and organize music through a customizable interface. It is free software licensed under GPL-3.0.

> **Disclaimer:** Audify is an independent third-party project. It is not affiliated with, endorsed by, or connected to Google or YouTube.

## Features

### A complete listening experience
- **Music discovery** — search tracks, albums, artists, and playlists across YouTube Music.
- **Personalized library** — sign in with Google to access personalized content and keep your music organized.
- **Queue management** — build and manage your listening queue with background playback support.
- **Local playback** — browse and play audio stored on your device.

### Playback controls
- **Gapless playback** — move between tracks without unwanted pauses.
- **Crossfade** — blend consecutive tracks with an adjustable transition duration.
- **Automix (Beta)** — experiment with DJ-style transitions, beat matching, and tempo stretching.
- **Fine-grained controls** — adjust playback speed, skip silence, set a sleep timer, and use your device's system equalizer.
- **Audio quality settings** — configure separate quality preferences for Wi-Fi and mobile data.

### Lyrics and visual experience
- **Synchronized lyrics** — follow line- and word-level timing from supported lyric providers.
- **Animated artwork** — view motion artwork and album art on the now-playing screen.
- **Dynamic theming** — adapt the interface palette to the currently playing artwork.
- **Material 3 interface** — a customizable interface with translucent visual elements.

### Downloads and integrations
- **Offline listening** — download tracks with embedded metadata for local playback.
- **Spotify connection** — access supported Spotify playlists and Liked Songs.
- **Discord Rich Presence** — share current track and playback progress.
- **Listening history** — scrobble tracks to Last.fm and ListenBrainz.
- **External sources** — configure and check supported module sources, including optional high-resolution audio sources.

### Built for more than phones
- **Android** — the primary mobile listening experience.
- **Android TV** — a dedicated build for television devices.
- **Wear OS** — a separate build for supported watches.
## Download

Download the latest signed APK from [GitHub Releases](https://github.com/sachit1751-art/Audify/releases).

Each release may include builds for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `universal`. Most modern Android phones use `arm64-v8a`. Installing an APK manually requires allowing the installer app to install unknown apps.

## Build from source

Audify uses the Gradle wrapper. Android Studio is optional.

```bash
# Build the phone release APK
./gradlew assembleProdRelease

# Build phone, Android TV, and Wear OS APKs on Windows
build-apks.bat
```

Other useful tasks:

```bash
./gradlew assembleProdDebug
./gradlew :tv:assembleProdRelease
./gradlew :wearos:assembleProdRelease
```

**Requirements:** JDK 17 and the Android SDK (AGP 8.10.1, compileSdk 37).

**Release signing:** Copy `keystore.properties.example` to `keystore.properties` and configure your own keystore. The properties file is gitignored. CI signing requires the appropriate repository secrets.

## Contributing

Contributions are welcome. Read the [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) before opening a pull request. Additional project documentation is available in [ADDITIONAL.md](ADDITIONAL.md).

## Disclaimer and legal notice

Audify is an independent third-party audio client. It is not associated with Google LLC, YouTube Music, Deezer, Telegram, or their parent companies.

- **Media hosting:** Audify does not host or upload music. It accesses local media and third-party services through their available interfaces.
- **User responsibility:** Users are responsible for complying with applicable laws and the terms governing any services they use.
- **Third-party services:** Availability and behavior of external services may change without notice. No continued access or ad-related behavior is guaranteed.
- **License:** Audify is distributed under GPL-3.0. Redistribution must comply with the license, including its source-code requirements.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
