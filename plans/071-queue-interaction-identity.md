# 071 — Preserve track identity during queue edits

- **Status**: TODO
- **Commit**: dd97d775
- **Severity**: HIGH
- **Category**: Interruptibility; performance; physicality & origin
- **Estimated scope**: 4–6 files; medium change

## Problem

Queue reordering currently keys rows by `song.setVideoId ?: song.videoId` at `app/src/main/java/com/music/audify/ui/components/ReorderPlaylistPage.kt:260-262`:

```kotlin
itemsIndexed(order, key = { _, song -> song.setVideoId ?: song.videoId }) { _, song ->
    val key = song.setVideoId ?: song.videoId
    val dragging = held == key
```

This key is media identity, not necessarily queue-occurrence identity. The product explicitly permits duplicate tracks in a queue. Two occurrences of the same recording can therefore collide or cause drag state to attach to the wrong row. The row currently uses `animateItem(fadeInSpec = null, fadeOutSpec = null)` for non-dragged rows (`:279-282`).

`QueueActionNotice.kt:42-52` already respects the app's Reduce Animation preference, but its current no-motion branch removes both enter and exit transitions entirely. Keep feedback understandable without requiring motion.

## Target

- Every queue occurrence has a stable unique key independent of media ID and current index. Use an existing queue-entry/occurrence ID if available; otherwise add an ephemeral stable occurrence key at the queue UI model boundary without persistence/schema changes.
- Reordering moves the selected occurrence; duplicate copies remain distinct and do not exchange identity.
- Insertion/removal animates neighboring rows with a short, interruptible positional transition. Avoid fade/scale on every row and avoid animation for high-frequency list navigation.
- Dragged row remains legible and visually elevated; drag cancellation restores the exact original order.
- Queue action notices retain a brief opacity/state change in reduced-motion mode, with movement removed.

## Repo conventions to follow

- Reorder list and gesture owner: `app/src/main/java/com/music/audify/ui/components/ReorderPlaylistPage.kt:260-366`.
- Queue feedback: `app/src/main/java/com/music/audify/ui/components/QueueActionNotice.kt:42-55`.
- Shared motion tokens: `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/motion/MotionTokens.kt:10-20`.
- Design rule: queue rows represent queue occurrences, not just media IDs (`docs/DESIGN.md`, Audio/player-specific design rules).

## Steps

1. Trace the queue domain model and playback queue adapter to locate an existing stable occurrence/entry ID. Do not use list index as a persistent identity because it changes during reorder.
2. If no occurrence ID exists, create a UI-layer stable key when queue entries enter the reorder model; preserve keys while applying reorder and do not change Room schema.
3. Replace media-ID-only keys and `held` drag identity with occurrence keys. Keep media ID separately for playback operations.
4. Verify duplicate tracks can be independently dragged, inserted and removed. Ensure removal targets the selected occurrence rather than all equal media IDs.
5. Review `animateItem` specs and drag lift feedback. Use a short ease-in-out positional transition only for structural edits; do not animate unrelated content or re-run animations on every progress update.
6. Change reduced-motion queue notice behavior to retain immediate opacity/state feedback without vertical movement. Keep the existing polite live-region announcement.
7. Add tests for duplicate media IDs, reorder stability, insertion/removal, drag cancel, rapid successive edits and queue refresh reconciliation.

## Boundaries

- Do not change playback order semantics, shuffle/repeat, queue persistence, database schema or migrations.
- Do not identify a queue row solely by title, artist, media ID or mutable index.
- Do not animate the entire queue for routine scroll or focus changes.

## Verification

- **Mechanical**: `./gradlew :app:compileFossDebugKotlin --console=plain`; `./gradlew :app:testFossDebugUnitTest --console=plain`; `./gradlew :app:assembleFossDebug --console=plain`.
- **Feel check**: create a queue containing the same song at least three times, reorder the middle occurrence, and verify only that occurrence moves.
- Insert and remove tracks above/below the current track; current playback must not jump or change identity.
- Cancel a drag halfway; original order and row identities must restore.
- Toggle Reduce Motion; list positions should update without travel while the edited row/action remains identifiable.
- **Done when**: duplicate occurrence tests pass and device verification confirms no row identity swap, flicker or accidental playback change.

