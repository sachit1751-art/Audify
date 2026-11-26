# Audify 1.9.5

## Search and visual polish

- Search's empty state is now deliberately quiet: mood and genre tiles, standard recent searches, and Addons recent searches are not shown before a query is entered.
- Search header, top-result cards, typeahead song rows and recent-result rows no longer paint opaque page-colour panels over the animated/background surface; the underlying backdrop remains visible.
- Restored full-width tablet layouts and corrected the bottom fade treatment to avoid a black slab around the bottom navigation.

## Playback, library and Wear OS reliability

- Skip Segment now uses one simple playable range with separate start and end sliders; audio before and after the selected range is excluded. The same in/out points are respected by standard crossfade and Automix, and the saved range can be edited or reset.
- The Skip Segment editor now reuses Audify's existing animated thin-slider control instead of decoding and drawing a waveform.
- Replay summary cards and the Share My Replay total now use readable hour/minute formatting instead of large minute totals.
- Removed the Jade built-in colour theme.
- Local-library permission failures now explain the required access instead of appearing as an empty or successful partial library scan.
- Queue identity on Wear OS now uses entry IDs only when uniqueness is explicitly verified across the full phone queue; otherwise it falls back to stable queue positions.
- Wear queue publication is generation-ordered so an older asynchronous snapshot cannot replace a newer one.
- Clearing phone now-playing state clears stale playback and queue state on the watch.
- Reduced-motion settings now suppress in-flight skip movement and queue auto-follow animation; player and navigation touch targets are consistently sized.

## Scrolling and rendering performance

- Removed per-card spring, alpha and scale entrance animations from actively scrolling horizontal shelves.
- Retained bounded nested-shelf prefetching and native momentum scrolling.
- Progressive Blur defaults to **off** on fresh installs; an explicitly saved user choice remains unchanged during upgrades.
- Increased the progressive edge blur's sampling radius and fade coverage while retaining reduced-resolution sampling.

## Appearance and theme refinements

- Refined light-theme surface and outline tones for clearer separation while preserving each theme pack's accent palette and existing dark-surface styles.
- Kept theme accents on component surfaces rather than allowing the accent colour to replace the neutral page canvas.

## Home stability and accessibility

- Home shelves now use stable identity keys instead of position-based keys, preventing feed updates from recreating shelf state when another shelf is inserted.
- The Recents loading placeholder and its loaded shelf retain the same identity; supplementary Recents loading starts after the primary Home feed is published.
- Suppressed shelf entrance reveals during active scrolling to avoid repeated visual flashes while the list is moving.
- Memoized Home's filtered shelf model and Library's pinned/sorted shelf model so unrelated playback and UI updates do not repeatedly allocate or sort the same feed data.
- Library grid cards now have stable, collision-safe item keys, including when provider data repeats a title or omits an ID.
- Home and Library layout controls expose named visibility and movement actions; move controls disable at pinned/unpinned boundaries.
- Home now defers podcast-library and discovery requests until the Podcasts shelf is actually composed, so those requests no longer compete with the initial feed load. Quick Picks grouping and current-track matching are memoized to avoid repeated work during playback-driven recompositions, especially with wider tablet viewports.
- The expanded player now shows a lightweight 160px artwork preview while the full-resolution cover loads, then hands off to the sharp cover without changing the existing artwork transition.
- Library startup keeps its loading state in the account-collections position and holds the On device shelf until the account shelves have settled, preventing it from appearing first and shifting the rest of the page afterward.
- The mini-to-full-player and full-player-to-mini spring is slightly quicker while remaining critically damped and velocity-aware, keeping tap, drag-release, close and interrupted-reopen motion on the same timing curve.
- Local Spotify playlist shelf models are memoized instead of being rebuilt on unrelated recompositions.
- Successful lyrics now use the existing bounded 20 MB / 30-day disk cache, initialized in the app-private cache directory; cache reads and writes run on the I/O dispatcher.
- The full-screen Canvas dissolve and lower glass-deck transition use narrower blur radii to reduce repeated GPU work while retaining the layered transition.
- The decoded artwork memory cache is released when Android reports running-low (or more severe) memory pressure; the disk cache remains intact for reloads.
- The YouTube Music login WebView now stops loading and destroys its view when released, rather than retaining its browser resources after leaving the screen.

## Onboarding and listening intent

- Onboarding progress and action copy now use the dedicated English resource file, and the progress indicator exposes an accessible step description.
- Radio sessions can be steered toward familiar artists, discovery, higher-energy or calmer tracks, or the provider's original order; explanations use available artist/energy signals and remain session-scoped.
- The radio explanation cache is bounded and refreshes recency when a track appears in a later batch.

## Version and build

- Phone, Wear OS and Android TV configuration remains at **1.9.5 (44)**. This is a 1.9.5 maintenance update; no version-code bump is made here.
- Android production-debug Kotlin compilation and shared UI JVM tests pass for the current working source state.
- Production APK packaging and signature verification are intentionally deferred; no APKs are attached to this draft. Physical-device, accessibility-service and audio-route acceptance remain unverified. No new benchmark result is claimed here.
