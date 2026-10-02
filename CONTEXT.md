# Sachit — Domain Glossary

The vocabulary of this codebase. Terms are defined by what they *mean* in the product, not by where they are implemented. For architectural decisions see `docs/adr/`.

If a term here conflicts with how you were describing something, that conflict is worth resolving out loud — update this file when it resolves.

---

## Playback

### Queue
The ordered list of tracks the user will hear next, together with its **provenance** — which playlist, album, or search result it came from. Provenance matters: it is what lets the app answer "why am I hearing this?" and what lets a queue be re-derived without losing context. A queue is not just a list of ids.

### Radio
An unbounded Queue grown by related-track endpoints from a single seed track. Radio has no end, so "the end of the queue" is not a meaningful concept for it — playback stops only when the user intervenes or nothing related can be found.

### Automix
A secondary Queue of related tracks held alongside the main one and interleaved into it. Distinct from Radio in that it supplements a Queue the user chose, rather than replacing their choice with an endless one.

### Track history
The record of which tracks the Queue has already moved past, and which it deliberately skipped over. Needed because "next" is not always "forward" — repeat, seek, and reordering all make it possible to land on a track the user passed over.

### Playback intent
A user's expressed wish about what playback should do — play this, queue that, skip, replace everything. Distinct from the *mechanism* that carries it out, which may be the local player, a cast speaker, or a synced guest. The distinction is load-bearing: see [ADR-0001](docs/adr/0001-alias-resolution-seam.md) and the transport vocabulary below.

### Transport verb
One of the small set of things a user can ask playback to do: play, pause, seek, skip forward, skip back. Every surface that offers playback controls must offer the same verbs with the same meaning, regardless of where the audio is actually coming from.

### Crossfade
Overlap between the tail of one track and the head of the next, so the transition is heard rather than noticed. Applies only where it makes musical sense — it is suppressed when consecutive tracks belong to the same album, because there the listener expects a gap, not an overlap.

### Stream health
A track's remembered history of *where its audio has been failing to load*. Distinguishes "this source is fine" from "this source is broken, go and get a different one". Health is per-track and time-limited, because a source that failed an hour ago is not evidence about now.

### Audio quality ceiling
The highest quality permitted on the current network. Not a user preference in isolation — the effective quality is the user's wish, lowered by network conditions when per-network limits are enabled.

---

## Library

### Library
The device's local mirror of the user's YouTube library: liked songs, subscriptions, saved playlists and albums, saved episodes. The Library is a *mirror*, not the source of truth. YouTube is authoritative; the Library is a cache that happens to be edited offline.

### Sync
Reconciliation between the Library and YouTube, carrying local edits back to the service and pulling remote changes in. The hard part is not the transfer but the **coalescing**: many rapid edits to the same playlist must not become many redundant round-trips.

### Library sort
The ordering a Library listing presents: by name, by artist, by date added, by play time. Name and artist orderings are *locale-sensitive* — they must follow the reader's language conventions, not byte order.

### Play event
A record that a track was listened to, for a period. The unit that all listening statistics are derived from — total play time, top artists, monthly recaps. Deliberately distinct from a play *count*: a track played for 5 seconds and one played to the end are not the same event.

### Listening stats
Anything computed from Play events: totals, top artists and albums, per-month recaps, "forgotten" favourites. Always derived, never stored — the derivation is what makes historical accuracy cheap.

### Recap
A periodic summary of listening over a window (a month, a year). Derived entirely from Play events and Library state; carries no state of its own beyond whether the user has seen it.

---

## Identity

### Artist alias
A user-chosen display name for an artist, overriding the name YouTube supplies. Aliases are keyed by identity (channel id), not by name, so an artist who renames themselves keeps their alias.

Aliases **chain**: one alias may point at a name that is itself aliased. Resolving follows the chain to its end, and must terminate even if the data contains a cycle.

---

## Listening together

### Room
A shared listening session. One participant is the **host**; the others are **guests**. The host's playback drives everyone.

### Host
The participant whose playback is authoritative. Exactly one per Room.

### Guest
A participant whose player is *driven* rather than authoritative. A guest's local controls do not move the Room; they are blocked, not merely overridden. A guest joining mid-track is reconciled to the host's position rather than started fresh.

### Drift
The accumulating difference between a guest's playback position and the host's. Small drift is corrected silently; sustained drift means the guest has missed a track change and must be reconciled.

### Track reconciliation
Bringing a guest back onto the host's current track at the host's position. Distinct from *seeking*: reconciliation also fixes the queue itself, not only the playhead.

### Protocol
The wire format rooms speak. Deliberately separate from the logic that *acts* on protocol messages, so the format can be verified independently of any playback behaviour.

---

## Lyrics

### Provider
A source of lyrics, interchangeable with every other provider. Providers are ordered by user preference and tried in turn.

### Lyrics format
The representation a Provider returns (timed line, plain text, translated pairs, …). Formats are normalized before display, so adding a Provider cannot change how lyrics are rendered.

---

## App shell

### Composition scope
The set of things available to a screen without being passed to it. Grows as features are added, which makes it a useful signal: a term that appears in scope for many screens has become part of the everyday vocabulary of the UI.

---

## Vocabulary deliberately *not* used

- **Component** — a UI element, but the word hides which kind. Name the thing (screen, row, sheet, item).
- **Service** — in Android this means two unrelated things: a bound playback process and a stateless helper. Name which.
- **Handler** — ambiguous between "receives events" and "contains logic". Name the behaviour.
- **Manager** — says nothing about what is managed. Prefer the domain noun (`Sync`, `StreamResolution`).
- **Util / Utils / Helper** — a file name that admits it has no domain meaning. Prefer naming the operation.