# Audify 1.9.5

## Search and visual polish

- Search's empty state is now deliberately quiet: mood and genre tiles, standard recent searches, and Addons recent searches are not shown before a query is entered.
- Search header, top-result cards, typeahead song rows and recent-result rows no longer paint opaque page-colour panels over the animated/background surface; the underlying backdrop remains visible.
- Restored full-width tablet layouts and corrected the bottom fade treatment to avoid a black slab around the bottom navigation.

## Playback, library and Wear OS reliability

- Local-library permission failures now explain the required access instead of appearing as an empty or successful partial library scan.
- Queue identity on Wear OS now uses entry IDs only when uniqueness is explicitly verified across the full phone queue; otherwise it falls back to stable queue positions.
- Wear queue publication is generation-ordered so an older asynchronous snapshot cannot replace a newer one.
- Clearing phone now-playing state clears stale playback and queue state on the watch.
- Reduced-motion settings now suppress in-flight skip movement and queue auto-follow animation; player and navigation touch targets are consistently sized.

## Scrolling and rendering performance

- Removed per-card spring, alpha and scale entrance animations from actively scrolling horizontal shelves.
- Retained bounded nested-shelf prefetching and native momentum scrolling.
- Progressive Blur defaults to **off** on fresh installs; an explicitly saved user choice remains unchanged during upgrades.

## Version and build

- Phone, Wear OS and Android TV configuration remains at **1.9.5 (44)**. This is a 1.9.5 maintenance update; no version-code bump is made here.
- Production APKs previously built for universal, ARM64, ARMv7 and x86_64 variants and passed signature/version verification.
- Shared UI JVM tests and production-debug unit-test tasks previously passed: 1,308 tests, zero failures, zero errors, one skipped.
- The current Search changes still require a fresh build and device verification before this update is considered fully validated. No new benchmark result is claimed here.
