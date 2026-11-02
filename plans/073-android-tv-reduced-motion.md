# 073 — Apply Reduce Motion across Android TV

- **Status**: TODO
- **Commit**: dd97d775
- **Severity**: HIGH
- **Category**: Accessibility; cohesion
- **Estimated scope**: 4–8 files; medium change

## Problem

TV Settings exposes the app-level Reduce Motion preference (`tv/src/main/java/com/music/audify/ui/tv/screens/TvSettingsScreen.kt:91`, `:298-301`), but multiple TV transitions shown in `TvApp.kt` are hard-coded and do not read it:

```kotlin
AnimatedContent(
    targetState = isRunningSetup,
    transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) },
    label = "tvAppView",
```

```kotlin
AnimatedVisibility(
    visible = isNowPlayingOpen,
    enter = scaleIn(initialScale = 0.94f, animationSpec = tween(260)) + fadeIn(tween(220)),
    exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.97f, animationSpec = tween(180)),
)
```

Artwork tint and bottom-bar movement also animate independently: `TvApp.kt:272` uses `animateColorAsState(... tween(900))`, and `:413` uses `animateFloatAsState(... tween(260))`. `TvMotion.kt` defines reusable tokens but does not itself enforce the preference.

## Target

- Every non-essential TV transition responds consistently to `AppSettings.reduceAnimation`.
- In reduced-motion mode, remove scale, translation, slide and decorative movement; preserve immediate focus/state changes and, where useful, a short opacity transition (100–160ms).
- Artwork-derived background tint may update immediately or with a brief color-only transition; never leave stale artwork color after a track change.
- Focus restoration and D-pad navigation must not wait for animation completion.
- Normal mode retains current visual character but uses shared TV motion tokens and stays within 300ms for UI transitions; decorative long tint transitions must be explicitly justified or shortened.

## Repo conventions to follow

- TV motion token helpers: `tv/src/main/java/com/music/audify/ui/tv/theme/TvMotion.kt`.
- Preference source: `AppSettings.reduceAnimation` (observed in `TvSettingsScreen.kt:91`).
- Transition owners: `tv/src/main/java/com/music/audify/ui/tv/TvApp.kt:196-226`, `:266-272`, `:411-413`.
- Settings toggle: `tv/src/main/java/com/music/audify/ui/tv/screens/TvSettingsScreen.kt:296-302`.

## Steps

1. Trace how `AppSettings.reduceAnimation` is collected in the TV application root and whether it is available at all transition owners. Avoid collecting it independently in every row if a composition local/provider is more appropriate.
2. Add a TV motion preference/local or small spec factory that maps normal and reduced modes. Reduced mode must use no scale/translation and a 100–160ms opacity-only transition where needed.
3. Apply the policy to setup content, Now Playing open/close, artwork tint, bottom-bar visibility and all other TV transitions found by a repository-wide animation search.
4. Ensure focus ownership/restoration remains immediate and deterministic when transitions are removed or shortened.
5. Add unit tests for the motion-policy mapping and a source-level audit checklist covering every TV animation call site.

## Boundaries

- Do not change the existing Reduce Motion setting's persistence/key or introduce a second preference.
- Do not disable all state feedback; preserve opacity/color where useful.
- Do not change D-pad focus order, navigation stack, screen layout or artwork palette computation.
- Do not modify phone or Wear OS motion policy in this plan.

## Verification

- **Mechanical**: `./gradlew :tv:compileFossDebugKotlin --console=plain` (confirm actual TV variant/task names first with Gradle task listing); run the relevant TV unit tests and `./gradlew :tv:assembleFossDebug --console=plain` if those tasks exist.
- **Feel check**: install on Android TV emulator and physical TV where available. Toggle Reduce Motion and open/close Now Playing, run setup transition, scroll until bottom bar hides/shows, and change tracks.
- In reduced mode verify no scale/slide/translation, no long tint interpolation, no delayed D-pad focus and no blank transition frame.
- In normal mode verify existing visual continuity and focus restoration.
- Test 720p and 1080p, remote Back, rapid open/close, and focus recovery after interrupted transitions.
- **Done when**: every TV animation call site is accounted for, both preference modes pass remote-driven checks, and the actual TV build/test tasks pass.

