# Audify Innovation Ideas — What Nobody Does Well

> **What this document is:** the originality track — features no competitor ships well, invented from user problems rather than copied.
> **Goal:** give Audify a differentiation layer beyond ecosystem parity.
> **Contains:** 12 ideas (honest queue, data budget, source resilience, Signal panel, queue multiplicity, time-aware discovery, offline truthfulness, accessible lyrics, privacy ledger, queue handoff, library hygiene, week recaps), each as `Problem / Why existing apps don't solve it well / Proposed Audify solution / Why users would care / Technical difficulty`.
> **Result / how to use:** ideas 1–4 overlap with Phase 1 of `AUDIFY_ROADMAP.md` (same capabilities, restated without a copy frame); ideas 5–12 are future plans — each gets its own `plans/NNN-*.md` when scheduled. Concepts only; nothing implemented.

---

## 1. Honest Queue — "what played, what I skipped, and why"

**Problem:** Every streaming queue silently forgets. You skip three songs by accident and cannot see later what you passed over; you come back after a crash and the queue is "restored" but the story of your session is gone.

**Why existing apps don't solve it well:** BitChord (issue #200) only added a scrollback after user outcry, and it doesn't mark *why* rows were skipped. Musicolet's multiple queues preserve position but not intent. YTM hides skip signals entirely.

**Proposed Audify solution:** Session-scoped queue memory: an "Earlier" boundary in the queue sheet, rows jumped over marked with a subtle skip glyph, and a one-tap "resume story" — reopen a queue session from History exactly as it looked (order + skips). No DB schema change: session state in memory, historical sessions reuse the existing history tables.

**Why users would care:** Turns the queue from a list into a trustworthy place. Skipped songs stay findable without guilt; accidental skips are reversible at a glance.

**Technical difficulty:** Medium. Logic is pure Kotlin (unit-testable); UI is one divider + glyph in the existing queue LazyColumn.

---

## 2. Data-Aware Listening — quality that adapts like a budget

**Problem:** Every client has one global quality knob. Users on metered plans either burn data on Wi-Fi-grade audio or manually downgrade and forget to upgrade.

**Why existing apps don't solve it well:** Only BitChord has per-network ceilings; none combine them with *live* feedback. YTM's auto quality is a black box.

**Proposed Audify solution:** Separate Wi-Fi / metered ceilings + a visible "this track ≈ X MB on current network" line in the stats panel, and an optional "data budget" that can auto-drop quality after a configurable monthly MB total (tracked locally, never phoned home).

**Why users would care:** Direct, visible money savings on capped plans — the #1 hidden cost of streaming clients.

**Technical difficulty:** Low–Medium (ceilings); Medium (budget tracking, local only).

---

## 3. Source Resilience — the client that remembers broken streams

**Problem:** Streams fail randomly (expired URLs, throttled clients, regional hiccups). Every client either retries blindly or gives up; users see the same failure repeatedly within a session.

**Why existing apps don't solve it well:** BitChord added a one-way fallback for *addon* sources only; SimpMusic/InnerTune treat every failure as fresh. Nobody surfaces "this source is struggling right now" to the user.

**Proposed Audify solution:** Per-mediaId failure memory with backoff: second failure bypasses the URL cache and forces a fresh client/itag class; the stats panel shows source health ("Healthy / Recovering (attempt 2) / Fallback active"). Downloads get the same memory via a sidecar file, so a retry doesn't refetch the same broken URL.

**Why users would care:** Fewer stalls and "song unavailable" moments; visible instead of mysterious recovery.

**Technical difficulty:** Medium. Choke-pointed in Audify's existing resolution path; guarded by a feature flag.

---

## 4. Signal Panel — diagnostics as a first-class citizen

**Problem:** "Why does this sound compressed?", "am I on Wi-Fi?", "is it buffering?" — every client buries the answers in settings or omits them. YTM's stats-for-nerds is loved *because* it exists, and no FOSS client matches it.

**Why existing apps don't solve it well:** BitChord has a good panel but fixed to its player layout; Audify's existing ShowMediaInfo dialog is the richest in the ecosystem but requires a menu hunt and shows static snapshots.

**Proposed Audify solution:** A one-tap "Signal" sheet from the player: live codec/sample rate/bitrate, stream client, source health, buffered-vs-played, network class, loudness offset. 1 Hz ticker only while open; zero cost when closed.

**Why users would care:** Audiophiles and data-conscious users get instant answers; support/debugging becomes self-service.

**Technical difficulty:** Low. All data already flows through existing PlayerConnection StateFlows.

---

## 5. Queue Multiplicity — Musicolet's superpower, brought to streaming

**Problem:** You can only hold one listening intention at a time. Start a workout radio, get interrupted by a podcast episode, and the radio is gone or polluted. Musicolet proved multiple named queues with resume is a crown-jewel feature — *no streaming client has it*.

**Why existing apps don't solve it well:** OuterTune had partial "multiple queues"; it died. Streaming clients assume one live queue; switching context destroys it.

**Proposed Audify solution:** Named queue slots ("Workout", "Focus", "Car") persisted as sidecar JSON (no DB schema change); switching slots swaps the live queue with resume position; the mini player shows the active slot name. Listening Together guests are excluded (host owns the queue).

**Why users would care:** Context-switching without loss — the daily reality of commuting/focus/gym users.

**Technical difficulty:** Medium–High. Serialization exists (PersistQueue); needs careful service-state swap and UI entry points.

---

## 6. Time-Aware Discovery — the app that knows 7 AM ≠ 11 PM

**Problem:** Spotify's daylist (2024) proved time-of-day taste matters; no FOSS client does anything with it. Audify already has a time-aware *greeting* — but the content doesn't follow the clock.

**Why existing apps don't solve it well:** YTM/Spotify keep the models server-side and closed; FOSS clients ignore temporal patterns entirely.

**Proposed Audify solution:** On-device, private "moments": bucket play history by hour-block (existing DB, no schema change), and surface a "Morning drive / Late night" shelf of the user's own most-played-for-that-block — occasionally seeded with one fresh radio suggestion. Fully local; wrapped-style explanatory copy.

**Why users would care:** Feels personal without surveillance; works offline; nobody in the FOSS space has it.

**Technical difficulty:** Medium. Pure queries over existing playCount/history tables + one Home shelf.

---

## 7. Offline Truthfulness — downloads that confess their state

**Problem:** Downloaded songs rot: sources change, quality upgrades arrive, and files go stale. Every client shows a binary downloaded/not; users discover breakage *at the gym, offline*.

**Why existing apps don't solve it well:** BitChord's tier work improved downloads but nobody re-validates or shows staleness; OuterTune's local+YTM union died with the app.

**Proposed Audify solution:** Download cards carry state chips: "Fresh / Re-verify available / Source changed / Quality upgrade available", computed lazily on library scroll (never on startup). A "verify offline library" action runs in background with WorkManager and repairs what it can.

**Why users would care:** Trust that offline actually works when there is no network to fix it.

**Technical difficulty:** Medium. Sidecar metadata + WorkManager; no destructive migration.

---

## 8. Accessibility-First Lyrics — karaoke that respects vision settings

**Problem:** Word-synced lyrics animate aggressively everywhere; for low-vision and motion-sensitive users they are unreadable or nauseating. No client offers lyric-specific accessibility controls.

**Why existing apps don't solve it well:** SimpMusic's landscape lyrics and styles target aesthetics; none respect `fontScale`, reduced-motion, or high-contrast for the lyric surface itself.

**Proposed Audify solution:** Lyric surface obeys system font scale up to 2×, offers a "steady" mode (line highlight only, no word bounce), honors animator-duration-scale=0 (reduced motion), and a high-contrast theme. A single LyricsSettings group.

**Why users would care:** The most-used surface in the app becomes usable for everyone; also a differentiator reviews notice.

**Technical difficulty:** Low–Medium. Constrained to the existing LyricsLine component.

---

## 9. Privacy Ledger — receipts for everything the app sends

**Problem:** FOSS clients say "no tracking" but users have to take it on faith. Audify's AI settings already gate cloud calls; nothing shows the user what actually left the device.

**Why existing apps don't solve it well:** Nobody does it. SimpMusic documents its one optional Google ping; the rest are silent.

**Proposed Audify solution:** A privacy ledger screen: per-feature log (last 50 events, local only) of outbound AI/lyrics-translation/recognition calls, with per-feature kill switches mirroring existing settings. Export as text for audits.

**Why users would care:** Verifiable privacy is a marketable, honest differentiator against both Big Tech and less-transparent forks.

**Technical difficulty:** Low–Medium. Intercept at existing network call sites; RingBuffer + screen.

---

## 10. Queue Handoff — continue exactly where life left off

**Problem:** Switching from headphones to car to speaker loses queue context or re-shuffles. Cast exists, but queue *identity* (history + position + slot) doesn't transfer cleanly; BitChord's own issue tracker is full of lock-screen/session control bugs.

**Why existing apps don't solve it well:** Cast handles transport, not session semantics. Musicolet's resume is per-queue but local-only.

**Proposed Audify solution:** "Handoff": on audio-device change, offer (once, dismissible) to snapshot the current queue session and offer resume-on-new-device via the existing persistent-queue restore path; on the same device it's automatic (slots from idea 5).

**Why users would care:** Removes a daily friction point; works with the car intrinsically.

**Technical difficulty:** Medium. AudioDeviceCallback + existing persistence; UI is one chip.

---

## 11. Library Hygiene — duplicate and ghost detection

**Problem:** Years of syncing create duplicates (same song, different videoIds) and ghosts (library rows whose source is region-blocked/dead). No client detects either.

**Why existing apps don't solve it well:** Musicolet handles file duplicates locally; streaming clients ignore the problem because matching is "hard". Users see the same track twice in smart playlists.

**Proposed Audify solution:** On-device matcher (normalized title+duration bucket) proposes merges in a "Library checkup" screen; ghosts get flagged when playback fails twice (feeding idea 3's memory) with a one-tap replace from search. All suggestions; never auto-deletes.

**Why users would care:** A library that stays clean over years — the difference between a tool and a hoarder's attic.

**Technical difficulty:** Medium. Pure matching logic (testable) + one screen; optional replace flow.

---

## 12. Session Recaps, Not Just Yearly — "your week had a shape"

**Problem:** Wrapped/recap features (Audify, SimpMusic, YTM, Spotify) are annual theater. The insight is real but arrives 11 months late to be useful.

**Why existing apps don't solve it well:** Everyone ships the December moment; nobody ships the weekly one. SimpMusic's monthly recap is the closest and still coarse.

**Proposed Audify solution:** A lightweight "week shape" card: top artist, new discoveries count, listening clock trend — computed locally from existing tables, rendered as one Home card + shareable image (reusing idea from lyrics-share infra). No AI, no network.

**Why users would care:** Regular, honest feedback loops build habit; shareable without login walls.

**Technical difficulty:** Low–Medium. Queries + one card + reuse of share-image composables.

---

## Selection note

Ideas 1, 2, 3, 4 correspond to the four Tier-A items already planned from the BitChord audit — restated here as *capability* (not copy) so the implementation stays Audify-native. Ideas 5–12 are the beyond-BitChord differentiation layer. The roadmap in `AUDIFY_ROADMAP.md` sequences both without overlap.
