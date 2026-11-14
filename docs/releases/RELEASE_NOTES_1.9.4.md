# Audify 1.9.4

## Restored settings and listening controls

- **Hide explicit content** now filters explicitly marked tracks from Home shelves, discovery and search results while leaving unmarked catalogue entries available.
- **Pause on mute** and **Resume on Bluetooth reconnection** are available again as playback preferences.
- **Time-aware discovery** brings a time-of-day listening shelf back to Home, with a setting to enable or disable it.
- **Weekly listening recap** can be enabled from Settings and appears on Home when a new weekly summary is available.
- **Clear search history** is available in Settings.
- **YouTube Music sync status** can be refreshed from Settings for signed-in accounts.
- **Wear OS management** is reachable from Settings and reports paired watches, installation state and available updates.
- **Backup status** shows the latest automatic and external backup timestamps. Backup import now presents a confirmation step before restoring data.
- **Manual update checking** is available alongside the existing update notification and download flow.

## Platform and reliability

- Updated shared catalogue filtering and Home presentation paths for the restored discovery controls.
- Retained the current player and appearance architecture while reconnecting the restored settings and Home entries.
- Updated the release version to **1.9.4 (43)** across the phone, TV and Wear OS build configuration.

## Build verification

- Android phone `prodRelease` APKs built for universal, ARM64, ARMv7 and x86_64.
- Android TV `prodRelease` APKs built for universal, ARM64, ARMv7 and x86_64.
- Wear OS `prodRelease` APKs are generated for universal, ARM64, ARMv7 and x86_64.
- Shared JVM tests, shared UI JVM tests, and phone dev/prod debug unit-test tasks passed.

Release APK signing and physical-device installation must be verified against the existing release keystore and a connected Android device before distribution.
