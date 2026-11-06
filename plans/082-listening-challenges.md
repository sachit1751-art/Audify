<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
-->

# Plan 082 — Listening Challenges

**Status: proposal — use only trustworthy local listening and library signals.**

## Outcome

Optional, private listening prompts help users explore their own library: revisit an artist, play a saved album, or discover a genre. Challenges feel like suggestions, not obligations.

## Implementation

1. Audit which listening aggregates and library metadata are reliable; do not assume complete per-play history.
2. Define a small set of opt-in challenges that can be evaluated from those signals, with clear time windows and reset behavior.
3. Show one or a few suggested challenges in an existing discovery surface; provide dismiss, pause, and opt-out controls.
4. Keep progress local and private. Avoid public leaderboards, social pressure, streak loss, or notifications by default.
5. Make unavailable signals produce no challenge rather than a fabricated completion state.

## Verification

- Unit-test challenge eligibility, date boundaries, progress, reset, missing data, and duplicate completion.
- Verify opt-out and dismiss persist correctly and no background work runs when disabled.
- Test timezone changes and device clock changes without awarding duplicate progress.
- Confirm no listening history is uploaded and no new permission is requested.

## Rollback

Hide the challenge surface and disable evaluation; retain existing listening data and preferences.

## Guardrails

No manipulative streak mechanics, public rankings, or body/health-style behavioral claims. Challenges must remain optional and non-punitive.
