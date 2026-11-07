<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Audify for Wear OS

A watch remote for the Audify phone app: now playing over the player's mesh
backdrop, play/pause on the cover with a progress ring (hold it to seek), skip,
Love where the phone offers it, Up Next, shuffle, repeat, the phone's volume on
the crown or bezel, an always-on player, and an Output picker covering the
phone's outputs and **this watch**.

The watch is never a second player with a queue of its own. As the speaker it
is a receiver, exactly like a Cast device: the phone keeps the queue (paused)
and decides every track, the watch plays the one it is told plus the next two,
reports back, and the phone's app, notification, widgets and the watch UI all
show the same state. Audio is read through the phone's own resolver and cache
over a Wearable channel, so the watch needs no network or account. Tracks the
phone serves as a DASH manifest can't play on the watch yet.

The APK is about 4.5 MB (most of it Media3, for the speaker), R8 is on for every
build type, and the app listens only while it is on screen or playing.

| Path | What it is |
| --- | --- |
| `protocol/.../WearProtocol.kt` | Paths, keys and commands. Compiled into both `:app` and `:wearos` |
| `src/main/java/.../data/PhoneLink.kt` | The watch's view of the phone: Data Layer state in, messages out |
| `src/main/java/.../ui/**` | Now Playing, Up Next, More, connection screens |
| `src/main/java/.../playback/**` | The watch as the speaker: `Speaker`, its player service, the phone-backed data source |
| `../app/src/main/java/.../wear/WearBridge.kt` | Phone side: publishes state, runs the watch's commands, `WatchSpeaker`, the stream server |
| `../app/src/main/java/.../playback/cast/RemoteSpeaker.kt` | The receiver seam Cast and the watch share |
| `../tv/src/main/java/.../wear/WearBridge.kt` | No-op stand-in, so the TV build carries none of it |

## Download the 1.9.0 release

The [AudifyWear 1.9.0 universal APK](https://github.com/sachit1751-art/Audify/releases/download/v1.9.0/AudifyWear-prod-universal-release.apk) is published with the [Audify 1.9.0 release](https://github.com/sachit1751-art/Audify/releases/tag/v1.9.0). Wear OS is a companion to the phone app, not a standalone music player. Phone and watch builds must have matching application flavor, package identity, and signing key to pair through the Wearable Data Layer. If pairing fails, verify that both devices are running compatible builds from the same release channel.
## Dev and prod

The Wearable Data Layer only connects apps that have the same package name and
the same signing key. So the watch app takes the phone app's ids:

| Watch build | Package | Pairs with |
| --- | --- | --- |
| `devDebug` | `com.dev.audify` | the phone's `devDebug` (debug key) |
| `prodRelease` | `com.music.audify` | the phone's `prodRelease` (release key) |

A watch build only finds a phone build of the same flavor **and** signed with
the same key. Published production APKs are signed in CI with the repository
secrets `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. The
keystore is not present in a normal local checkout, so the production watch APK
intended to pair with a published phone build must also come from the same CI
release run. The release workflow stages the Wear OS universal APK alongside
the phone and TV packages; see [`RELEASE_PROCESS.md`](../docs/future%20updates/RELEASE_PROCESS.md).

## Build and install

```bash
./gradlew :wearos:assembleDevDebug :wearos:assembleProdRelease
```

APKs land in `wearos/build/outputs/apk/<flavor>/<type>/AudifyWear-*.apk`.
To install on a Galaxy Watch, turn on Developer options → ADB debugging and
Wireless debugging on the watch, then:

```bash
adb pair <watch-ip>:<pairing-port>
adb connect <watch-ip>:<port>
adb -s <watch-ip>:<port> install -r wearos/build/outputs/apk/dev/debug/AudifyWear-dev-debug.apk
```

The phone needs a Audify build that includes `WearBridge` (anything from this
change on), from the same flavor and signed with the same key.

Debug builds are shrunk and non-debuggable, because an un-R8'd, debuggable
Compose app is noticeably slower on a watch CPU. To get a debugger back, pass
`-Paudify.wearDebuggable=true`.
---

> **Audify** — made by [Sachit](https://github.com/sachit1751-art).
