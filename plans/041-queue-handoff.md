# Plan 041 — Queue handoff

**Status: not started.** Verified at commit `61778ab4` — no queue handoff exists. Requires named queue 
slots (plan 031) as a dependency — slots make same-device handoff automatic.

## Objective

When the audio output device changes (e.g. phone disconnected from Bluetooth → speaker, or headphones 
unplugged), snapshot the current queue + playback position and offer to resume on the new output. With slots 
(plan 031), the snapshot auto-loads into a "current session" slot so the user can resume exactly where they 
were, on the new output, with one tap.

## Why

- Innovation #10: audio-device-change snapshot + resume offer; slots (plan 031) make same-device automatic.
- A queue handoff is a quality-of-life feature for Bluetooth/headphone users whose connection drops mid-listen 
— without it, the queue is gone when the output changes. With it, the user resumes in one tap.
- It's a small, focused feature: snapshot on output change, offer resume. The slot integration makes it 
smooth.

## What exists today

- Audio focus / output change detection may exist at the Android level (the executor checks at implementation 
time what hooks are available — `AudioManager`, `BluetoothAdapter`, audio routing callbacks).
- The live queue + playback position exist and are persisted (plan 022 playback-target work, `PersistQueue`).
- Named queue slots (plan 031) are the dependency — without slots, "resume" would need a separate temp 
storage; with slots, the snapshot auto-loads into a slot.

## Scope

**In scope:**
- Detect audio output changes (Bluetooth connect/disconnect, headphone unplug/replug, speaker switch) at the 
Android level.
- On output change: snapshot the current queue (song list + order) + playback position (current song + 
position-in-song) into a "handoff" snapshot.
- If slots (plan 031) are implemented: auto-load the snapshot into a designated "current session" slot (or 
the most recent slot) so the user can resume with one tap from the mini-player / slot picker.
- Offer a resume action: in the mini-player or a toast/snackbar, "Output changed — resume playback?" with a 
tap to resume (from the snapshot / slot). If the user dismisses, the snapshot is kept for a while (so they 
can resume later) but not auto-played.
- Guest exclusion: in Listen Together guest mode, don't offer handoff / don't snapshot (guests don't control 
the queue).

**Out of scope:**
- Cross-device handoff (phone → tablet, or phone → PC) — defer; v1 is same-device output change.
- Automatic resume without user confirmation — defer; v1 offers a tap-to-resume, not silent auto-resume.
- Snapshot persistence beyond the session — the snapshot is kept for a while (minutes/hours) so the user can 
resume later, but it's not a permanent slot. With slots, the snapshot can live in a slot; without slots, a 
transient snapshot with a TTL.
- Bluetooth-specific beyond output change — treat all output changes the same (Bluetooth, headphone, speaker).

## Affected files

| File | Change |
|------|--------|
| `playback/AudioOutputMonitor.kt` (new) | Detect audio output changes: Bluetooth connect/disconnect, headphone unplug/replug, speaker switch. Android-level hooks: `AudioManager`, `BluetoothAdapter`, audio routing callbacks. The executor picks the exact hooks at implementation time. |
| `playback/QueueHandoffManager.kt` (new) | On output change: snapshot queue + position; store snapshot (in a slot if slots exist, else transient with TTL); offer resume; handle resume tap; handle dismiss (keep snapshot for a while). |
| `playback/PersistQueue.kt` (or the queue persistence) | May need to extend to snapshot + resume position; the executor checks at implementation time whether the existing persistence covers the snapshot or needs an extension. |
| `viewmodels/PlayerViewModel.kt` (or the player VM) | Expose handoff state: snapshot available, resume action, dismiss action. Bridge to `QueueHandoffManager`. |
| `ui/player/MiniPlayer.kt` (or the mini-player) | Show resume offer if a snapshot is available (e.g. "Output changed — tap to resume" chip / snackbar). |
| `sachit_strings.xml` | "Output changed", "Resume playback", "Cancel", "Resume from…", handoff labels. |

## Technical approach

1. **Output change detection** — At the Android level, listen for audio routing changes. The executor picks 
the hooks at implementation time: `AudioManager.ACTION_AUDIO_BECOMING_NOISY` (headphone unplug), 
`BluetoothAdapter` state changes (Bluetooth connect/disconnect), audio route changes via 
`AudioManager` listeners. The key is: on a significant output change (headphones out, Bluetooth 
disconnect, switch to speaker), trigger the handoff.
2. **Snapshot** — On output change, snapshot: (a) the current queue (song list + order), (b) the current 
song + position-in-song. Store it. If slots exist (plan 031), store it in a designated "current session" 
slot (or the most recent slot) so resume is one tap. If slots don't exist yet, store a transient snapshot 
with a TTL (e.g. 30 minutes) and a resume action.
3. **Resume offer** — Show a resume offer in the mini-player or as a snackbar/toast: "Output changed — tap to 
resume". Tap → resume from the snapshot (load the queue + seek to the saved position). Dismiss → keep the 
snapshot for a while (so the user can resume later) but don't auto-play.
4. **Slot integration (with plan 031)** — If slots exist, the snapshot auto-loads into a slot (e.g. a 
"current session" slot that the handoff manager manages). The resume offer becomes: "Resume from [slot name]" 
→ loads the slot. This is the smooth path: slots make handoff automatic and resumable from the slot picker.
5. **Guest exclusion** — In Listen Together guest mode, skip the handoff: don't snapshot, don't offer resume. 
Guests don't control the queue; handoff is a host feature.
6. **TTL / cleanup** — The snapshot (transient or in a slot) has a TTL: after a while (e.g. 1 hour), it's 
discarded. The user can resume within the window; after that, the snapshot is gone. With slots, the slot 
persists (it's a slot) but the handoff "resume" badge ages out.

## Testing

- Unit test for `QueueHandoffManager`: snapshot queue + position, resume from snapshot, verify queue + 
position restored; dismiss, verify snapshot kept for TTL; expiry, verify snapshot discarded.
- Unit test for slot integration (if slots exist): snapshot → slot auto-loaded → resume from slot → verify 
queue + position restored.
- Unit test for guest exclusion: in guest mode, output change → no snapshot, no offer.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: on a device with Bluetooth, play a queue, disconnect Bluetooth, verify resume offer appears, tap 
resume, verify queue + position restored. Test dismiss (resume later from slot/offer). Test guest mode 
(guest → no offer). Test output change back to original (no duplicate offer).
- Regression: existing queue behavior, playback position, persist/restore unchanged for non-handoff cases.

## Performance notes

- Snapshot is a small struct (queue + position) — cheap. Storage is a slot or a transient in-memory/blob — 
cheap.
- Output change detection is Android-level callbacks — cheap, battery-friendly (it's event-driven, not 
polling).

## Rollback

- Remove `AudioOutputMonitor`, `QueueHandoffManager`, the resume offer UI, and the snapshot logic → no 
handoff, no resume offer, queue behavior unchanged. If slots exist, the handoff slot logic is removed but 
slots still work for their own purpose.

## Anti-BitChord check

- A queue handoff is a natural feature for any music player with Bluetooth — not a copy of a specific 
competitor. Implement it as Audify: slot-integrated (with plan 031), tap-to-resume, guest-excluded. Don't 
copy a competitor's handoff UX — use Audify's mini-player / slot picker language.
- Tap-to-resume (not auto-resume) is the right default for v1 — respects the user's control. Don't auto-
resume without confirmation for v1.
