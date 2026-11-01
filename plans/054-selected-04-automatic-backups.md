# Plan 054 — Selected feature 04: Automatic backup scheduling

**Status: planned — extends existing plan 050 and `Backup` implementation.**

## Objective

Offer opt-in scheduled backups with clear destination, retention, integrity verification, last-success status, and a safe restore preview. Preserve the current manual export/import workflow.

## Why

Manual backups are easy to forget. Scheduling is useful only if failures are visible and the produced file can be validated before restore.

## What exists today

- `data/stats/Backup.kt` exports and imports a versioned backup document.
- `SettingsSheet.kt` wires manual SAF export/import and status messages.
- `ListeningStats.kt` exposes stats data for backup.
- Plan 050 covers automatic scheduling and integrity; reconcile it before implementation.

## Scope

**In scope:** explicit opt-in; WorkManager periodic scheduling with Android constraints; user-selected SAF destination or a clearly explained app-private destination; atomic temp-write/replace where provider supports it; checksum and format validation; retention policy; last attempt/result; restore preview and confirmation; cancellation and permission-revocation handling.

**Out of scope:** cloud account credentials, silent uploads, backing up re-fetchable audio caches, automatic destructive restore, schema changes, version bumps.

## Affected files

| File | Expected change |
|------|-----------------|
| `data/stats/Backup.kt` | Add verification metadata and non-destructive validation |
| `ui/screens/SettingsSheet.kt` | Schedule controls, destination, status, restore preview |
| `AudifyApplication.kt` or dedicated worker package | Register scheduling without doing work on startup thread |
| `app/build.gradle.kts` | Only if WorkManager dependency is absent; verify existing dependency first |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels and error states |

## Implementation

1. Re-audit plan 050 and backup payload contents; explicitly classify included and excluded data.
2. Add a verification routine that parses the complete payload, checks app/version compatibility, and validates checksum before import.
3. Implement a worker that writes to a durable destination, handles revoked SAF grants, and records a truthful result.
4. Add opt-in scheduling, frequency, retention, and destination controls.
5. Add restore preview and require confirmation before any state replacement.
6. Test app upgrade, reboot, Doze/constraints, storage full, provider failure, and permission revocation.

## Testing

- Backup round-trip and corrupted/truncated/tampered-file tests.
- Worker retry/idempotency tests and no duplicate backup creation on retries.
- Verify excluded caches, secrets, and authentication material never enter the archive.
- Manual restore into a disposable profile; never test destructive restore against the user's only data.

## Performance notes

Use streaming I/O for large archives and background work. Avoid frequent wakeups; periodic scheduling follows Android's minimum interval and is not exact-time scheduling.

## Rollback

Cancel scheduled work and retain manual backup actions. Do not delete existing backups or change their format during rollback.

## Rejected alternatives

- Promise exact clock-time backups with WorkManager: Android does not guarantee exact periodic execution.
- Restore automatically after detecting a backup: restore must remain user initiated.
- Back up caches/audio files by default: large, redundant, and potentially sensitive.

## Implementation contract and acceptance criteria

### Backup contract
- Publish a versioned manifest listing payload sections, format version, app version, creation time, byte length, and checksum algorithm/value. Checksum covers the exact payload bytes and is verified before parsing or restore.
- Define included/excluded data explicitly: user settings, playlists/library references, stats/history only if supported, and never credentials, tokens, transient caches, or re-fetchable audio by default.
- A backup is successful only after write completion, flush/close, reopen/read-back, checksum verification, and parse validation. Provider APIs may not support atomic replace; use unique temporary document plus finalization where possible and explain limitations.
- SAF URI permission revocation, renamed/moved documents, full storage, cancellation, and provider failures must be represented as distinct outcomes.

### Scheduling and restore
- Scheduling is opt-in, cancellable, unique (no duplicate periodic workers), network-independent unless the payload truly requires network, and constrained for battery/storage. State clearly that WorkManager is inexact.
- Retention applies only to backups Audify owns and can identify safely; never delete arbitrary documents from a user-selected folder.
- Restore preview shows backup date/version, included sections, compatibility warnings, and conflicts. Require confirmation; stage validation before mutating current data. Define partial-restore policy explicitly.

### Done when
- Tests prove corrupt/truncated/tampered/incompatible backups are rejected before mutation.
- Worker retries do not create uncontrolled duplicate files; last-attempt and last-success are distinct.
- Manual backup/import remains functional and backward-compatible; restore is verified against a disposable profile.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
