# BitChord → Audify Feature Audit

> **Status:** PLANNING ONLY. Nothing in this document has been implemented; the partial implementation from an earlier session was reverted before completion. The four Tier-A features are now scheduled as Phase 1 plans in `AUDIFY_ROADMAP.md`.
> **Audify:** `sachit1751-art/Audify` (this repo, versionName 13.6.3, minSdk 26, targetSdk 36, Compose + Media3)
> **BitChord:** `kushagrasinghx/BitChord` (local clone at `BitChord/`, latest tag `v1.7`, commit `2f14700`)
> **Licenses:** Both projects are GPL-3.0. Capability-level study is unrestricted; implementations will be original code with Audify-native UX — no BitChord UI, layout, naming, or visual language.
>
> Companion docs: `COMPETITOR_FEATURE_MATRIX.md` (ecosystem-wide), `AUDIFY_INNOVATION_IDEAS.md` (beyond-copy ideas), `AUDIFY_DESIGN_SYSTEM.md` (identity), `AUDIFY_ROADMAP.md` (execution order).

---

## 1. Executive Summary

BitChord and Audify overlap heavily on the core client feature set (search, stream extraction, lyrics, downloads, Listen Together, EQ, Discord, widgets, sleep timer, crossfade, automix). Audited against BitChord v1.7, Audify's genuine gaps concentrate in five areas:

1. **Queue history** — BitChord keeps a 25-song scrollback behind the playhead and marks songs skipped by queue jumps; Audify retains past timeline windows but has no skip tracking and no Earlier/Up-next separation.
2. **Per-network audio quality ceilings** — BitChord keeps separate max-quality settings for Wi-Fi and mobile data; Audify has one global knob.
3. **Source fallback engineering** — BitChord has an explicit one-way "failed alternative → direct YouTube" escape plus source health checks; Audify retries blindly.
4. **In-player diagnostics** — BitChord surfaces codec/bit-depth/sample-rate/stream-health on the player; Audify's richer `ShowMediaInfo` dialog is menu-buried and static.
5. **Resilience polish** — BitChord's 197 open issues document real failure modes (lock-screen controls, stale shuffle, artwork loading, SMB auth) whose lessons inform the plans below.

Planned Tier A (Phase 1): **queue history with skip tracking**, **in-player Signal panel**, **per-network quality ceilings**, **source health with fallback memory**. All additive; no DB schema changes; no existing behavior removed.

## 2. Audify Current Architecture (audit snapshot)

```text
Audify (com.sachit.music)
├── Current features
│   ├── Playback: Media3 ExoPlayer in MusicService, crossfade (player swap, gapless
│   │   option), skip-silence, varispeed, perceptual volume normalization, custom-EQ
│   │   DSP, Cast, sleep timer, persistent queue + automix queue, auto-load-more,
│   │   song/album/local radio, Listen Together, alarm, quick-settings tiles
│   ├── Content: innertube module, PO token, InnerTubeX stream resolution,
│   │   KUGou/BetterLyrics/LRCLIB lyrics, word-sync, romanization, translation,
│   │   Shazam/Paxsenix recognition, lossless plan (016)
│   ├── Library: Room DB, smart auto-playlists, monthly/yearly recap, downloads with
│   │   metadata + artwork embedding, storage settings, backup/restore, podcasts
│   ├── UX: Compose M3, artwork tap-toggle, double-tap seek, corner-radius picker,
│   │   mesh gradient + artwork visualizer, mini player up-next peek + lyric line,
│   │   widgets, Android Auto, Discord RPC
│   └── Perf: heap-aware Coil cache, crossfade gating, decode caps (DevicePerformance.kt, tested)
├── Missing capabilities
│   ├── Skip tracking / Earlier concept in queue UI
│   ├── Per-network quality ceilings
│   ├── Source health memory + explicit fallback state
│   ├── Live in-player diagnostics surface
│   ├── Local on-device library, WebDAV/SMB, ListenBrainz
├── Weak implementations / bottlenecks
│   ├── MusicService god-class (~5k lines) — extend incrementally only
│   ├── Blind stream retry (no failure memory)
│   ├── Single global AudioQuality
├── UX problems
│   ├── No "what just played / what did I skip" answer
│   └── Manual quality toggling when leaving Wi-Fi
└── Safe extension points
    ├── PlayerConnection StateFlows (queueWindows, currentWindowIndex, currentStreamClient)
    ├── Queue.kt keyed LazyColumn (uid-stable, drag/dismiss math localized)
    ├── constants/PreferenceKeys.kt (additive keys)
    ├── MusicService.onMediaItemTransition + onPlayerError (single funnels)
    └── Data-source factory resolution choke point
```

## 3. BitChord Capability Map (verified in source)

| Capability | BitChord implementation (v1.7) | Audify | Gap → plan |
|---|---|---|---|
| Queue history | `playback/QueueHistory.kt` pure functions: 25-entry cap, `skippedByQueueJump`, `queueStartingAt`; #1 requested feature (issue #200) | timeline keeps past windows; no skip marks, no Earlier view | Roadmap 1.3 |
| Per-network quality | `AppSettings.kt`: wifi/mobile ceilings, `meteredConnection` flow, `wifiOnlyDownloads` | single global quality | Roadmap 1.1 |
| One-way fallback | `PlaybackFallback.kt`: failed alternative → pinned direct-YouTube URI for the session | blind retry + cache invalidate | Roadmap 1.4 |
| Source health | `data/sources/*` checks + Sources screen | none | Roadmap 1.4 |
| Stats for nerds | player sheet: codec, bit depth, sample rate, stream client | `ShowMediaInfo` dialog (richer, but static + buried) | Roadmap 1.2 |
| Automix DSP | `native/` C++ analyzer, ONNX vocal/mel models, beat matching | automix present, no native DSP | no plan (perf/cost) |
| Local music | LocalMusicScreen + scanner + local radio | `LOCAL_ALBUM_RADIO` plumbing only | Roadmap 2.1 |
| SMB/WebDAV | full clients; v1.7 guest-login bug cluster | none | Roadmap 5.5 (WebDAV only) |
| ListenBrainz | `data/scrobbling` (Last.fm + ListenBrainz) | Last.fm only | Roadmap 3.3 |
| Version alignment | `VersionAudioAligner` | none | deferred (incorrect-match risk) |
| Queue persistence | v1.7 queue-preservation branch | already stronger (15 s periodic + state) | no work needed |
| Cache marking | mark fully-cached only on natural finish | same behavior exists (`lastTransitionedMediaId`) | parity — keep |
| Buffer recovery | stability passes | `initialBufferRecovery` + retry jobs (ahead) | sharpen via 1.4 |
| Tablet player | `e96ac9b` | partial | Roadmap 2.2 |
| Romanization | v1.7 secondary line | shipped (`RomanizationSettings`) | parity |
| Liquid glass / canvas | Haze bars, animated canvas | mesh gradient + visualizer (own identity) | deliberately not copied |
| Audio pipeline viz | PR #416 decorative | — | deliberately not copied |
| Encrypted-prefs backup safety | `b293022` | to audit | Roadmap 5.6 |

## 4. Improvement Audit (planned, not implemented)

Each item: BitChord behavior / Audify current / Gap / Benefit / Audify-native solution / Approach / Complexity / Risk / Dependencies / Plan.

### Feature 01 — Queue History + Skip Marks
- **BitChord:** 25-entry scrollback; forward jumps mark jumped-over rows skipped; selecting a row starts the queue there.
- **Audify current:** Timeline already retains past windows (`getQueueWindows` walks `getPreviousWindowIndex`), but: no skip concept, no Earlier/Up-next visual boundary, session restart wipes queue entirely (persistent restore replaces it).
- **Gap:** No answer to "what played / what did I skip" in one glance.
- **Why it matters:** Top user request in the reference ecosystem; core queue trust.
- **Audify adaptation (differs from BitChord):** Skip marks stored as a session-scoped id set (`QueueHistoryState`, bounded FIFO, pure + unit-tested); an "Earlier" label+hairline divider rendered *inside the active row* so all existing drag/dismiss/automix index math stays untouched; skipped rows dimmed 50%; new-queue start clears marks; Listen Together guests excluded from marking.
- **Technical approach:** `playback/QueueHistoryState.kt` (pure), hook in `onMediaItemTransition` (SEEK reason, gap > 1), expose `skippedInQueueIds` StateFlow via `PlayerConnection`, `Queue.kt` divider + alpha.
- **Complexity:** Medium. **Risk:** Low (additive, UI-local). **Dependencies:** none.
- **Plan:** logic + tests → service hook → UI → `assembleFossDebug` + regression check. Full steps: `AUDIFY_ROADMAP.md` 1.3.

### Feature 02 — In-Player Signal Panel
- **BitChord:** stats-for-nerds sheet on the player.
- **Audify current:** `ShowMediaInfo` = best-in-class but static (DB/network snapshot) and menu-buried.
- **Gap:** no live panel at arm's reach.
- **Audify adaptation:** "Signal" bottom sheet — live codec/sample rate/bitrate from the active player, format entity, stream client, source health (from Feature 04), buffered-vs-played, network class, loudness offset. 1 Hz ticker only while open; zero cost closed. Audify placement (player menu), Audify naming and `Material3SettingsGroup`-style rows.
- **Complexity:** Low–Medium. **Risk:** Low. **Dependencies:** existing flows.
- **Plan:** Roadmap 1.2.

### Feature 03 — Per-Network Quality Ceilings
- **BitChord:** separate Wi-Fi/mobile max quality; metered-aware.
- **Audify current:** single global `AudioQuality`; `InnerTubeXPlayer.toInnerTubeX(connectivityManager)` already receives the connectivity manager (AUTO already maps to LOW on metered).
- **Gap:** manual toggling or silent data drain.
- **Audify adaptation:** `WifiQualityKey`/`MeteredQualityKey` defaulting from the legacy global key (zero behavior change until user opts in); pure resolution function picks the ceiling by current network; settings group in `PlayerSettings`. Later (Innovation #2): visible per-track MB estimate + optional local data budget.
- **Complexity:** Low–Medium. **Risk:** Low (falls back to global). **Dependencies:** none.
- **Plan:** Roadmap 1.1.

### Feature 04 — Source Health + Fallback Memory
- **BitChord:** `PlaybackFallback` demotes failed alternative sources permanently per session; sources screen health checks.
- **Audify current:** `onPlayerError` classifies error types and recovers, but retries the same resolution path; `StreamUrlCache` is invalidated but has no failure memory.
- **Gap:** repeated failures on the same broken URL class within a session.
- **Audify adaptation:** `StreamSourceHealth` per-mediaId counter with backoff: 2nd failure bypasses URL cache and forces fresh resolution (alternate client/itag class); health state feeds the Signal panel; downloads get a JSON sidecar `failedCount` (no DB change) in a later pass.
- **Complexity:** Medium. **Risk:** Medium (touches resolution; feature-flagged, fails open to current behavior). **Dependencies:** none.
- **Plan:** Roadmap 1.4.

### Features 05–24
Tracked in `COMPETITOR_FEATURE_MATRIX.md` §3 (selection + reasons) and scheduled across Roadmap Phases 2–5. Notables: local library (2.1), queue slots (3.1), ListenBrainz (3.3), SponsorBlock (3.4), offline truthfulness (4.1), library hygiene (4.2). Deliberately *not* adopted: native DSP automix rewrite, SMB-first, liquid-glass UI, skins, desktop app, AI DJ voice — reasons in the matrix.

## 5. Feature Roadmap

See `AUDIFY_ROADMAP.md` (Phases 1–5). Phase 1 = Features 01–04 above in order 1.1 quality → 1.2 Signal → 1.3 skip marks → 1.4 source health (Signal panel exists before health states land in it).

## 6. Implementation Status

| Feature | Status |
|---|---|
| Phase 1 implementation (Features 01–04) | ⏳ planned — no code changed yet (earlier partial attempt reverted) |
| Phases 2–5 | ⏳ planned |

Note: the audit, ecosystem research, feature matrix, innovation ideas, design system and roadmap are **reference material only** — inputs prepared to guide the future feature work above. They are not completed goals or achievements; the only meaningful milestone is the shipped feature itself, and nothing has shipped yet.

## 7. Files Changed

```text
BITCHORD_AUDIFY_FEATURE_AUDIT.md   (new, this file)
COMPETITOR_FEATURE_MATRIX.md       (new)
AUDIFY_INNOVATION_IDEAS.md         (new)
AUDIFY_DESIGN_SYSTEM.md            (new)
AUDIFY_ROADMAP.md                  (new)
```

No source files modified. A partial Phase-1 implementation (queue-history hook + queue UI marks + strings) was reverted to baseline; `git status` confirms a clean tree apart from the docs and the pre-existing untracked BitChord clone.

## 8. Testing Requirements (when Phase 1 executes)

- `QueueHistoryStateTest` — skip marking, cap eviction, clear-on-new-queue.
- Network-quality resolution test — metered/unmetered/offline mapping.
- `StreamSourceHealthTest` — counter, backoff, cache-bypass threshold.
- `./gradlew :app:assembleFossDebug` after each feature; manual: queue sheet marks, Signal sheet values vs `ShowMediaInfo`, quality switch on network change, forced stream failure recovery.
- Regression watchlist: persistent queue restore, automix section, Listen Together guest mode, Cast queue sync.

## 9. Performance Impact (projected)

- Skip marks: bounded 100-id FIFO, O(1) transitions; no per-frame cost.
- Signal panel: 1 Hz ticker only while open; closed cost zero.
- Quality ceilings: pure function per stream resolution; no polling.
- Source health: one small map, cleared on queue change; no main-thread I/O.

## 10. Visual Differentiation Checklist

Queued as design-system §6 gate; key deltas for Phase 1: Audify's "Earlier" divider inside the active row (BitChord inlines history rows differently), "Signal" sheet from the player menu with grouped rows (not BitChord's overlay), settings in Audify's grouped-card system. Feature names are Audify's ("Signal", "Earlier"); BitChord wording never enters UI.

## 11. Licensing / Attribution Notes

Both projects GPL-3.0: capability-level study unrestricted; verbatim reuse would carry notices (none planned). `QueueHistoryState` is original code following BitChord's *pure-function* pattern; its constant (25-entry cap) informed the design but no code was copied. BitChord's third-party attributions (binimum lyrics, etc.) apply to nothing ported.

## 12. Remaining Opportunities

Post-Phase-1: wear OS companion (own backlog), playlist folders (blocked by DB freeze — ask a human), gapless lyric share cards, Listen Together public infrastructure (BitChord's self-hosted Oracle VM is an operational liability not worth copying), version alignment (blocked on incorrect-match risk), full Android TV app.
