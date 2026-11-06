<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 081 — Library Backup and Restore

**Status: proposal — define portable data boundaries before implementation.**

## Outcome

Users can export a backup of supported Audify-owned library data and restore it on a replacement or reinstalled device, with a preview and clear conflict handling.

## Implementation

1. Inventory local playlists, preferences, bookmarks, queue settings, and provider-specific data; classify each as portable, device-bound, or non-exportable.
2. Define a versioned backup format containing only supported Audify-owned metadata. Exclude credentials, tokens, private provider databases, and inaccessible media files.
3. Export through Android's Storage Access Framework; validate size, format, and version on import.
4. Preview item counts and conflicts before restore. Require confirmation and make restore recoverable if any step fails.
5. Reuse existing persistence APIs; schema or migration changes require separate approval.

## Verification

- Round-trip test export/import for each supported data type and backup version.
- Test corrupt, truncated, oversized, unknown-version, duplicate, and partial backups.
- Verify cancellation or failure does not leave a half-restored library.
- Confirm credentials and private tokens never appear in the exported file; manually restore onto a clean app install.

## Rollback

Disable backup/restore entry points while leaving existing library data untouched.

## Guardrails

Never promise recovery of provider-owned playlists or downloaded media unless the provider explicitly supports it. Do not export secrets or silently overwrite newer user data.
