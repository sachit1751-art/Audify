# Plan 022 — Deepen the architecture (Settings → MusicService → transports)

Written against commit **`1ef56d88`**. Read `CONTEXT.md` for the domain vocabulary and
`docs/adr/` before starting — three decisions in there look like mistakes but are deliberate.

Source analysis: `plans/ARCHITECTURE_AUDIT.md` (candidates C1–C7). Read it first; this plan is
the execution track for the parts that remain.

## Status

**Not started.** One slice has already landed as the template to copy: `CrossfadeController`
(commit `c810e1ce`) — see "What is already done" below.

## Why this plan exists

`app/src/main/kotlin/com/sachit/music/` is 118k lines across 388 files, with ~249 unit tests
(~3% line coverage, concentrated in the few modules that were deliberately extracted).

The two structural problems that cost the most:

1. **`playback/MusicService.kt` is 5,079 lines** with 29 public members spanning ~12 unrelated
   concerns. It churns *less* than any comparable file (28 touches since 2026-07-01, against 126
   for `ui/component`) precisely because it is expensive to change — so features route around it.
2. **Settings-dependent behaviour is untestable by construction.** There are 391
   `rememberPreference(...)` call sites (Compose-bound, untestable) and 131
   `context.dataStore.get(...)` call sites, which resolve via `runBlocking(Dispatchers.IO)` and
   therefore block the caller. `runBlocking` appears 34 times in total.

Everything else in this plan is downstream of fixing the second one.

## Scope rules (inherited, not optional)

- Never bump `versionCode` / `versionName` in `app/build.gradle.kts`.
- Never touch the database schema or Room migrations. C3 below is a pure-Kotlin extraction over
  data already returned by existing queries — if it seems to need a schema change, it is wrong.
- String edits go only in `app/src/main/res/values/sachit_strings.xml` (English). Never edit
  `values-*/sachit_strings.xml`; Crowdin reconciles translations.
- No commits, pushes, or merges without explicit human authorization.
- Markdown changes only inside `plans/`, `docs/`, `CONTEXT.md`, or the top-level planning docs.

## Step 1 — Settings interface (audit candidate C5)

**The prerequisite.** Every later step's testability win depends on it.

### Current shape

Two competing interfaces, no rule about which to use:

| Interface | Count | Mechanism |
|---|---|---|
| `rememberPreference(key, default)` | 391 | Compose-observed, async, correct but untestable |
| `context.dataStore.get(key, default)` | 131 | `runBlocking(Dispatchers.IO)` — blocks the caller |

`utils/DataStore.kt:83` defines the blocking read:

```kotlin
fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, defaultValue: T): T =
    runBlocking(Dispatchers.IO) { data.first()[key] ?: defaultValue }
```

43 of the 131 live in `utils/SyncUtils.kt`. `playback/PlayerConnection.checkAndStartAutomaticSleepTimer()`
alone reads six preferences in six consecutive blocking calls.

### In-scope files

- `utils/DataStore.kt` — add the new interface alongside; do not delete `get()` in this step.
- `constants/PreferenceKeys.kt` (740 lines) — stays as the storage detail.
- New: a `Settings` interface exposing typed, named properties.
- `ui/screens/settings/*.kt` — 176 delegate call sites across the settings screens.

### Steps

1. Define a `Settings` interface with named, typed properties (`Settings.skipSilence`, not
   `dataStore[SkipSilenceKey]`). Start with only the properties the crossfade and stream
   resolution code actually read — roughly 15.
2. Provide two implementations: a `Flow`-backed one for Compose, a suspend one for services.
3. Migrate `CrossfadeController`'s config reads to the interface. This is the proof: its tests
   currently hardcode config through `updateConfig`, and after this step they can be driven
   from a fake `Settings`.
4. Migrate the 131 blocking `get()` call sites, service-first (`SyncUtils`, `MusicService`,
   `PlayerConnection`), then UI. Do this in its own commit per area.
5. Only after every call site has moved, delete `DataStore.get()` so the blocking read is
   unrepresentable rather than merely discouraged.

### Tests

Every settings-dependent behaviour becomes testable by injecting a fake `Settings`: crossfade
gating, sleep timer start/stop, silence skip, per-network quality ceilings, skip-silence
instant mode. These are the highest-value new tests available in the codebase.

### Rollback

Steps 1–3 are additive and independently revertible. Steps 4–5 are mechanical; a step-5 revert
restores one function.

## Step 2 — MusicService decomposition (candidate C1)

### Current shape

`MusicService` holds twelve concerns on one mutable instance. Keyword frequency in the file:
crossfade 54, radio 53, Widget 48, Notification 50, AudioFocus 41, Silence 37, Cast 24,
Automix 21, Alarm 19, Download 13, Lyrics 12.

Its public surface is fifteen ways to say "do something to playback": `playQueue`,
`adoptQueue`, `startRadioSeamlessly`, `getAutomix`, `addToQueueAutomix`, `playNextAutomix`,
`clearAutomix`, `playNext`, `addToQueue`, `toggleLibrary`, `toggleLike`, `addToTargetPlaylist`,
`toggleStartRadio`, `startRadioAsync`, `getStreamUrl` — each with its own failure mode.

### What is already done — use this as the template

`playback/CrossfadeController.kt` (commit `c810e1ce`) is the reference implementation. It owns
the *policy* (enabled/duration/gapless guards, trigger arithmetic) and takes a narrow
`CrossfadeHost` interface for the player facts it needs. `MusicService` supplies the facts and
still performs the swap. 30 unit tests, no emulator, no service binding. **Copy this shape
rather than inventing a new one.**

### Remaining extractions, in order

1. **`StreamResolution`** — owns `utils/InnerTubeXPlayer.kt`, `playback/StreamUrlCache.kt`,
   `playback/StreamSourceHealth.kt`, `playback/NetworkQuality.kt`, retry/fallback policy.
   Interface: `suspend fun resolve(mediaId, quality): StreamHandle`.
   `StreamSourceHealth` is already a clean, injectable-clock, tested module — the extraction
   stopped one level short of where the complexity actually lives. This is where real user-facing
   bugs live (cache poisoning, wrong-client fallback), and it is currently untestable.
2. **`PlaybackIntent`** — collapse the fifteen queue/radio/automix mutations into one verb set
   (`Play`, `EnqueueNext`, `Enqueue`, `Replace`, `ClearAutomix`) plus a result type.
   `MusicService.RadioStartResult` is already the right shape.
3. **`MediaSessionPresenter`** — notification building, widget updates, Cast state,
   `MediaLibrarySessionCallback` glue.

### Tests

`StreamResolution` is the target: retry counting, cache bypass after a `FALLBACK` health state,
per-network ceiling selection, degraded-offload interaction with crossfade. All achievable on
the JVM with a fake cache and an injectable clock.

### Rollback

Each extraction is additive — a new class plus a delegating call site. Reverting restores the
inline behaviour exactly.

## Step 3 — `PlaybackTarget` adapters (candidate C2)

**Cannot start before step 2** moves the transport verbs out of the service.

### Current shape

`playback/PlayerConnection.kt` (703 lines) re-opens the Cast-vs-local question in **20 separate
places**. Every transport method independently re-decides which player, wraps itself in
`try/catch`, and logs its own message:

```kotlin
fun play() {
    try {
        val castHandler = service.castConnectionHandler
        if (castHandler?.isCasting?.value == true) { castHandler.play() }
        else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.playWhenReady = true }
    } catch (e: Exception) { Timber.tag(TAG).e(e, "Error in play") }
}
```

The failure mode is silent and expensive: add a transport verb, forget the Cast branch, and the
feature works on the phone and does nothing on the speaker.

A *second* concern is duplicated seven times independently of transport — the Listen Together
guest guard (`if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) return`)
on `playQueue`, `startRadioSeamlessly`, `startRadioWithFeedback`, `playNext`, `addToQueue`,
`toggleLike`, `toggleLibrary`.

### Steps

1. Define `PlaybackTarget` with `play`, `pause`, `togglePlayPause`, `seekTo`, `skipNext`,
   `skipPrevious`.
2. Implement `LocalPlayerTarget` and `CastPlayerTarget`; select one at connection time.
3. Collapse the 20 branches to 2.
4. Extract the guest guard into a single `GuestGuard` consulted at the connection's front door,
   not at seven call sites.

### Tests

A `FakePlaybackTarget` makes the whole `PlayerConnection` surface testable with no ExoPlayer,
no Service binding and no Binder. It currently has **zero** tests despite being the app's
most-used interface (71 UI call sites).

## Not in this plan — deliberate

- **C3 `DatabaseDao` domain logic** (176 queries hiding ~60 default methods, 20 inline
  `Collator` sites, 13 `when (sortType)` dispatchers). Accepted as a Room constraint for now;
  see `docs/adr/0003`. Extract only opportunistically, when touching the file anyway.
- **C4 artist alias resolution** (51 call sites, two competing APIs). Recorded in
  `docs/adr/0001` with the full rationale; the sweep is mechanical but must land in one commit —
  a partial migration is worse than either end state.
- **C6 Listen Together orchestration** (2,069-line `ListenTogetherManager`, 265-line event
  `when`). Needs a live two-client room to verify first; a pure-logic refactor of code nobody
  can exercise end to end is how regressions hide.
- **C7 documentation** — done: `CONTEXT.md`, `docs/adr/0001–0003`, this plan.

## Human gates

- **Crossfade must be listened to on real hardware** before any prerelease is promoted to a full
  release. 30 unit tests prove the trigger rules; they cannot prove the overlap sounds right.
- Step 3 changes playback routing. Verify with a real Cast device, not just the emulator.
- Step 1 step 5 deletes `DataStore.get()`. Confirm zero remaining call sites first — a missed one
  is a runtime block on a hot path.