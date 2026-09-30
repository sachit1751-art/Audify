# Plan 018 — Per-network audio quality ceilings

Written against commit **`605975d5`**. Feature plan: self-contained for an executor with zero
session context. Run `git rev-parse --short HEAD` before starting; if the tip has moved past
`605975d5`, check drift in the in-scope files first.

## Goal

Let users set **separate maximum audio quality for Wi-Fi and for mobile data**. Today Audify
has one global `AudioQualityKey`; its `AUTO` value already degrades to LOW on metered
networks inside the InnerTubeX mapping, but any explicit choice (e.g. LOSSLESS at home) is
applied unchanged on data — silently burning a capped plan. After this plan, a user can keep
LOSSLESS on Wi-Fi and HIGH (or LOW) on mobile without ever toggling anything.

Zero behavior change for existing users until they touch the new setting: unset network keys
fall back to today's global key.

## Existing infrastructure (recon at `605975d5`)

- `constants/PreferenceKeys.kt:118-126`: `AudioQualityKey` (string pref) +
  `enum class AudioQuality { AUTO, LOW, HIGH, LOSSLESS }`.
- `utils/InnerTubeXPlayer.kt:261`: `private fun AudioQuality.toInnerTubeX(connectivityManager)` —
  the single mapping point; line 274 already reads `connectivityManager.isActiveNetworkMetered`
  for the AUTO case. **This is the only place the network class is consulted** — perfect choke
  point. Player init (`MusicService`) passes its `connectivityManager` through per resolution.
- The data-source factory in `MusicService.kt` (~line 3800) reads `audioQuality` from
  DataStore and logs `quality=$audioQuality` per fetch; the quality-change cache bypass
  (`bypassCacheForQualityChange`) already exists for "quality changed mid-session" — the same
  mechanism can serve "network class changed mid-session".
- Settings UI: `ui/screens/settings/PlayerSettings.kt` uses `Material3SettingsGroup`
  (lines 298/667/780/1040) with `rememberEnumPreference` rows (audio quality row at ~100).

## In-scope files

| File | Role in this plan |
|---|---|
| `app/src/main/kotlin/com/sachit/music/constants/PreferenceKeys.kt` | `WifiAudioQualityKey`, `MeteredAudioQualityKey` (string prefs, unset by default) |
| `app/src/main/kotlin/com/sachit/music/playback/NetworkQuality.kt` | **New.** Pure resolution function |
| `app/src/main/kotlin/com/sachit/music/utils/InnerTubeXPlayer.kt` | Accept the two resolved values instead of reading the single global key at call sites (or accept a resolver lambda — see step 3) |
| `app/src/main/kotlin/com/sachit/music/playback/MusicService.kt` | Pass the per-network resolution into the data-source factory; optional network-class-change invalidation |
| `app/src/main/kotlin/com/sachit/music/ui/screens/settings/PlayerSettings.kt` | "Audio quality per network" group: two enum rows + explainer |
| `app/src/main/res/values/sachit_strings.xml` | Strings (names below) |
| `app/src/test/java/com/sachit/music/NetworkQualityTest.kt` | **New.** Resolution function tests |

Out of scope: `DownloadUtil.kt` (downloads keep their own Wi-Fi-only logic), WrappedAudioService,
AudioExporter (both keep the global key — they are offline/export jobs), every `values-*` file.

## Design

- **Pure function** (unit-testable, no Android types):

```kotlin
enum class NetworkClass { WIFI, METERED, OFFLINE }

fun resolveAudioQuality(
    networkClass: NetworkClass,
    global: AudioQuality?,      // AudioQualityKey value, null when unset
    wifi: AudioQuality?,        // WifiAudioQualityKey value, null when unset
    metered: AudioQuality?,     // MeteredAudioQualityKey value, null when unset
): AudioQuality
```

Rules: explicit network key wins → fall back to global key → fall back to `AUTO`.
`OFFLINE` returns `global ?: AUTO` (irrelevant in practice — nothing resolves offline —
but keeps the function total).

- **Call-site change**: the mapping `toInnerTubeX(connectivityManager)` becomes
  `toInnerTubeX(resolved: AudioQuality, connectivityManager)` — the AUTO→LOW-on-metered
  special case inside the mapping stays exactly as is (it is about the *streaming client's*
  metered behavior, orthogonal to the user's ceiling).

- **Network-class change mid-session** (polish, optional step 6): observing
  `connectivityManager.registerNetworkCallback`/`registerDefaultNetworkCallback` is already
  done somewhere in the app for the waiting-for-network flow — locate it during drift check;
  if a suitable callback exists, add `songUrlCache` invalidation + `bypassCacheForQualityChange`
  marks on class flips so the *next* track honors the new ceiling immediately. If no suitable
  callback exists, skip: quality applies on next stream resolution, same as BitChord.

## Implementation steps

1. **Drift check**: confirm `toInnerTubeX` signature and the factory call site
   (`MusicService.kt` ~3805, `InnerTubeXPlayer.playerResponseForPlayback` with
   `audioQuality =` argument); confirm PlayerSettings row pattern.
2. **Keys + enum**: add the two string prefs (same pattern as `AudioQualityKey`, unset by
   default — do NOT migrate the global key's value into them; the fallback chain handles it).
3. **`NetworkQuality.kt`**: pure function above + `NetworkClass` mapping from
   `ConnectivityManager` (small `fun ConnectivityManager.networkClass(): NetworkClass` using
   `isActiveNetworkMetered` + active-network-null check — keep the Android-dependent bit
   separate from the pure resolver).
4. **Wire the factory**: replace `audioQuality = <global read>` with
   `audioQuality = resolveAudioQuality(networkClass, global, wifi, metered)`; log the
   resolved value (`quality=$resolved (wifi=$wifi metered=$metered)`).
5. **Settings UI**: new `Material3SettingsGroup` "Audio quality per network" under the
   existing audio-quality row: Wi-Fi row + mobile-data row, each an
   `EnumDialog`-based `rememberEnumPreference` with an explicit **"Same as global"**-style
   unset handling (represent unset as null — either a separate "Use general setting" option
   in the enum dialog or a master switch; prefer the dialog option, one control fewer).
6. **Optional**: network-class-change invalidation (see Design).
7. **Build + tests**: `./gradlew :app:assembleFossDebug --console=plain` (JAVA_HOME per
   AGENTS.md); run `NetworkQualityTest`.

## Testing

- **Unit** (`NetworkQualityTest`): wifi-set/metered-unset → metered falls to global; both set →
  each wins; both unset → global; all unset → AUTO; AUTO + metered → AUTO (the mapping's own
  metered special-case handles degradation — assert resolver returns AUTO, not LOW).
- **Manual**: set Wi-Fi=LOSSLESS, data=LOW; verify `quality=` log line flips when toggling
  airplane/Wi-Fi; confirm no quality change mid-song (applies at next resolution); global-key
  users see zero change without touching the new group.

## Performance notes

One pref read per stream resolution (cached prefs pattern in the factory); no polling, no
listeners required for the core feature. Optional step 6 adds a system callback already
patterned elsewhere in the app.

## Rollback

Revert single commit: new file + additive prefs + one call-site expression. Unset-by-default
keys mean pre-feature behavior is the fallback chain's bottom rung.

## Strings (exact names)

`per_network_quality`, `per_network_quality_desc`, `quality_on_wifi`, `quality_on_metered`,
`quality_same_as_global`.
