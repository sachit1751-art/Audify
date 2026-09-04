# Plan 007 — Spike: localization pipeline for Sachit-specific strings

Written against commit **`7ca7a9653`**. **Direction/spike plan** — the deliverable is a
recommendation + runnable config change, not a full localization rollout.

## Why this matters (evidence)

- The repo splits strings: `app/src/main/res/values/strings.xml` (346 lines, upstream
  InnerTune strings, header says "Do not add new features here") and
  `app/src/main/res/values/sachit_strings.xml` (1,008 lines, the app's own strings —
  AGENTS.md mandates all new strings land here).
- `crowdin.yml` sources ONLY `values/strings.xml`:
  ```yaml
  files:
    - source: /app/src/main/res/values/strings.xml
      translation: /app/src/main/res/values-%android_code%/strings.xml
  ```
- Locale folders (`values-af/`, `values-ar/`, `values-ars/`, `values-as/`, `values-az/`, …)
  exist for the upstream strings (translated via InnerTune's Weblate era). **No locale has a
  translated `sachit_strings.xml`** — the entire Sachit-specific surface (new settings,
  player UI, Listen Together screens, recognition, visualizer strings) is English-only for
  every non-English user.
- AGENTS.md forbids AI edits to translated string files — translations must come from
  humans/Crowdin, which is exactly why the *pipeline* is the right thing to build.

## Files in scope (spike may produce)

- `crowdin.yml` (config change, if recommended)
- `.github/workflows/*.yml` (only if a sync step is recommended — do not add without
  justification)
- A findings note appended under `plans/` (allowed markdown) — the spike's report
- **Out of scope:** editing any `values-*/` file, moving strings between files, touching
  `sachit_strings.xml` content.

## Spike steps

1. **Inventory the split**: count how many string names exist only in `sachit_strings.xml`
   vs shared/duplicated in `strings.xml`:
   ```bash
   grep -oP '(?<=<string name=")[^"]+' app/src/main/res/values/sachit_strings.xml | sort -u > /tmp/sachit.txt
   grep -oP '(?<=<string name=")[^"]+' app/src/main/res/values/strings.xml | sort -u > /tmp/upstream.txt
   comm -23 /tmp/sachit.txt /tmp/upstream.txt | wc -l   # Sachit-only keys (translation gap)
   comm -12 /tmp/sachit.txt /tmp/upstream.txt | wc -l   # duplicates (which file wins at runtime? report)
   ```
   Record the numbers and the duplicates question — duplicated keys across the two files is a
   latent bug (resource resolution order is by file, and it matters which file Android picks;
   check `lint.xml`/build for any merge ordering and report).

2. **Design the Crowdin mapping** for the Sachit file following the existing one, e.g.:
   ```yaml
   - source: /app/src/main/res/values/sachit_strings.xml
     translation: /app/src/main/res/values-%android_code%/sachit_strings.xml
   ```
   Verify `%android_code%` is the correct Crowdin placeholder for Android locale codes for
   BOTH files and note any locale-code mismatches (e.g. `ars` — Android's
   `values-ars`? confirm it is a valid BCP-47-style folder Android accepts).

3. **Check for conflicts**: if `strings.xml` and `sachit_strings.xml` both define the same
   name, Crowdin will produce duplicate keys across two files per locale. Recommend either
   de-duplicating (move upstream-dup keys into one file — a **human** decision, since it
   touches the "do not add features here" file's conventions) or accept the duplication with
   a documented resolution order.

4. **Write the spike report** as `plans/results/007-localization-findings.md` containing:
   the gap numbers, the recommended crowdin.yml addition (exact YAML), whether a CI sync
   step is worth it (weigh: repo has `crowdin.yml` but no visible Crowdin GitHub Action in
   `.github/workflows/` — verify), and the duplicate-keys finding. Keep the report under ~60
   lines; decisions belong to the human.

5. **Apply only the safe config change** if step 4 concludes the crowdin.yml addition is
   safe on its own (it is inert until Crowdin picks it up) — otherwise leave `crowdin.yml`
   untouched and hand the recommendation to the human.

## Verification

- `crowdin.yml` (if edited) parses as YAML: `python -c "import yaml,sys; yaml.safe_load(open('crowdin.yml'))"` (or equivalent) → no error.
- No `values-*/` files and no `sachit_strings.xml`/`strings.xml` content changed.
- `git status` shows only `crowdin.yml` (+ the report file under `plans/results/`).

## Done criteria

- Spike report written with: Sachit-only key count, duplicate-key inventory + resolution
  recommendation, exact crowdin.yml mapping, CI-sync recommendation with evidence.
- Safe config change applied or explicitly deferred with reason.
- Build not required (no code touched), but run `./gradlew :app:assembleFossDebug` once if
  any resource file changed — none should have.

## Escape hatches

- If the two files share keys AND Android resource merging makes the winner non-obvious
  from static analysis, do not guess — recommend a human-verified runtime check (a one-off
  debug build logging resolved strings) and stop.
- Do not create or edit any translated resource file, ever, in this plan.

## Maintenance note

AGENTS.md's single-file rule exists so the translation pipeline has one moving target. The
end state this spike points toward: `sachit_strings.xml` fully covered by Crowdin, duplicate
keys eliminated, and new-feature PRs never adding translatable strings to `strings.xml`.
