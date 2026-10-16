# Plan 031 — Named queue slots with resume

**Status: not started.** Verified at commit `4a3bee56` — no named queue slots, no sidecar-persisted
queue storage, no slot selector in the mini-player. `PersistQueue` exists for the live queue but has no
concept of multiple named slots.

## Objective

2–3 user-named queue slots that persist across sessions. Each slot holds a queue (list of songs + order).
The user can swap the live queue with any slot (with resume position), view slots in the mini-player, and
exclude slots from Listen Together guest control. Highest-value, hardest item in Phase 3.

## Why

- Matrix #10 + Innovation #5: no YTM client has named queue slots with resume. Musicolet has named queues
  but as local-file constructs; Audify can own this for streaming clients.
- Real user pain: "I had a queue going and I lost it when I started something else." Slots solve that.
- Playback-target architecture (plan 022, shipped) already consolidated transport verbs — slots are the
  next layer: multiple persistent queues behind one transport surface.

## What exists today

- `playback/PlaybackTarget.kt` + `PlaybackIntent.kt` — transport verbs consolidated (shipped in plan 022).
- `playback/PersistQueue.kt` — persists the *live* queue to DB, restores on service start.
- `playback/queues/` — queue types (`ListQueue`, `LocalAlbumRadio`, etc.).
- Mini-player shows the current queue title and a peek of upcoming songs (Up Next peek, shipped context).
- No slot concept, no slot storage, no slot selector UI.

## Scope

**In scope:**
- 2–3 named slots, user-editable name, persisted in a sidecar table (queue slots table: slotId, name,
  songIds in order, createdAt, updatedAt). 2–3 is a deliberate cap — enough to be useful, not a full
  playlist manager.
- Swap live queue with a slot: "Load slot" replaces the live queue with the slot's songs (and remembers
  the previous live queue so "Resume" can go back). "Save to slot" persisting the current live queue into
  a slot (overwrite for v1 clarity).
- Mini-player shows the current slot name (if a slot is loaded) and a quick slot picker (tap to switch).
- Listen Together guests excluded — guests can't see or load slots (guest mode already gates some controls).
- Slot persistence across app restarts — stored in DB, restored on service start like the live queue today.

**Out of scope:**
- Unlimited slots / full playlist manager — 2–3 is the cap for v1.
- Slot sharing / export — defer.
- Auto-switching slots by context (e.g. "morning slot" auto-loads in the morning) — that's plan 029's
  territory; don't conflate.
- Slot analytics / play counts per slot — defer.

## Affected files

| File | Change |
|------|--------|
| `db/entities/QueueSlotEntity.kt` (new) | Entity: slotId, name, songIds (JSON array of song IDs in order), createdAt, updatedAt |
| `db/MusicDatabase.kt` | Add `queueSlots` DAO: insert, update, delete, get all, get by id; get songs for a slot (expand songIds → Song entities) |
| `playback/QueueSlotsManager.kt` (new) | Core logic: load slots, save slot, load slot into live queue (with resume memory), save live queue to slot, current slot tracking |
| `playback/PersistQueue.kt` | Small extension: persist the "previous live queue" so Resume can go back after a slot swap |
| `viewmodels/QueueViewModel.kt` (or new slots VM) | Expose slots list, current slot, load/save actions; bridge to `QueueSlotsManager` |
| `ui/player/MiniPlayer.kt` | Show current slot name (if loaded); tap slot name → slot picker sheet |
| `ui/component/QueueSlotPicker.kt` (new) | Sheet/composable: list of slots, rename, load, save-current-to-slot, delete slot |
| `ui/menu/PlayerMenu.kt` (or queue menu) | Add "Slots" entry that opens the slot picker |
| `sachit_strings.xml` | Slot names, "Load slot", "Save to slot", "Rename slot", "Delete slot", "No slots", "Current queue", "Resume previous", Listen Together guest exclusion label |
| `playback/PlaybackTarget.kt` / `PlaybackIntent.kt` | May need a new intent for "load slot" / "save to slot" if the transport surface doesn't cover it — check at implementation time |

## Technical approach

1. **Slot entity + DAO** — A `queue_slots` table with `slotId` (auto), `name`, `songIds` (JSON array of
   song IDs in order — simpler than a join table for a 2–3 slot cap), timestamps. The executor chooses
   JSON vs a join table at implementation time; JSON is simpler for this scale.
2. **QueueSlotsManager** — The core logic: load all slots, get songs for a slot (expand IDs → Songs via
   DB), load a slot into the live queue (replace live queue, remember previous live queue for resume),
   save current live queue to a slot (overwrite), delete slot, rename slot. This is the testable core —
   keep it as pure as possible; DB calls are the impure boundary.
3. **Resume memory** — When a slot is loaded, the current live queue (with its play position) is remembered
   as the "previous" queue. "Resume" goes back to it. Per-session memory (not persisted) — when the app
   restarts, there's no "previous" to resume to; the last-loaded slot is just the live queue. Keep it simple.
4. **Mini-player slot indicator** — If a slot is loaded, the mini-player shows the slot name (instead of or
   alongside the queue title). Tap → slot picker sheet.
5. **Slot picker** — A sheet with the list of slots. Each row: name, song count. Actions: Load, Rename,
   Save current to slot, Delete. "Save current to slot" overwrites the slot with the current live queue.
   "Load" swaps in the slot's queue and remembers the previous.
6. **Listen Together exclusion** — Guests can't load slots, save to slots, or rename/delete slots. The slot
   picker is gated behind the existing guest check. The live queue still works for guests (it already does).
7. **Persistence across restarts** — Slots are in DB. On service start, the live queue is restored as today
   (from `PersistQueue`). Slots don't auto-load on restart — the user loads a slot explicitly.

## Testing

- Unit test for `QueueSlotsManager`: create slots, save to slot, load slot, verify live queue swapped and
  previous remembered, verify resume goes back, verify delete clears the slot.
- Unit test for slot song expansion: slot with N song IDs → correct list of Song entities in order.
- Unit test for rename and delete.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: create 2 slots, load one, verify queue swaps and mini-player shows slot name, save current to
  another slot, restart app, verify slots persist, verify guest exclusion in Listen Together.
- Regression: existing queue behavior (live queue, persist, restore) still works; Listen Together still
  works for non-slot actions.

## Performance notes

- Slots are tiny (2–3 rows, each with a JSON array of song IDs). No performance concern.
- Slot load is a DB read + a live queue replace — same cost as loading a playlist today.

## Rollback

- Remove the slot picker UI, the `QueueSlotsManager`, the slot DAO, and the slot entity → no slots, live
  queue unchanged.
- The "previous queue" memory extension to `PersistQueue` is separable — remove it if needed.

## Anti-BitChord check

- Named queue slots are a genuine differentiator — no YTM client has this. Don't downgrade to "saved
  queues" that are just playlists. Slots are ephemeral-queue-persistence with resume, not playlist
  management.
- 2–3 is the cap. Don't let this become a playlist manager — that's a different (larger) feature.
- Use Audify's existing queue UI language — not a copied slot UI from another app.

## Rejected alternatives

- Full playlist manager with unlimited playlists — too large, different feature. Slots are a focused 2–3
  queue-swapping feature.
- Auto-loading slots by time/context — that's plan 029's shelf territory; don't scope-creep slots into that.
- Slot sharing — defer; not core to the v1 slot experience.
