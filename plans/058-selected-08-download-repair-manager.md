# Plan 058 — Selected feature 08: Download repair manager

**Status: planned — builds on existing download verification; no repair UI assumed.**

## Objective

Provide a truthful download health view that distinguishes complete, incomplete, unreadable, missing, and unverified items. Offer safe retry or cleanup only when the underlying download state supports it.

## Why

An “offline” label is useful only when the file is actually readable. Repair must not delete a valid download or pretend that a metadata check proves playable media.

## What exists today

- `download/DownloadVerification.kt` contains verification logic.
- `download/DownloadStore.kt`, `DownloadSession.kt`, `DownloadService.kt`, `Downloader.kt`, `Downloads.kt`, and `Downloads` UI manage download lifecycle.
- Plan 039 documents the gap between scheduled file-access checks and actual playback verification.

## Scope

**In scope:** health classification; retry only for recoverable failed/incomplete jobs; verify stored URI accessibility and expected container/manifest structure; surface unknown states; cleanup orphaned temporary artifacts after revalidation; per-item actions and progress.

**Out of scope:** claiming full decode/playback verification from file existence; silently redownloading; deleting user-selected exports; changing download formats or storage schema.

## Affected files

| File | Expected change |
|------|-----------------|
| `download/DownloadVerification.kt` | Extend verifiable checks and result taxonomy |
| `download/DownloadStore.kt` | Safe state reconciliation, no destructive implicit cleanup |
| `download/DownloadSession.kt` / `DownloadService.kt` | Retry state and idempotent recovery hooks |
| `ui/components/DownloadManagerSheet.kt` | Health indicators and explicit repair actions |
| `app/src/main/res/values/sachit_strings.xml` | English-only statuses and explanations |

## Implementation

1. Trace all download states and define what evidence each status requires.
2. Add a pure health classifier separating accessible, structurally valid, and playback-tested states.
3. Implement idempotent retry for known resumable/failed states; do not restart completed downloads automatically.
4. Add a repair queue with bounded concurrency, cancellation, and per-item results.
5. Add cleanup preview; re-check file ownership and state immediately before deletion.
6. Reconcile offline badges so “verified” never overstates the evidence collected.

## Testing

- Partial transfer, missing URI, revoked permission, truncated container, valid-but-unplayable, and completed download cases.
- Retry idempotency and duplicate worker prevention.
- Ensure valid downloads and user-owned files are never removed by cleanup.
- Manual offline playback test after repair and after process restart.

## Performance notes

Use bounded reads and container-header checks before any full-file hash. Do not decode every download in a background sweep.

## Rollback

Disable repair actions while retaining existing downloads and verification results. Never run bulk deletion as part of rollback.

## Rejected alternatives

- File-exists equals playable: false for truncated or invalid media.
- Automatically delete and redownload every suspicious file: risks data loss and network usage.
- Label all downloads “verified” after a URI stat: evidence is insufficient.

## Implementation contract and acceptance criteria

### Health-state evidence
- Use distinct states: complete-and-accessible, incomplete, missing, permission-denied, structurally-invalid, unverified, and unknown. Only claim playable after an actual playback/decode signal; structural validation is not full playback validation.
- Classification must include evidence, timestamp, and next safe action. A transient provider error must not downgrade a completed file to corrupt.
- Repair eligibility is explicit by state. Retry only known resumable/failed sessions; completed downloads are never restarted automatically.

### Recovery safety
- Repair requests are idempotent and unique per download ID; cap concurrency and expose progress/cancel/retry result.
- Before cleanup, verify app ownership, temp-file marker, no active session, and stale age threshold. Never delete user-selected exports or arbitrary files.
- Reconcile store state after interrupted worker/process lifecycle; do not create duplicate downloads or overwrite a valid completed artifact.

### Done when
- Tests cover all health states, revoked grants, partial transfers, truncated data, duplicate workers, active-session races, cleanup ownership, and cancellation.
- UI clearly distinguishes verification level from playback guarantee.
- Offline playback after repair is manually verified across process restart; existing download flows remain unchanged.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
