# AUDIFY Codebase Untangle - Decision Log

## Scope and invariants

- Refactor source organization without changing user-visible behavior, wire formats, persistence formats, playback semantics, or public APIs.
- Preserve all pre-existing local modifications, deletions, untracked files, local BLA output, and private build/signing material.
- Do not edit `plans/`, `docs/future updates/`, release notes, roadmap/future-feature proposals, build/release configuration, or future-build instructions.
- No release or merge was performed.
- Remove only comments that duplicate the immediately visible code. Preserve comments that explain rationale, compatibility, security, timing, performance, or non-obvious invariants.
- Keep tests that protect real behavior. Remove only scaffolding with no meaningful regression value.

## Repository map observed

- Android app: `app/` (Kotlin/Compose, service, data and UI), with shared KMP modules in `shared/` and `sharedUi/`.
- TV and Wear OS: `tv/`, `wearos/`; the phone build intentionally shares the Wear protocol source directory.
- Desktop: `desktop/` Rust workspace, with separate `music`, `state`, `views`, `ui`, `storage`, `router`, and platform crates.
- Web: `web/` pnpm workspace containing app, browser extension, and shared bridge package.
- Listen Together backend: `backend/` Go module with focused `clock`, `codes`, `config`, `hub`, `party`, `presence`, and `protocol` packages plus a large root HTTP entrypoint.

## Structural findings and decisions

1. **Backend HTTP entrypoint mixes unrelated responsibilities.** `backend/main.go` contains server setup, middleware/rate limiting, JSON/auth helpers, REST handlers, invite HTML, WebSocket frame handling, and background tickers. Centralize route registration in `newHandler()` while retaining package `main` and all handler signatures. A full multi-file split is deferred because the Go 1.27.3 toolchain was installed user-locally; `go test ./...` and `go vet ./...` both pass from `backend/`.
2. **Production and test route tables had drifted.** `main.go` registered `GET /api/parties/{code}/preview`; `main_test.go` built a separate mux and omitted that route. Both now use one route-registration function, and the test suite has a party-preview route assertion.
3. **Android playback service contained a standalone policy helper.** `ManualUpgradeThrottle` was a pure stateful policy object inside the 8,889-line `PlaybackService.kt`, despite having its own unit test. It now lives in `playback/ManualUpgradeThrottle.kt`.
4. **Android starter arithmetic test had no product regression value.** Removed `ExampleUnitTest.kt`. Kept the meaningful application/package identity instrumentation check and renamed it to `ApplicationIdentityTest.kt`.
5. **Web test commands masked absent coverage.** The shared package had no test files but exposed a `--passWithNoTests` script; that empty script was removed. The app has 16 real test files, so its test command now fails if the suite disappears.
6. **Rust view tests were embedded in large source files.** Moved the `aside` and `sidebar_left` test modules to sibling `tests.rs` files. All assertions and test helpers were retained.
7. **Large Android and desktop source files need staged extraction, not blind splitting.** `PlaybackService.kt`, `MainActivity.kt`, `MainViewModel.kt`, `NowPlayingScreen.kt`, `SettingsSheet.kt`, desktop `aside.rs`, and `library.rs` contain multiple concerns. Their state, callbacks, and private visibility are tightly coupled. Do not mechanically split files in ways that merely relocate complexity or change lifecycle ownership. Extract only boundaries with explicit ownership and tests.
8. **Existing KMP and Rust crate boundaries are intentional.** Preserve the shared Android/JVM source-set structure, the shared Wear protocol source, and desktop crate boundaries. Do not introduce new dependencies or move platform code across these boundaries.
9. **Comments are mostly rationale-bearing, and the test inventory is substantial.** Preserve compatibility/security/performance comments and meaningful behavior tests. Remove only verified redundant section labels and the arithmetic scaffold. Keep the AutoMix `selftest.ts`; it is a developer diagnostic, not an empty test-suite placeholder.

## Implementation sequence

1. Centralize backend route registration and add a party-preview route regression check.
2. Extract Android policy helpers, rename the identity instrumentation test, and remove the arithmetic scaffold.
3. Move the two Rust view test modules into dedicated files and make web test commands fail honestly when app tests are missing.
4. Run web, Android, Rust, and Go verification. Go tests and vet passed after installing the user-local toolchain.
5. Review the final diff and confirm future-build documents and pre-existing working-tree changes remain untouched.

## Explicitly deferred

No mass refactor of the large Android Compose/service or Rust state/view files without a demonstrated ownership boundary and targeted regression coverage. No build, release, roadmap, future-plan, dependency-version, or credential changes.
