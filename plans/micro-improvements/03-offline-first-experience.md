# 03 — Offline-First Experience

**Status:** Proposal — reconcile with Plan 081 and existing download/provider capabilities.

## Problem
A music app becomes frustrating when browsing or playback stops working during weak connectivity, even for content already available locally.

## Brief solution
Define a clear local-versus-online capability boundary. Improve downloaded-item visibility, offline library browsing and search, download state, retry handling, and seamless fallback to local files. Do not imply that provider streams are downloadable when licensing or provider APIs do not allow it.

## Expected outcome
Users can reliably find and play eligible local/downloaded content without a connection and can understand which features require internet access.

**Acceptance signal:** airplane-mode tests cover library browsing, search, playback, download state, and recovery after reconnection.
