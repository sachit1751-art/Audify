# Plan 003 — Remove the `200B` watermark comments (513 files)

Written against commit **`7ca7a9653`**. Commit `ee568a4da` ("add invisible code watermarks")
touched 513 Kotlin files (+874/−361): it rewrote each file's license header first line AND
added a provenance comment line. **Correction from audit vetting:** the marker is literal
ASCII — `// 200Bsachit-2026-original200B` — there are no zero-width/invisible bytes
(verified with `od -c`). The line is a stray provenance comment that pollutes every diff,
every changelog, and every crash attribution in this repo.

## Scope decision (read first)

This plan **only deletes the added watermark comment lines**. It does NOT normalize the
license-header first line (currently `SachitMusic Project (C) 2026` in many files) — that is
brand-text churn across 500+ files with real merge risk, and the brand's canonical spelling
("Sachit" vs "Sachit Music") is a human decision. If you want headers normalized, STOP and
ask the human; do not improvise a rename.

## Files in scope

- Exactly the files under `app/src` and `innertube/src` that contain a line whose only
  content is the watermark comment `// 200Bsachit-2026-original200B`.
- **Out of scope:** everything else; do not reformat, reorder imports, or touch header text.

## Current state

Detection is exact and cheap — the marker substring appears 513 times today:

```bash
grep -rln "200Bsachit-2026-original200B" app/src innertube/src | wc -l   # 513
```

Sample occurrence (`app/src/main/kotlin/com/sachit/music/App.kt:6`):

```kotlin
/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// 200Bsachit-2026-original200B
package com.sachit.music
```

## Steps

1. **Drift check**: `git status` should contain only the 11 uncommitted WIP paths from the
   session handoff (see plan 002 step 1). The watermark files are all committed, so this
   plan can run alongside the WIP. If unrelated uncommitted changes exist, stop and report.

2. **Confirm exact-match deletion set**:
   ```bash
   grep -rl "200Bsachit-2026-original200B" app/src innertube/src > /tmp/wm_files.txt
   wc -l /tmp/wm_files.txt   # expect 513
   ```
   Sample the first/last 5 files to confirm the line format is uniform
   (`// 200Bsachit-2026-original200B`, alone on its line).

3. **Delete the lines** with a script that removes only lines whose entire trimmed content
   equals the marker. On Git Bash:
   ```bash
   while read -r f; do
     sed -i '/^\/\/ 200Bsachit-2026-original200B$/d' "$f"
   done < /tmp/wm_files.txt
   ```
   If the file list includes non-Kotlin files (XML/etc.), inspect one first and keep the same
   whole-line rule.

4. **Verify removal**:
   ```bash
   grep -rln "200Bsachit-2026-original200B" app/src innertube/src | wc -l   # 0
   git diff --stat | tail -5
   ```
   Diff must be **only deletions** (each file −1 line), no other hunks. If a file shows any
   non-deletion hunk, `git checkout` that single file and re-run the deletion for it.

5. **Sanity-check formatting was not disturbed**: pick 5 files from the list and confirm the
   line before `package`/first declaration still looks right (blank-line handling). If a
   deletion left a doubled blank line, that is acceptable noise — do NOT run a formatter
   (no formatter is configured; running one would create massive unrelated diffs).

6. **Build + tests**:
   ```bash
   export JAVA_HOME="C:\\Users\\HP\\.jdks\\jbr-21.0.11"
   ./gradlew :app:assembleFossDebug --console=plain
   ./gradlew :app:testFossDebugUnitTest :innertube:testDebugUnitTest --console=plain
   ```

## Verification

- `grep -rln "200Bsachit" app/src innertube/src` → no output.
- `git diff` contains only single-line deletions of the marker comment.
- Build and both unit-test tasks exit 0.

## Done criteria

- Marker gone from every tracked file under `app/src` and `innertube/src`.
- Diff is deletions-only; no header text, formatting, or import changes.
- Full unit-test suite and FOSS debug build pass.

## Escape hatches

- If any file contains the marker in a form other than a whole-line comment (e.g. inline in
  a string), STOP and report that file — do not guess at a fix.
- If removal exposes a pre-existing compile issue in any file (the marker sat on its own
  line, so this is not expected), fix nothing: report it as a separate pre-existing issue.

## Maintenance note

If a future commit re-introduces watermark-style provenance comments, the one-line grep above
is the regression check. The related header-text inconsistency (`SachitMusic` vs
`Sachit Music`) remains open and human-owned — record it as a follow-up in your completion
report so it can be decided deliberately rather than mass-edited.
