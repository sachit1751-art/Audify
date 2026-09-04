# Working with Sachit as an AI agent

Sachit is a 3rd party YouTube Music client written in Kotlin. It follows material 3 design guidelines closely.

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
3. All string edits should be made to the `app/src/main/res/values/sachit_strings.xml` file, NOT `app/src/main/res/values/strings.xml`. Do not touch other `strings.xml` or `sachit_strings.xml` files in the project. ONLY edit the default (English) `sachit_strings.xml` file, DO NOT EDIT OTHER LANGUAGES.
4. You are to follow best practices for Kotlin and Android development.
5. DO NOT EDIT THE APP'S DATABASE SCHEMA.

## AI-only guidelines

1. You are strictly prohibited from making ANY changes to the readme/markdown files, including this one. This is to ensure that the documentation remains accurate and consistent for all contributors.
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Always follow the guidelines and instructions provided by human contributors.
4. Ensure the absolutely highest code quality in all contributions, including proper formatting, clear variable naming, and comprehensive comments where necessary.
5. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
6. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
7. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
8. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
9. You are absolutely **not allowed to bump the version** of the app in ANY way. Version bumps are only done by the core development team after manual review.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following command from the root directory of the project:

```bash
./gradlew :app:assembleFossDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/foss/debug/app-foss-debug.apk` and ask a human for help testing the specific features you worked on.

## Session handoff (September 2026)

Environment notes for the main dev machine (Windows, no global JAVA_HOME):

```bash
export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"
./gradlew :app:assembleFossDebug --console=plain
```

APK output: `app/build/outputs/apk/foss/debug/app-foss-debug.apk` (FOSS flavor).

UI/feature work already shipped in recent sessions (do NOT re-add; all of it exists and most has settings toggles):

- Rebranded to **"Sachit"** with a new launcher icon (bold black "S" on off-white parchment). The icon exists in every format: adaptive vector foreground/monochrome/v31, density webps + round variants, static PNGs, and the Play Store icon (`fastlane/metadata/android/en-US/images/icon.png`).
- Player screen: single-tap on the artwork toggles play/pause (toggle in Player settings, disabled for Listen Together guests); double-tap seek (±5s, pre-existing) now fires a haptic tick; artwork corner-radius picker (None/Subtle/Rounded) in Appearance settings.
- Home screen: time-aware greeting header ("Good morning/afternoon/evening, {name}" when signed in).
- Mini player: "Up Next peek" card slides up on song change showing the next 3 queued tracks; tap a row to jump; auto-dismisses after 5s; toggle in Appearance settings.
- Low-end device optimizations: heap-aware Coil memory cache (10% vs 15% of heap) and crossfade disabled when the app heap is < 128 MB; density-aware image decode cap on the Home "Daily Discover" card. Tuning logic lives in `DevicePerformance.kt` with unit tests in `DevicePerformanceTest.kt`. Capable devices are unchanged.

Artifact & release practice:

- **Do NOT commit APKs into git history.** The repo `.gitignore` excludes `*.apk`; a multi-MB binary permanently bloats the public repo and GitHub warns above 50 MB. A previous session force-committed one and the history was rewritten to remove it again.
- Ship build artifacts by attaching them to a GitHub **Release** instead. Tag convention matches the CI: `v<versionName>` (e.g. `v13.6.3`), marked prerelease for debug builds. GitHub API credentials are stored in the local git credential manager (username `sachit1751-art`); a fresh session can read them non-interactively with `git credential fill` (host `github.com`) and drive the REST API with curl — no `gh` CLI or CAVE/CI token is available on this machine.
