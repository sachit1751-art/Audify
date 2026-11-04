<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Contributing to Audify

Thank you for contributing to Audify. Follow these guidelines to ensure an efficient development and review process.

## Proposing Contributions
- Substantial features, refactors, or UI redesigns should normally be discussed before implementation. Open an issue on GitHub to outline your proposal.
- Straightforward bug fixes, documentation improvements, or minor corrections can be submitted directly as a Pull Request.

## Branch and Pull Request Workflow
Always check the repository for the current active version or development branch before starting work.

New work should normally be based on the branch or release explicitly named by the maintainers. Check the current repository state and open pull requests before choosing a base; do not copy an old release-cycle example from this guide.

When contributing:
- Fork the repository and create a descriptive branch (e.g., `fix/streaming-buffer`, `docs/translation-guide`).
- Keep pull requests focused on a single change or bug fix. Avoid mixing unrelated refactors, cosmetic tweaks, or mass reformatting with functional changes.
- Keep your branch reasonably synchronized with the target base branch.
- Provide a clear PR description explaining what was changed and why.
- Include relevant testing and validation details with your PR.
- Address review feedback promptly and keep discussions focused on technical merits.

## Development Setup

For a fresh machine, use the [cross-platform environment setup guide](docs/DEVELOPMENT_ENVIRONMENT.md). It pins the SDK components and preserves machine-local credentials.
The project requires the following tools:
- **JDK**: JDK 21 to run the current Gradle/Kotlin build. The Android app's Java/Kotlin bytecode target remains JVM 17; that is not the required Gradle runtime JDK.
- **Android SDK**: phone and TV use `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`; Wear OS uses `compileSdk = 37`, `targetSdk = 36`, `minSdk = 30`.
- **C/C++ NDK & CMake**: CMake 3.22.1+ and Android NDK (for native audio DSP components configured under `app/src/main/cpp`).
- **Listen Together Backend (Optional)**: Go 1.27.3 or compatible toolchain support for the version declared in `backend/go.mod`.

## Build and Test Commands
Run Gradle commands from the repository root.

Build:
```bash
./gradlew :app:assembleDevDebug
```

Windows:
```powershell
.\gradlew.bat :app:assembleDevDebug
```

Tests:
```bash
./gradlew :app:testDevDebugUnitTest
```

Windows:
```powershell
.\gradlew.bat :app:testDevDebugUnitTest
```

Run a specific test:
```bash
./gradlew :app:testDevDebugUnitTest --tests "com.music.audify.playback.audio.DirectAudioStreamingRegressionTest"
```

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests, must not introduce new failures, and should document known pre-existing failures when applicable.
- Keep diffs focused and minimal. Avoid unnecessary third-party dependencies.

## Audio Changes and Telemetry
For audio changes: application telemetry must not be presented as proof of physical hardware behavior unless that hardware behavior was actually verified.

Distinguish clearly between source metadata, decoder format, internal DSP format, AudioTrack output, AudioFlinger/HAL state, advertised device capability, and external physical endpoint behavior.

## Review Process
All pull requests require review and approval by repository maintainers before merging. Maintainers review changes for correctness, architecture fit, and maintainability.

## Dependencies and Licensing
Audify is licensed under the **GNU General Public License v3.0 (GPLv3)**.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
---

> **Audify** — made by [Sachit](https://github.com/sachit1751-art).
