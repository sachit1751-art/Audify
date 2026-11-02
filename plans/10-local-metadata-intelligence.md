<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 10 — Local metadata intelligence

**Status: proposal — reconcile with existing local-library and tag-editing plans first.**

## Objective

Improve local-library accuracy by extracting and normalizing richer audio metadata, supporting multi-value tags where feasible, and reconciling metadata with MediaStore without silently rewriting user files.

## User problem

Inconsistent tags can split albums/artists, produce incorrect sorting, and weaken search and artwork association. Metadata improvements must not corrupt files or misrepresent what Android can access.

## Scope

**In scope:** format/field capability matrix; read-only metadata extraction; normalization rules; multi-value artist/genre handling; conflict reporting between embedded tags and MediaStore; optional user-reviewed metadata correction only if existing tag-writing support and permissions are proven safe.

**Out of scope:** broad storage permission, silent file mutation, unsupported format claims, destructive bulk retagging, schema changes, or duplicating an existing local-library indexing plan.

## Implementation

1. Reconcile plans covering local-library indexing, tag editing, cleanup, and MediaStore improvements. If they own this scope, merge requirements into the canonical plan.
2. Inventory current local-file discovery, metadata readers, supported codecs/containers, URI permissions, and model fields.
3. Define a format-by-format extraction matrix with field provenance and confidence. Unknown/missing values remain unknown; do not fabricate defaults.
4. Implement read-only extraction and normalization first, preserving original embedded values and source provenance.
5. Define deterministic grouping rules for multi-value artist/album-artist/genre tags without breaking existing library IDs or playlist references.
6. Surface conflicts for user review. Any tag-writing feature requires a separate safety review, backup/undo design, permission analysis, and explicit approval.

## Acceptance criteria

- Tests cover supported formats, malformed tags, Unicode, multi-value separators, missing fields, and conflicting MediaStore values.
- Existing library entries and playlists remain stable when metadata is missing or changes.
- The UI distinguishes embedded metadata, MediaStore metadata, normalized display values, and unknown values.
- No media file is modified by the read-only implementation.
- Large libraries are processed off the main thread with cancellation/progress where appropriate.
- Device tests cover scoped-storage/content-URI behavior and representative audio files.

## Rollback

Disable enhanced extraction/normalization and return to the previous metadata selection path; leave source files untouched.

## Mandatory pre-implementation audit

Inspect older plans 025–068 for local indexing, metadata/tag editing, library cleanup, and search overlap. Identify the actual parser, model, repository, and permission owners. Produce a supported-format matrix and exact test plan before code changes.

## Project constraints and handoff

Follow `plans/README.md`, `PRODUCT.md`, and `DESIGN.md`. Never silently rewrite user files. No schema/migration, version bump, translated-string edits, or unauthorized commits/pushes. State format limitations and rollback steps.
