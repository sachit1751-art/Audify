# Design context — Audify

> **Purpose:** practical visual and interaction guidance derived from the current Android/Compose implementation. Treat this as the design baseline, not permission to replace established patterns wholesale. Verify current rendered screens before changing them.

## Design direction

Audify uses a dark-first, music-focused visual language with a restrained, high-contrast palette, artwork-led surfaces, compact information hierarchy, and motion that supports playback context. The Android app theme also defines a light scheme; individual screens and player surfaces may use their own artwork-aware or intentionally dark presentation. Do not assume every surface is forced dark.

The visual direction is premium and editorial without sacrificing scanability or control density. Album artwork and track identity should carry visual emphasis; controls and metadata should remain legible and predictable.

## Current foundations (source-backed)

- **UI framework:** Jetpack Compose / Compose Multiplatform, Material 3 foundations, shared UI in `sharedUi/` and Android-specific UI in `app/`.
- **Android theme:** `app/src/main/java/com/music/audify/ui/theme/Theme.kt` defines light and dark Material color schemes, SF Pro Display font resources, and a compact, weight-forward typography scale.
- **Typography:** SF Pro Display is bundled for weights 400, 500, 600, 700, and 800. Existing type styles use strong headlines, tight negative tracking on display/headline/title styles, and conventional body sizes. Preserve system font scaling and avoid hard-coded pixel typography.
- **Color:** the base dark scheme uses near-black backgrounds/surfaces, white primary text, and muted gray secondary text; the light scheme uses white/off-white surfaces and black text. `AccentRed` is retained for specific legacy/rank emphasis, not as the general primary accent.
- **Artwork theming:** artwork palette utilities and the artwork-mesh player backdrop are existing visual capabilities. Dynamic artwork colors must not reduce text/control contrast.
- **Motion:** `sharedUi/.../ui/motion/MotionTokens.kt` defines shared durations and easing (`CONTROL_STATE_MS`, `SHORT_FADE_MS`, `CONTENT_TRANSITION_MS`, `SHEET_ENTER_MS`, standard and linear easing). Reuse tokens; do not introduce arbitrary durations without a reason.
- **Player composition:** shared player UI is decomposed into now-playing, controls, lyrics, queue, dock/drawer, side panels, output sheet, and landscape player components. Extend the owning component rather than creating parallel player surfaces.
- **Navigation surfaces:** Home/Explore, Library, Search, player, lyrics, queue, Settings, and dedicated TV/Wear surfaces already exist. New navigation must follow current route/state ownership.

## Layout and hierarchy

1. **Lead with the task:** each screen should make its primary job obvious (discover, search, organize, listen, configure).
2. **Keep playback identity stable:** artwork, title, artist, and transport state should remain visually coherent as the player expands/collapses or content changes.
3. **Use progressive disclosure:** secondary actions belong in existing overflow, sheets, drawers, or detail surfaces; do not crowd the primary playback controls.
4. **Prefer content-aware density:** library/search lists should optimize scanning; the player can be more expressive but must retain clear transport hierarchy.
5. **Respect adaptive constraints:** phone layouts are the compact baseline. Tablet/foldable two-pane behavior remains planned work, not an assumed current guarantee. TV must remain navigable with a remote; Wear OS must respect watch-sized interaction constraints.

## Components and interaction patterns

- Reuse established Material 3 controls, cards, chips, sheets, dialogs, list rows, and Compose semantics where they already exist.
- Use stable keys for repeated media/queue occurrences; duplicate tracks may appear more than once and must remain distinguishable in interaction state.
- Destructive or irreversible actions require clear labels and confirmation. Bulk actions require a preview of affected items and must preserve user-owned data.
- Loading, empty, unavailable, permission-denied, stale, partial, and error states need distinct and actionable presentation. Do not use a generic spinner or empty state for every failure.
- Make disabled actions explain why they are unavailable when that reason is useful to the user.
- Sheets and dialogs must have predictable dismissal/back behavior and preserve relevant selection/state.
- Provide semantic labels, roles, selected/disabled states, and logical focus order. Do not announce rapidly changing lyric words or playback ticks continuously to assistive technology.

## Motion and feedback

- Reuse `MotionTokens` for control-state changes, short fades, content transitions, and sheet entry.
- Motion should communicate hierarchy, continuity, and state; avoid decorative movement that competes with lyrics or transport controls.
- Respect reduced-motion preferences where supported. Avoid relying on animation to communicate essential state.
- Audio/queue changes must not create misleading visual transitions. A source retry is not a track change; a queue edit is not playback completion.
- Show progress for long-running scans/imports/repairs and support cancellation where the operation supports it.

## Accessibility and legibility

- Preserve Android system font scaling; avoid fixed-height text containers that clip at larger scales.
- Maintain sufficient contrast for text, icons, focus indicators, and controls across light/dark and artwork-derived backgrounds. Validate measured contrast rather than relying on visual intuition.
- Support TalkBack semantics, keyboard/remote focus, minimum practical touch targets, and non-color-only status communication.
- Reduced motion should remove non-essential movement while preserving understandable state updates.
- Lyrics must retain a usable manual-focus/auto-follow model; synchronized updates must not fight user navigation.

## Audio/player-specific design rules

- Primary transport controls remain dominant over secondary actions.
- EQ/output/source status must accurately distinguish app-controlled processing from Android, external device, or Cast-owned behavior.
- Crossfade and Automix visuals must not imply a transition is supported when the audio engine cannot perform it.
- Queue rows must represent queue occurrences, not just media IDs; duplicate tracks are valid occurrences.
- Error/retry UI should explain what is being retried and whether the recording itself is unchanged. Never silently present a different version as the requested track.

## Planned design work — not current design commitments

Plans 051–068 include queue selection and named slots, richer import preview, history views, backup/cleanup/repair interfaces, release alerts, local-library indexing, output presets, playlist health, source fallback explanations, advanced search filters, Automix controls, tablet two-pane layout, accessibility-first lyrics, statistics dashboard, and contextual player actions.

These are not all shipped. Before designing any of them:
- inspect the corresponding plan and reconcile older plans 025–050;
- inspect the current component and rendered UI at target screen sizes;
- identify the canonical state owner and existing interaction pattern;
- read `reference/craft-floor.md` from the Impeccable skill before UI edits when that skill is available;
- verify the rendered result on relevant phone/tablet/TV/watch/device sizes and accessibility settings.

## Design anti-patterns to avoid

- Replacing the current design language with a generic dashboard/template aesthetic.
- Adding gradients, glass, blur, shadows, pills, or animation solely for decoration.
- Introducing a second design system or parallel component library without an explicit architecture decision.
- Hiding important playback, source, permission, or failure state behind color alone.
- Using artwork-derived colors without contrast safeguards.
- Making controls visually prominent when the underlying action is unsupported or unsafe.
- Treating tablet, foldable, TV, or Wear OS layouts as scaled phone layouts.
- Polishing screenshots while leaving loading, empty, error, focus, and large-font states incomplete.

## Design change acceptance checklist

- [ ] Current screen and existing pattern inspected before editing.
- [ ] Primary task and visual hierarchy remain clear.
- [ ] Light/dark and artwork-derived contrast checked where applicable.
- [ ] System font scaling, TalkBack, keyboard/remote focus, and touch targets checked for affected controls.
- [ ] Motion uses shared tokens and has a reduced-motion-safe behavior.
- [ ] Loading, empty, partial, unavailable, permission, and error states are designed.
- [ ] Compact and expanded/device-specific layouts are checked where in scope.
- [ ] Screenshot/device verification is recorded; unverified behavior is explicitly marked.
