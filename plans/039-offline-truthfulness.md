# Plan 039 — Offline truthfulness

**Status: not started.** Verified at commit `61778ab4` — no background download verification exists. 
Downloaded songs show a download state but there's no background verification that the file still plays.

## Objective

Make downloaded songs truthfully represent their offline availability: show a download state chip, and run 
background verification (via WorkManager) that re-tests downloaded files periodically to catch bit rot, 
deleted files, or broken caches. If a file fails verification, surface it (chip changes, "verify" action) 
but never auto-delete — suggestions only.

## Why

- Innovation #7: download state chips + background verify via WorkManager.
- A download that doesn't actually play is a lies-on-the-ground problem — the user sees "downloaded" and 
expects it to work offline.
- Existing download state is "were we able to download it", not "does it still play".

## What exists today

- Download state tracking exists (`DownloadService`, `DownloadUtil`, download state flows) — the "is 
downloaded" state is tracked.
- No post-download verification — once downloaded, the app doesn't re-test that the file plays.
- No WorkManager background task for download verification.
- Download state chips exist in list/grid items (the download icon shows state).

## Scope

**In scope:**
- A download verification pipeline: on a schedule (WorkManager periodic task, e.g. daily/weekly), pick a 
subset of downloaded songs, test that the file still exists and can be opened/played, mark verification 
result per song.
- A download state enhancement: beyond "downloaded / not downloaded", add a verification state (verified / 
unverified / failed-verification). The chip shows this when relevant.
- Failed-verification handling: surface the song (chip shows "verification failed" or similar), offer a 
"Re-download" or "Remove" action. Never auto-delete — suggestions only.
- The verification is a background task — doesn't block the user, doesn't run on every open. Periodic, 
respectful of battery.

**Out of scope:**
- Real-time playback verification on every play — defer; v1 is background periodic verification.
- Auto-delete failed downloads — explicitly rejected. Suggestion only.
- Verification of artwork files separately — defer; v1 verifies the audio file.
- A full download integrity dashboard — defer; v1 is the chip + background task + re-download action.

## Affected files

| File | Change |
|------|--------|
| `playback/DownloadVerificationWorker.kt` (new) | WorkManager Worker: pick a subset of downloaded songs, verify each (file exists, can be opened/played), update verification state per song |
| `playback/DownloadVerificationScheduler.kt` (new) | Schedule the periodic verification WorkManager task; enforce constraints (charging, network if needed) |
| `db/entities/DownloadEntity.kt` (or download record) | Add verification state field (verified / unverified / failed) + last-verified timestamp, if not already tracked |
| `db/MusicDatabase.kt` | Migration to add verification fields; query to list songs needing verification / failed verification |
| `playback/DownloadUtil.kt` (or download state flow) | Expose verification state in the download state flow; update verification state after a verification run |
| `ui/component/DownloadIcon.kt` (or the download badge) | Show verification state in the download chip when relevant (verified = normal, failed = warning, unverified = neutral) |
| `ui/component/SongListItem.kt` / `SongGridItem.kt` | Pass verification state to the download icon; optional "Re-download" action on failed-verification songs |
| `sachit_strings.xml` | "Verification failed", "Re-download", "Last verified", offline truthfulness labels |

## Technical approach

1. **Verification worker** — A WorkManager `Worker` (periodic, e.g. daily) that: queries a batch of downloaded 
songs (the executor picks the batch size and selection — e.g. unverified first, then LRU, or a simple slice), 
for each: check the file exists on disk, optionally attempt to open/read the file header (not full play — 
cheap integrity check), update the verification state. Failures are recorded, not acted on automatically.
2. **Verification state** — Add to the download record: `verificationState` (verified / unverified / 
failed-verification) + `lastVerified` timestamp. New downloads start as "unverified"; after a verification 
run passes, "verified"; after a failure, "failed-verification".
3. **Chip enhancement** — The download icon/badge shows the verification state when relevant. A "verified" 
download shows the normal download state. A "failed-verification" download shows a warning indicator + a 
"Re-download" action. An "unverified" download shows neutral (no warning, just not yet verified).
4. **Scheduling** — WorkManager periodic task with reasonable constraints (e.g. not on battery-maintaining 
mode, maybe Wi-Fi only for a full re-check). The executor picks the exact constraints at implementation time. 
The task should be lightweight — verify a batch, not all downloads at once.
5. **Failed-verification action** — On the song (in list/grid or download management screen), a "Re-download" 
action re-downloads the song (which also resets verification state to unverified → verified on success). A 
"Remove" action removes the download. The user decides.

## Testing

- Unit test for the verification logic (pure function): given a file that exists / doesn't exist / is 
corrupt, assert the verification state decision. The actual file check is the impure boundary — test it with 
test files or mock the file check.
- Unit test for the worker logic (the batch selection, state update) — mock the DB / file check.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: trigger a verification run (or wait for the periodic run), verify chips update: unverified → 
verified for good files, failed for a bad file (create a bad file to test). Verify "Re-download" on a failed 
song re-downloads and resets verification. Verify no auto-delete.
- Regression: existing download state, download icon, download flow unchanged for non-verified songs.

## Performance notes

- The verification worker is periodic and batch-limited — not all downloads at once. Cheap per-file check 
(file exists + header read), no full playback.
- WorkManager respects battery / constraints — don't run aggressively.

## Rollback

- Remove the verification worker, scheduler, verification state field, and chip enhancement → downloads revert 
to the existing "downloaded / not downloaded" state with no verification. Zero impact on the core download 
flow.

## Anti-BitChord check

- Offline truthfulness is an Audify identity feature — "we don't lie about downloads". The implementation is 
honest: background check, chip shows truth, failed → suggestion not auto-delete. Don't copy a competitor's 
integrity approach — this is a values-aligned feature, implement it as Audify.
- Never auto-delete — that's the core rule. Suggestions only, user decides.
