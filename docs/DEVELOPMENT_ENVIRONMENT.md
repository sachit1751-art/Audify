<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Development environment setup

This guide covers the Android, Android TV, Wear OS, and shared Kotlin projects. The desktop application has a separate toolchain documented in [`desktop/README.md`](../desktop/README.md).

## Required tools

- **JDK 21** for Gradle and Kotlin builds. The Android bytecode target remains JVM 17.
- **Android SDK Platform 37**, **Build Tools 36.0.0**, and **Platform Tools**.
- **Android NDK 28.2.13676358** and **CMake 3.22.1** for native audio components.
- Internet access for the Gradle wrapper and dependency downloads.

Install the SDK packages through Android Studio's SDK Manager, or use the Android command-line tools. Set `ANDROID_HOME` (and, if needed, `ANDROID_SDK_ROOT`) to the SDK directory. Do not commit machine-specific `local.properties`.

## Windows bootstrap

From PowerShell at the repository root, run:

```powershell
.\scripts\first-run.ps1 -AcceptLicenses
```

The script checks JDK 21, validates or installs the Android SDK packages, preserves existing local settings while writing the SDK path to the ignored `local.properties`, runs `:app:testProdDebugUnitTest` and `:sharedUi:jvmTest`, then builds the phone `prodDebug` APK. It does not build TV or Wear OS APKs.

To validate SDK setup without running the test/build sequence, use:

```powershell
.\scripts\setup.ps1 -AcceptLicenses
```

## macOS and Linux setup

Install JDK 21 and the Android SDK packages listed above using Android Studio or `sdkmanager`. Export the actual SDK path before invoking Gradle. Typical examples are:

```bash
# Linux
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# macOS
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

Then run the wrapper from the repository root:

```bash
./gradlew :app:testProdDebugUnitTest :sharedUi:jvmTest
./gradlew :app:assembleProdDebug
```

The PowerShell bootstrap scripts are Windows-oriented; on macOS and Linux, install and accept SDK packages through Android Studio or `sdkmanager` before running the Gradle commands.

## Platform build tasks

Run only the targets you need:

```bash
# Android phone / tablet
./gradlew :app:assembleProdDebug

# Android TV
./gradlew :tv:assembleProdDebug

# Wear OS
./gradlew :wearos:assembleProdDebug

# Shared UI JVM tests
./gradlew :sharedUi:jvmTest
```

Use `gradlew.bat` instead of `./gradlew` on Windows. A separate Gradle installation is not required.

## Signing and local configuration

Debug builds do not require release signing credentials. Production release signing uses the repository's configured CI secrets or a maintainer-provided private local keystore. Never commit keystores, signing properties, tokens, or machine-specific SDK paths. Keep private signing material outside the repository and restore it only from an approved secure backup.
