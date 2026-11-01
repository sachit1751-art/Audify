<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->
# Plan 062 — Selected feature 12: Improved source fallback

**Status: planned — builds on plan 021, which already added stream-source health and fresh-resolution fallback.**

## Objective

Make source fallback understandable and deterministic: expose why the preferred source failed, retry only eligible alternatives, preserve the requested track identity, and never silently substitute a different recording.

## Why

Fallback improves reliability only when the alternative is genuinely the same requested recording and the user can understand what changed.

## What exists today

- Plan 021 shipped per-media source health and cache bypass after repeated failures.
- `playback/StreamResolver.kt`, `PlaybackIntent.kt`, and `PlaybackTarget.kt` centralize resolution and transport.
- `playback/` includes stream health and diagnostics; inspect current implementation before extending.

## Scope

**In scope:** typed failure categories; bounded retry policy; eligible source ordering; same-recording confidence gate; user-visible fallback reason/status; manual retry/source choice; diagnostics suitable for support without leaking tokens or URLs.

**Out of scope:** silently swapping to a cover/live/remix; unbounded retries; exposing signed stream URLs or credentials; inventing a new source provider without approval.

## Affected files

| File | Expected change |
|------|-----------------|
| `playback/StreamResolver.kt` | Structured resolution result and fallback policy |
| `playback/StreamSourceHealth.kt` | Reuse failure memory and TTL semantics |
| `playback/PlaybackIntent.kt` | Preserve requested identity and user intent |
| player error/status UI | Explain retry/fallback and allow manual action |
| playback tests | Verify same-track and bounded-retry invariants |

## Implementation

1. Re-audit plan 021 and enumerate existing retry paths; avoid a second fallback engine.
2. Classify failures as transient, unsupported, unavailable, auth/permission, or unknown.
3. Define source eligibility and recording-match confidence; stop rather than substitute when confidence is insufficient.
4. Add bounded backoff and per-request attempt budget; make retries idempotent.
5. Surface a concise explanation and manual retry/source action; redact sensitive diagnostics.
6. Instrument tests around cache poisoning, source cooldown, and playback continuity.

## Testing

- Unit tests for failure classification, retry cap, cooldown expiry, and source ordering.
- Ensure alternate source maps to same recording or is rejected.
- Test auth errors are not retried indefinitely and user cancellation stops retries.
- Manual network loss/recovery and provider outage tests.

## Performance notes

Reuse source-health cache and avoid sequential long timeouts. Do not prefetch multiple full streams just to compare candidates.

## Rollback

Disable new explanatory UI/policy while retaining plan 021's existing source-health behavior.

## Rejected alternatives

- Any playable track is a valid fallback: this breaks user intent.
- Infinite retries: drains battery and delays actionable errors.
- Expose raw resolver URLs in UI/logs: may leak signed or sensitive data.

## Implementation contract and acceptance criteria

### Failure and candidate contract
- Structured failure result must include category, retryability, source/provider, safe user message key, and redacted diagnostic code. Never persist signed URLs, headers, tokens, or raw credentials.
- Retry budget is finite and scoped to one playback request. Define per-category behavior: transient/network may retry with backoff; auth/permission requires user action; unsupported/unavailable should not loop.
- A fallback candidate must preserve requested recording identity. Define matching evidence and rejection threshold; title-only similarity is insufficient. If identity is uncertain, stop and ask the user rather than silently playing another version.
- User cancellation, next-track action, and newer playback intent invalidate in-flight retries.

### UX behavior
- Show concise status such as retrying preferred source, using equivalent source, or unable to find same recording. Distinguish source change from track change.
- Offer manual retry and source choice only where provider/source metadata is available and supported. Do not expose internal provider URLs.

### Done when
- Tests cover every failure category, attempt cap, cooldown expiry, stale request cancellation, cache bypass, candidate mismatch, and recovery after connectivity returns.
- No fallback can change recording identity without explicit user choice; existing plan 021 health behavior remains intact.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
