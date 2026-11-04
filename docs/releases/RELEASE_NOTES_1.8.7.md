# Audify 1.8.7

**Version code:** 35 (phone and Wear OS) · 33 (Android TV)

This release focuses on a more complete listening-statistics experience, smoother playback and search interactions, and reliability fixes across phone, TV, and Wear OS.

## Highlights

- Redesigned Listening Statistics with compact profile header, selectable time ranges, clearer listening metrics, hourly activity, most-played tracks, top artists, and genre breakdowns based on available listening data.
- Improved Audio output selector sizing so the device picker can use a responsive, scrollable sheet area above persistent player controls.
- Refined search fields and shared UI components for more consistent interaction and layout.
- Improved lyrics lookup query handling with additional regression coverage.
- Hardened app-update version comparison and local-group behaviour, with corresponding unit tests.
- Refined Android TV playback, settings, onboarding, motion, and equalizer presentation.
- Improved Wear OS list, Now Playing, and app navigation presentation.
- Added a structured GitHub bug-report issue form.

## Verification

Release APKs are built from the `prodRelease` variants for phone, Android TV, and Wear OS. See the GitHub Actions release workflow for the automated test, lint, and signed-artifact results.

Listening insights are derived from locally available telemetry. Sections that lack source data are omitted rather than populated with invented values.
