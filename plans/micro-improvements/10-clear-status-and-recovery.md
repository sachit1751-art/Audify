# 10 — Clear Status and Recovery

**Status:** Proposal — audit existing error, sync, provider, and diagnostics patterns first.

## Problem
When artwork, lyrics, provider search, downloads, or synchronization fails, vague loading states leave users unsure whether to wait, retry, or take another action.

## Brief solution
Standardize actionable status and error states: distinguish loading, empty, unavailable, offline, permission-required, and failed states. Add safe retry actions and concise diagnostics that exclude credentials and private listening data.

## Expected outcome
Users can understand what happened and recover without guessing; support reports become more actionable.

**Acceptance signal:** major failure states have a clear explanation and next action, and diagnostics are privacy-reviewed.
