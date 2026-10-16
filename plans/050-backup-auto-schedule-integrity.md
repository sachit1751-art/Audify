# Plan 050 — Backup auto-schedule + integrity

**Status: not started.** Verified at commit `61778ab4` — no backup auto-schedule exists. Backup/restore exists 
(the executor checks at implementation time what backup infra is already there — the plan assumes a backup/restore 
flow exists and adds scheduling + integrity). Restore validation + encrypted-prefs exclusion are the BitChord v1.7 
lesson (the roadmap explicitly says "ensure encrypted prefs stay excluded").

## Objective

Two related backup features: (1) auto-schedule backups via WorkManager (e.g. daily/weekly, or on a cadence the 
user picks) so backups happen automatically without the user remembering; (2) restore validation — when restoring, 
validate the backup integrity (corrupt backup detection, partial backup handling) before applying. Plus the 
BitChord lesson: ensure encrypted preferences stay excluded from the backup (don't back up sensitive prefs that 
shouldn't be restored, or exclude them explicitly).

## Why

- Matrix #24: Musicolet ships auto backup. Audify has backup/restore but no scheduling. Auto-schedule is a 
quality-of-life feature — backups happen automatically, the user doesn't have to remember.
- Restore validation is a safety feature — a corrupt or partial backup shouldn't break the restore. Validate 
before applying.
- BitChord v1.7 lesson: encrypted preferences shouldn't be backed up / restored blindly — ensure they're 
excluded or handled safely. The plan bakes that lesson in.

## What exists today

- Backup/restore exists (the executor checks at implementation time what backup infra is already there — the plan 
assumes a backup/restore flow exists and adds scheduling + validation + encrypted-prefs handling).
- WorkManager may or may not be used already (the executor checks at implementation time — the plan uses WorkManager 
for scheduling if available, or the executor picks the scheduling approach at implementation time).
- Encrypted preferences may or may not be in use (the executor checks at implementation time — if there are encrypted 
prefs, they're excluded from backup per the BitChord lesson).

## Scope

**In scope:**
- **Auto-schedule**: a WorkManager periodic task that triggers a backup on a cadence the user picks (e.g. daily, 
weekly, or a custom interval). The backup uses the existing backup flow. The schedule is a setting the user 
controls (on/off, cadence). Constraints: network + charging (the executor picks at implementation time — backup 
shouldn't drain battery / use data aggressively).
- **Restore validation**: before applying a restore, validate the backup: check it's a valid backup (format, 
structure, completeness). If invalid / partial, don't apply — show an error and let the user decide (try again, 
pick a different backup). The validation is a pre-restore check, not a post-hoc thing.
- **Encrypted prefs exclusion**: ensure encrypted preferences are excluded from the backup (or handled safely — 
the executor checks at implementation time which prefs are encrypted and ensures they're not blindly backed up / 
restored). The BitChord lesson: encrypted prefs shouldn't be restored blindly. The plan implements that: the 
backup excludes encrypted prefs, or restores them safely (the executor picks the approach at implementation time — 
exclude vs restore-with-reauthentication).
- **User controls**: backup schedule toggle + cadence, restore validation toggle (the user can choose to enforce 
validation — default on), encrypted-prefs handling (the executor documents the decision). The user controls the 
schedule; validation is on by default (safe restore).

**Out of scope:**
- Cloud backup / cloud sync of backups — defer; v1 is local backup files (the existing backup flow's output) with 
scheduling + validation. Cloud sync of backups is a different feature.
- Incremental backups — defer; v1 is full backup on schedule (the existing backup flow's output). Incremental is a 
later enhancement if needed.
- Backup versioning / rotation (keep last N backups) — defer; v1 may keep a simple backup file (overwrite or keep 
one). The executor decides at implementation time; versioning is a later enhancement.
- Backup to a specific cloud service — defer; v1 is local backup files. Cloud is a different feature.
- Restore from a partial backup with "best effort" — v1 is validate + error on invalid/partial, not best-effort 
restore. The user decides (try again, pick a different backup).

## Affected files

| File | Change |
|------|--------|
| `utils/BackupScheduler.kt` (new) | WorkManager periodic task to trigger a backup on the user's cadence. Constraints (network/charging). Toggle + cadence setting. |
| `utils/BackupRestore.kt` (or the existing backup/restore flow) | Restore validation: pre-restore check of backup validity/structure/completeness. On invalid/partial → error, don't apply. The executor extends the existing backup/restore flow at implementation time. |
| `utils/BackupEncryptionHandler.kt` (new, or extend existing) | Encrypted-prefs handling: identify encrypted prefs, exclude from backup / restore safely. The executor picks the approach at implementation time (exclude vs restore-with-reauth). |
| `constants/PreferenceKeys.kt` | Backup schedule toggle + cadence, restore validation toggle, encrypted-prefs handling decision. |
| `ui/screens/settings/StorageSettings.kt` (or a backup settings screen) | Backup schedule UI (on/off, cadence), restore validation toggle, encrypted-prefs safety note. The executor picks the settings surface at implementation time. |
| `sachit_strings.xml` | Backup schedule labels, cadence labels, restore validation labels, encrypted-prefs safety note, error strings for invalid/partial backup. |

## Technical approach

1. **Backup scheduling** — A WorkManager periodic task (e.g. `PeriodicWorkRequest` with a cadence the user picks — 
daily, weekly, or a custom interval). The task triggers the existing backup flow. Constraints: network (the backup 
may need network if it includes remote data) + possibly charging (the executor picks at implementation time — 
backups shouldn't drain battery). The schedule is a setting the user controls (on/off + cadence).
2. **Restore validation** — Before applying a restore, validate the backup: check it's a valid backup file/structure 
(the executor picks the validation at implementation time — e.g. check the backup is a valid JSON/DB/file, has the 
expected structure, isn't truncated/corrupt). If invalid/partial → error, don't apply, let the user pick a 
different backup or try again. Validation is on by default (safe restore).
3. **Encrypted prefs** — At implementation time, identify which prefs are encrypted (the executor checks the app's 
DataStore / SharedPreferences encryption at implementation time). Ensure they're handled safely in backup/restore: 
either excluded from backup (the backup doesn't include them) or restored with re-authentication (the user confirms 
on restore). The plan defaults to: encrypted prefs are excluded from the backup unless the app has a safe way to 
restore them. The BitChord lesson is baked in.
4. **User controls** — Backup schedule toggle + cadence (settings), restore validation toggle (on by default — safe 
restore), encrypted-prefs safety note (the settings UI notes how encrypted prefs are handled). The user controls the 
schedule; validation is on by default; encrypted-prefs handling is transparent (the app handles it, the user sees a 
note).
5. **Backup output** — The backup uses the existing backup flow's output (the executor checks at implementation time 
what the backup produces — a file, a DB dump, a JSON). Scheduling re-triggers that flow. Validation validates that 
output. No new backup format for v1 — reuse the existing one + add validation.

## Testing

- Unit test for backup scheduling: given a schedule toggle + cadence, assert the WorkManager periodic task is 
scheduled / unscheduled correctly. Mock WorkManager at implementation time.
- Unit test for restore validation: given a valid backup, assert validation passes; given an invalid/partial backup, 
assert validation fails + no restore applied. The executor implements validation at implementation time.
- Unit test for encrypted-prefs handling: given encrypted prefs, assert they're excluded from backup / handled safely 
per the implementation decision.
- Build: `./gradlew :app:assembleFossDebug` must pass + existing backup/restore tests must pass.
- Manual: set a backup schedule, verify a backup runs on schedule (or trigger it manually). Restore a backup, verify 
validation passes for a valid backup and fails for an invalid/partial one (the executor creates a test invalid backup). 
Verify encrypted prefs are excluded/handled safely. Test the settings UI (schedule toggle, cadence, validation toggle).
- Regression: existing backup/restore flow unchanged for the core path (backup produces its output, restore applies a 
valid backup). Validation + schedule are additive.

## Performance notes

- Backup scheduling is a periodic WorkManager task — cheap, battery/constraint-aware.
- Restore validation is a pre-restore check — cheap (format/structure check). No full restore attempt on invalid 
backups.
- Encrypted-prefs handling is a small check at backup/restore time — cheap.

## Rollback

- Remove the scheduler, validation, and encrypted-prefs handling → backup/restore reverts to the existing flow 
(no schedule, no validation, whatever encrypted-prefs handling existed before). Zero impact on the existing 
backup/restore core.

## Anti-BitChord check

- Backup auto-schedule + restore validation are standard backup quality features — implement as Audify: schedule + 
validate + encrypted-prefs safety. Don't copy Musicolet's backup approach blindly — implement Audify's take with the 
BitChord v1.7 lesson baked in (encrypted prefs handled safely).
- Restore validation is on by default (safe restore) — the user doesn't lose data to a corrupt backup. The user 
controls the schedule; validation is a safety net, not an annoyance.
