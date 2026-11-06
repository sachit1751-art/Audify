# Audify development environment

Audify's build toolchain is pinned by the repository: JDK 21, Gradle 9.7.0 (Gradle Wrapper), Android SDK Platform 37, Android Build Tools 36.0.0, NDK 28.2.13676358, and CMake 3.22.1. Do not install Gradle globally.

## New Windows PC

1. Install **Android Studio** and **Eclipse Temurin JDK 21**. During Android Studio's first-run setup, install the Android SDK. In **SDK Manager → SDK Tools**, enable **Android SDK Command-line Tools (latest)**.
2. Clone this repository and open PowerShell in its root folder.
3. Run the one-command setup, test, and debug-build flow:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\scripts\first-run.ps1 -AcceptLicenses
   ```

   This runs the SDK bootstrap, all configured unit/JVM test gates, then builds the phone, TV, and Wear OS dev/prod debug APKs. The setup step detects the SDK, installs the exact project SDK packages through `sdkmanager`, and creates/updates the ignored `local.properties`. Existing API keys and settings in that file are preserved.
4. To also build the production release APK using the signing files already tracked in this repository:

   ```powershell
   .\gradlew.bat --version
   .\scripts\first-run.ps1 -AcceptLicenses -Release
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

This repository currently includes its release keystore and `keystore.properties`, so a fresh clone receives the same signing material and does not need a separate restore step. **Security warning:** these files and credentials have been exposed in a public repository. GitHub account access controls who can push to your repository; they do not stop anyone who already downloaded the signing files from signing an APK with the same certificate. This can enable convincing malicious sideloaded APKs and may affect update trust. The `-Release` option uses the existing tracked credentials because that is the current project configuration; do not interpret a successful build as proof that the signing identity is safe.

## Recovery checklist when changing PCs

1. Install Android Studio + JDK 21.
2. Clone repo; run `scripts/first-run.ps1 -AcceptLicenses` (or `-Release` when private signing material is provisioned).
3. The current tracked signing files are included in the clone. Restore only machine-local API configuration if needed.
4. Run the Gradle wrapper build/test commands. Gradle and dependency caches are downloaded automatically.

No absolute paths, SDK installation directories, Gradle installations, or credentials are meant to be shared through Git.
