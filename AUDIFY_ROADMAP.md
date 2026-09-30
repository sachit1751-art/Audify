# Audify Roadmap

> **What this document is:** the execution order for everything selected in the matrix and invented in the ideas doc.
> **Goal:** sequence 24 selected features + 12 innovation ideas into 5 phases with dependencies, so implementation never becomes a giant uncontrolled change.
> **Contains:** Phase 1 Foundation (quick wins) · Phase 2 Core UX · Phase 3 Power User · Phase 4 Differentiation · Phase 5 Polish — each feature with objective, affected files, and approach summary; sequencing rules and DB-schema guardrails at the end.
> **Result / how to use:** pick the next feature in phase order and execute it via its self-contained plan in `plans/NNN-*.md` (plans exist for 017–021; write one before implementing anything that lacks one). One feature at a time: inspect → design → implement → build → test → regressions → document. Status of each shipped feature is recorded in `plans/README.md`, not here.

**Implementation rule (unchanged from the original brief):** one feature at a time — inspect → design Audify-native → implement → `./gradlew :app:assembleFossDebug` → unit tests → fix → regression check → document. Per-feature decision profile (value / cost / risk / perf / maintenance / uniqueness) lives in the matrix; this doc is the order of work.

## Phase 1 — Foundation (quick wins, low risk)

**1.1 Per-network quality ceilings** (matrix #1, Innovation #2a)
- Objective: separate max quality for Wi-Fi vs mobile data; existing global key becomes fallback.
- Files: `constants/PreferenceKeys.kt` (add `WifiQualityKey`/`MeteredQualityKey`), `utils/InnerTubeXPlayer.kt` (`toInnerTubeX` picks ceiling by `connectivityManager.isActiveNetworkMetered`), `PlayerSettings.kt` (new network group), `sachit_strings.xml`.
- Steps: add keys defaulting from legacy global → resolution function (pure, unit-tested) → wire into player init → settings UI.
- Tests: resolution-function unit test (metered/unmetered/offline). Rollback: pref defaults replicate current behavior exactly.

**1.2 In-player Signal panel** (matrix #3, Innovation #4)
- Objective: live diagnostics sheet: codec/sample rate/bitrate, stream client, buffered-vs-played, network class, loudness offset; 1 Hz ticker only while open.
- Files: new `ui/player/PlaybackStatsSheet.kt`, `Player.kt` (entry chip), strings.
- Steps: build sheet from existing PlayerConnection flows + FormatEntity → add entry in player menu → wire ticker lifecycle.
- Tests: build + manual. Rollback: delete sheet + entry.

**1.3 Queue skip marks + "Earlier" boundary** (matrix #2, Innovation #1)
- Objective: mark rows jumped over by forward queue jumps; visible Earlier divider at the active row.
- Files: new `playback/QueueHistoryState.kt` (pure logic), `MusicService.kt` (detect SEEK jumps in `onMediaItemTransition`, reset on new queue), `PlayerConnection.kt` (expose skipped-ids flow), `Queue.kt` (divider inside active row + 50% alpha skipped rows), strings.
- Steps: pure logic + tests → service hook → UI marks.
- Tests: `QueueHistoryStateTest`. Rollback: pref-off removes UI marks; logic revert is self-contained.

**1.4 Source health + fallback memory** (matrix #4, Innovation #3)
- Objective: per-mediaId failure memory; second failure bypasses URL cache with fresh client/itag; health shown in Signal panel.
- Files: new `playback/StreamSourceHealth.kt`, `MusicService.kt` (`onPlayerError` + data-source factory choke point), strings.
- Steps: tracker + tests → hook error path → cache-bypass on unhealthy → surface in 1.2's sheet.
- Tests: `StreamSourceHealthTest`. Rollback: feature flag; falls back to current retry behavior.

## Phase 2 — Core UX (everyday value)

**2.1 Local file library beside YTM** (matrix #5) — MediaStore scan → "On device" library tab; reuse `LocalAlbumRadio`/`PersistQueue.LOCAL_ALBUM_RADIO` plumbing that already exists. High effort: permission flow, scanner, list screens. Audify-native: one tab, no folder maze.
**2.2 Tablet / two-pane player** (matrix #15) — window-size-class: queue sheet becomes side pane ≥ expanded width. Pure UI.
**2.3 Landscape fullscreen lyrics** (matrix #16) — portrait `LyricsLine` reused in a landscape layout; completes the lyrics story.
**2.4 Week-shape recap card** (Innovation #12) — local queries + one Home card; reuse wrapped/share-image infra.
**2.5 Time-aware discovery shelf** (Innovation #6) — hour-block play counts → "Morning/Late night" shelf, private and offline.
**2.6 Lyrics share-as-image** (matrix #8) — compose lyric card to bitmap, system share sheet.

## Phase 3 — Power user

**3.1 Named queue slots with resume** (matrix #10, Innovation #5) — 2–3 sidecar-persisted slots; swap live queue with resume; mini player shows slot; excluded for Listen Together guests. Highest-value hardest item in Phase 3.
**3.2 10-band EQ + AutoEq profiles** (matrix #9, #22) — extend existing custom DSP with preset bands, AutoEq import (parse CSV/text), per-output association.
**3.3 ListenBrainz scrobbling** (matrix #6) — REST + user token alongside existing ScrobbleManager lifecycle.
**3.4 SponsorBlock** (matrix #7) — community segments API, skip categories user-selected, off by default; only for video-song content.
**3.5 Download quality upgrade + storage budget** (matrix #19) — re-download at current quality when it improved; budget cap with LRU eviction prompt.
**3.6 Tag editor for downloads** (matrix #18) — edit embedded metadata/artwork on downloaded files.
**3.7 Stats export (CSV)** (matrix #20) — export play counts/history from existing tables.
**3.8 Playlist import expansion** (matrix #14) — beyond CSV/M3U (Spotify-converted lists).

## Phase 4 — Differentiation (the "nobody does this" layer)

**4.1 Offline truthfulness** (Innovation #7) — download state chips + background verify via WorkManager.
**4.2 Library hygiene** (Innovation #11) — duplicate detection (normalized title+duration), ghost flagging fed by 1.4's failure memory; suggestions only, never auto-delete.
**4.3 Queue handoff** (Innovation #10) — audio-device-change snapshot + resume offer; slots from 3.1 make same-device automatic.
**4.4 "Ask Audify" conversational queue** (matrix #21) — local prompt → queue using existing AiSettings providers; privacy-safe, no server.
**4.5 Privacy ledger** (Innovation #9) — local log + kill switches for outbound AI/lyrics/recognition calls; exportable.
**4.6 Artist new-release notifications** (matrix #12) — subscription checks in existing sync worker → notification channels.

## Phase 5 — Polish

**5.1 Accessibility-first lyrics** (Innovation #8) — fontScale to 2×, steady mode, reduced-motion compliance, high-contrast.
**5.2 MotionTokens cleanup** — centralize all durations/easings per design system §4; remove literals.
**5.3 Performance pass** — startup trace, recomposition audit on Home/Queue, Coil cache tuning revisit; APK size check.
**5.4 Preferred audio language** (matrix #13) — multi-track selection for videos that have it.
**5.5 TV intents + WebDAV source** (matrix #17, #23) — lean-back intent handling; WebDAV-only remote source (SMB deferred by design).
**5.6 Backup auto-schedule + integrity** (matrix #24) — WorkManager scheduled backups; restore validation; ensure encrypted prefs stay excluded (BitChord v1.7 lesson).

## Sequencing notes

- Phase 1 items are independent; do them in order 1.1→1.4 to build the Signal panel before health states land in it.
- 3.1 (queue slots) is the dependency root for 4.3 (handoff) — don't reorder.
- Everything in Phases 1–3 avoids DB schema changes; if a feature ever *needs* one, stop and ask a human contributor (AGENTS.md rule).
- Anti-BitChord check (design system §6) runs on every UI PR; licensing note: all reference projects are GPL-3.0 or inspected read-only, capability-level reimplementation with original code.
