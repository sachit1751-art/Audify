# 007 Spike findings — localization pipeline (Audify)

Written 2026-09-04 against commit `7ca7a9653`. Spike deliverable; no code changed.

## Corrected baseline (differs from plan 007's premise)

- `app/src/main/res/values/sachit_strings.xml` holds **857 unique string keys**.
- `values/strings.xml` (upstream InnerTune strings) shares **0 duplicated keys** with it — the
  two-file split is clean, no resource-resolution conflict.
- **62 of 71** locale folders already contain a translated `sachit_strings.xml` (e.g.
  `values-de` has 831 of 857 keys). The Sachit-specific surface is *not* untranslated — it is
  mostly translated in-tree.
- 9 locale folders have no `sachit_strings.xml` (likely recently added locales).

## Real gaps

1. **`crowdin.yml` only maps `values/strings.xml`** — the file that holds 857 Sachit keys is
   not wired into Crowdin, so the 62 in-tree translations were produced/synced outside the
   declared config (or the config is stale). Any future Crowdin round-trip would touch only
   the 346-key upstream file and could drift from the Sachit file.
2. **Brand text inside translations**: 12 lines in `values-de/sachit_strings.xml` still
   contain the old "Sachit"/"Audify" tokens in *value* position, e.g. translated "Tap to open
   Sachit …" strings. Per AGENTS.md, AI must not edit translated files; these need a Crowdin
   re-key/re-translate pass after the English strings were rebranded (the Audify rebrand
   changed English values only).
3. The updater-branding and deep-link domain questions (plans 001/other) interact with locale
   UI text only minimally.

## Recommendation

Add the Sachit source to `crowdin.yml` alongside the existing mapping:

```yaml
files:
  - source: /app/src/main/res/values/strings.xml
    translation: /app/src/main/res/values-%android_code%/strings.xml
  - source: /app/src/main/res/values/sachit_strings.xml
    translation: /app/src/main/res/values-%android_code%/sachit_strings.xml
```

Then in Crowdin: (a) run a translation memory / machine pass on the rebranded English
strings (brand token "Sachit" → "Audify" across the 62 files), (b) add the 9 missing
locales. A CI sync step is optional — repo already had translations committed without a
visible Crowdin GitHub Action; keep the human-driven pull until one is proven.

## Actions taken

None in this spike (config left untouched for the owner to flip in Crowdin's UI alongside
the TM pass, since editing `crowdin.yml` alone has no effect until Crowdin re-reads it).
