# Plan 005 — Characterization tests for YouTube.kt client + ListenTogetherManager state

> **Execution outcome (2026-09-04):** escape hatches fired on both parts. The innertube
> facade's HTTP client is hardwired (no seam); after adding a temporary constructor seam,
> MockEngine tests proved the wiring works but InnerTubeX rejects synthetic endpoints
> ("Playback statistics URL must use an approved YouTube HTTPS endpoint") and request
> construction lives inside the external library — endpoint tests require a captured real
> stream URL fixture, which was not available. The `ListenTogetherManager` session state is
> inline in websocket/coroutine callbacks with no safe extraction seam. Both parts deferred;
> the temporary seam was reverted to keep the diff clean. Re-run this plan once a real
> response fixture is captured (e.g. from a debug log) or after an InnerTube refactor adds an
> injectable client.

Written against commit **`7ca7a9653`**. Add regression (characterization) tests for the two
highest-risk, least-directly-tested logic cores. **No production behavior change** — this
plan only adds tests and, where strictly needed for testability, extracts *pure* logic with
zero behavior change.

## Context (accurate scope, corrected during audit vetting)

- `app/src/main/kotlin/com/sachit/music/playback/MusicService.kt` is 4,914 lines and
  `innertube/src/main/kotlin/com/sachit/innertube/YouTube.kt` is ~4,000 lines. Both churn
  constantly (YouTube API breakage is the #1 real-world failure mode for this app class).
- Existing coverage is sparse in exactly the riskiest seams:
  - `innertube/src/test` has 4 files (`MusicResponsiveHeaderRendererTest.kt`,
    `PageHelperTest.kt`, `SearchPageTest.kt`, `UploadProgressInputStreamTest.kt`) — page
    parsing only. The client request-building/response-mapping for the many endpoints in
    `YouTube.kt` (queue, songs, search, accounts, playlists, liked, history, suggestions) is
    untested.
  - `app/src/test/kotlin/com/sachit/music/listentogether/` has `MessageCodecTest.kt`,
    `PlaybackSyncTest.kt`, `QueueSyncTest.kt`, `ServerClockTest.kt` — codec + sync math.
    The manager's session state machine (`ListenTogetherManager.kt`, 94KB) — join-request
    approval, pending-suggestion dedupe, kick/block lists, host transfer — has no direct
    tests.
- Test conventions to follow: plain JUnit4 + Robolectric where Android framework is needed,
  deterministic no-network tests. Exemplars: `MessageCodecTest.kt` (pure protobuf logic),
  `app/src/test/kotlin/com/sachit/music/utils/DevicePerformanceTest.kt` (pure JVM),
  `innertube/src/test/.../SearchPageTest.kt` (JSON fixture parsing). Ktor `MockEngine` is
  available to the app module (`ktor-client-mock` in `libs.versions.toml`) but NOT yet to
  `innertube` (its `build.gradle.kts` only has `testImplementation(libs.junit)`).

## Files in scope

- `innertube/build.gradle.kts` (add `testImplementation(libs.ktor.client.mock)` — one line;
  catalog entry already exists)
- `innertube/src/test/kotlin/com/sachit/innertube/*.kt` (new tests)
- `app/src/test/kotlin/com/sachit/music/listentogether/*.kt` (new tests)
- Possibly `app/src/main/kotlin/com/sachit/music/listentogether/ListenTogetherManager.kt`
  **only** if a pure-state extraction is unavoidable and provably behavior-preserving
  (prefer not to touch it; see escape hatch)

## Steps

### Part A — innertube endpoint characterization (Ktor MockEngine)

1. Read `YouTube.kt` and identify the 3–5 highest-value endpoints for regression tests —
   prioritize: (a) `queue`/watch-endpoint resolution (used by every play/deep link),
   (b) `search` continuation or `suggestions`, (c) account cookie-based auth setter
   (`YouTube.cookie` etc. — currently plain `var`s; only test what is testable without
   changing them), (d) a playlist/`browse` mapper. For each chosen endpoint, extract the
   request construction (URL, headers, JSON body) from the code.
2. For each endpoint, write a MockEngine-based test that:
   - serves a canned success JSON body (inline in the test file, following `SearchPageTest`
     fixture style),
   - asserts the outgoing request (URL + auth header presence) is as constructed,
   - asserts the parsed result type maps fields you can verify from the fixture.
   Construct fixtures from real response shapes visible in the code's parsing logic (do NOT
   invent plausible shapes — copy field names/structure from the code under test).
3. Add `testImplementation(libs.ktor.client.mock)` to `innertube/build.gradle.kts`.

### Part B — ListenTogetherManager state characterization

1. Read `ListenTogetherManager.kt` and map the pure decision logic: approval of a join
   request, dedupe/expiry of pending suggestions, blocking/kick bookkeeping, host-transfer
   conditions. If that logic is already factored into pure functions/objects, test them
   directly. If it lives inline in coroutine/websocket callbacks, do ONE of:
   - (preferred) test through the existing public surface using a fake transport if the
     manager already abstracts its socket; or
   - extract the smallest pure helper (e.g. a `RoomSessionState` data class with pure
     transition functions in the same package, `com.sachit.music.listentogether`) and
     delegate the manager's mutations to it verbatim.
2. Write tests covering: join-request accept/reject transitions, suggestion dedupe
   (same track twice), kick removes user from room state, host transfer updates roles.
3. Do NOT test network/timing behavior (reconnect, clock skew) — those have tests already.

## Verification

```bash
export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"
./gradlew :innertube:testDebugUnitTest --console=plain
./gradlew :app:testFossDebugUnitTest --console=plain
./gradlew :app:assembleFossDebug --console=plain
```

All three exit 0. New tests fail meaningfully if you break the behavior they pin (spot-check
by reverting one assertion mentally, not by editing production code).

## Done criteria

- ≥3 innertube endpoint tests with MockEngine, using request + response assertions.
- ≥4 LT state tests covering the transitions in Part B step 2.
- Zero production-behavior changes (if `ListenTogetherManager.kt` was touched, `git diff` on
  it must show only moved/verbatim logic, no altered branches).
- Build + both unit-test tasks green.

## Escape hatches

- If an endpoint's request construction is inseparable from Android framework calls or
  singletons that cannot be injected, skip that endpoint and pick another — report which
  ones were skipped and why.
- If extracting LT state requires touching websocket/callback plumbing beyond a pure
  helper, STOP and report — do not refactor the manager's I/O for testability in this plan.
- If `ktor-client-mock` on the innertube module creates a version conflict, revert that
  dependency change and report before proceeding with Part A alternatives.

## Maintenance note

These tests are characterization, not spec — they pin current behavior so future YouTube API
changes and refactors are visible. When the API client or LT manager next changes, update
the affected fixture + assertions in the same commit. Watch that `innertube` keeps its
dependency footprint minimal (one test-only dep added here).
