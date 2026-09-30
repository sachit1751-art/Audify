# Audify Design System

> **What this document is:** Audify's written design identity — the reference every future UI change is checked against.
> **Goal:** make "does this feel like Audify?" answerable by rule, not taste, and keep Audify visually distinct from every competitor (BitChord check included).
> **Contains:** identity thesis ("calm signal") + 3 test rules · visual language (color, typography, spacing, radius, elevation, artwork) · interaction language (gestures, loading/empty/error states) · motion language (durations, easing, transitions, reduced-motion) · component language (cards, sliders, sheets, player controls, queue items) · an anti-BitChord merge checklist.
> **Result / how to use:** run §6's checklist before merging any UI feature; consult §2–§5 when adding or changing any component. Target specification — existing components are named where they already comply, new rules are marked as proposals.
>
> **Purpose:** make every future feature answer "does this feel like Audify?" with a written reference instead of taste. BitChord is frosted-glass Apple-Music; SimpMusic is style-switcher; RiMusic was maximal customization. Audify is none of those — Audify is **calm signal**: a warm, editorial surface where the artwork is the color engine and every control is honest about what it does.

---

## 1. Identity thesis

> **"Calm signal."** Warm paper surfaces, one saturated signal color drawn from the artwork, generous type, and motion that explains rather than decorates.

Three rules any change can be tested against:

1. **The artwork leads.** Color, gradient and emphasis derive from the current artwork; chrome stays quiet.
2. **State is visible.** Buffering, skipped, downloading, fallback — every state has a glyph, never a guess.
3. **Motion explains.** An animation exists only if it shows where something came from or what just changed.

## 2. Visual language

### 2.1 Color

| Role | Rule | Source of truth |
|---|---|---|
| Primary (signal) | Artwork-extracted accent; never hard-coded. Fallback: warm amber `#E8A13D` when extraction fails | dynamic color scheme, artwork palette |
| Surfaces | Warm neutrals (paper-toned), not pure white/black: `#FAF7F2` light, `#141210` dark | theme constants |
| Secondary | Desaturated complement of primary, for containers/chips | scheme derivation |
| Error/Success | Standard M3 roles; skip-marks and health states use *tertiary*, not error red | M3 |
| Gradient | Mesh gradient behind player anchors on 3 artwork-sampled hues (existing `MeshGradient.kt`), max 3 animated anchors on low-RAM devices | existing component |

Rules: no more than one saturated hue per screen besides the signal color; gray text is `onSurfaceVariant` only; "lossless" or quality badges use tertiary container, never neon.

### 2.2 Typography

| Use | Face / style | Notes |
|---|---|---|
| App-wide | Roboto variable (system default M3) | no bundled font in Phase 1–2; keeps APK lean and respects user font scale |
| Screen titles | `headlineMedium`, weight 400–500, never all-caps | editorial calm |
| Section labels | `labelMedium`, `letterSpacing 0.08em`, signal color allowed | e.g. "Earlier", "Signal" |
| Body/list | `bodyMedium`/`bodyLarge`, one line marquee max | existing list items |
| Lyrics | `titleLarge` baseline; **must scale with system fontScale up to 2×**; steady mode disables per-word motion (Innovation #8) | existing LyricsLine |

### 2.3 Spacing & radius

- **Spacing scale (dp):** 4 / 8 / 12 / 16 / 24 / 32. Nothing else without a written reason. Screen gutters 16; card padding 12; list item min-height 56 (existing `ListItemHeight`).
- **Corner radius:** 12 dp small (chips, thumbnails in lists), 20 dp medium (cards), 28 dp large (sheets, expanded artwork default). The existing artwork corner-radius picker (None/Subtle/Rounded) maps to 0 / 6 / 28.
- **Elevation:** flat by default. Tonal surfaces (`surfaceContainerLow/High`) instead of shadows; shadow only on floating mini-player, 6 dp max.

### 2.4 Artwork treatment

- Artwork is the largest element on the player (min 72% screen width) with the user-chosen radius.
- In lists, thumbnail 48 dp, radius 12; active row's thumbnail carries a small equalizer glyph (existing pattern).
- No blur-of-artwork backgrounds outside the player; Home cards use scrim, not blur (perf rule).

## 3. Interaction language

| Pattern | Rule |
|---|---|
| Gestures | Player artwork: single-tap toggles play (existing), double-tap ±5 s with haptic (existing). Mini player: tap opens; no swipe-to-skip (deliberate — BitChord issue #168 shows accidental-swipe pain). Queue rows: drag via handle, swipe to dismiss with undo (existing) |
| Back | Sheets dismiss first, then screens; player collapse animates downward into mini player origin |
| Selection | Long-press anywhere enters select mode with haptic (existing queue behavior) — the one "power" gesture Audify keeps consistent |
| Loading states | Shimmer on first paint (existing shimmer host); **never** spinners for list content; skeleton matches final layout to avoid reflow |
| Empty states | One line of warm copy + one action button, artwork-agnostic illustration forbidden (no mascots); example: "Nothing here yet — start a radio and it'll fill itself" |
| Error states | Name the cause in plain words + one retry action; network errors offer "wait for connection" chip (existing waiting-for-network flow) |
| Confirmation | Destructive actions use a bottom sheet with the item's name bolded, not a generic dialog |
| Settings | Grouped cards (`Material3SettingsGroup`, existing); one setting = one row; never bury behind long-press |

## 4. Motion language

| Motion | Spec |
|---|---|
| Durations | 150 ms (chips/taps) · 250 ms (sheets/cards) · 400 ms (player expand/collapse). Sourced from one `MotionTokens` object to be added; no literal durations in features |
| Easing | Standard M3 `EasingEmphasized` for spatial moves; `LinearEasing` only inside loops (visualizer) |
| Screen transitions | Fade-through: outgoing 90 ms fade-out, incoming 150 ms fade + 4 dp slide. No shared-axis zoom everywhere — reserved for artwork → detail |
| Player transition | Artwork expands from mini-player thumbnail bounds (shared element intent); queue sheet slides over the player's lower half |
| Artwork animation | Mesh gradient anchors drift ≤ 0.16 amplitude (existing); paused state freezes drift to save battery |
| Micro-interactions | Like: 200 ms scale 1→1.15→1 with haptic; skip-mark: glyph fades in, never slides; queue reorder: 250 ms position tween (existing) |
| Reduced motion | If system animator scale = 0: all of the above become 0 ms or opacity-only (hard rule; feeds Innovation #8) |

## 5. Component language

| Component | Audify rule |
|---|---|
| Cards | Tonal, radius 20, no border; content-first, label `labelMedium` on top edge inside 12 padding |
| Buttons | Filled = one per view (the primary action); tonal for secondary; text for tertiary. Icon buttons 48 dp targets |
| Sliders | Existing `WavySlider` on player (progress), `ThinSlider` in settings; scrubbing shows time bubble, artwork dims 20% while scrubbing |
| Bottom sheets | Modal sheets radius 28 top; the queue uses the persistent bottom-sheet pattern (existing); every sheet has a grab handle and closes on backdrop tap |
| Dialogs | `EnumDialog` pattern (existing) for choices; max 3 dialogs deep, never chained |
| Player controls | Play 72 dp, skip 48 dp; secondary row (like/lyrics/signal/timer) uses tonal chips, not icons-only — labels make state legible |
| Queue items | Active row: signal-color title + equalizer glyph; skipped rows 50% alpha + skip glyph (Innovation #1); "Earlier" divider = label + hairline (Innovation #1); automix section separated by divider (existing) |
| Stats/health | "Signal" sheet: two-column key/value, monospaced numerals, health states colored tertiary container (Innovations #3, #4) |
| Widgets | Squircle-safe, monochrome-icon aware, one accent from current artwork; turntable-style spin only when playing (SimpMusic-inspired, Audify-styled) |

## 6. Anti-BitChord checklist (run before merging any UI feature)

- [ ] Layout: not a reskin of BitChord's player/list arrangement
- [ ] Navigation: Audify's structure (existing NavigationBuilder), no tab hierarchy clones
- [ ] Color: signal color is artwork-derived, not BitChord's accent
- [ ] Type: no BitChord typographic scale or all-caps treatment
- [ ] Motion: transitions explain; nothing copied frame-for-frame
- [ ] Naming: features use Audify names ("Signal", "Earlier", "Handoff") — never BitChord's wording ("stats for nerds" appears only in docs, not UI)
- [ ] Empty/loading/error states follow §3, not BitChord's patterns
- [ ] Icons: stock Material Symbols only, no BitChord icon set
