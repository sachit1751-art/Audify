# AUDIFY Micro Improvements

Small, focused improvements to everyday usability, reliability, and polish. Each file is a self-contained, agent-executable implementation plan modelled on the depth and gating structure of Plan 06. Before starting any item, inspect the current source and reconcile it with the existing numbered plans linked below.

## Plan format
Each plan defines the product problem and outcome, canonical owner/dependencies, repository reconnaissance, staged implementation, feature-specific tests, performance/privacy constraints, acceptance criteria, rollback, repository-wide guardrails, and an evidence-based handoff. Paths and capabilities must be re-verified against the live checkout before implementation.

## Plans

| Plan | Improvement | Relationship / notes |
|---|---|---|
| [01 — playback reliability](01-playback-reliability.md) | Playback reliability | **Occurrence-aware recovery and per-occurrence retry budgets; automated source checks passed; playback lifecycle/device acceptance open** |
| [02 — better queue experience](02-better-queue-experience.md) | Queue experience | **Stable occurrence identity, reduced-motion feedback and stale Wear snapshot protection; device acceptance open** |
| [03 — offline first experience](03-offline-first-experience.md) | Offline-first experience | **Partial-scan trust, failed-delete preservation and actionable storage-exhaustion errors implemented; focused tests pass; offline browse/play/reconnect acceptance open** |
| [04 — intent aware search](04-intent-aware-search.md) | Intent-aware search | **Conservative intent inference, manual-filter precedence, bounded non-cancelling submitted searches and stale-result suppression tested; provider boundaries preserved; device acceptance open** |
| [05 — personalized home](05-personalized-home.md) | Personalized home | **Pin/reorder/visibility/reset implemented; drag and accessible movement stay within pinned/unpinned groups; stable-key and boundary tests pass; form-factor/device acceptance open** |
| [06 — audio quality controls](06-audio-quality-controls.md) | Audio quality controls | **Session/route lifecycle and output negotiation tests pass; Dolby Atmos requires positive decoder evidence; measured per-headphone correction remains blocked by licensing and verified output identity/attachment prerequisites** |
| [07 — accessibility polish](07-accessibility-polish.md) | Accessibility polish | **Home/Library non-drag reorder, pinned-boundary-safe movement availability, pin controls, visibility semantics and onboarding progress announcement; focused tests pass; manual assistive-technology and form-factor acceptance open** |
| [08 — guided onboarding](08-guided-onboarding.md) | Guided onboarding | **Skippable account-free flow and Back navigation tests; onboarding resources follow the dedicated English resource convention; process-recreation/device acceptance open** |
| [09 — context aware listening](09-context-aware-listening.md) | Context-aware listening | **Session-only intent, deterministic artist/energy ranking, tie-group diversity, signal-backed explanations and bounded recency cache; focused tests pass; device listening-quality acceptance open** |
| [10 — clear status and recovery](10-clear-status-and-recovery.md) | Clear status and recovery | **Retryability-aware errors, safe retry affordances, partial-scan preservation and distinct storage-full failures; workflow/device acceptance open** |

## Consolidated verification — 2026-11-22

The following serialized Gradle run completed successfully after the implementation changes (JDK 21, Android SDK configured, one Gradle worker, no parallel project execution):

```text
:shared:jvmTest                         PASS
:sharedUi:jvmTest                       PASS
:app:compileDevDebugKotlin              PASS
:app:testDevDebugUnitTest               PASS
:wearos:compileDevDebugKotlin           PASS
:wearos:testDevDebugUnitTest            PASS
:tv:compileDevDebugKotlin               PASS
:tv:testDevDebugUnitTest                PASS
Test totals: 1,527 passed, 0 failed, 1 skipped across shared JVM, shared UI JVM, Android phone DevDebug, Wear OS DevDebug and Android TV DevDebug reports.
:app:lintDevDebug                       PASS — 0 errors, 12 warnings
:wearos:lintDevDebug                    PASS — 0 errors, 5 warnings
:tv:lintDevDebug                        PASS — 0 errors, 11 warnings
git diff --check                        PASS
```

Warnings are reported by the existing lint baselines; no lint errors were reported in the last completed lint run before this final pass. The latest UI/string edits still require a lint rerun. No dedicated formatter task/plugin (ktlint, Spotless, ktfmt or Detekt) is configured in the inspected Gradle build, so formatting validation is limited to `git diff --check` and lint. No APK packaging, emulator, physical-device, accessibility-service, audio-route, offline reconnect or lifecycle acceptance test was run. Those checks remain explicitly unverified. Plan 06 remains partially blocked by the external headphone-profile prerequisites; Plan 09 is no longer blocked on owner discovery but still has per-track explanation/diversity and device acceptance gaps.

## Resume verification — Plans 03–04 — 2026-11-22

After the resume-pass fixes, the following command completed successfully with JDK 21, the installed Android SDK, and one Gradle worker:

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
./gradlew.bat :app:compileDevDebugKotlin :app:testDevDebugUnitTest `
  --tests '*SearchPipelineStateTest' `
  --tests '*OfflineDashTest' `
  --tests '*LocalLibraryStateTest' `
  --tests '*LocalMediaFilterTest' `
  --tests '*DownloadStoreTest' `
  --tests '*DownloadFailureTest' `
  --no-daemon --max-workers=1
```

```text
:app:compileDevDebugKotlin  PASS
:app:testDevDebugUnitTest   PASS
Focused test reports: 49 passed, 0 failed, 0 errors, 0 skipped
git diff --check            PASS
```

The selected test set includes local-library scan trust and UI state, offline/download state, MediaStore persistence, storage-exhaustion classification, natural-language filter inference, manual-filter precedence, and request-generation guards. `adb devices -l` returned no connected devices, so emulator/physical-device offline playback, reconnect and provider interaction checks remain open. No APK was built in this pass.

## Resume verification — Plans 05–06 — 2026-11-22

The following serialized verification completed successfully:

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
./gradlew.bat :shared:jvmTest :app:compileDevDebugKotlin :app:testDevDebugUnitTest `
  --tests '*HomeLayoutTest' `
  --tests '*DeviceCodecsTest' `
  --tests '*AudioEffectSessionPolicyTest' `
  --tests '*AudioRoutingLifecycleTest' `
  --tests '*AudioOutputPolicyTest' `
  --tests '*OutputNegotiatorTest' `
  --tests '*FloatDspChainTest' `
  --tests '*BluetoothAudioTrackerTest' `
  --no-daemon --max-workers=1
```

```text
:shared:jvmTest               PASS — 144 tests
:app:compileDevDebugKotlin    PASS
:app:testDevDebugUnitTest     PASS — 69 selected tests
Selected app tests            69 passed, 0 failed, 0 errors, 0 skipped
git diff --check              PASS
```

Plan 05 now prevents drag and accessible move-up/move-down actions from crossing the pinned/unpinned boundary; visibility and pin state remain preserved. Plan 06 now requires positive decoder evidence for Dolby Atmos; an unavailable/failed decoder query no longer assumes support. Session advertisement, route-selection invalidation, Bluetooth telemetry, output negotiation and DSP regression tests passed. `adb devices -l` still reports no connected device, so actual form-factor layout, Bluetooth/wired/USB route transitions, effect attachment and audio-output measurements remain unverified. Per-headphone correction remains blocked pending licensed measurements and verified device identity/attachment. No APK was built in this pass.

## Resume verification — Plans 07–09 and 1.9.5 follow-up — 2026-11-22

The focused verification run used JDK 21 and one Gradle worker:

```powershell
./gradlew.bat :shared:jvmTest :sharedUi:compileAndroidMain :app:compileDevDebugKotlin `
  :app:testDevDebugUnitTest `
  --tests '*RadioListeningIntentTest' `
  --tests '*OnboardingNavigationTest' `
  --tests '*HomeLayoutTest' `
  --tests '*AppIconStateTest' `
  --tests '*SettingsSearchMatcherTest' `
  --no-daemon --max-workers=1 --console=plain
```

```text
:shared:jvmTest                 PASS — 144 tests
:sharedUi:compileAndroidMain    PASS
:app:compileDevDebugKotlin      PASS
:app:testDevDebugUnitTest       PASS — 32 selected tests
git diff --check                PASS
```

The follow-up fixes keep onboarding strings in `sachit_strings.xml`, make Home layout movement availability share the pinned-boundary rule with the domain operation, and keep radio explanations bounded to 100 entries while refreshing recently reused tracks. The 1.9.5 draft now records the theme, Home stability, onboarding and radio-intent changes. Full phone/TV/Wear verification, lint, production APK rebuilds, signing checks and device acceptance are still pending; no APK was built in this pass.

The additional performance pass also memoizes Home's filtered shelf list and Library's pinned/sorted shelf model, and replaces the Library grid's title-only fallback keys with stable collision-safe keys. These changes do not alter ordering, filters, or visible content; they avoid repeated list work on unrelated recompositions and prevent duplicate lazy-grid keys. Cross-form-factor compilation/tests and final release packaging are being run against this source state.

## Final 1.9.5 verification and packaging — 2026-11-22

The final serialized verification and production packaging completed successfully with JDK 21 and one Gradle worker:

```text
:shared:jvmTest                         PASS — 144 tests
:sharedUi:jvmTest                       PASS — 57 tests
:app:testDevDebugUnitTest               PASS — 1,313 tests, 1 skipped
:tv:testDevDebugUnitTest                PASS — 24 tests
:wearos:testDevDebugUnitTest            PASS — 15 tests
Total                                   PASS — 1,553 tests, 0 failures, 1 skipped
:app:lintDevDebug                       PASS — 0 errors, 18 warnings
:tv:lintDevDebug                        PASS — 0 errors, 17 warnings
:wearos:lintDevDebug                    PASS — 0 errors, 5 warnings
:app/:tv/:wearos:compileProdReleaseKotlin PASS
:app/:tv/:wearos:assembleProdRelease    PASS
git diff --check                        PASS (line-ending warning only)
```

Twelve production APKs were generated: phone, TV and Wear OS each have universal, ARM64, ARMv7 and x86_64 variants. `apksigner verify` passed for all 12; every APK reports version `1.9.5` / version code `44`, and all share the same temporary signing-certificate SHA-256 fingerprint. The temporary keystore configuration remains local and Git-ignored. This fingerprint must be compared with the published 1.9.4 signing certificate before distributing as an in-place update; a different certificate prevents Android from accepting the APK as an update to an existing installation. `adb devices -l` returned no connected devices, so installation, UI, accessibility, audio-route and runtime acceptance remain open. No new performance benchmark is claimed.

## Execution rules
- These proposals do not override existing P0 priorities or active bug fixes.
- Reconcile overlapping plans before implementation; extend the existing owner plan instead of duplicating architecture.
- Verify every claimed capability against source code and tests.
- Follow the repository-wide planning constraints in `plans/README.md`.
