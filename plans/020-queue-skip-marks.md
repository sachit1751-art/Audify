# Plan 020 — Queue skip marks + "Earlier" boundary

Written against commit **`605975d5`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`605975d5`, check drift in the in-scope files first.

## Goal

Answer "what did I skip?" in the queue sheet. When the listener taps a row further ahead in
the queue, the jumped-over rows were queued but never heard — mark them (dimmed + glyph) and
draw an **"Earlier" divider** at the active row so the sheet reads as a timeline (earlier /
now / up next) instead of a flat list. Session-scoped and additive; no history list is built,
no persistence, no index math changes in the queue UI.

## Existing infrastructure (recon at `605975d5`)

- **The timeline already retains past items.** `extensions/PlayerExt.kt:36`
  (`getQueueWindows`) walks `getPreviousWindowIndex` from the current item and returns
  everything still in the timeline, earlier items included; `Queue.kt:658` renders them. There
  is nothing to "keep" — the gap is purely marks + boundary.
- Jump detection funnel: `MusicService.onMediaItemTransition` (line 2508). It already tracks
  `previousMediaItemIndex` (line 2466), already special-cases AUTO reason (repeat-one rewind
  at 2548-2553), and updates `previousMediaItemIndex` at line 2555. A SEEK-reason transition
  with `current - previous > 1` is exactly a forward queue jump.
- New-queue boundary: `MusicService.playQueue` (line 1740) — clear marks here when a new queue
  starts (`restoringQueue = false`).
- Queue UI: `ui/player/Queue.kt` — windows list at 658, active-row detection via
  `currentPlayingUid` (673), row content composable ~840 (`MediaMetadataListItem`), row click
  seeks via `seekToDefaultPosition(window.firstPeriodIndex)` (~910). Drag/dismiss/automix all
  index off `queueWindows` — **do not add or remove list items**; render the divider *inside
  the active row's content* (a Column wrapping the existing row + a zero-contribution divider
  above it) so all index math is untouched.
- Crossfade complication: `performCrossfadeSwap` (line ~4812) swaps players and re-fires
  `onMediaItemTransition(player.currentMediaItem, AUTO)` manually — AUTO reason is excluded
  from jump detection, so crossfades can't produce false skips. Verify during drift check.
- Guest mode: queue interactions are already gated by `isListenTogetherGuest` in `Queue.kt`;
  the service-side marks are harmless for guests (host's marks mirror the shared queue), but
  the UI may choose to hide glyphs for guests — decide in step 4, default: show them (the
  skipped rows are equally skipped for the room).

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/playback/QueueHistoryState.kt` | **New.** Pure skip-mark state (bounded FIFO of mediaIds) |
| `app/src/main/kotlin/com/sachit/music/playback/MusicService.kt` | State instance + jump detection in `onMediaItemTransition` + clear in `playQueue` |
| `app/src/main/kotlin/com/sachit/music/playback/PlayerConnection.kt` | Expose `skippedInQueueIds` StateFlow (delegate to service flow, same pattern as `currentStreamClient`) |
| `app/src/main/kotlin/com/sachit/music/ui/player/Queue.kt` | "Earlier" divider inside active row + dim/glyph on skipped rows |
| `app/src/main/res/values/sachit_strings.xml` | One string: `queue_earlier` |
| `app/src/test/java/com/sachit/music/QueueHistoryStateTest.kt` | **New.** Pure state tests |

Out of scope: mini player, queue persistence (`PersistQueue`), history screens, any DB entity,
`values-*` files.

## Design

**`QueueHistoryState`** (pure, no Android imports):

```kotlin
class QueueHistoryState(private val maxEntries: Int = 100) {
    fun markSkipped(mediaIds: Collection<String>)   // dedupe, FIFO-evict past maxEntries
    fun skippedIdsSnapshot(): Set<String>
    fun clear()
}
```

100-entry cap: only the most recent jumps can still be visible rows; beyond that the marks
would mislead anyway (rows scrolled far off). Service holds one instance + a
`MutableStateFlow<Set<String>>` updated only when the set actually changes.

**Jump detection** (in `onMediaItemTransition`, before `previousMediaItemIndex` is updated):

```kotlin
if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
    previousMediaItemIndex != C.INDEX_UNSET
) {
    val gap = player.currentMediaItemIndex - previousMediaItemIndex
    if (gap > 1) {
        val end = player.currentMediaItemIndex.coerceAtMost(player.mediaItemCount)
        val jumpedOver = ((previousMediaItemIndex + 1) until end).mapNotNull {
            player.getMediaItemAt(it).metadata?.id
        }
        if (jumpedOver.isNotEmpty()) { state.markSkipped(jumpedOver); flow.value = snapshot }
    }
}
```

Excluded on purpose: AUTO (natural advance = gap 1; repeat-one rewinds; crossfade swap
re-fires as AUTO), SEEK backward (gap < 0 — going back is not skipping), SEEK gap == 1
(adjacent tap, nothing skipped), PLAYLIST_CHANGED reasons (queue replacement ≠ listening
choice).

**Queue UI** — inside the active row's content composable, wrap the existing
`MediaMetadataListItem` in a `Column` whose first child is the divider (`labelMedium` "Earlier"
in primary color + `HorizontalDivider`), zero height contribution elsewhere; skipped rows get
`.alpha(0.5f)` on their existing modifier chain + a small skip glyph in the row trailing area
only if it doesn't collide with the drag handle (if it collides: alpha alone is enough for v1).

## Implementation steps

1. **Drift check**: read `onMediaItemTransition` fully (repeat-one interplay), the crossfade
   swap path, `Queue.kt` row content + `ReorderableItem` structure; confirm
   `getMediaItemAt(idx).metadata` is non-null for all queue sources (search for
   `.metadata!!` usages in Queue.kt — it is assumed non-null in UI already).
2. **`QueueHistoryState.kt`** + tests (mark/dedupe/evict/clear; boundary: exact maxEntries).
3. **Service wiring**: instance + flow + detection block (exact position: immediately before
   `previousMediaItemIndex = player.currentMediaItemIndex`) + clear in `playQueue` when
   `!restoringQueue`.
4. **UI**: divider-in-active-row + skipped dimming; check Listen Together guest rendering
   (default: show marks).
5. **String**: `queue_earlier` = "Earlier".
6. **Build + manual**: `./gradlew :app:assembleFossDebug --console=plain`; queue 5+ songs,
   tap the last → verify intermediate rows dim + Earlier divider sits at the new active row;
   skip-forward one-by-one → no marks (gap 1); start a new queue → marks cleared; drag a row →
   no crash, marks persist; swipe-dismiss a skipped row → undo works; crossfade on → advance
   naturally → no false marks.

## Testing

- **Unit** (`QueueHistoryStateTest`): dedupe, FIFO eviction order, clear, empty input no-op,
  cap boundary (exactly maxEntries → no eviction; +1 → oldest evicted).
- **Manual**: matrix above; plus Play Together guest session sanity (no crash when host jumps),
  landscape, font-scale 2×, dark/light.

## Performance notes

Detection is O(gap) only on actual jumps; state is a ≤100-entry `ArrayDeque<String>`;
UI change is one alpha modifier + one conditional divider inside an already-composed row. No
new recomposition triggers beyond the (rare) skipped-set emission.

## Rollback

Single revert: new file + one detection block + UI wrapper. `playQueue` clear line and the
PlayerConnection delegation are trivially removable.

## Anti-clone / visual identity

BitChord inlines history rows with its own styling and builds a real 25-entry history buffer;
Audify's take is marks-on-the-live-queue with an "Earlier" boundary inside the active row —
different structure, no extra rows, Audify naming and styling.

## Considered and rejected for this plan

- **BitChord-style 25-entry history buffer + tap-to-seek-back** — valuable but bigger: needs
  seek-back removal semantics, reorder interplay and (to survive process death) persistence.
  Split into a future plan if skip marks prove out.
- **Persisting marks in `PersistQueue`** — touches the restore path for marginal value;
  session-only is honest about what the marks mean.
- **Skip-reason taxonomy (auto-skipped vs error-skipped)** — depends on plan 021's failure
  memory; revisit after it lands.
