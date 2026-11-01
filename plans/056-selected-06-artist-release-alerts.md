<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 056 — Selected feature 06: Artist release alerts

**Status: planned — reconciles existing plan 044.**

## Objective

Let users follow artists and receive opt-in notifications when a new release is discovered. Deduplicate releases, explain the matched artist/release, and provide per-artist and global controls.

## Why

Release alerts are useful discovery, but false matches, duplicate notifications, and unexpected background network activity would undermine trust.

## What exists today

- Artist identity and metadata are surfaced through existing artist screens and `data/canvas/ArtistSignatures.kt`.
- Existing notification patterns include `data/webdav/WebDavUploadNotifications.kt`; inspect Android notification channels and permission handling.
- Plan 044 is the original feature plan and must be reconciled before implementation.

## Scope

**In scope:** explicit follow/unfollow; stable artist identity where available; release discovery through an approved existing provider; scheduled, battery-aware refresh; duplicate suppression; notification permission/channel handling; per-artist mute; release details and open action; last-checked status.

**Out of scope:** scraping unsupported endpoints, bypassing provider limits, notifications enabled by default, account credentials, social feed features.

## Affected files

| File | Expected change |
|------|-----------------|
| artist data/repository layer | Followed-artist identity and release query abstraction; inspect current models first |
| new `data/discovery/ArtistReleaseChecker.kt` | Matching, freshness, and deduplication logic |
| `AudifyApplication.kt` / worker package | Background scheduling |
| Android notification channel/receiver files | Permission-aware release notifications |
| artist screen and settings UI | Follow/unfollow and alert preferences |
| `app/src/main/res/values/sachit_strings.xml` | English-only copy |

## Implementation

1. Audit plan 044, provider terms/capabilities, artist ID stability, and current notification infrastructure.
2. Define a release record and matching confidence; avoid name-only matching where artist identity is ambiguous.
3. Add explicit follow state and controls using existing permitted persistence; no schema change without approval.
4. Implement rate-limited background checks with backoff and a bounded lookback window.
5. Deduplicate on provider release ID, falling back to a stable composite only with tests.
6. Add permission-aware notifications and a history/status screen or existing destination.

## Testing

- Artist alias/collaboration/remaster/compilation matching tests.
- Duplicate delivery, provider failure, rate-limit, clock skew, and permission-denied tests.
- Verify disabled alerts cause no scheduled checks and no notifications.
- Manual notification channel and Android 13+ permission test.

## Performance notes

Batch artist checks when provider supports it; bound the followed list and polling frequency; use WorkManager constraints.

## Rollback

Cancel workers, disable notification posting, and preserve follow preferences. Existing artist browsing must remain unaffected.

## Rejected alternatives

- Poll every artist frequently: battery and provider load are unjustified.
- Match by display name alone: creates false positives.
- Push notifications from a new backend: unnecessary until a supported provider requires it.

## Implementation contract and acceptance criteria

### Identity and discovery contract
- Follow state must use stable provider artist identity plus provider namespace; display name is presentation only. If identity cannot be made stable, do not enable alerts for that artist.
- A release candidate includes stable release ID, artist IDs, title, release type/date, source link, and discovery timestamp. Define whether singles, albums, EPs, compilations, collaborations, and reissues notify; default conservatively and document it.
- First enable/follow establishes a baseline and must not notify the entire back catalogue. Lookback and pagination limits must be bounded.
- Dedup key is provider namespace + release ID. Composite fallback must be deterministic and collision-tested.

### Scheduling and controls
- Alerts are opt-in; Android 13+ notification permission denial is a normal state, not a worker failure loop. Global off cancels scheduled work. Per-artist mute does not unfollow.
- Apply provider rate limits, exponential backoff, unique WorkManager work, and a maximum artist batch per run. Do not poll unsupported endpoints or scrape pages contrary to provider terms.
- Notification tap must resolve a valid in-app destination; stale/deleted releases fall back to artist page with a clear explanation.

### Done when
- Tests cover alias collisions, collaborations, reissues, first-follow baseline, duplicate runs, clock skew, provider outage/rate limit, permission denial, mute/unfollow, and worker cancellation.
- No notification is posted twice for one release and no scheduled network check runs when the feature is disabled.
- Provider feasibility and terms are documented before implementation; if no supported release feed exists, mark blocked rather than faking it.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
