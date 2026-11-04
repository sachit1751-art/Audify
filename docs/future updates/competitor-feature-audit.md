# Audify Competitor Feature Audit

**Prepared:** 2 November 2026
**Scope:** Feature comparison of Audify against InterTune, AsterTune, Metrolist, and ArchiveTune.
**Purpose:** Identify capabilities Audify could adopt or improve, while avoiding duplication of features already present or planned.

> **Audit limitation:** This is a feature-level review based on the competitors' public repositories, READMEs, release notes, and official project site, compared with the Audify project context and documented feature set. It is not a line-by-line code audit, security review, licensing review, or independent verification that every advertised competitor feature works as described. Audify statuses marked "verify" require a source-level or runtime check before implementation decisions.

## 1. Executive summary

The four projects suggest three promising directions for Audify:

1. **Audify Intelligence:** listening-aware recommendations, explainable picks, discovery controls, and music recognition.
2. **Audify Studio:** configurable player layouts, backgrounds, gestures, haptics, and refined animation controls.
3. **Audify Library:** stronger metadata extraction, complete listening history, backups, playlist portability, and library repair.

Audify already has a substantial playback and ecosystem foundation. The opportunity is not to copy every competitor feature; it is to build a coherent experience around intelligence, personalization, and library reliability.

## 2. Comparative feature matrix

| Feature | InterTune | AsterTune | Metrolist | ArchiveTune | Audify status |
|---|---|---|---|---|---|
| Local music playback and library | Yes | Yes | Yes | Yes | **Present** — core local playback exists |
| YouTube Music integration | Yes | Yes | Yes | Yes | **Present / verify details** — existing source integration; review current provider implementation separately |
| Learning-based recommendations | Yes — Quick Picks learn from listening signals | Not a central documented feature | Yes — Quick Picks | Discovery/personalization features | **Gap to investigate** — build on existing home/discovery shelves rather than duplicate them |
| Explanations for recommendations | Yes | Not documented | Not documented | Not documented | **Gap** |
| Familiarity / adventurousness controls | Yes | Not documented | Not documented | Not documented | **Gap** |
| Music recognition / Shazam-style lookup | Yes | Not documented | Not documented | Yes | **Gap** |
| Recognition history and auto-playlist addition | Yes | Not documented | Not documented | Recognition is listed; workflow needs verification | **Gap** |
| Similar-track discovery via Last.fm | Yes | Not documented | Last.fm integration | Last.fm integration | **Partial** — Last.fm exists; verify whether track-similarity recommendations are implemented |
| Custom metadata extraction and advanced tags | Not a primary focus | Yes | Not a primary focus | Local-media reconciliation features | **Gap to investigate** — assess MediaStore limitations and current tag handling |
| Synced lyrics | Yes | Yes; multiple providers | Yes | Yes | **Present** — synced lyrics exist |
| Lyrics editing / importing | Yes | Yes | Not clearly documented | Lyrics features documented | **Partial / verify** |
| Lyrics translation | Not clearly documented | Provider ecosystem | AI translation | Translation / AI translation | **Partial / verify** — translation foundation exists; AI translation is not confirmed |
| Lyrics romanization | Yes | Not clearly documented | Not clearly documented | Yes | **Gap / verify** |
| Equalizer and audio effects | Ten-band EQ, AutoEQ, spatial audio | Audio normalization | EQ, normalization, tempo/pitch, crossfade | System EQ, spatial audio, normalization | **Partial** — audio pipeline and system-EQ foundations exist; AutoEQ and advanced headphone profiles are not confirmed |
| Tempo and pitch controls | Not a primary documented differentiator | Not documented | Yes | Yes | **Partial / verify** — Audify has tempo-stretching and beat-match foundations; inspect independent pitch controls |
| Crossfade and sleep timer | Not a central differentiator | Sleep timer with fade | Yes | Yes | **Present** — existing playback features |
| Listening history and statistics | Full history and on-device stats | Not a central focus | Not a central focus | Listening statistics | **Partial** — weekly recap and monthly aggregates exist; complete history remains a separate capability |
| Automatic backups | Yes | Not documented | Not documented | Google Drive backup | **Gap / planned** — backup work is already represented in the Audify roadmap |
| Playlist import / portability | Multi-service import and CSV | Not a central focus | Spotify import ecosystem | Spotify import and CSV export | **Partial / planned** — playlist importer is already on the Audify roadmap |
| Last.fm / ListenBrainz / Discord | Last.fm | Not central | Last.fm / Discord | Last.fm / ListenBrainz / Discord | **Present** — Audify already has these integrations |
| Listen Together / social listening | Not a central focus | Not documented | Listen Together | Community features | **Present** — Audify already has Listen Together |
| Home widgets | Yes | Not a central focus | Yes | Yes | **Present** |
| Android Auto | Yes | Yes | Not consistently documented | Yes | **Present** |
| Chromecast / casting alternatives | Chromecast, FCast, DLNA | Not a central focus | Not a central focus | Casting ecosystem needs verification | **Verify** — do not assume FCast or DLNA support |
| Player themes, layouts, backgrounds | Material You / liquid-glass styling | Accent colors and responsive layouts | Material styling | Multiple player and background styles | **Partial** — animated artwork and dynamic surfaces exist; extensive player-style customization is a gap |
| Gesture customization | Not a central focus | Not documented | Not documented | Yes | **Gap** |
| Haptics and Always-On Display | Not a central focus | Not documented | Not documented | Yes | **Gap / verify** |
| Podcasts and episode discovery | Not documented | Not documented | Not documented | Yes | **Gap** |
| Stream preloading and playback recovery | Not a central focus | Not documented | Not documented | Yes | **Gap to investigate** — audit existing caching, fallback, and buffering behavior first |
| Tablet / landscape experience | Not a central focus | Yes | Not a central focus | Responsive layouts | **Partial / planned** — a two-pane tablet plan exists |
| Android TV and Wear OS variants | Not a central focus | Not a central focus | Not a central focus | Not a central focus | **Audify strength** — separate TV and Wear OS variants are already built |

### Status definitions

- **Present:** the capability is part of Audify's known feature set.
- **Partial:** some of the capability or underlying foundation exists, but the competitor's full workflow is not confirmed.
- **Gap:** no equivalent capability is established in the reviewed Audify context.
- **Planned:** an Audify roadmap item already covers the capability; extend or reconcile that plan rather than creating a duplicate.
- **Verify:** available evidence is insufficient to make a confident claim about the current implementation.

## 3. Competitor-by-competitor findings

### 3.1 InterTune

**Repository:** https://github.com/ItzSkyeYT/InterTune

InterTune combines local music playback with YouTube Music and puts particular emphasis on personalized discovery and recognition.

Key documented capabilities:

- **Learning Quick Picks:** recommendations adapt to listening behavior such as likes, skips, searches, and completed plays.
- **Explainable recommendations:** the app can explain why a track was selected.
- **Discovery controls:** familiarity/adventurousness settings, context or mood chips, and a never-played discovery row.
- **Music recognition:** Shazam-style recognition, background listening, recognition history, and the ability to add recognized songs to a playlist.
- **Audio customization:** ten-band equalizer, presets, AutoEQ headphone profiles, spatial audio, head tracking, and Bluetooth walk-away volume behavior.
- **External services and portability:** Chromecast, FCast, DLNA, and playlist import from multiple music services and CSV.
- **Library and lyrics:** listening history, backups, synced lyrics, lyrics search/editing, and romanization.
- **Additional experience features:** widgets, Android Auto, low-data mode, codec/bitrate details, and animated visual styling.

**What Audify should learn from it:** the strongest opportunity is an explainable recommendation system that learns from real listening behavior. Music recognition is another substantial differentiator. Avoid copying its entire feature surface before Audify's existing playback and library workflows are fully reconciled.

### 3.2 AsterTune

**Repository:** https://github.com/yuuichi-s/AsterTune

AsterTune focuses on local-library correctness, metadata, and lyrics.

Key documented capabilities:

- Custom metadata extraction rather than relying only on Android MediaStore.
- Support for richer and multi-value tags.
- Local browsing across songs, albums, artists, and playlists, with search and filtering.
- MP3, OGG, FLAC, and ALAC support.
- Local files and YouTube Music tracks in a shared queue.
- Synchronized lyrics in formats such as LRC and TTML.
- Multiple lyrics providers queried in parallel, with import and editing workflows.
- Local audio normalization.
- Sleep-timer fade-out and Wi-Fi-only download controls.
- Tablet and landscape layout improvements, plus custom accent colors.

**What Audify should learn from it:** robust metadata extraction can improve the accuracy of the entire local library: artist/album grouping, sorting, search, and artwork association. Its multi-provider lyrics approach is also worth studying, especially provider fallback and matching quality.

### 3.3 Metrolist

**Repository:** https://github.com/MetrolistGroup/Metrolist

Metrolist documents a broad playback and discovery feature set:

- Tempo and pitch controls, including linked tempo/pitch behavior.
- Audio normalization, equalizer, crossfade, skip silence, and sleep timer.
- AI-powered lyrics translation.
- Personalized Quick Picks.
- Search across songs, albums, artists, videos, and playlists.
- Listen Together, Last.fm, Discord Rich Presence, and home widgets.

**Project status:** the repository describes itself as being in maintenance mode, focusing on bug fixes and minor improvements rather than accepting major new feature work.

**What Audify should learn from it:** use Metrolist as a reference for playback controls and discovery behavior, not as the primary upstream target for new major features. Audify already overlaps with several of its integrations and playback capabilities.

### 3.4 ArchiveTune

**Original URL supplied:** https://github.com/koiverse/ArchiveTune
**Maintained repository:** https://github.com/rukamori/ArchiveTune
**Official site:** https://archivetune.miuka.moe/

The supplied repository redirects to the maintained ArchiveTune project. It has a broad feature surface spanning playback, customization, integrations, and content discovery.

Key documented capabilities:

- Multiple accounts, local files, and YouTube Music.
- EBU R128 loudness normalization, tempo/pitch/speed controls, crossfade, and system audio effects.
- Synchronized lyrics, translation, AI translation, and romanization.
- Music recognition and listening statistics.
- Spotify playlist import and CSV playlist export.
- Last.fm, ListenBrainz, Discord Rich Presence, and Listen Together.
- Multiple player styles and background styles, dynamic color, responsive layouts, and gesture customization.
- Podcasts, episode search, and community discovery.
- SponsorBlock, Google Drive backup, and Spotify/TIDAL Canvas.
- Stream preloading and playback fallback improvements.
- Android Auto, Always-On Display, haptic feedback, low-data mode, and widgets.

The official site and recent release notes also describe continued development around community content, podcast support, Android Auto, Canvas capture, local-media reconciliation, Browse, notifications, and playback reliability.

**What Audify should learn from it:** ArchiveTune is the strongest reference for a configurable player experience and a broad integration ecosystem. Its feature breadth is not a reason to implement everything; prioritize coherent workflows and reliability over feature count.

## 4. What Audify already has

Based on the reviewed Audify project context, these are established strengths or existing foundations:

- Local music playback, downloads, and core library workflows.
- Automix, beat matching, and tempo stretching.
- Crossfade, sleep timer, skip silence, audio quality controls, and audio diagnostics.
- Audio normalization and system-equalizer/audio-pipeline foundations.
- Synchronized lyrics and a translation foundation.
- Last.fm, ListenBrainz, Discord Rich Presence, and Listen Together.
- Weekly recap, monthly statistics aggregates, and statistics export.
- Home widgets and Android Auto.
- Spotify Canvas foundation.
- Animated artwork, artwork-driven colors, translucent surfaces, and progressive scroll-edge blur.
- Separate Android phone, Android TV, and Wear OS variants.

Some related capabilities remain unverified or incomplete: complete play history, full AI lyrics translation, romanization, advanced headphone AutoEQ, broad casting protocols, and robust metadata extraction.

## 5. Recommended feature opportunities

The following are opportunities, not authorization to implement. Existing Audify plans must be checked before creating new plan files or changing code.

| Priority | Opportunity | User value | Effort | Recommendation |
|---:|---|---|---|---|
| 1 | Learning recommendation engine with explanations | Very high | High | Build on existing discovery shelves and listening signals; make recommendations explainable and controllable |
| 2 | Music recognition and recognition history | Very high | High | Add a deliberate recognition flow first; consider background recognition only after privacy, battery, and permissions are designed |
| 3 | Player Studio: layouts, backgrounds, gestures, animation controls | Very high | High | Differentiate Audify through customization without compromising playback usability or accessibility |
| 4 | Professional local-library metadata handling | High | High | Improve tag extraction, multi-value metadata, grouping, and matching |
| 5 | Advanced audio and headphone profiles | High | High | Evaluate AutoEQ and per-output profiles against the current audio pipeline |
| 6 | Complete listening history | High | Medium–high | Expand beyond recaps and monthly aggregates; reconcile with the existing roadmap |
| 7 | Better lyrics provider management, translation, and romanization | High | Medium | Improve matching, provider fallback, and user correction/import workflows |
| 8 | Additional casting protocols | Medium–high | High | Investigate FCast/DLNA only after confirming current Cast architecture and maintenance cost |
| 9 | Podcast library and episode discovery | Medium | High | Treat as a separate product surface, not a small music-player add-on |
| 10 | Stream preloading and playback recovery | High | Medium–high | Measure current buffering/fallback failure modes before designing changes |

### Three coherent product pillars

#### Audify Intelligence
- Listening-aware recommendations.
- Clear reasons behind recommendations.
- Familiarity and discovery controls.
- Never-played and similar-track discovery.
- Music recognition and recognition history.

#### Audify Studio
- Multiple player layouts and background treatments.
- Custom gesture actions.
- Animation intensity and motion preferences.
- Haptic feedback controls.
- Audio profiles and per-output preferences.

#### Audify Library
- Stronger metadata and tag extraction.
- Complete listening history.
- Automatic backups and restore.
- Playlist import/export portability.
- Library and download health/repair workflows.

## 6. Existing Audify roadmap overlap

Do not create duplicate plans for capabilities already represented in the Audify roadmap. The reviewed plan index includes work covering:

- Named queue slots.
- Playlist importing.
- Full listening history.
- Automatic backups.
- Library cleanup and local music library improvements.
- Download repair and smarter queue management.
- Audio presets per output.
- Playlist health checks and source-fallback transparency.
- Automix controls.
- Tablet two-pane interface.
- Accessibility-first lyrics.
- Contextual player actions.
- Advanced search filters and statistics dashboard.

Before implementation, inspect the current plan files and code to confirm which items remain active, completed, reverted, or superseded. A plan's presence in the index does not prove the feature is implemented.

## 7. Suggested implementation order

1. **Audit the existing recommendation and discovery code.** Identify current shelves, ranking logic, mood/context inputs, and available listening signals.
2. **Design Audify Intelligence as an incremental system.** Start with transparent rules and explanations before introducing a complex learning model.
3. **Reconcile library and history roadmap items.** Avoid duplicating plans 052–058 and related work; confirm current status from source.
4. **Prototype Player Studio after defining constraints.** Preserve existing navigation, accessibility, reduced-motion behavior, and playback controls.
5. **Evaluate recognition and advanced audio separately.** These introduce external-service, permission, battery, compatibility, and maintenance considerations.

## 8. Sources

The following are the primary public project sources reviewed for this comparison:

- InterTune repository: https://github.com/ItzSkyeYT/InterTune
- InterTune releases: https://github.com/ItzSkyeYT/InterTune/releases
- AsterTune repository: https://github.com/yuuichi-s/AsterTune
- AsterTune releases: https://github.com/yuuichi-s/AsterTune/releases
- Metrolist repository: https://github.com/MetrolistGroup/Metrolist
- Metrolist releases: https://github.com/MetrolistGroup/Metrolist/releases
- ArchiveTune maintained repository: https://github.com/rukamori/ArchiveTune
- ArchiveTune releases: https://github.com/rukamori/ArchiveTune/releases
- ArchiveTune official site: https://archivetune.miuka.moe/

---

**Conclusion:** Audify does not need to match every competitor feature. Its strongest next move is to combine explainable discovery, a highly configurable player, and a dependable local library—while protecting the playback reliability and cross-device support it already has.
