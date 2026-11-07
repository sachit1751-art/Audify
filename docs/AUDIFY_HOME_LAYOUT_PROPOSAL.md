# AUDIFY Home — Current, BitChord Reference, and Proposed Redesign

**Status:** Design specification / visual proposal
**Scope:** Home screen only
**Implementation status:** Not implemented
**Primary reference:** BitChord Home screen structure
**Product constraint:** Preserve AUDIFY branding, design system, navigation, playback, and existing data sources.

---

## 1. AUDIFY today — source-backed current layout

The current AUDIFY Home is a vertically scrolling, data-driven feed. It is not a fixed collection of named sections: the music backend supplies `HomeShelf` items, and the screen chooses the presentation for each shelf.

### Visual map

```text
┌─────────────────────────────────────────┐
│ AUDIFY             Search  Bluetooth  ◉ │
│                                         │
│ Good evening                           │
│ [Optional listener name]                │
├─────────────────────────────────────────┤
│ [Optional sign-in banner]               │
├─────────────────────────────────────────┤
│ [Optional time-aware discovery shelf]   │
├─────────────────────────────────────────┤
│                                         │
│ [Loading / error / success feed]        │
│                                         │
│ If Recents is loading:                  │
│   Recently Played skeleton              │
│   Regular shelf skeletons               │
│                                         │
│ If feed has loaded:                     │
│   Recents (when supplied first)         │
│     ├─ List/grid presentation           │
│     └─ View toggle                      │
│                                         │
│   First non-Recents shelf               │
│     └─ Hero cards on phone              │
│                                         │
│   Remaining shelves                     │
│     └─ Standard horizontal card rows    │
│                                         │
│   Quick Picks                           │
│     ├─ Find Your Mood                   │
│     └─ Listen Together                  │
│                                         │
│   [Load more when more feed is available]│
└─────────────────────────────────────────┘
```

### What the current implementation does

- **Header:** AUDIFY mark, time-based greeting, optional listener name, Search, Bluetooth/audio-output, and profile action.
- **Optional content:** sign-in banner and time-aware discovery shelf can appear before the main feed.
- **Recents:** when the first supplied shelf is `Recents` / chronological history, it receives a dedicated presentation and a list/grid toggle. While its data is loading, its skeleton is shown first.
- **Hero and standard shelves:** the first eligible non-Recents shelf can use full-bleed hero cards on phone; other shelves use standard horizontal rows.
- **Quick Picks:** Find Your Mood and Listen Together are inserted after Quick Picks; if Quick Picks is absent, those sections can appear at the end.
- **Feed behaviour:** pull-to-refresh, loading skeletons, retry state, long-press item menus, current-track indication, and pagination.

**Important:** the precise shelf names and order depend on the data returned by the Home feed. The diagram shows rendering rules, not a guaranteed playlist order.

**Source:** [AUDIFY HomeScreen.kt](https://github.com/sachit1751-art/Audify/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/HomeScreen.kt)

---

## 2. BitChord — reference layout

BitChord uses a similar scrolling, data-driven feed, but adds a dedicated **Top Picks for You** section above the feed and inserts **Find Your Mood** after the first feed shelf.

### Visual map

```text
┌─────────────────────────────────────────┐
│ BitChord wordmark                       │
├─────────────────────────────────────────┤
│ [Optional sign-in banner]               │
├─────────────────────────────────────────┤
│ Top Picks for You                       │
│ [Pick 1]       [Pick 2]       [Pick 3]   │
│                                         │
│ (or Top Picks loading skeleton)         │
├─────────────────────────────────────────┤
│ [Loading / error / success feed]        │
│                                         │
│ Recently Played / Recents               │
│ ┌──────────────┐ ┌──────────────┐        │
│ │ art  Track 1 │ │ art  Track 5 │        │
│ │ art  Track 2 │ │ art  Track 6 │        │
│ │ art  Track 3 │ │ art  Track 7 │        │
│ │ art  Track 4 │ │ art  Track 8 │        │
│ └──────────────┘ └──────────────┘        │
│                                         │
│ [First eligible card shelf / Hero]      │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │          Large artwork              │ │
│ │                                     │ │
│ │       Title and subtitle             │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Find Your Mood                          │
│ [Mood] [Mood] [Mood]                    │
│ [Mood] [Mood] [Mood]                    │
│                                         │
│ [Remaining dynamic shelves]             │
└─────────────────────────────────────────┘
```

### What makes the reference distinctive

- **Top Picks for You** is a separate, actionable recommendation shelf above the normal feed. It appears only when the screen receives the relevant callback and pick data; it can show a loading skeleton while picks are being prepared.
- **Recents / list shelves** use compact track rows with small artwork. Rows are arranged in horizontally paged columns of four.
- **Hero treatment** gives the first eligible card shelf large, full-bleed artwork with text overlaid near the bottom.
- **Find Your Mood** is inserted after the first feed shelf when the mood action is available. It is not necessarily a fixed section at the top.
- **Remaining shelves** retain their data-provided layout. List shelves remain compact lists; card shelves use standard cards.
- Pull-to-refresh, loading/error states, long-press menus, current-track state, and feed pagination are supported.

**Source:** [BitChord HomeScreen.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt)

---

## 3. Proposed AUDIFY after redesign

### Design direction

Adopt BitChord's **content hierarchy and visual rhythm**, not its identity or implementation wholesale.

AUDIFY should retain its existing header and product controls. The feed should become easier to scan: compact listening history, one prominent lead recommendation, mood discovery close to the top, then the remaining personalized shelves.

### Proposed visual map

```text
┌─────────────────────────────────────────┐
│ AUDIFY             Search  Bluetooth  ◉ │
│                                         │
│ Good evening                           │
│ [Optional listener name]                │
├─────────────────────────────────────────┤
│ [Optional sign-in banner]               │
├─────────────────────────────────────────┤
│ [Optional time-aware / personalized     │
│  lead shelf, if supplied by the feed]   │
├─────────────────────────────────────────┤
│ Recently Played                         │
│ ┌──────────────┐ ┌──────────────┐        │
│ │ art  Track 1 │ │ art  Track 5 │        │
│ │ art  Track 2 │ │ art  Track 6 │        │
│ │ art  Track 3 │ │ art  Track 7 │        │
│ │ art  Track 4 │ │ art  Track 8 │        │
│ └──────────────┘ └──────────────┘        │
│ [Keep AUDIFY's list/grid toggle]         │
│                                         │
│ Featured / lead recommendation          │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │          Large artwork              │ │
│ │                                     │ │
│ │       Title and subtitle             │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Find Your Mood                          │
│ [Energy] [Relax] [Focus]                │
│ [Feel Good] [Feeling Blue] [Love]       │
│                                         │
│ Listen Together                         │
│ [Existing AUDIFY Listen Together UI]    │
│                                         │
│ More music for you                      │
│ [Dynamic horizontal shelf]              │
│ [Dynamic horizontal shelf]              │
│ [Dynamic horizontal shelf]              │
└─────────────────────────────────────────┘
```

### Proposed section order and rules

| Order | Section | Proposed treatment |
|---:|---|---|
| 1 | Existing AUDIFY header | Keep greeting, logo, search, Bluetooth/audio output, and profile. |
| 2 | Sign-in / time-aware content | Preserve existing conditional content and its actions. |
| 3 | Recently Played | Prefer BitChord's compact, paged track-row treatment. Preserve AUDIFY's list/grid toggle. |
| 4 | Lead recommendation | Use a large artwork-led hero for the first eligible card shelf. |
| 5 | Find Your Mood | Bring mood discovery into the early feed, following the lead shelf when the mood action is available. |
| 6 | Listen Together | Keep the existing AUDIFY entry point and behaviour. |
| 7 | Remaining shelves | Continue rendering backend-provided shelves using their appropriate list/card layout. |

### Visual and interaction rules

1. **Keep AUDIFY recognisable.** Retain AUDIFY's colors, typography, icon style, logo, surfaces, corner system, and existing navigation.
2. **Use artwork as the visual anchor.** Hero artwork should be large and edge-to-edge within the established page gutters, with readable title/subtitle contrast.
3. **Keep listening history compact.** Track rows should prioritise artwork, title, artist, and current playback state. Do not turn every recent track into a large card.
4. **Keep content data-driven.** Do not hardcode fake Top Picks, playlists, albums, or recommendations. Only show sections when real AUDIFY data and callbacks support them.
5. **Preserve working behaviour.** Search, profile, audio output, playback, item taps, long-press menus, refresh, loading, errors/retry, pagination, and bottom navigation must continue to work.

### Important implementation distinction

BitChord has a dedicated `TopPicks` model and callbacks. The inspected AUDIFY Home screen does not expose that same Top Picks contract. Therefore, **do not add a visually identical Top Picks shelf with placeholder content**. If AUDIFY already has a suitable real personalized/time-aware shelf, it can occupy the lead recommendation position. A dedicated Top Picks feature would require a separate product/data decision.

The proposed layout is a structural redesign, not permission to invent backend capabilities or replace existing AUDIFY behaviour.

---

## 4. Comparison at a glance

| Area | AUDIFY today | BitChord reference | Proposed AUDIFY |
|---|---|---|---|
| Brand header | AUDIFY greeting + actions | BitChord wordmark | Keep AUDIFY header |
| Dedicated Top Picks | No matching dedicated contract in inspected HomeScreen | Yes, conditional | Use only real AUDIFY recommendation data |
| Recents | List/grid toggle; dedicated shelf | Compact four-track columns | Compact BitChord-style rows + retain toggle |
| Lead shelf | First eligible shelf can be hero | First eligible card shelf can be hero | Retain hero, refine hierarchy |
| Mood discovery | After Quick Picks, or at end | After first feed shelf | Move into early feed after lead shelf |
| Listen Together | Existing AUDIFY section/action | Not part of inspected Home feed contract | Preserve AUDIFY feature |
| Other shelves | Dynamic | Dynamic | Dynamic |
| Refresh/loading/errors/pagination | Supported | Supported | Preserve |

---

## 5. Scope and acceptance checklist

### In scope

- [ ] Reorder and refine the AUDIFY Home feed.
- [ ] Apply compact recent-track rows inspired by BitChord.
- [ ] Improve the first eligible recommendation shelf's hero presentation.
- [ ] Position mood discovery earlier without inventing content.
- [ ] Preserve AUDIFY's Listen Together feature.
- [ ] Preserve responsive phone/desktop behaviour.

### Must not regress

- [ ] Search, profile, and Bluetooth/audio-output actions.
- [ ] Track and collection click behaviour.
- [ ] Long-press menus and current-track indicators.
- [ ] Recents list/grid preference.
- [ ] Pull-to-refresh and loading skeletons.
- [ ] Error/retry states and pagination.
- [ ] Existing bottom navigation and mini-player behaviour.

### Out of scope

- Explore, Library, Settings, and other screens.
- Playback architecture or backend recommendation logic.
- New recommendation APIs or fabricated Top Picks.
- Rebranding AUDIFY to look like BitChord.

---

## 6. References

- [AUDIFY — current HomeScreen.kt](https://github.com/sachit1751-art/Audify/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/HomeScreen.kt)
- [BitChord — reference HomeScreen.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt)

**Final direction:** BitChord's hierarchy and compact/hero feed rhythm, implemented with AUDIFY's existing components, data, identity, and interactions.
