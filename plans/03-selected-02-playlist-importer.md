<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 03 — Selected feature 02: Complete playlist importer

**Status: planned — extends existing plan 038; importer is partially implemented.**

## Objective

Complete a safe, preview-first import pipeline for M3U/M3U8, Audify JSON, and Spotify CSV/TSV exports. Resolve imported entries to playable Audify tracks, show match confidence and unmatched rows, and create a playlist only after explicit confirmation.

## Why

The existing Spotify CSV/TSV SAF flow is only one format and matching failures need to be visible rather than silently discarded. Import should be predictable and recoverable.

## What exists today

- `shared/src/jvmSharedMain/kotlin/com/music/audify/data/spotify/SpotifyPlaylistFileParser.kt` and its JVM tests parse Spotify exports.
- `shared/src/jvmSharedMain/kotlin/com/music/audify/data/spotify/SpotifyImporter.kt` handles matching/import logic.
- `app/src/main/java/com/music/audify/ui/components/SpotifyImportAlert.kt` and `MainActivity.kt` contain Android import integration.
- Plan 038 documents partial support. Re-read it and tests before changing parser contracts.

## Scope

**In scope:** M3U and M3U8 parsing (including relative paths and extended metadata); documented Audify JSON format with explicit version; Spotify CSV/TSV header mapping; robust charset/newline handling; duplicate detection; deterministic matching; confidence/reason display; unmatched and ambiguous review; import preview; explicit create/merge decision; malformed-file diagnostics; SAF URI handling.

**Out of scope:** Spotify account scraping, credential collection, bypassing service restrictions, automatic import without preview, arbitrary playlist-folder hierarchy, schema changes.

## Affected files

| File | Expected change |
|------|-----------------|
| `shared/.../data/spotify/SpotifyPlaylistFileParser.kt` | Reuse/refactor shared parser primitives |
| `shared/.../data/spotify/SpotifyImporter.kt` | Matching result model and import orchestration |
| `shared/src/jvmTest/.../SpotifyPlaylistFileParserTest.kt` | Format and malformed-input tests |
| `shared/src/jvmTest/.../SpotifyLibraryTest.kt` | Matching regressions where applicable |
| `app/.../SpotifyImportAlert.kt` | Preview, match confidence, unmatched review |
| `MainActivity.kt` | SAF routing and confirmed import callback |
| `app/src/main/res/values/sachit_strings.xml` | English-only import labels and errors |

## Implementation

1. Audit plan 038, parser, SAF contract, matching, and playlist creation; list exact unsupported cases.
2. Define a format-neutral import document and row result model; keep file parsing independent from Android UI.
3. Add strict M3U/M3U8 and versioned JSON parsers with size/row limits and safe URI treatment.
4. Harden CSV/TSV mapping for BOM, quoted delimiters, alternate headers, missing columns, and malformed rows.
5. Add matching tiers (stable ID when legitimately present, then normalized title/artist/album/duration); never silently choose among near-equal candidates.
6. Build a preview that separates matched, ambiguous, duplicate, and unmatched rows; let users exclude rows.
7. Commit playlist creation only after confirmation; report imported/skipped counts and retain no source file contents unnecessarily.

## Testing

- Parser fixtures for CRLF/LF, BOM, quotes, Unicode, relative paths, extended M3U, empty files, oversized files, malformed JSON, and duplicate rows.
- Deterministic matcher tests for exact, fuzzy, ambiguous, missing, and duplicate matches.
- Import cancellation and failure tests proving no partial playlist is created.
- Android SAF manual tests for local and cloud document providers.

## Performance notes

Cap input bytes and rows; batch matching and avoid one network request per imported row. Keep preview rendering lazy for large lists.

## Rollback

Retain the current Spotify CSV/TSV path behind the existing entry point; new formats can be disabled independently without altering existing playlists.

## Rejected alternatives

- Best-guess matching with no review: silently imports wrong versions.
- Parsing every format in Compose: makes parser behavior untestable and platform-dependent.
- Importing external local paths as trusted playable URIs: path access must follow Android SAF grants.

## Implementation contract and acceptance criteria

### Import document contract
- Normalize every supported file into a platform-neutral document: source format/version, playlist name, ordered entries, source row number, title, artist, album, duration, external ID if present, and local path/URI text as untrusted metadata.
- Preserve row order and duplicate occurrences. Never treat an external ID as an Audify playback ID unless a documented provider mapping proves equivalence.
- Define explicit limits for file bytes, row count, field length, and parser nesting before implementation; reject over-limit input with actionable diagnostics.
- M3U relative paths resolve only against the selected document's permitted parent context; never turn arbitrary paths into unrestricted file access. Audify JSON requires a documented version and rejects unsupported major versions.

### Matching and transaction behavior
- Matching result per row is exactly one of matched, ambiguous, unmatched, duplicate-occurrence, excluded, or invalid; include evidence and confidence tier, not a misleading numeric confidence unless calibrated.
- Exact stable provider ID is first only when source/provider identity is known; then normalized title+artist, album, duration tolerance. Do not fuzzy-match across artist mismatch without review.
- Preview supports row exclusion and explicit create-new versus merge-into-existing behavior. Define duplicate handling separately for repeated source rows and already-present playlist entries.
- Import commit is atomic from the user's perspective: cancellation or failure leaves the target playlist unchanged. If backend writes cannot be transactional, stage and compensate or explicitly document the limitation before coding.

### Done when
- Every supported format has fixtures for valid, malformed, empty, oversized, Unicode/BOM, newline, quoting, and duplicate cases.
- Preview counts reconcile exactly with source row count; every skipped row has a reason.
- SAF grants remain scoped; no credentials are requested or stored; no source file is retained after import unless user explicitly saves it.
- Existing Spotify CSV/TSV import remains regression-tested.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
