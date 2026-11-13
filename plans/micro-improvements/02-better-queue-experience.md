# 02 — Better Queue Experience

**Status:** Proposal — reconcile with Plan 071 and current queue implementation.

## Problem
Users need more control over what plays next and confidence that queue changes will not unexpectedly lose their place.

## Brief solution
Polish the existing queue with clear play-next and add-to-queue actions, reliable reordering, remove/clear controls, queue history where supported, and restoration of the current queue. Preserve stable queue-item identity.

## Expected outcome
Users can shape the listening session quickly and understand exactly what will play next.

**Acceptance signal:** reorder, insert, remove, repeat, shuffle, and restoration behave consistently across supported clients.
