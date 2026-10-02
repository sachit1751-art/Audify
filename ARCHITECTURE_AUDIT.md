# Architecture Audit — Sachit

**Date:** 2026-10-02
**Scope:** `app/` (118k lines Kotlin, 388 files) + `innertube/` (9.6k lines)
**Method:** hot-spot-weighted read of the 40 most-changed files since 2026-06, plus structural scans of every module boundary.
**Note:** written at the explicit request of the repo owner. AGENTS.md rule 1 forbids the agent from editing markdown; the owner authorized this exception.

---

## Domain vocabulary

Sachit is a third-party YouTube Music client. The domain nouns that matter for this audit:

| Term | Meaning | Lives in |
|---|---|---|
| **Queue** | The ordered list of tracks the user will hear next, plus its provenance (a playlist, an album, a radio seed) | `playback/queues/` |
| **Radio** | An unbounded queue grown by related-track endpoints from a seed track | `playback/queues/*Radio.kt` |
| **Automix** | A side queue of related tracks interleaved into the main queue | `MusicService.automixItems` |
| **Library** | The locally-persisted mirror of the user's YouTube library: liked songs, subscriptions, saved playlists | `db/`, `utils/SyncUtils.kt` |
| **Sync** | The reconciliation loop pushing local Library mutations back to YouTube, with coalescing and retry | `utils/SyncUtils.kt` |
| **Listen Together room** | A multi-user session where one host's playback drives every guest's player | `listentogether/` |
| **Stream health** | Per-track memory of which audio source recently failed, driving fallback to a fresh source | `playback/StreamSourceHealth.kt` |
| **Artist alias** | A user-supplied display-name override for an artist, keyed by channel id | `utils/ArtistNameAliases.kt` |
| **Provider** | A lyrics source behind a common `LyricsProvider` interface, ordered by user preference | `lyrics/` |

There is no `CONTEXT.md` and no `docs/adr/` in this repo. That absence is itself a finding (see **C7**).

---

## Shape of the codebase

```
app/src/main/kotlin/com/sachit/music/     118,147 lines / 388 files
├── ui/                3.7M   4 subsystems: screens (2.0M), component (649K),
│                              menu (616K), player (364K)
├── playback/          429K   MusicService.kt alone = 5,079 lines
├── viewmodels/        296K   28 ViewModels
├── utils/             271K   SyncUtils 1,600 · DataStore 161
├── db/                250K   DatabaseDao 2,115 (238 fns / 176 @Query)
├── listentogether/    216K   Manager 2,069 + Client 1,979
├── lyrics/            164K   7 providers behind LyricsProvider
├── MainActivity.kt    88K    1,736 lines, 10 CompositionLocals
└── eq/ recognition/ discord/ widget/ di/ extensions/ constants/
```

**Churn ranking since 2026-07-01** (files touched, by directory) — where future work will land:

```
ui/component          126      ← hottest by a wide margin
db/entities            93
viewmodels             87
ui/menu                61
ui/screens/settings    55
utils                  54
ui/screens             52
lyrics                 42
playback               28      ← churn is low, but file size is extreme
```

The mismatch is the story: **the hottest area (`ui/component`, `db/entities`) is not where the architectural risk lives, and the highest-risk area (`playback`) barely churns** — because touching `MusicService.kt` is expensive enough that people route around it.

**Test coverage reality:** 39 test files, ~4,000 lines, against 118k lines of production code (≈3%). The tests are concentrated exactly where extraction was deliberate — `betterlyrics/`, `discord/`, `db/` migration & dedup, `playback/QueueHistoryState`, `playback/StreamSourceHealth`, `utils/StreamPicker`, `utils/DevicePerformance`. That is a healthy *shape* but it means the untested 97% is untested for structural reasons, not oversight.

---

## Candidates

Seven deepening opportunities, ordered by the leverage they would return.

---

### C1 — `MusicService` is a shallow module wearing a deep module's clothes

**Strength: Strong**

**Files:** `playback/MusicService.kt` (5,079 lines), `playback/PlayerConnection.kt` (703)

**Problem.** `MusicService` has 29 public members and 117 total functions across at least twelve independent concerns, all sharing one mutable instance and one lifecycle:

```
crossfade        54 refs    radio           53
Widget           48        Notification     50
AudioFocus       41        Silence          37
Cast             24        Automix          21
Alarm            19        Download         13
Lyrics           12
```

Its interface is not simpler than its implementation — `playQueue`, `adoptQueue`, `startRadioSeamlessly`, `getAutomix`, `addToQueueAutomix`, `playNextAutomix`, `clearAutomix`, `playNext`, `addToQueue`, `toggleLibrary`, `toggleLike`, `addToTargetPlaylist`, `toggleStartRadio`, `startRadioAsync`, `getStreamUrl` is fifteen ways to say "do something to playback", each with its own failure mode. The class is *big*, not *deep*: a deep module hides complexity behind a small interface, and this one leaks all of it.

**Deletion test:** deleting `MusicService` would not concentrate complexity anywhere — it would scatter it into `MainActivity`, the notification receiver, the widget, the alarm receiver, and Cast. Complexity **moves**, it doesn't concentrate. That is the signature of a shallow module.

**Solution.** Carve four modules that each already have a latent home:

- `StreamResolution` — owns `InnerTubeXPlayer`, `StreamUrlCache`, `StreamSourceHealth`, `NetworkQuality`, retry/fallback policy. Its interface becomes `suspend fun resolve(mediaId, quality): StreamHandle`. This is the piece where the pure-function extraction already happened (`StreamSourceHealth` is a clean, injectable-clock, 107-line-tested module) — the extraction stopped one level short of where the real complexity lives.
- `CrossfadeController` — owns `secondaryPlayer`, `fadingPlayer`, `crossfadeJob`, `scheduleCrossfade`, `startCrossfade`, `isNextItemGapless`. Interface: `fun onTransition()`, `fun onSeek()`. Almost pure scheduling logic, trivially testable with a fake clock.
- `PlaybackIntent` — collapses the fifteen queue/radio/automix mutations into one verb set (`Play`, `EnqueueNext`, `Enqueue`, `Replace`, `ClearAutomix`) plus a result type. `RadioStartResult` already exists and is the right shape.
- `MediaSessionPresenter` — notification building, widget updates, Cast state, MediaLibrarySessionCallback glue.

**Benefits.** Locality: crossfade rules stop being interleaved with notification code in one file. Leverage: the Cast adapter and the local-ExoPlayer adapter become swappable behind `PlaybackIntent`, which is the only way `PlayerConnection`'s twenty `castHandler` branches (see **C2**) ever collapse. Tests: `CrossfadeController` and `StreamResolution` are the two places where real bugs live — off-by-one in the trigger time, cache-poisoning after a source failure — and neither is currently reachable from a test.

---

### C2 — `PlayerConnection` re-implements the Cast branch 20 times instead of hiding it

**Strength: Strong**

**Files:** `playback/PlayerConnection.kt`

**Problem.** `PlayerConnection` is the UI's handle on playback, and its whole job should be: expose state, forward intents. Instead, every transport method re-opens the same question:

```kotlin
fun play() {
    try {
        val castHandler = service.castConnectionHandler
        if (castHandler?.isCasting?.value == true) { castHandler.play() }
        else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.playWhenReady = true }
    } catch (e: Exception) { Timber.tag(TAG).e(e, "Error in play") }
}
```

That shape repeats for `play`, `pause`, `togglePlayPause`, `seekTo`, `seekToNext`, `seekToPrevious` — **20 `castHandler` references** across the file. Each site independently re-decides *which* player, wraps itself in `try/catch`, and logs its own message. The bug class this produces is silent and expensive: add a new transport verb and forget the Cast branch, and the feature works perfectly on the phone and does nothing on the speaker.

Layered on top, seven methods (`playQueue`, `startRadioSeamlessly`, `startRadioWithFeedback`, `playNext`, `addToQueue`, `toggleLike`, `toggleLibrary`) carry a *second* repeated concern — a Listen Together guest guard (`if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) return`) — that has nothing to do with transport.

**Deletion test:** deleting `PlayerConnection` and pointing the 71 UI call sites at `MusicService` directly would **not** concentrate complexity, because the complexity that lives here is already a thin scatter of conditionals. It would just move them. Shallow.

**Solution.** One `PlaybackTarget` interface with two adapters — `LocalPlayerTarget` and `CastPlayerTarget` — chosen once at connection time:

```kotlin
interface PlaybackTarget {
    fun play(); fun pause(); fun togglePlayPause()
    fun seekTo(position: Long); fun skipNext(); fun skipPrevious()
}
```

`PlayerConnection` holds a single `target` and forwards. The 20 branches become 2. Separately, extract the guest guard into a single `GuestGuard` consulted at the connection's front door rather than at seven call sites.

**Benefits.** Locality: "does Cast change this behaviour?" has exactly one answer per verb. Leverage: adding a third target (Android Auto is already half-way there via `MediaLibrarySessionCallback`) stops being a rewrite. Tests: a `FakePlaybackTarget` makes the entire `PlayerConnection` surface testable with no ExoPlayer, no Service binding, no Binder — which is why `PlayerConnection` currently has zero tests despite being the app's most-used interface.

---

### C3 — `DatabaseDao` is 176 queries and 62 default methods wearing one interface

**Strength: Strong**

**Files:** `db/DatabaseDao.kt` (2,115 lines), `db/MusicDatabase.kt` (868)

**Problem.** `DatabaseDao` declares **238 functions** against **176 `@Query` annotations**. The gap is roughly 60 default methods with real Kotlin bodies — and those bodies are where the domain logic actually lives:

- **20 sites** construct `Collator.getInstance(Locale.getDefault())` inline to sort by title, inside `map { }` operators on Flows.
- **13 sites** are `when (sortType)` dispatchers that pick a query by sort enum — the same four-way shape repeated for songs, liked songs, artist songs, artists, bookmarked artists, albums, liked albums, uploaded albums, playlists, downloaded songs, uploaded songs, podcast episodes, saved episodes.
- `songs(sortType, descending)` also carries a hand-rolled album-grouping algorithm (`groupBy { it.album?.title }.flatMap { ... sortedBy ... }`) that exists purely because SQLite can't express it.
- `replaceSongArtists`, `insert` (×2), `update` (×4) contain artist-alias resolution and merge logic inline.
- The file imports `Collator`, `Locale`, `MediaMetadata`, `ArtistNameAliases`, and `ui.utils.resize` — a DAO that reaches into the UI package is a seam that has dissolved.

Room requires default methods to live in the interface, so this is Room's constraint, not laziness. But the constraint is being used as an excuse: the sorting and grouping rules are *domain* rules being smuggled through a persistence adapter.

**Deletion test:** delete `DatabaseDao` and the queries must live somewhere — but the 60 default methods are pure functions over data that has nothing to do with SQLite. They'd concentrate cleanly in a `LibraryQueries` module. Deleting the *whole file* doesn't concentrate, but deleting the default methods *does*. That's the signal: the queries are legitimately in place, the logic is not.

**Solution.** Two modules:

- `LibrarySort` — a pure, JVM-testable module holding the `Collator` comparators, the sort-enum dispatch, and the album-grouping algorithm. Call it once with `Locale.getDefault()` captured at construction, not 20 times per Flow emission (which is a real, if minor, per-frame cost today).
- `LibraryAssembly` — the entity-merge and alias-resolution rules currently inside `insert`/`update`/`replaceSongArtists` default methods.

`DatabaseDao` shrinks to queries plus thin delegations. Both new modules are pure functions over data — exactly the shape that the existing `db/` tests (`SongMetadataEditingTest`, `AddSongsToPlaylistTest`, `PlaylistDuplicatesBatchedTest`, `HistoryDatabaseDeduplicationTest`) already know how to test.

**Benefits.** Locality: "how do we order songs by artist" is answered in one file instead of 13. Leverage: adding a sort option becomes a one-line enum change instead of a four-site query addition. Tests: sorting and grouping rules become pure-function tests with no Room, no Robolectric, no migration fixture — the current `db/` test suite needs 4,000 lines of scaffolding to test comparatively little.

---

### C4 — 51 call sites resolve artist aliases by hand; the rule has no single owner

**Strength: Worth exploring**

**Files:** `utils/ArtistNameAliases.kt`, `models/MediaMetadata.kt`, `extensions/MediaItemExt.kt`, `db/DatabaseDao.kt`, `playback/MediaLibrarySessionCallback.kt`, `ui/component/Items.kt`, and 10 more

**Problem.** The alias rule is "display name = user override if one exists, else the original." It is expressed **51 times across 14 files**, in two different shapes:

```kotlin
ArtistNameAliases.resolve(it.id, it.name)                    // 30 sites — reads global singleton state
ArtistNameAliases.resolve(aliasMap, it.id, it.name)          // 21 sites — reads CompositionLocal
```

Both exist because the singleton needs a `Context` to initialize but the UI needs synchronous reads during composition, so `MainActivity` mirrors the alias map into `LocalArtistNameAliases` (14 consumers). Two APIs for one rule, and each new call site has a 50/50 chance of picking the wrong one — a real hazard, because the singleton form silently returns the *unresolved* name if `initialize()` hasn't run, while the map form works. That's a bug class that only appears on cold start or in a test.

**Deletion test:** deleting `LocalArtistNameAliases` and forcing everyone through the singleton would *concentrate* complexity (removes a dual-API hazard) rather than move it. **Concentrates → shallow, deepen it.**

**Solution.** Resolve once, upstream, at the single point where a `MediaMetadata` enters the system. `MediaMetadata.toMediaMetadata()` in `models/MediaMetadata.kt` already touches aliases at 3 sites; make that the *only* place, and delete the 48 downstream call sites plus the `CompositionLocal`. Display name becomes a property of the model rather than a step every reader must remember.

**Benefits.** Locality: one rule, one owner, one place to test. Leverage: new surfaces (widgets, notifications, Android Auto) get correct names for free. Tests: `ArtistNameAliasesTest` (57 lines) currently tests the alias *store*; after this change it can test the *rule*, which is the part that actually breaks.

**Risk:** DB-persisted names are resolved at write time in some paths (`SyncUtils`, `ArtistViewModel`). Those need to keep resolving on write — so the change is "resolve on ingest **and** on persist, not on read", which needs a careful pass over the 51 sites.

---

### C5 — Preferences are read two incompatible ways, one of them blocking

**Strength: Worth exploring**

**Files:** `utils/DataStore.kt` (161), `constants/PreferenceKeys.kt` (740), 391 call sites

**Problem.** The codebase has two preference interfaces and no rule about which to use:

| Interface | Count | Mechanism |
|---|---|---|
| `rememberPreference(key, default)` | **391** | Compose-observed, async, correct |
| `context.dataStore.get(key, default)` | **131** | `runBlocking(Dispatchers.IO)` — blocks the caller |

`DataStore.get()` is defined as:

```kotlin
fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, defaultValue: T): T =
    runBlocking(Dispatchers.IO) { data.first()[key] ?: defaultValue }
```

131 call sites can block a thread. 43 of them sit in `SyncUtils` alone, and `PlayerConnection.checkAndStartAutomaticSleepTimer()` reads six preferences in six consecutive blocking calls. `runBlocking` appears 34 times total.

This is not just a perf smell — it's a **testability seam that was never made**. The 391 `rememberPreference` sites are, by construction, untestable: they need a Composable. The 131 `get()` sites are testable in principle but block, so nobody writes those tests either. Preference-dependent logic is the single largest untested surface in the app.

**Deletion test:** deleting `DataStore.get()` would concentrate — every blocking read becomes a suspend read at a real async boundary. **Concentrates → deepen it.**

**Solution.** One interface. Make settings a proper module: a `Settings` interface exposing typed, named properties (`Settings.skipSilence`, not `dataStore[SkipSilenceKey]`), backed by a `Flow`-based implementation for Compose and a suspend implementation for services. The 740-line `PreferenceKeys.kt` stays as the storage detail; the type-safe named surface becomes the interface.

**Benefits.** Locality: "what settings exist and what are their defaults" answered once, not 131 times. Leverage: adding a setting becomes a key + a property + a settings-screen row, with the compiler enforcing that all three exist — today nothing enforces that, which is why `PreferenceKeys.kt` has grown to 740 lines with drift. Tests: every settings-dependent behaviour (crossfade gating, sleep timer, silence skip, per-network quality) becomes testable by injecting a fake `Settings`. This is the highest-leverage *unlock* in the audit — it is the prerequisite that makes several other candidates testable.

---

### C6 — Listen Together: the protocol is clean, the orchestration is not

**Strength: Worth exploring**

**Files:** `listentogether/ListenTogetherManager.kt` (2,069), `ListenTogetherClient.kt` (1,979)

**Problem.** This is the codebase's best and worst module at once. `MessageCodec.kt` (91 lines) and `Protocol.kt` (315 lines) are genuinely deep — a wire format behind a clean interface, tested (`MessageCodecTest`, `ServerClockTest`). That part is right.

The other 3,948 lines are not. `ListenTogetherManager.handleEvent()` is a 265-line `when` over the event union; `handlePlaybackSync` spans 408 lines; `syncToTrack` spans 139. Plus a duplicated pair the compiler can't catch:

```kotlin
private fun stopQueueSyncObservation() { ... }   // line 1942
private fun stopQueueSyncObservation() { ... }   // line 1962  — two definitions
```

Two private functions with identical signatures in the same class means one is dead code that was meant to be removed in a merge. Drift correction, guest reconciliation, queue mutation queuing, volume sync, and mute state restoration all live as sibling methods on one object.

**Deletion test:** deleting `ListenTogetherClient` (1,979 lines) would concentrate complexity into `Manager`, not eliminate it — the two are entangled through a shared mutable `Context` and a raw `Context.dataStore.get()` inside the client (11 sites). Real seam, poorly placed.

**Solution.** Keep `Protocol` and `MessageCodec` exactly as they are — they are the model to copy elsewhere. Split the Manager along its natural seams:

- `GuestReconciler` — given host state and local state, decide what to do. Pure, table-driven, fully testable.
- `DriftCorrector` — the periodic nudge when guest and host drift apart. Needs only a clock and a player.
- `QueueMutations` — the enqueue/debounce/apply path.

Each collapses the 265-line `when` into a dispatch table, because the event union already *is* the interface — the `Protocol.kt` types are the seam, they're just not being used as one.

**Benefits.** Locality: "guest jumps to host's track" is one testable function, not 400 lines inside an event handler. Leverage: new event types become a new branch in one dispatch table instead of an arm in a 265-line `when`. Tests: `MessageCodec` already proves the team can test this domain — the payoff was simply never collected at the orchestration layer.

---

### C7 — No `CONTEXT.md`, no ADRs, and a two-package history in the git log

**Strength: Speculative (but cheap)**

**Files:** repo root; `app/src/main/kotlin/com/metrolist/` → `com/sachit/` rename visible in history

**Problem.** Three documentation-shaped gaps:

1. **`CONTEXT.md` does not exist.** The domain vocabulary in this audit — Queue, Radio, Automix, Library, Sync, room, stream health — is currently reconstructed by reading 5,079 lines of `MusicService`. A new contributor has no map.
2. **`docs/adr/` does not exist.** Decisions like "we resolve aliases on write *and* on read" (C4), "the extractor returns one stream so the picker is speculative" (`StreamPicker`'s own doc comment says so), and "Room default methods are where domain logic must live" (C3) are all load-bearing and all undocumented. They will be re-litigated by the next audit — including this one.
3. **The git history straddles two package roots.** `com.metrolist.music` and `com.sachit.music` both appear in commits since 2026-06. Current source is fully migrated (0 files remain under `metrolist`), but churn analysis spanning the rename will show phantom hot spots — `com/metrolist/music/playback/MusicService.kt` (45 touches) and `com/sachit/music/playback/MusicService.kt` (5) are *the same file*. Any future audit, or any `git log`-based tooling, needs to know this.

**Solution.** Create `CONTEXT.md` from the vocabulary table above. Create `docs/adr/` with three ADRs: the alias-resolution seam (C4), the `StreamPicker` speculative status, and the Room default-method constraint (C3). Add a note to `CONTEXT.md` about the package rename so churn tooling normalizes it.

**Benefits.** Locality: future audits start from the map instead of from `MusicService`. Leverage: prevents re-litigation, which is the actual cost. Cheap relative to everything else here.

---

## What is already right

Worth recording so a future audit doesn't propose undoing it:

- **`playback/queues/`** — seven queue types behind a 61-line `Queue.kt` interface. Deep, small, correctly factored. `EmptyQueue` at 19 lines is the right answer to a real problem.
- **`lyrics/LyricsProviderRegistry`** — seven providers behind `LyricsProvider`, with order serialized through one registry. The clearest example of the shape this audit recommends elsewhere.
- **`StreamSourceHealth`, `QueueHistoryState`, `DevicePerformance`, `VisualizerLevels`, `ArtistNameAliases`** — small, pure, injectable-clock, unit-tested. These are the extraction pattern the rest of the codebase should be copying.
- **`StreamPicker`'s honesty** — its doc comment explicitly says the candidate-based picker is unused until the fork surfaces multiple audio variants. A speculative module that says so out loud is better than a speculative module that pretends.

---

## Top recommendation

**Start with C5 (preferences), then C1 (MusicService).**

The ordering is not by severity — C1 and C2 are the most *visibly* broken. It's by dependency:

C5 is the prerequisite. Every other candidate wants to become testable, and settings-dependent behaviour is currently untestable by construction (391 Compose-bound delegates, 131 blocking reads). Fixing it first means C1's `CrossfadeController`, C3's `LibrarySort`, and C4's alias rule all inherit a fake-able settings surface for free.

C1 is the payoff. `MusicService` at 5,079 lines is the single hardest file in the repo to change safely, it barely churns (28 touches) precisely because it is expensive, and every feature in the recent commit log — per-network quality ceilings, lossless, song radio, smart auto-playlists — has had to route around it. It is also the reason **C2** exists: you cannot fix the 20 duplicated Cast branches until transport verbs are consolidated behind one interface, and you cannot consolidate them without pulling them out of the service.

C2, C3, C6, C4, C7 follow in that order. **C7 is worth doing immediately regardless** — it costs an hour and makes the next audit cheaper than this one.

### Suggested first slice

Small enough to review in one sitting, valuable enough to prove the pattern:

1. Create `Settings` with typed properties for the ~15 settings `CrossfadeController` needs.
2. Extract `CrossfadeController` from `MusicService` behind `fun onTransition()` / `fun onSeek()`.
3. Write `CrossfadeControllerTest` — trigger-time math, gapless skip, repeat-one, duration-unset, disabled.

That slice touches ~400 lines out and adds ~200 in, deletes roughly 300 lines from the hardest file in the repo, and produces the first test that has ever run against `MusicService`'s crossfade logic. If it goes well, `StreamResolution` is the natural next extraction — it is the piece where actual user-visible bugs (cache poisoning, wrong-client fallback) live, and `StreamSourceHealth` already proves the shape works.

---

## Caveats

- This was a static read. No code was changed, nothing was built, and no test was run — the structural claims are verified by grep and by reading, but nothing here is verified at runtime.
- Line counts and churn figures come from `git log` since 2026-06-01 and are affected by the package rename described in C7.
- "Untouched" claims mean "no test file references it", not "verified unreachable".
- The churn ranking deliberately de-prioritised `ui/component` and `db/entities` despite them being the hottest. That is a judgement call: high churn in a well-factored area is a *good* sign, not a smell. A separate UI-focused audit may disagree.