# Plan 047 — Performance pass

**Status: not started.** Verified at commit `61778ab4` — no systematic performance pass has been done. The app 
has some perf tuning (DevicePerformance.kt with heap-aware Coil cache, crossfade gating, decode caps) but no 
comprehensive startup trace, recomposition audit, or APK size review. This plan is a structured perf pass.

## Objective

Run a structured performance pass: (1) startup trace — measure and improve cold/warm startup; (2) recomposition 
audit on Home and Queue (the two heaviest screens) — find and fix unnecessary recompositions; (3) Coil cache 
tuning revisit — check the existing heap % / decode caps are right for today's device landscape; (4) APK size 
check — measure and note the current size, flag bloat. The plan is a measurement + targeted-fix pass, not a 
rewrite.

## Why

- Design system §5.3 lists the perf pass. It's a polish/quality feature — not user-facing new capability, but 
a measurable improvement in startup speed, scroll smoothness, and APK size.
- The app has grown a lot (118k lines, many screens) — a perf pass catches the regressions that accumulate.
- Some perf tuning exists (DevicePerformance.kt) but it's partial — a full pass revisits it.

## What exists today

- `utils/DevicePerformance.kt` — heap-aware Coil memory cache (10% vs 15% of heap), crossfade disabled when 
heap < 128 MB, density-aware image decode cap on the Home "Daily Discover" card. Unit tests in 
`DevicePerformanceTest.kt`.
- No startup trace (the executor checks at implementation time whether AndroidX Startup or a custom trace is 
used; the plan assumes the app measures startup at implementation time).
- No systematic recomposition audit (the executor uses the Compose compiler metrics / recomposition counts at 
implementation time to find hot recompositions on Home and Queue).
- APK size is whatever it is today — the plan measures it and flags bloat.

## Scope

**In scope:**
- **Startup trace**: measure cold + warm startup (the executor picks the tool at implementation time — Android 
Studio Profiler / Perfetto / a custom trace). Identify the top startup bottlenecks (slow first frame, blocking 
init, heavy Compose setup) and fix the top 1-3. Document the before/after.
- **Recomposition audit**: on Home and Queue, measure recomposition counts / hot spots (the executor uses the 
Compose compiler metrics or runtime recomposition hints at implementation time). Find recompositions that aren't 
driven by state changes and fix them (stable data classes, `remember`/`derivedStateOf` where needed, lambda 
stabilization). Document the before/after recomposition count on a typical interaction.
- **Coil cache revisit**: re-check the existing DevicePerformance tuning (heap % for Coil memory cache, crossfade 
gate, decode caps) against today's device landscape. If the existing values are still good, leave them. If 
they're off (e.g. the 128 MB heap gate is too aggressive or not aggressive enough for today's devices), adjust. 
Document the decision.
- **APK size check**: measure the current APK size (per flavor). Flag bloat (large assets, unused resources, 
dependency bloat). Document the size and the top size contributors. No aggressive shrinking for v1 — just 
measure + note + flag the top contributors.
- **Documentation**: at the end, document the before/after for each sub-pass (startup times, recomposition 
counts, Coil cache decision, APK size + top contributors). The plan's result is a perf report + targeted fixes.

**Out of scope:**
- A full performance overhaul — this is a focused pass on startup + Home/Queue recomposition + Coil cache + APK 
size. Other screens / other perf issues are deferred.
- Replacing engines / libraries for perf — defer; this plan measures and fixes within the existing stack.
- Real-time profiling on device for every screen — defer; v1 is startup + Home/Queue + cache + APK size.
- Network perf (stream resolution, download speed) — defer; that's a different pass (network/proxy).
- Native DSP / canvas perf — defer; that's a different pass if/when those features land.

## Affected files

| File | Change |
|------|--------|
| `utils/DevicePerformance.kt` + `DevicePerformanceTest.kt` | Revisit the existing tuning; adjust if needed; add tests for any new tuning decision. |
| Startup trace tooling (the executor adds at implementation time) | A startup trace setup (e.g. a trace function / Profiler config) + the before/after docs. |
| Recomposition audit tooling (the executor adds at implementation time) | Compose compiler metrics config / runtime recomposition hints on Home + Queue. |
| HomeScreen.kt / Queue screen files | Targeted recomposition fixes (the executor picks the hot spots at implementation time). |
| APK size notes (a doc the executor writes at implementation time) | A short perf report: before/after startup, recomposition counts, Coil cache decision, APK size + top contributors. |

## Technical approach

1. **Startup trace** — The executor sets up a startup trace at implementation time (e.g. Profiler / Perfetto / 
a custom trace around the first Compose frame). Measure cold + warm startup. Identify the top bottlenecks 
(blocking init, heavy first-frame Compose, network on startup, DB queries on startup). Fix the top 1-3. 
Document before/after.
2. **Recomposition audit** — The executor measures recompositions on Home and Queue at implementation time 
(Compose compiler metrics / runtime recomposition counts). Find recompositions not driven by state changes 
(unstable data classes, lambdas that change identity, missing `remember`/`derivedStateOf`). Fix the top 
hotspots. Document before/after recomposition counts on a typical scroll/interaction.
3. **Coil cache revisit** — Re-check `DevicePerformance.kt`: heap % for Coil memory cache (10% vs 15%), 
crossfade gate (heap < 128 MB), decode caps on Daily Discover. The executor checks today's device landscape and 
decides: keep, adjust up, or adjust down. Document the decision + rationale. Add/update tests in 
`DevicePerformanceTest.kt` if the tuning changes.
4. **APK size check** — Measure the APK size per flavor at implementation time (e.g. `./gradlew :app:moduleApk 
--debug` or similar). Flag bloat: large assets (images, fonts), unused resources, dependency bloat (big 
libraries). Document the size + top contributors. No aggressive shrinking for v1 — just measure + flag.
5. **Documentation** — A short perf report at the end: startup (before/after), recomposition (before/after on 
Home/Queue), Coil cache decision, APK size + top contributors. The report is the deliverable — it shows what 
was measured and what was fixed.

## Testing

- Build: `./gradlew :app:assembleFossDebug` must pass + `./gradlew :app:testFossDebugUnitTest` must pass 
(the plan's fixes shouldn't break existing tests).
- Manual: verify the perf changes don't regress behavior — startup still works, Home/Queue still scroll smoothly, 
Coil cache still loads images, APK still installs. The executor verifies at implementation time.
- The perf improvements themselves are measured (startup time, recomposition count) — the before/after is the 
proof.

## Performance notes

- This is a measurement + targeted-fix pass — the perf impact is the point. The executor measures before/after 
and documents it. No perf claim without measurement.
- The fixes are scoped: startup, Home/Queue recomposition, Coil cache, APK size. Other perf issues are deferred.

## Rollback

- Revert the specific fixes (startup trace fix, recomposition fix, Coil cache tweak, APK size fix) → perf 
reverts to before. Each fix is separable; the executor rolls back individually if needed.

## Anti-BitChord check

- A perf pass is a standard engineering pass — implement it as Audify: measure, fix the top issues, document. 
Don't scope-creep into a full perf overhaul — keep it focused on the four sub-passes.
- No perf claim without measurement — the before/after is the proof. Don't claim speedups without measuring.
