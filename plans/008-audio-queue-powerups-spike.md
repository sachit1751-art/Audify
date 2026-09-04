# Plan 008 — Spike: pick & scope ONE audio/queue power-up

Written against commit **`7ca7a9653`**. **Direction/spike plan**: choose one feature from a
shortlist, validate it against existing code, and produce a concrete build spec for a human
to approve. No feature code is written in this plan.

## Shortlist (grounded in existing infrastructure — each candidate reuses code already present)

- **A. EQ preset import/export**: `app/src/main/kotlin/com/sachit/music/eq/` has
  `EQProfileRepository`, an `EqualizerService`, and a profiles UI; app-wide Backup/Restore
  exists (`viewmodels/BackupRestoreViewModel.kt`) but EQ presets appear to live only in the
  local profile store. Export/share a profile as JSON (kotlinx-serialization is a core dep),
  import via the share sheet (`MainActivity` already handles `SEND text/plain`).
- **B. Smarter offline strategy beyond like-triggered downloads**: `AutoDownloadOnLikeKey`
  exists in `MusicService.kt`; playback caches via `DownloadCache`/`PlayerCache` DI.
  Candidate: "auto-download next N of a liked playlist / recently played radio on Wi-Fi +
  charging" using the existing `NetworkConnectivityObserver` and `DownloadUtil`. Battery and
  storage policy decisions are the bulk of this feature.
- **C. Related-song / radio refinements**: `SimilarContent` constant + `startRadioSeamlessly()`
  in `PlayerMenu.kt` exist; candidate: show upcoming radio tracks with a "why this was
  picked" affordance or pre-fetch the radio queue while current radio track plays.
- **D. Queue sharing**: Listen Together infra (`ListenTogetherManager`, room codes) could
  back "share this queue as a room" without playback sync — a lightweight, infra-reuse win.

## Spike steps

1. **Drift check**: `git status` (expect the 11 WIP paths only — see plan 002 step 1).
2. **Recon each candidate briefly** (30–60 min equivalent of reading): for A read
   `eq/data/EQProfileRepository.kt` and the EQ settings UI; for B read `DownloadUtil.kt` +
   `AutoDownloadOnLikeKey` usage + `NetworkConnectivityObserver`; for C read
   `startRadioSeamlessly` + `automixItems` in `MusicService.kt`; for D read
   `ListenTogetherManager.createRoom/joinRoom` signatures.
3. **Score candidates** on: user value (per README feature gaps), implementation effort,
   risk to the playback service (the 4,914-line file is the crown jewel — prefer candidates
   that do NOT require `MusicService` surgery), and testability with the existing JVM test
   patterns. **Prefer A or D** if scores tie — both are additive and avoid core-playback
   risk.
4. **Pick ONE** and write `plans/results/008-feature-spec.md` (~60–100 lines) containing:
   - chosen feature + why (one paragraph, scores of all four in a table),
   - the exact files it will touch (from the recon, with current-state line references),
   - new strings needed (names only — content added later by the implementer in
     `sachit_strings.xml`),
   - UI/flow sketch (which screen, what the user taps),
   - edge cases + what to do on failure,
   - verification plan (unit tests following `DevicePerformanceTest.kt` style where pure;
     manual QA checklist),
   - explicit "out of scope" list.
   This file is the handoff spec; the human approves it before any implementation plan is
   written.

## Verification

- No production files modified: `git status` unchanged apart from the new file under
  `plans/results/`.
- Spec file exists and contains the required sections (read it back once).

## Done criteria

- One feature chosen with evidence-backed rationale; four candidates scored.
- Handoff spec written under `plans/results/008-feature-spec.md`.
- Zero code changes.

## Escape hatches

- If recon reveals a candidate is already half-implemented or blocked by a dependency the
  shortlist missed, surface it in the report and re-score rather than forcing the pick.
- If any candidate requires a DB schema change, it is automatically disqualified (AGENTS.md)
  — say so in the report.

## Maintenance note

Whatever feature is chosen later, the constraint set is fixed: no DB schema changes, no
version bumps, strings only in default `sachit_strings.xml`, prefer additive code over
edits to `MusicService.kt`. The spec file records the decision so a future session doesn't
re-litigate the shortlist.
