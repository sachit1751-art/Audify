# Audify navigation performance scenarios

Capture each scenario with `powershell -ExecutionPolicy Bypass -File tools/perf/Start-AudifyTrace.ps1 <scenario>` on one connected Android device. Keep the device, refresh rate, battery mode, account, network and content set constant between comparisons. Use the same app build and repeat each scenario at least five times; compare medians, not the best run.

| Scenario | Manual actions during the trace | Primary signals |
| --- | --- | --- |
| `home_first_scroll` | Cold launch; wait for Home content; perform one slow full-screen scroll, then one medium fling. | Startup-to-first-content, frame timeline, image decode and main-thread work |
| `home_scroll` | From settled Home, scroll through at least six shelves, reverse direction, then fling to the bottom. | Janky frames, missed deadlines, nested LazyRow prefetch and GC |
| `player_open_close` | Open the mini-player, wait for the artwork morph to settle, close it; repeat five times. | Frame timeline during sheet motion, RenderThread/GPU and layout work |
| `lyrics_scroll` | Open a track with synced lyrics; scroll forward and backward through a long section while audio continues. | UI frame time, lyric clock recomposition and main-thread contention |
| `playlist` | Open a long playlist, scroll to the middle, return to the top, open a track menu and dismiss it. | Lazy item composition, artwork requests, allocations and frame timeline |
| `search` | Search a common term, switch filters, scroll results, clear and run a second query. | Typing latency, result composition, image decode and list jank |
| `settings_walk` | Open Settings; visit Appearance, Performance and Backup; return to Settings between each. | Navigation latency, recomposition and memory churn |
| `artist_album` | Open an artist, scroll its album shelves, open an album, scroll tracks, then return. | Shared-cover transition, nested row prefetch and list frame time |

## Comparison rules

- Capture five cold-start runs and five warm navigation runs per build.
- Keep animation scale and display refresh rate unchanged; record the values with the trace.
- Compare p50 and p90 frame duration, janky-frame percentage, app CPU time and peak memory.
- Inspect traces in Perfetto UI. Do not infer a performance win from APK size or a single smooth run.
- `core/app/src/main/baseline-prof.txt` is the current broad baseline profile. Replace or augment it with a generated profile only after a repeatable Macrobenchmark journey has been recorded and validated.
