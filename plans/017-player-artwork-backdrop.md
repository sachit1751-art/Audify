# Plan 017 — Artwork-mesh player backdrop (smooth animated background)

Written against commit **`d536fad5`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`d536fad5`, check drift in the in-scope files first.

## Goal

Bring the smooth, artwork-driven animated background of BitChord's v1.7 player to **Audify's
player screen only** — the full-cover "artwork mesh" backdrop plus the pre-blurred full-artwork
layer — as a selectable player backdrop style. Everything else in Audify's player (layout,
controls, typography, gestures) stays exactly as it is. This upgrades the capability Audify
already has (quantizer blob mesh) with BitChord's newer, cheaper, better-looking approach.

Scope guard: **player UI only.** No mini player, queue, home, or settings-screen restyling.

## Why the source implementation is good (recon summary)

BitChord reference files (read-only checkout at `reference-player-sdk/`; GPL-3.0 — same
license as Audify, porting with attribution is fine):

- `reference-player-sdk/.../ui/player/ArtworkMeshBackdrop.kt` (749 lines):
  - `ArtworkMesh` (line ~83): **no color quantiser.** The artwork is averaged into a
    `MESH_GRID` square of cell means; the row at the seam is kept, everything below is flipped
    and rotated sideways. Result: the cover's own colors in the cover's own proportions
    (9/10-black covers stay 9/10 black), neighbors keep their spatial relation, but the
    rotation breaks any column-for-column "reflection" read on structured covers.
  - Held as a **tiny bitmap**, drawn with one `DrawScope.drawImage` + bilinear filtering — the
    sampler does the smooth interpolation. (Their old approach = one full-screen radial
    gradient per grid cell = 36 gradients per frame; this is one image draw.)
  - `rememberFullArtworkBlurImage` (line ~96): 128px decode **from Coil's existing artwork
    request** (same URL as the 1200px sleeve → no extra download), CPU box-blur once on a
    worker thread, cached by URL, staggered after the song-change hand-off.
  - `FullArtworkBlurBackdrop` (line ~142) and `ArtworkMeshBackdrop` (line ~300, blurRadius
    32.dp default): deliberately **no live full-screen `Modifier.blur`** — that's a
    per-frame RenderEffect; the blur here is a one-shot cached bitmap so it costs nothing at
    60 Hz.
- `reference-player-sdk/.../ui/player/MeshGradient.kt` (333 lines): the older quantiser approach Audify
  already ports (`ui/player/MeshGradient.kt`, used by `BitchordStylePlayer.kt:161`).

## Audify current state (recon at `d536fad5`)

- `ui/player/MeshGradient.kt` (287 lines): ported quantiser-blob mesh (`MeshGradientBackground`
  line 78, `rememberArtworkColors` line 190). Already gated for low-RAM via `DevicePerformance`
  patterns elsewhere.
- `ui/player/BitchordStylePlayer.kt:161`: calls `MeshGradientBackground` — the only current
  consumer.
- `ui/player/Player.kt` (main player): background is composed around lines 2038/2117 with
  `PlayerArtworkVisualizerOverlay`; the executor must read the surrounding composable to find
  the exact background insertion point (behind artwork + controls, above nav bar padding).
- `ui/player/PlayerArtworkVisualizer.kt`: audio-reactive visualizer (separate feature — must
  remain composable with the new backdrop, e.g. backdrop + visualizer overlay both enabled).
- Appearance settings live in `ui/screens/settings/AppearanceSettings.kt`; player-style prefs
  pattern exists (`BitchordStyleHideVolumeKey`, `UseNewMiniPlayerDesignKey`).

## Design

New setting **"Player backdrop"** (Appearance settings), enum, default `MESH` (current
behavior — zero change for existing users):

- `MESH` — existing quantiser blob mesh (today's look).
- `ARTWORK_MESH` — new: BitChord-style artwork-cell mesh (the star of this plan).
- `BLUR` — new: pre-blurred full-cover backdrop.
- `OFF` — flat surface color.

All styles compose with the artwork visualizer overlay. Reduced motion (system animator
scale 0, or Audify's low-RAM gating) freezes drift and skips the cross-fade animation —
colors snap.

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/ui/player/PlayerBackdrop.kt` | **New.** Port of `ArtworkMeshBackdrop.kt` + `rememberFullArtworkBlurImage`/`FullArtworkBlurBackdrop`, Audify-renamed |
| `app/src/main/kotlin/com/sachit/music/ui/player/MeshGradient.kt` | No functional change expected; keep as the `MESH` implementation |
| `app/src/main/kotlin/com/sachit/music/ui/player/Player.kt` | Insert backdrop switch at the identified background composable (lines ~2038/2117 region) |
| `app/src/main/kotlin/com/sachit/music/ui/player/BitchordStylePlayer.kt` | Only if the style switch should also cover this player variant — decide during recon; default: leave it on `MESH` |
| `app/src/main/kotlin/com/sachit/music/constants/PreferenceKeys.kt` | `PlayerBackdropKey` (string enum pref, default `MESH`) |
| `app/src/main/kotlin/com/sachit/music/ui/screens/settings/AppearanceSettings.kt` | Backdrop style picker (reuse `EnumDialog`/settings-row pattern) |
| `app/src/main/res/values/sachit_strings.xml` | Strings only (names below) |
| `app/src/test/java/com/sachit/music/` | Unit test for the pure mesh math (cell means, flip+rotate mapping) |

Out of scope: `MusicService.kt`, mini player, queue, every `values-*` file, `app/build.gradle.kts`
(no new dependencies — Coil + Palette + Compose are all present).

## Implementation steps

1. **Drift check**: confirm `Player.kt` background composable location and that
   `MeshGradientBackground` signature is unchanged.
2. **Port `PlayerBackdrop.kt`** from `ArtworkMeshBackdrop.kt`:
   - `meshOf(bitmap, seed)` → `MESH_GRID` cell means, `rotatedBelowSeam` flip+rotate; keep the
     pure bitmap math in top-level functions so it is unit-testable on the JVM (no Android
     framework types in the math — pass `Bitmap`/`ImageBitmap` only at the edges).
   - `rememberArtworkMesh(imageUrl)`: decode small via Coil (same URL trick), `meshOf` on
     `Dispatchers.Default`, hold as `ImageBitmap`.
   - `rememberFullArtworkBlurImage(imageUrl)`: 128px decode + box blur, URL-keyed cache,
     stagger after song change (BitChord staggers to avoid the track hand-off — keep that).
   - `ArtworkMeshBackdrop` / `FullArtworkBlurBackdrop` composables, cross-fading on
     `mediaId` change (BitChord uses ~1400 ms tween; snap under reduced motion).
   - Header comment: "Backdrop approach ported from the BitChord project (GPL-3.0)" — same
     attribution style as the existing `MeshGradient.kt:5`.
3. **Setting**: `PlayerBackdrop` enum (`MESH`, `ARTWORK_MESH`, `BLUR`, `OFF`) +
   `rememberEnumPreference`; picker row in AppearanceSettings.
4. **Wire `Player.kt`**: replace the current direct background with a `when (backdropStyle)`
   switch. `OFF` = `MaterialTheme.colorScheme.surface`. Ensure the visualizer overlay keeps
   drawing on top for every style.
5. **Perf gating**: use `DevicePerformance` heuristics — on low-RAM devices skip the blur
   style's extra decode (fall back to `ARTWORK_MESH`) and freeze mesh drift when paused
   (BitChord freezes anchor drift when paused; keep that behavior in `ARTWORK_MESH`).
6. **Strings** (exact names): `player_backdrop`, `player_backdrop_desc`,
   `player_backdrop_mesh`, `player_backdrop_artwork_mesh`, `player_backdrop_blur`,
   `player_backdrop_off`. English only, `sachit_strings.xml`.
7. **Build + test**: `./gradlew :app:assembleFossDebug --console=plain`
   (JAVA_HOME per AGENTS.md), unit test for mesh math, manual visual pass on the player for
   all 4 styles × dark/light × paused/playing.

## Testing

- **Unit**: cell-mean averaging, flip+rotate index mapping (given a 2×2 grid, assert the
  output bitmap cell order), cache eviction by URL.
- **Manual**: all styles on the main player; song-change cross-fade has no flicker; no lag on
  track hand-off (decode staggered); visualizer still animates above backdrop; Listen Together
  guest mode unaffected (pure UI); battery sanity — no animation while paused, none at all in
  `OFF`.

## Performance notes (from the source, keep these)

- Never use live full-screen `Modifier.blur` — one-shot CPU blur of a 128px copy, cached.
- One `drawImage` with bilinear filtering beats N radial gradients per frame.
- Decode work on `Dispatchers.Default`, staggered off the transition moment; bitmaps small
  (mesh grid + 128px blur only).
- Freeze animation when paused; respect animator-scale 0.

## Rollback

Single-revert: new file + one `when` in `Player.kt` + additive pref/strings. Default style is
today's `MESH`, so even without revert, behavior for existing users is unchanged unless they
opt in.

## Anti-clone / visual identity

The backdrop is a *capability*, and Audify already claims mesh-gradient identity in its design
system — porting the better engine strengthens that identity rather than borrowing BitChord's
UI. No BitChord layout, controls, or naming appears in Audify's UI: the setting is called
"Player backdrop" with Audify naming for styles. Existing `BitchordStylePlayer.kt` naming is
legacy code and out of scope here.

## Considered and rejected for this plan

- **Porting BitChord's `NowPlayingScreen.kt` layout** (3k lines) — explicitly out of scope;
  Audify keeps its own player layout.
- **Canvas-frame integration** (`FrameHeuristics.kt`/`CanvasArtworkPlayer.kt` feeding the mesh
  from video frames) — depends on canvas artwork support Audify doesn't have; revisit if/when
  canvas lands.
- **AGSL RuntimeShader mesh** — more modern, but minSdk 26 + broad device matrix makes the
  bitmap-draw approach the safer port; shader version can be a later experiment.
