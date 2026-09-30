# Audify Competitor Feature Matrix

> **What this document is:** the research-and-selection reference for Audify's feature work.
> **Goal:** decide *which* capabilities are worth building by comparing Audify against every serious competitor, from primary sources.
> **Contains:** verified competitor states · a 10-column feature matrix (~50 rows, ✅/⚠️/❌/⭐) · 48 candidate features narrowed to 24 selected (with reasons) · 10 explicitly rejected (with reasons) · numbered source table.
> **Result / how to use:** the 24 selected items feed `AUDIFY_ROADMAP.md`, which sequences them; individual `plans/NNN-*.md` files turn each into an executable spec. Nothing here is implemented — this is decision material, not an achievement log.
> **Research date:** 30 September 2026 · **Method:** primary sources (GitHub repos, official product pages, vendor newsrooms) + BitChord v1.7 source audited locally at `reference-player-sdk/`.

## 1. Competitors inspected (primary sources)

| Project | State (verified) | Key source |
|---|---|---|
| **BitChord** | Active, v1.7 (Sep 2026). GPL-3.0. Lossless module sources, automix DSP, SMB/WebDAV, stats-for-nerds, ListenBrainz, canvas art | Local clone + git log v1.6..v1.7 |
| **InnerTune** | Upstream, Material 3. Ads-free playback, offline cache/downloads, synced lyrics + translator, tempo/pitch, dynamic theme, Android Auto, Discord RP. Scrobbling deliberately delegated to external apps | github.com/z-huang/innertune README |
| **OuterTune** | **Development ended** (maintainer handoff notice). InnerTune fork: local file playback + YTM in one library, custom tag extractor, multiple queues, word-by-word TTML/LRC lyrics | github.com/OuterTune/OuterTune README |
| **SimpMusic** | Active, Compose Multiplatform (Android + Desktop). 3 player styles, 10-band EQ + AutoEq profiles, reverb/delay, SponsorBlock, ReturnYouTubeDislike, AI suggestions, lyrics share-as-image, landscape lyrics, QR sign-in handoff to desktop, preferred audio language, canvas art, monthly recap | github.com/maxrave-dev/SimpMusic README |
| **RiMusic** | **Closed/archived** (Feb 2025 per community; repo states "This project is closed"). ViMusic extension: mic-permission visualizer, listening stats, playlist share/import-export, heavy theming | github.com/fast4x/RiMusic README |
| **ViMusic** | Effectively dead; survived via forks (RiMusic). Concept: lightweight YTM stream client | RiMusic README lineage |
| **Metrolist** | Maintenance mode. InnerTune/OuterTune lineage: crossfade, Listen Together, Last.fm, 19 preset palettes, AI lyrics translation, playlist import | github.com/metrolistgroup/metrolist README |
| **Musicolet** | Active local player (no internet permission). Multiple named queues with resume, folder browsing, per-output EQ presets, tag editor+, synchronized-lyrics editor, bookmarks/notes, home-screen shortcuts, CSV play-count export, queue merge/replace on conflict | Play Store listing (Krosbits), changelog v6.13–6.14 |
| **Poweramp** | Commercial hi-res local player. Gapless, crossfade, ReplayGain, DSP stack, skins | powerampapp.com |
| **YouTube Music** | Official: **Ask Music** conversational control (Sep 2026), podcast lineup AI guide, queue customization, speed-dial pins, hum-based sound search | blog.youtube (Sep 23, 2026) |
| **Spotify** | Official: AI DJ (4 new languages May 2026), daylist, Jam, AI playlists | newsroom.spotify.com |

## 2. Feature matrix

Legend: ✅ exists · ⚠️ partial · ❌ missing · ⭐ particularly interesting implementation. Audify column audited from this repo's source (not its README).

| Feature | Audify | BitChord | InnerTune | OuterTune | SimpMusic | RiMusic | Metrolist | Musicolet | Poweramp | YTM / Spotify |
|---|---|---|---|---|---|---|---|---|---|---|
| Ad-free YTM streaming | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ (local only) | ❌ | ❌ |
| Background playback + media session | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Downloads with metadata/artwork embedding | ✅ | ✅ ⭐ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ❌ | ❌ | ⚠️ (Premium) |
| Offline player cache | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ⚠️ |
| Local file library + YTM in one app | ❌ | ⚠️ | ❌ | ✅ ⭐ | ⚠️ | ❌ | ❌ | ✅ | ✅ | ❌ |
| Multiple independent named queues w/ resume | ❌ | ❌ | ❌ | ⚠️ | ❌ | ❌ | ❌ | ✅ ⭐ | ⚠️ | ❌ |
| Queue history scrollback + skip marks | ❌ | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ (named queues) | ⚠️ | ⚠️ |
| Crossfade (configurable) | ✅ | ✅ ⭐ DJ-style | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| Beat-matched automix / DJ transitions | ✅ ⚠️ | ✅ ⭐ (native DSP) | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ⚠️ |
| Gapless | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Skip silence | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ✅ | ⚠️ | ✅ | ✅ |
| Playback speed/tempo-pitch | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ⚠️ |
| Volume normalization | ✅ (perceptual) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ (ReplayGain) | ✅ ("consistent volume") |
| System EQ | ✅ | ✅ | ⚠️ | ✅ | ✅ ⭐ (10-band + AutoEq, reverb/delay) | ⚠️ | ✅ | ✅ ⭐ (per-output) | ✅ ⭐ | ⚠️ |
| Audio quality selection | ✅ | ✅ ⭐ per-network + lossless modules | ⚠️ | ⚠️ | ⚠️ (256k Premium) | ⚠️ | ⚠️ | n/a | ⚠️ (bit-perfect) | ✅ |
| Lossless source (FLAC/ALAC) | ✅ ⚠️ (module, plan 016) | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ (local files) | ✅ | ✅ (Premium) |
| Per-network (Wi-Fi vs metered) quality | ❌ | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ | n/a | ❌ | ✅ |
| In-player stream diagnostics ("stats for nerds") | ⚠️ (menu dialog) | ✅ ⭐ | ❌ | ❌ | ⚠️ | ⚠️ | ❌ | ❌ | ⚠️ | ✅ ⭐ ("stats for nerds") |
| Word-by-word lyrics | ✅ | ✅ | ✅ | ✅ ⭐ (TTML) | ✅ ⭐ | ✅ | ✅ | ⚠️ (manual LRC) | ❌ | ✅ |
| Lyrics translation / romanization | ✅ | ✅ | ✅ | ⚠️ | ✅ ⭐ (12 langs, AI) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Lyrics share as image | ❌ | ❌ | ❌ | ❌ | ✅ ⭐ | ⚠️ | ❌ | ❌ | ❌ | ❌ |
| Multiple lyrics providers + manual timing offset | ✅ | ✅ | ⚠️ | ✅ | ✅ ⭐ | ✅ | ⚠️ | ✅ (offline) | ⚠️ | ❌ |
| SponsorBlock integration | ❌ | ❌ | ❌ | ❌ | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Return YouTube Dislike | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Sleep timer | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ⭐ (N-songs mode) | ✅ | ✅ |
| Scrobbling Last.fm | ✅ | ✅ | ❌ (by design) | ⚠️ | ✅ (Full) | ⚠️ | ✅ | ❌ | ⚠️ | ❌ |
| Scrobbling ListenBrainz | ❌ | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Discord Rich Presence | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Listen Together rooms | ✅ | ✅ (self-hosted server) | ❌ | ❌ | ✅ ⭐ (Metrolist-compatible) | ❌ | ✅ | ❌ | ❌ | ❌ (Jam: Premium) |
| Music recognition (Shazam-style) | ✅ ⭐ | ⚠️ | ❌ | ❌ | ⚠️ | ❌ | ⚠️ (lib credit) | ❌ | ❌ | ✅ (hum/search) |
| Home-screen widgets | ✅ | ✅ | ✅ | ✅ | ✅ ⭐ (turntable/insights) | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| Android Auto | ✅ | ✅ | ✅ | ✅ | ✅ ⭐ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Android TV | ❌ | ⚠️ | ❌ | ❌ | ⚠️ | ✅ | ❌ | ❌ | ❌ | ✅ |
| Desktop/companion app | ❌ | ❌ | ❌ | ❌ | ✅ ⭐ (QR sign-in handoff) | ⚠️ | ❌ | ❌ | ❌ | ✅ |
| SMB/WebDAV remote sources | ❌ | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ (folders/SD) | ⚠️ | ❌ |
| Backup & restore | ✅ | ✅ | ⚠️ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ ⭐ (auto) | ✅ | ⚠️ (cloud) |
| Listening stats / recap | ✅ ⭐ (wrapped + recap) | ⚠️ | ⚠️ | ⚠️ | ✅ ⭐ (charts, listening clock) | ✅ | ⚠️ | ✅ ⭐ (CSV export) | ⚠️ | ✅ (Recap) |
| Smart auto-playlists | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ (playlists by folder) | ❌ | ✅ |
| AI conversational requests ("Ask Music" style) | ❌ | ❌ | ❌ | ❌ | ✅ ⚠️ (suggestions) | ❌ | ❌ | ❌ | ❌ | ✅ ⭐ |
| AI playlist generation | ❌ | ❌ | ❌ | ❌ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Podcasts | ✅ ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ | ❌ | ⚠️ | ❌ | ❌ | ✅ |
| Video playback (1080p + subtitles) | ⚠️ (video songs) | ✅ | ⚠️ | ⚠️ | ✅ ⭐ | ⚠️ | ⚠️ | ❌ | ❌ | ✅ |
| Preferred audio language (multi-track) | ❌ | ❌ | ❌ | ❌ | ✅ ⭐ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Playlist import from other services | ⚠️ (CSV/M3U) | ⚠️ | ❌ | ⚠️ | ✅ ⭐ (Spotify-converted) | ✅ | ✅ | ❌ | ❌ | ✅ |
| Playlist share as link/file | ⚠️ | ✅ | ⚠️ | ⚠️ | ✅ | ✅ ⭐ | ✅ | ⚠️ | ❌ | ✅ |
| Canvas / animated artwork | ✅ ⚠️ (visualizer) | ✅ ⭐ | ⚠️ (dynamic theme) | ⚠️ | ✅ ⭐ (Spotify canvas) | ⚠️ | ⚠️ | ❌ | ❌ | ✅ |
| Highly customizable UI/themes | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ (3 player styles) | ✅ ⭐ | ✅ (19 palettes) | ⚠️ | ✅ ⭐ (skins) | ❌ |
| Landscape fullscreen lyrics | ⚠️ | ⚠️ | ⚠️ | ✅ | ✅ ⭐ | ⚠️ | ⚠️ | ⚠️ | ❌ | ⚠️ |
| Artist notification/new-release alerts | ❌ | ❌ | ⚠️ | ⚠️ | ✅ ⭐ | ⚠️ | ❌ | ❌ | ❌ | ✅ |
| Alarm/quick tiles/recognition extras | ✅ ⭐ (alarm + tiles) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ | ❌ |
| Bookmarks/notes on songs | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ ⭐ | ❌ | ❌ |
| Tag editor | ❌ | ❌ | ❌ | ✅ ⭐ (custom extractor) | ⚠️ | ❌ | ❌ | ✅ ⭐ | ✅ | ❌ |
| Update checker | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | n/a |

## 3. Candidate pool (48 candidates → 24 selected)

### Selected for Audify (24)

1. **Per-network quality ceilings** (BitChord) — Wi-Fi vs metered quality; most practical data-saver missing from Audify.
2. **Queue history + skip marks** (BitChord; Musicolet's named-queue philosophy as a cousin) — the single most requested BitChord feature (issue #200).
3. **In-player stream diagnostics** (BitChord/YTM) — live codec/bitrate/buffer panel; Audify has the data, hides it.
4. **Source health + fallback memory** (BitChord `PlaybackFallback` + issue lessons) — stop re-trying dead sources.
5. **Local file library beside YTM** (OuterTune/Musicolet) — OuterTune died but its feature was its identity; Audify already has `LocalAlbumRadio` plumbing.
6. **ListenBrainz scrobbling** (BitChord) — open protocol; Musicolet-style "no lock-in" ethos.
7. **SponsorBlock** (SimpMusic) — skip in-video sponsor segments; unique among YTM clients besides SimpMusic.
8. **Lyrics share-as-image** (SimpMusic) — viral, low-cost, identity-building.
9. **10-band EQ + AutoEq profiles** (SimpMusic) — Audify has custom DSP; add preset profiles + headphone correction.
10. **Multiple named queues with resume** (Musicolet ⭐) — genuinely differentiating in streaming clients; nobody in the YTM-client space has it.
11. **Bookmarks/notes + timestamps** (Musicolet ⭐) — "mark the moment" for podcast/music hybrid use.
12. **Artist new-release notifications** (SimpMusic/YTM) — pull-to-push discovery.
13. **Preferred audio language for multi-track videos** (SimpMusic ⭐) — niche but zero-competition.
14. **Playlist import beyond CSV/M3U** (SimpMusic/Metrolist) — Spotify-converted imports.
15. **Tablet/two-pane player layout** (BitChord v1.7 `e96ac9b`) — window-size-class aware.
16. **Landscape fullscreen lyrics** (SimpMusic/OuterTune) — Audify has portrait; complete the story.
17. **TV intent support** (RiMusic legacy) — lean-back playback via existing Auto infrastructure.
18. **Tag editor for downloads** (OuterTune/Musicolet) — fix metadata post-download.
19. **Download quality upgrade + storage budget** (BitChord download tier work + Musicolet backups) — manage "how much offline".
20. **Stats export (CSV)** (Musicolet v6.13) — data portability, cheap to build on wrapped DB.
21. **Conversational queue requests ("Ask Audify")** (YTM ⭐) — Audify already has AiSettings + Shazam/recognition; a local, privacy-safe prompt→queue path.
22. **AutoEq import UX for Bluetooth outputs** (SimpMusic ⭐ + Poweramp) — per-device correction.
23. **WebDAV remote source** (BitChord, WebDAV-only scope) — BitChord's SMB issues (#413) argue for HTTP-first.
24. **Backup auto-scheduling + integrity check** (Musicolet ⭐) — extend existing BackupAndRestore.

### Not selected (with reason)

- **Android TV full app** (RiMusic) — large surface, small audience; TV intent (#17) covers the lean-back case.
- **Desktop app** (SimpMusic) — out of scope for an Android product; revisit only after Wear.
- **AI DJ voice host** (Spotify) — costs + identity mismatch; conversational queue (#21) is the useful 10%.
- **Canvas video artwork** (SimpMusic) — licensing/throughput cost; Audify's visualizer is already its identity.
- **Skins/theming free-for-all** (Poweramp/RiMusic) — maintenance sink; dynamic color + palettes covers it.
- **SMB** (BitChord) — deferred behind WebDAV; BitChord's own v1.7 guest-login bug cluster shows the cost.
- **Return YouTube Dislike** (SimpMusic) — brittle third-party API, low value.
- **1080p video + subtitles** (SimpMusic) — Audify is a music-first client; video songs suffice.
- **Multi-account YTM** (SimpMusic) — auth complexity vs tiny audience.
- **"Local-first no-network mode"** (Musicolet philosophy) — Audify is a streaming client by design; offline paths already exist.

## 4. Sources

| # | Title | URL | Quality | Accessed |
|---|-------|-----|---------|----------|
| 1 | InnerTune README (features) | https://github.com/z-huang/innertune | primary | 2026-09-30 |
| 2 | OuterTune README (development-ended notice, features) | https://github.com/OuterTune/OuterTune | primary | 2026-09-30 |
| 3 | SimpMusic README (features, privacy, desktop) | https://github.com/maxrave-dev/SimpMusic | primary | 2026-09-30 |
| 4 | RiMusic README (closed project, features) | https://github.com/fast4x/RiMusic | primary | 2026-09-30 |
| 5 | Metrolist README (maintenance mode, features) | https://github.com/metrolistgroup/metrolist | primary | 2026-09-30 |
| 6 | Musicolet Play Store listing (features, changelog) | https://play.google.com/store/apps/details?id=in.krosbits.musicolet | primary | 2026-09-30 |
| 7 | Poweramp official site (gapless/crossfade/ReplayGain) | https://powerampapp.com/ | primary | 2026-09-30 |
| 8 | YouTube blog: Ask Music, podcast lineup (2026-09-23) | https://blog.youtube/news-and-events/made-on-youtube-music-new-discovery-features/ | primary | 2026-09-30 |
| 9 | Spotify newsroom: DJ expansion 4 languages (2026-05-07); daylist (2024-09-04); AI playlists (2024-04-07) | https://newsroom.spotify.com/2026-05-07/dj-expansion-4-new-languages/ | primary | 2026-09-30 |
| 10 | BitChord v1.7 source + git history (local read-only checkout) | `reference-player-sdk/` | primary | 2026-09-30 |
| 11 | GrapheneOS forum: RiMusic discontinued report (corroboration for #4) | https://discuss.grapheneos.org/d/20339-rimusic-has-become-unuseable | secondary | 2026-09-30 |
| 12 | OuterTune "State of the 'Tune" discussion #1116 (corroboration for #2) | https://github.com/OuterTune/OuterTune/discussions/1116 | primary | 2026-09-30 |

**Quality distribution:** 11 primary · 1 secondary · 0 tertiary — healthy. Dead-project statuses are double-confirmed (#4+#11, #2+#12) [VERIFIED]; all feature claims are read from project READMEs [SOURCED]; Audify's own column is from local source inspection [SOURCED].
