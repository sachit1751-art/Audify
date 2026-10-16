# Plan 040 — Library hygiene

**Status: not started.** Verified at commit `61778ab4` — no duplicate detection or ghost flagging exists. 
The library has songs from import / sync / auto-playlists, and some may be duplicates (same song, multiple 
entries) or ghosts (a song that was removed from YouTube Music but is still in the local library).

## Objective

Two library-cleanliness features: (1) duplicate detection — find songs that appear more than once in the 
library (normalized title + duration match), flag them as duplicates, suggest which to keep; (2) ghost 
flagging — flag songs whose YouTube Music source is gone (can't be streamed / no longer in the user's 
library on YT) as ghosts, suggest removal. Suggestions only — never auto-delete. The user decides.

## Why

- Innovation #11: duplicate detection + ghost flagging, suggestions only, never auto-delete.
- A library accumulated over time has duplicates (imported twice, added from different sources) and ghosts 
(removed YT songs still sitting in the library). Hygiene features clean this up without the app being 
destructive.
- Source-health failure memory (plan 014 / shipped) feeds ghost flagging — if a song's source is 
permanently failed, it's a ghost candidate.

## What exists today

- The library has songs with metadata (title, artist, album, duration, YouTube IDs) and source info 
(whether it's a local file, a YT download, a synced song, etc.).
- Source health (plan 014 / shipped) tracks per-mediaId failure memory — a song with permanent source 
failure is a ghost candidate.
- No duplicate detection, no ghost flagging, no cleanup suggestions UI.

## Scope

**In scope:**
- **Duplicate detection**: a background scan (or on-demand) that finds duplicate songs — same normalized 
title + same duration (± a small tolerance) counts as a duplicate. Group duplicates, pick a "keep" candidate 
(the one with the most complete metadata / most recent / the first added — executor's choice for the 
tiebreak), flag the rest as "duplicate of X". Show duplicates in a library-hygiene UI: groups of duplicates, 
which to keep, which to remove. Suggestions only — the user removes duplicates manually.
- **Ghost flagging**: a song whose source is permanently failed (from source-health memory) or whose YT 
source is gone (can't be streamed, no YT ID, or the YT lookup fails) is flagged as a ghost. Show ghosts in 
the same hygiene UI (or a separate section): ghost songs, suggest "Remove". Suggestions only.
- A library-hygiene screen/section: shows duplicates and ghosts, with actions to remove the suggested songs. 
The user confirms each removal.

**Out of scope:**
- Auto-deleting duplicates or ghosts — explicitly rejected. Suggestions only, user confirms each removal.
- Fuzzy duplicate matching beyond normalized title + duration — v1 is normalized-title + duration. Fuzzy 
matching (Levenshtein, artist normalization) is a later enhancement.
- Removing duplicates automatically with a smart tiebreak — defer; v1 is suggestions.
- Fixing ghosts by re-downloading / re-syncing — defer; v1 flags ghosts and suggests removal. Re-fetch is a 
different feature.
- A full library-cleanup wizard — v1 is a hygiene screen with duplicates + ghosts and manual removal.

## Affected files

| File | Change |
|------|--------|
| `db/MusicDatabase.kt` | Add duplicate-detection query (normalized title + duration group) and ghost-query (songs with no playable source / permanent source failure / no YT ID). May reuse source-health tables. |
| `utils/LibraryHygieneAnalyzer.kt` (new) | Pure-ish analyzer: given library songs, return duplicate groups + ghost candidates. Normalized title function (lowercase, strip punctuation, trim). Duration tolerance. Keep-candidate tiebreak. |
| `ui/screens/library/LibraryHygieneScreen.kt` (new) | Hygiene screen: duplicates section (groups with keep/remove), ghosts section (list with remove). Suggestions only — remove actions confirm per song. |
| `ui/menu/LibraryMenu.kt` (or the library nav) | Add "Library hygiene" entry that opens the hygiene screen. |
| `constants/PreferenceKeys.kt` | Add a hygiene toggle (show hygiene screen) if the executor wants an opt-in; otherwise it's always available. |
| `sachit_strings.xml` | "Library hygiene", "Duplicates", "Ghosts", "Keep", "Remove", "Duplicate of…", "No source / removed from YouTube", "Remove ghost", empty states. |

## Technical approach

1. **Normalized title** — A pure function: lowercase, strip punctuation/whitespace, trim. Two songs are 
"same title" if their normalized titles match. The executor picks the exact normalization at implementation 
time (e.g. also strip featured artists, parentheses content? — keep v1 simple: lowercase + strip + trim).
2. **Duration tolerance** — Same duration ± a small tolerance (e.g. 1 second) to account for encoding 
differences. The executor picks the tolerance at implementation time.
3. **Duplicate groups** — Group songs by (normalized title, duration-bucket). Each group with >1 song is a 
duplicate group. Pick a "keep" candidate: the one with the most complete metadata, or the most recently 
added, or the first added — executor's choice. Flag the rest as "duplicate of [keep]".
4. **Ghost candidates** — A song is a ghost candidate if: (a) its source is permanently failed (source-health 
memory says so), OR (b) it has no playable source (no local file, no YT ID, can't be streamed), OR (c) a YT 
lookup for its ID fails (if it has a YT ID). The executor picks the exact ghost criteria at implementation 
time; the key is that a ghost is "this song can't be played from its source anymore".
5. **Hygiene UI** — A screen with two sections: Duplicates and Ghosts. Each shows groups/lists with a "keep" 
or "source" indicator and a "Remove" action. Removal is per-song, with confirmation. The user removes what 
they want; the app never auto-deletes.
6. **Constraints** — Hygiene runs on-demand (user opens the screen) or as a lazy background scan. Don't block 
the screen on a full scan — show empty state while scanning, populate as results come in. The executor picks 
the refresh policy at implementation time.

## Testing

- Unit test for normalized title: assert known title pairs normalize to the same / different strings.
- Unit test for duplicate detection: given a list of songs with known duplicates, assert the duplicate groups 
and keep-candidate are correct.
- Unit test for ghost detection: given songs with various source states, assert which are flagged as ghosts.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: open hygiene screen, verify duplicates are detected (create test duplicates), verify ghosts are 
flagged (mark a song's source as permanently failed), verify remove-individual works, verify no auto-delete. 
Test empty states (no duplicates, no ghosts).
- Regression: existing library screens unchanged; existing source-health state unchanged (hygiene reads it, 
doesn't change it).

## Performance notes

- Duplicate detection is a self-join or group-by on the library — cheap for typical library sizes. For large 
libraries (10k+ songs), the executor may paginate or scan incrementally. Defer large-library optimization 
until observed.
- Ghost detection uses existing source-health state — cheap.

## Rollback

- Remove the hygiene screen, analyzer, duplicate/ghost queries, and the menu entry → no hygiene features, 
library unchanged. Zero impact on existing library/songs.

## Anti-BitChord check

- Suggestions only, never auto-delete — that's the core rule. The hygiene screen shows what *could* be 
cleaned up; the user removes what they want. Don't copy a competitor's auto-clean approach — Audify is 
suggestions-first.
- Duplicate detection is simple (normalized title + duration) — don't over-engineer fuzzy matching for v1. 
Keep it readable and predictable.
