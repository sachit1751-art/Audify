# Plan 066 — Selected feature 16: Accessibility-first lyrics

**Status: planned — reconcile with existing plan 045. No previous accessibility preference patch should be assumed present.**

## Objective

Make lyrics readable and controllable for users with low vision, motion sensitivity, or assistive technology: scalable text, contrast-safe presentation, reduced motion, stable/manual focus, and correct accessibility announcements across portrait, landscape, and shared lyrics surfaces.

## Why

Lyrics are time-sensitive content. Excessive animation, small text, poor contrast, and repeated TalkBack announcements can make them unusable.

## What exists today

- Lyrics surfaces include `sharedUi/.../ui/player/PlayerLyrics.kt`, `SyncedLyricsPanel.kt`, `LandscapePlayer.kt`, and lyrics controls/clock helpers.
- `MainActivity.kt` loads lyrics and exposes provider/offset controls.
- Plan 045 is the existing proposal. Previous attempted preference wiring was reverted; verify current source rather than assuming those settings exist.

## Scope

**In scope:** user text scale with sane limits; reduced-motion behavior; stable/manual line focus; adequate contrast in all themes; TalkBack semantics for current line and controls; avoid announcing every lyric tick; preserve timing and scrolling behavior; consistent portrait/landscape/share preview behavior.

**Out of scope:** changing lyric providers or synchronization algorithms; automatic translation; new network features; body/visual preference claims beyond accessibility needs.

## Affected files

| File | Expected change |
|------|-----------------|
| `sharedUi/.../ui/player/PlayerLyrics.kt` | Shared lyric typography, focus, and semantics |
| `sharedUi/.../ui/player/SyncedLyricsPanel.kt` | Timing-aware announcements and motion behavior |
| `sharedUi/.../ui/player/LandscapePlayer.kt` | Parity with portrait controls and settings |
| `sharedUi/.../ui/player/LyricsControlsGesture.kt` | Manual focus/gesture accessibility |
| `app/.../ui/screens/SettingsSheet.kt` | User-facing accessibility preferences if not already available |
| `app/src/main/res/values/sachit_strings.xml` | English-only labels and summaries |

## Implementation

1. Reconcile plan 045 and audit every lyrics rendering path, including embedded/offline and landscape modes.
2. Capture baseline semantics, contrast, font scaling, and motion behavior with current themes.
3. Define shared preferences and defaults; avoid separate settings for each screen.
4. Implement bounded text scaling, reduced-motion transitions, and manual/stable focus.
5. Add concise TalkBack semantics that announce meaningful line changes without a rapid event stream.
6. Test all lyrics surfaces and ensure no synchronization offset or line-selection behavior regresses.

## Testing

- Compose semantics tests for labels, roles, current-line state, and controls.
- Font scale, high contrast/theme, reduced motion, portrait/landscape, and offline tests.
- TalkBack manual test for line changes, seek controls, provider selection, and focus restoration.
- Regression tests for lyric timing/offset and line alignment.

## Performance notes

Do not recompose the full lyrics document at playback tick frequency. Keep semantics updates throttled to meaningful line changes.

## Rollback

Disable the new preferences individually while preserving existing lyric display and synchronization. Do not revert unrelated player accessibility semantics.

## Rejected alternatives

- Announce every timestamp update: creates unusable screen-reader noise.
- Apply reduced motion only in portrait: behavior must be consistent across surfaces.
- Change lyric timing to compensate for visual animation: rendering and synchronization are separate concerns.

## Implementation contract and acceptance criteria

### Preference and rendering contract
- One shared preference model applies consistently to portrait, landscape, mini-player, and any lyrics preview. Defaults preserve current behavior unless Android's reduced-motion preference requires motion reduction.
- Text scaling composes with system font scale; define a bounded user multiplier and test the combined maximum to prevent clipping. Use scalable text units, not pixel sizes.
- Reduced motion removes non-essential word-level animation and animated scrolling; line synchronization may continue updating the semantic current line without motion.
- Contrast must be measured against WCAG 2.2 AA text contrast targets where applicable (4.5:1 normal text, 3:1 large text). Do not hard-code white-on-black if theme surfaces can provide a compliant adaptive pair.
- TalkBack announces meaningful current-line changes at a controlled cadence, never every word/timestamp tick. Provide labels/roles/state for controls and a way to pause auto-follow or manually focus lines without fighting the scroll.

### Platform and regression rules
- Prefer supported Android/Compose accessibility and animator-scale APIs; do not read unrelated secure/global settings or invent a nonexistent Compose API. Confirm the exact platform API and min-SDK behavior before coding.
- Do not alter lyric timestamps, offset calculation, parser behavior, or provider selection as part of presentation accessibility.
- Previous preference implementation was reverted; inspect current source and do not assume keys or UI exist.

### Done when
- Semantics tests cover labels, roles, focus, current-line announcements, and reduced-motion behavior.
- Contrast is measured in light/dark themes; font scale tested at system and app maxima; portrait/landscape/mini-player parity verified.
- Manual TalkBack test confirms announcements are useful and not noisy; sync offset/timing regression suite passes.

## Mandatory pre-implementation source audit

Before changing code, the implementer must:
1. Read every referenced existing plan and mark each requirement shipped, partially shipped, absent, obsolete, or blocked with source/test evidence.
2. Search the current repository for the actual owning types, state holders, persistence APIs, navigation routes, and existing UI actions. The affected-file table is a starting map, not permission to create similarly named duplicate classes or assume every path still exists.
3. Record the exact files/functions to change and the exact tests to add. Update this plan's affected-file table if ownership differs; do not leave placeholder paths in an implementation PR.
4. Write down unresolved product decisions and platform/provider limitations. Stop for user approval where this plan marks a human gate, schema/storage change, external-provider dependency, or destructive behavior.
5. Establish baseline behavior with focused tests/build and, for UI/audio work, current rendered/device behavior before editing. Preserve unrelated working-tree changes.

## Implementation handoff checklist

A task is not complete merely because it compiles. The implementation report must include: changed files; decisions made; tests executed with actual results; manual/device checks completed or unavailable; known limitations; data migration/compatibility impact (or explicit none); and rollback steps. Do not mark this plan shipped while any `Done when` criterion remains unverified.
