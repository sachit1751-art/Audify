# Plan 006 — Fix development-guide + CI drift

Written against commit **`7ca7a9653`**. Small DX cleanup. **IMPORTANT: markdown edits in
this repo are gated on a human** (AGENTS.md: "prohibited from making ANY changes to the
readme/markdown files"). This plan has two tracks: Track A (CI/workflow YAML — executor may
edit) and Track B (markdown — executor must NOT edit; prepare the corrected content and stop
for the human to apply).

## Why this matters (evidence)

1. `development_guide.md` tells new contributors:
   ```bash
   git clone https://github.com/sachit1751-art/Sachit-Music
   cd Sachit Music            # ← directory is actually "Sachit-Music"
   git submodule update --init --recursive   # ← repo has NO submodules (settings.gradle.kts includes only :app and :innertube, both local)
   ```
2. AGENTS.md session-handoff guidance describes FOSS debug APK output at
   `app/build/outputs/apk/foss/debug/app-foss-debug.apk` and release practice for three
   flavors — but `.github/workflows/build.yml` nightly matrix builds **only GMS** (matrix
   comment explicitly drops FOSS/Izzy). Nothing produces FOSS or Izzy debug/nightly
   artifacts in CI anymore, so contributors told to grab the FOSS nightly can't.

## Files in scope

- Track A: `.github/workflows/build.yml` (only if the human wants flavor coverage restored —
   see step 2; otherwise Track A is empty)
- Track B: `development_guide.md` (prepared content only — human applies)

## Steps

1. **Re-read** `development_guide.md` (16 lines) and `.github/workflows/build.yml`
   (build_debug/build_release jobs) to confirm the excerpts above still hold at execution
   time.

2. **Ask the human which track(s) to do** — present these options in your report:
   - **B only** (recommended if CI runtime budget matters): correct the guide text; no CI
     change. 15-minute CI timeouts × GMS-only is a deliberate cost decision recorded in the
     workflow comments — do not silently undo it.
   - **A + B**: restore FOSS/Izzy coverage in `build.yml` (re-add them to both matrices, and
     re-verify the `publish_nightly` rename step handles `Sachit-with-Google-Cast.apk` vs
     `Sachit.apk` naming) AND fix the guide.

3. **Track B execution** (only with human go-ahead to edit, or prepare-only otherwise):
   Produce the corrected first paragraph of `development_guide.md`:
   - `cd Sachit-Music`
   - drop the submodule line
   - keep the keystore + `./gradlew :app:assembleFossDebug` steps (still correct)
   Write the corrected block into your completion report / this plan's "Prepared text"
   section below for the human to paste. Do not edit the file yourself unless explicitly
   authorized.

### Prepared text (for the human to apply)

```markdown
git clone https://github.com/sachit1751-art/Sachit-Music
cd Sachit-Music
[ ! -f "app/persistent-debug.keystore" ] && keytool -genkeypair -v -keystore app/persistent-debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" || echo "Keystore already exists."
./gradlew :app:assembleFossDebug
ls app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

## Verification

- Track A (if taken): push to a fork/PR branch and confirm the matrix lists foss/gms/izzy —
  or, at minimum, `grep -n "variant:" .github/workflows/build.yml` shows the re-added
  variants. Note: workflows cannot be dry-run locally; a human must trigger one.
- Track B: human applies text; then `grep -n "submodule\|Sachit Music$" development_guide.md`
  returns nothing.

## Done criteria

- Track A (chosen): build.yml matrices include foss/gms/izzy with correct artifact naming.
- Track B: guide no longer references submodules or the wrong directory.
- No code, strings, schema, or version changes anywhere.

## Escape hatches

- If the human declines both tracks, the deliverable is this report alone (drift documented,
  nothing changed) — that is a valid outcome.
- If the executor lacks authority to touch `.github/` (repo permission), do Track B prep only
  and report.

## Maintenance note

The guide and CI drift apart because release flows changed faster than docs. Any future
flavor-matrix change should update `development_guide.md` and AGENTS.md references in the
same PR — worth a checklist note in the workflow PR template if one exists.
