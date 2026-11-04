# Audify development environment

Audify's build toolchain is pinned by the repository: JDK 21, Gradle 9.7.0 (Gradle Wrapper), Android SDK Platform 37, Android Build Tools 36.0.0, NDK 28.2.13676358, and CMake 3.22.1. Do not install Gradle globally.

## New Windows PC

1. Install **Android Studio** and **Eclipse Temurin JDK 21**. During Android Studio's first-run setup, install the Android SDK. In **SDK Manager → SDK Tools**, enable **Android SDK Command-line Tools (latest)**.
2. Clone this repository and open PowerShell in its root folder.
3. Run:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\scripts\setup.ps1 -AcceptLicenses
   ```

   The script detects the SDK, installs the exact project SDK packages through `sdkmanager`, and creates/updates the ignored `local.properties`. Existing API keys and settings in that file are preserved.
4. Validate the toolchain and compile:

   ```powershell
   .\gradlew.bat --version
   .\gradlew.bat :app:assembleDevDebug
   ```

## macOS / Linux

Install Android Studio, JDK 21, and Android SDK Command-line Tools (latest), then run `./scripts/setup.sh` from the repository root. Set `ANDROID_HOME` if the SDK is not in the standard location. The script installs missing SDK packages and writes the machine-specific `sdk.dir` without replacing other local properties.

## GitHub and Git account setup

GitHub CLI authentication and global Git author identity are tied to each computer and must not be committed to the repository. On a new PC, install Git and GitHub CLI, then run:

```powershell
gh auth login
git config --global user.name "Your GitHub name"
git config --global user.email "Your GitHub commit email"
```

The repository's GitHub Actions secrets stay in GitHub when you change PCs; they do not need to be copied locally. The `gh` login token is intentionally not portable through this repository.
## What Git does and does not carry

- **Portable in Git:** source, Gradle wrapper and its checksum, build configuration, CI workflow, and setup scripts.
- **Generated per computer:** `local.properties`, Gradle caches, Android SDK, NDK, CMake, build outputs, and emulator images. These should not be copied or committed; the bootstrap recreates the required SDK configuration.
- **Private and not in Git:** `keystore.properties`, `.jks`/`.keystore` files, Last.fm API credentials, and other secrets. Back these up in an encrypted password manager or encrypted offline storage. Restore them to the repository root on a new PC. Never commit or upload the signing key. GitHub Actions production signing is separately configured through repository Actions secrets.
- **Optional local API settings:** add `LASTFM_API_KEY`, `LASTFM_SECRET`, and `LISTEN_TOGETHER_SERVER` to the ignored `local.properties`. Environment variables with the same names are also supported.

A fresh clone can build debug variants without release-signing files. A locally signed production build needs the original release keystore and matching credentials; generating a new key is not a replacement for the existing Play signing key.

## Recovery checklist when changing PCs

1. Install Android Studio + JDK 21.
2. Clone repo; run the bootstrap script.
3. Restore private signing and API configuration from your secure backup if needed.
4. Run the Gradle wrapper build/test commands. Gradle and dependency caches are downloaded automatically.

No absolute paths, SDK installation directories, Gradle installations, or credentials are meant to be shared through Git.
