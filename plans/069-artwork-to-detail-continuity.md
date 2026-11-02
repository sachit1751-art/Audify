# 069 — Connect artwork to album and playlist details

- **Status**: TODO
- **Commit**: dd97d775
- **Severity**: HIGH
- **Category**: Missed opportunities; physicality & origin
- **Estimated scope**: 5–8 files; medium/large UI change

## Problem

The album/playlist card-to-detail navigation is a spatial seam: the destination currently rebuilds its own hero artwork rather than proving that the selected cover became the detail-page cover. The detail page's internal shelf change is an `AnimatedContent` fade, not a card-to-hero transition.

Evidence: `app/src/main/java/com/music/audify/ui/screens/DetailScreen.kt:504-512`:

```kotlin
AnimatedContent(
    targetState = activeShelf,
    transitionSpec = {
        fadeIn(animationSpec = tween(MotionTokens.CONTENT_TRANSITION_MS)) togetherWith
            fadeOut(animationSpec = tween(MotionTokens.SHORT_FADE_MS))
    },
    label = "artist_shelf_transition",
    modifier = modifier.fillMaxSize(),
) { targetShelf ->
```

The player already has a measured artwork-docking abstraction. `app/src/main/java/com/music/audify/ui/components/MiniPlayer.kt:327-340` reports the mini artwork's `LayoutCoordinates` to `PlayerDock` and hides the mini artwork while the player cover travels. Reuse that architectural principle; do not create a competing global transition coordinator.

## Target

- Selecting an album or playlist card visually carries that exact cover into the destination hero; reverse navigation returns the same cover to the originating card when the origin still exists.
- Use Compose shared-element/container-transform APIs only if the project's resolved Compose version exposes a stable API compatible with current navigation/state ownership. Otherwise implement a narrowly scoped measured-bounds overlay with one artwork instance and a clean fallback.
- Match source and destination artwork crop, corner radius and image request identity. Do not animate a generic placeholder as though it were the selected artwork.
- Keep title/artist/metadata readable during the transition; do not animate a full-page snapshot or duplicate the whole destination.
- Respect `AppSettings.reduceAnimation`: remove positional/scale travel and retain a brief opacity/state change.
- Motion duration: 200–260ms; strong ease-in-out for the on-screen movement. Do not exceed the UI budget of 300ms.

## Repo conventions to follow

- Shared durations/easing live in `sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/motion/MotionTokens.kt` (current values: `CONTENT_TRANSITION_MS = 220`, `SHORT_FADE_MS = 180`, `STANDARD_EASING = FastOutSlowInEasing`). Extend tokens only when a genuinely distinct curve is required.
- Detail artwork is selected around `DetailScreen.kt:462-470` and rendered in the hero around `DetailScreen.kt:888-946` / `1378-1415`.
- Existing player artwork coordinate reporting: `app/src/main/java/com/music/audify/ui/components/MiniPlayer.kt:327-340`.

## Steps

1. Trace card click/navigation ownership from `DetailScreen.kt` callers and the Home/Search/Library card components. Identify stable source keys for album and playlist entities; never key only by title.
2. Trace destination/back-stack ownership and determine whether the source card remains composed during navigation. Document the chosen source/destination ownership before editing.
3. Add a reusable artwork transition state/host in the owning navigation layer. Capture source bounds, destination bounds, exact artwork URL and shape; release all references on completion, cancellation and navigation failure.
4. Wire album and playlist cards plus their destination hero to the same transition key. Artist pages are out of scope unless they use the same verified artwork entity model.
5. Add reduced-motion behavior using the existing `AppSettings.reduceAnimation` preference. Do not assume Android system animator scale is equivalent to this app preference.
6. Add focused tests for stable-key matching, duplicate titles with distinct IDs, missing source bounds, missing artwork, rapid repeated navigation and cancellation.

## Boundaries

- Do not modify playback state, queue state, artwork fetching/cache semantics, database schema, migrations, version, or unrelated navigation transitions.
- Do not introduce a new animation dependency.
- Do not create a shared-element transition that depends on source composables remaining alive unless that lifecycle is proven by the current navigator.
- If the resolved Compose/navigation APIs do not support the required continuity, stop and report the API limitation rather than inventing an unsupported API.

## Verification

- **Mechanical**: `./gradlew :app:compileFossDebugKotlin --console=plain`; `./gradlew :app:testFossDebugUnitTest --console=plain`; `./gradlew :app:assembleFossDebug --console=plain`.
- **Feel check**: on a phone emulator and a real device, open album and playlist details from Home, Search and Library; verify cover continuity in both directions, crop/corners, no duplicate flash, no stale source image and correct Back behavior.
- Repeat taps/navigation rapidly; transition must cancel/retarget without a stuck overlay or leaked coordinates.
- Enable Reduce Motion; confirm no artwork translation/scale travel while destination state remains immediately understandable.
- Test missing artwork, slow image loading, landscape and large font scale.
- **Done when**: album and playlist cover continuity is visually verified on-device in both directions, fallback and reduced-motion paths pass, and tests/build pass.

