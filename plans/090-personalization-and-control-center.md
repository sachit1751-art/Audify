# 090 — Personalization Engine and Control Centre

**Target release:** 1.9.2  
**Status:** Planned  
**Priority:** High  
**Baseline:** `5a6e9fd0` (2026-11-08)

## Goal

Make AUDIFY substantially more customizable without turning Settings into an unstructured list of switches. Introduce a coherent personalization experience with live previews, predictable persistence, accessible defaults, and controls that are actually consumed by the relevant UI components.

This is a planning record for 1.9.2, not authorization to start implementation or change the app version. Do not bump `versionCode` or `versionName` as part of this plan.

## Current-state audit

The baseline already has:

- System, Light, and Dark theme selection.
- Three app-font choices: Audify, System, Serif.
- Reduce-animation and reduce-dynamic-blur preferences, plus existing appearance options such as Liquid Glass.
- Home shelf visibility controls and Recently Played list/grid selection.
- Playlist pinning (currently limited to five).
- Existing player features and playback preferences, including crossfade, automix, loudness normalization, equalizer, playback speed, and sleep timer.
- General preference backup/import functionality.
- Gesture handling in several UI surfaces, but not a user-defined gesture-to-action map.

The following user-facing capabilities are absent or not exposed as general-purpose customization at this baseline and are the proposed scope:

- User-selectable accent colour picker and saved custom palettes.
- Artwork-derived theme as an explicit selectable appearance mode.
- Adjustable typography scale, artwork shape/corner radius, and navigation appearance/arrangement.
- Multiple selectable player and mini-player layouts with previews.
- Drag-and-drop Home shelf ordering, independent shelf visibility management, and saved Home layout presets.
- Configurable gesture actions for supported surfaces, with conflict handling and reset-to-default.
- Reusable named personalization profiles and selective profile export/import.
- A searchable, category-aware Personalization/Control Centre entry point.

Do not reimplement existing playback features merely to make them appear customizable. Per-headphone/output tuning belongs to Plan 11; player layout/background/gesture/motion work must be reconciled with Plan 09; external playback shortcuts/widgets belong to Plan 080; portable library data remains under Plan 081.

## Product principles

1. **Progressive disclosure:** keep sensible defaults and simple controls easy to find; place advanced controls behind clear categories.
2. **Preview before commit:** appearance and layout changes must be previewable and reversible before applying.
3. **One source of truth:** preferences must have typed definitions, documented defaults, persistence, and a single observable state path.
4. **No dead settings:** every exposed preference must affect the intended UI and have a test proving it.
5. **Accessible by default:** preserve contrast, touch target sizes, screen-reader labels, reduced-motion behavior, and usable layouts at large font scales.
6. **Safe migration:** absent or invalid preferences fall back to existing AUDIFY defaults; do not reset existing user settings.

## Scope and implementation sequence

### A. Preference architecture and Control Centre

- Audit `AppSettings`, preference keys, settings category navigation, and existing backup/import before designing new storage.
- Group personalization preferences by domain (appearance, Home layout, player presentation, interactions, profiles) without duplicating the existing settings store.
- Add a Personalization/Control Centre destination with searchable categories and clear reset actions scoped to a category.
- Define typed models and stable serialization for user-configurable values; validate enum values, colour values, list identities, and unsupported actions on load.
- Keep defaults equivalent to the current experience.

### B. Appearance editor

- Add accent colour presets and a custom colour picker with contrast validation.
- Add an explicit artwork-derived accent mode, with deterministic fallback when artwork colours are unavailable.
- Add typography scale and supported artwork shape/radius controls.
- Preview changes on representative surfaces before applying.
- Ensure theme state updates live and persists across process restart.

### C. Home layout editor

- Represent Home shelves with stable IDs rather than display labels.
- Support reorder, hide/show, and reset-to-default; persist order independently from shelf data.
- Add a small set of useful presets (for example Minimal, Discovery, Library-focused) implemented as configurations of the same model.
- Preserve unknown/new shelves when loading older saved configurations; never silently discard data.
- Respect loading, empty, error, and accessibility states while editing.

### D. Player and mini-player presentation

- Reconcile with Plan 09 before implementation; avoid parallel player-layout systems.
- Expose only layouts that are already implemented and tested. Each layout needs a preview and a stable persisted identifier.
- Allow supported player controls to be shown/hidden or reordered only where the interaction remains accessible.
- Keep playback state, queue state, and player transitions independent from presentation preferences.

### E. Gesture mapping

- Reconcile with Plan 09 and audit existing pointer input handlers before adding a shared gesture registry.
- Support a bounded, explicit set of actions per surface; reject incompatible or duplicate mappings.
- Provide per-surface defaults, conflict messaging, accessibility alternatives, and one-tap reset.
- Do not intercept system navigation gestures or break scrolling, drag-and-drop, TalkBack, or Android Auto behavior.

### F. Personalization profiles

- Save named profiles for appearance, Home layout, and supported player presentation.
- Support switching profiles without restarting the app where technically safe.
- Extend existing backup/import rather than introducing a competing backup format.
- Offer selective import/export and schema-versioned migration; validate imported content and never import executable content.
- Keep account data, listening history, credentials, and provider tokens out of personalization profile exports.

## Out of scope

- Rewriting playback/audio engine features that already exist.
- Per-headphone EQ and output-specific audio tuning (Plan 11).
- Replacing the player implementation or duplicating Player Studio (Plan 09).
- Home-screen widgets and Quick Settings tiles (Plan 080).
- Library database/schema changes or a new cloud profile-sync service.
- A full plugin/script system for user-defined actions.

## Likely code areas to inspect

- `app/src/main/java/com/music/audify/data/settings/AppSettings.kt`
- `app/src/main/java/com/music/audify/ui/screens/SettingsSheet.kt`
- `app/src/main/java/com/music/audify/MainActivity.kt`
- `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/HomeScreen.kt`
- Shared and app-module player/mini-player components.
- Existing preference backup/import implementation and tests.

These are investigation starting points, not a final file allowlist. Re-run repository search at execution time and update the plan if ownership has moved.

## Required tests and verification

- Unit tests for defaults, validation, serialization, migration, ordering, visibility, preset application, and profile import/export.
- UI tests for live preview, apply/cancel, reset, profile switching, Home reorder/hide, and back navigation.
- Regression tests proving existing settings and playback behavior are unchanged when new preferences are absent.
- Accessibility checks for contrast, semantics, large font scales, touch targets, and reduced motion.
- Verify persistence after process death and behavior when imported profiles contain unknown IDs or invalid values.
- Build and test affected Android, shared UI, desktop, and other targets that consume shared preferences.
- Manually verify on phone-sized and tablet-sized layouts before marking complete.

## Rollout and rollback

- Ship with all new preferences defaulting to the current UI and behavior.
- Keep each category independently resettable.
- If a preference causes a rendering or migration regression, ignore the invalid value and fall back to the prior default; do not wipe the entire preference store.
- Do not change database schema or Room migrations for this feature without separate explicit approval.

## Completion criteria

- Every setting shown in the Control Centre has a real consumer and a regression test.
- Appearance and Home changes preview correctly, apply immediately where safe, and survive restart.
- Existing users retain their current appearance and behavior after upgrade.
- Profiles round-trip through validated import/export without including secrets or personal listening data.
- Player and gesture work is reconciled with Plan 09; headphone work with Plan 11; widgets with Plan 080.
- No regressions in accessibility, navigation, playback, or cross-platform compilation.
