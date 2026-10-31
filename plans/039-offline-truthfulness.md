# Plan 039 — Offline truthfulness

**Status: partially implemented — 2026-10-31.** The first implementation checks whether a saved URI exists, opens, and yields a readable byte prefix. This is a file-access check; it does **not** establish that audio decodes or plays.

Implemented in this pass:
- Added WorkManager 2.10.5, a weekly battery-not-low worker, and a manual check action in the download manager.
- Checks at most 25 unique file URIs per run, prioritising unchecked entries and then the oldest checks.
- Persists `unchecked` / `available` / `unavailable` plus the last-check timestamp in the existing SharedPreferences download metadata. Old records decode as `unchecked`.
- Shows the file-check result on completed rows in the download manager and explains the limit of the check.
- No check path deletes a file.

Deferred: a persistent status chip across library/download lists, re-download/remove actions for failed checks, and actual media-decoder/playback verification. Do not describe this pass as proving playability.

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

## Baseline when this plan was drafted

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
| `download/DownloadVerification.kt` (new) | WorkManager worker, scheduler, file-header check and batch selection |
| `download/Downloads.kt` | Persist per-file check state/timestamp in existing SharedPreferences metadata and expose status flow |
| `ui/components/DownloadManagerSheet.kt` | Manual check action, disclosure, and status on completed manager rows |
| `app/build.gradle.kts` | Add WorkManager runtime dependency |
| `app/src/main/res/values/sachit_strings.xml` (new) | English labels and the file-check limitation disclosure |
| `app/src/test/.../DownloadVerificationTest.kt` (new) | Header and batch-selection tests |
| `app/src/test/.../DownloadStoreTest.kt` | Backward-compatible metadata defaults and state/timestamp serialization |

## Technical approach

1. **File-check worker** — WorkManager runs weekly with a battery-not-low constraint. Each pass checks up to 25 unique URIs; unchecked entries come first, then the oldest check timestamps. No network is required.
2. **File-check state** — Existing `SavedSongMetadata` carries `fileCheckState` and `fileCheckAtMillis`. New downloads start `unchecked`; readable files become `available`; failed existence/open/read checks become `unavailable`.
3. **Manual action and display** — The download manager exposes `Check downloaded files`, explains the check's limit, and shows the result on completed rows.
4. **Deferred work** — A persistent library chip, re-download/remove affordances, and actual media-decoder verification remain open items.

## Testing

- Passed: `:app:testDevDebugUnitTest --tests com.music.audify.DownloadVerificationTest --tests com.music.audify.DownloadStoreTest`. Covers readable/empty headers, batch limits, URI deduplication, unchecked-first ordering, and metadata compatibility/serialization.
- Worker integration against real MediaStore/content URIs and manual UI behavior still need device testing.
- Passed: `:app:assembleDevDebug` (the configured dev flavor; this checkout has no Foss flavor).
- Manual: trigger the manager action on a device and confirm readable/missing URI states update. Confirm no file is deleted. The check must not be presented as proof of playback.
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
