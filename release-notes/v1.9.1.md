# Audify 1.9.1

Audify 1.9.1 is a cross-platform maintenance release focused on reliability, continuous music discovery, library workflows, and consistent playback across Android and Windows desktop.

## What's new

- **Continuous Home feed:** Restored automatic continuation while scrolling. Audify requests and appends additional recommendation shelves as the user approaches the end of the loaded feed, displays loading feedback, and guards against duplicate shelves and repeated continuation tokens.
- **Smart Playlists:** Improved the candidate catalogue by including local tracks, downloaded tracks, listening history, favourites, and listening aggregates. Refreshing reloads the source data, and generated results are deduplicated by track identity.
- **Library experience:** Refined Smart Playlist loading, empty-library, and refresh behavior while preserving saved playlist definitions.
- **Playback and navigation:** Fixed the Android minimized-player return path that could briefly summon and dismiss the keyboard, and refined player-control sizing, icon alignment, and interaction consistency.
- **Home presentation:** Restored the v1.9.0 Home metadata parsing and rendering baseline after the 1.9.1 metadata regression investigation. Generic source labels are not presented as artist credits.
- **Interface consistency:** Improved dialog, sheet, settings, browse, and detail-screen loading, empty, error, and retry states.
- **Explicit-content controls:** Added catalogue-backed `E` markers to supported song, album, and playlist surfaces, plus a persistent option to hide positively labelled explicit catalogue entries. Unknown or unlabelled items remain visible.
- **Player feedback:** Added an artwork-coloured heart-and-sparkle burst when a track is liked from the player.
- **Settings navigation:** Removed the repeated Settings heading inside category pages and made the in-page return path clearer. The top-bar Back action returns to the Settings category index before leaving Settings.
- **Loading feedback:** Extended shimmer skeletons to Spotify playlist loading and listening-statistics refreshes, replacing generic centered progress indicators with layout-matched placeholders.
- **Windows desktop:** Carries the desktop client forward with the 1.9.1 package version and refreshed release packaging.

## Android downloads

- **Phone / tablet (universal):** `Audify-1.9.1.apk` — universal APK containing supported ABIs.
- **Phone / tablet (ARM64):** `Audify-1.9.1_arm64-v8a.apk` — smaller device-specific APK.
- **Android TV / Google TV:** `AudifyTV-1.9.1.apk` — TV-specific application.
- **Wear OS:** `AudifyWear-1.9.1.apk` — companion application; install alongside a compatible phone build signed with the same release identity.

## Windows desktop

- **Windows x64 installer:** `Audify-Setup.exe`.
- The installer is intentionally unsigned with Authenticode. Windows may display an unrecognized-publisher warning; no self-signed certificate is represented as trusted.

## Compatibility and release notes

- Android package version: **1.9.1** (version code **40**).
- Phone and Wear OS release APKs use the approved Audify Android release signing identity.
- Linux and macOS desktop installers are not included in this release.
- A guest YouTube Music session may return a different Home feed from an authenticated account; personalized feed contents depend on the account and service response.

## Feedback and bug reports

- **Bug reports:** https://github.com/sachit1751-art/Audify/issues/new?template=bug_report.yml
- **General tester feedback:** https://github.com/sachit1751-art/Audify/issues/new?template=tester-feedback.yml
- **All releases:** https://github.com/sachit1751-art/Audify/releases
