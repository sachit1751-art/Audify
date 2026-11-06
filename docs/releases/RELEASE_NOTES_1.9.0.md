# Audify 1.9.0

Audify 1.9.0 expands the project across Android phones, Android TV, Wear OS, and Windows desktop. It adds a more capable local listening and playlist experience, Android Auto media browsing, a typed playback foundation for podcast episodes, and a refreshed first-run experience.

## What's new

- **Windows desktop support:** Audify is now available as a native desktop app for Windows PCs, with a dedicated installer and desktop-sized interface.
- **One Audify project across devices:** The desktop app joins the Android phone, Android TV, and Wear OS builds. Each platform has an interface suited to its form factor.
- **Desktop app identity:** Added the Audify Windows application icon and aligned desktop package and installer versioning with the 1.9.0 release.
- **Onboarding visual refresh:** Restored the animated multicolour mesh-gradient background across setup screens, with a warmer red, pink, orange, and violet palette for account connection.

## Android

- **Backup and recovery:** Refined the dedicated Backup settings with separate, timestamped status for the last completed on-device snapshot and the last external export. The screen now explains what is included and reminds users to keep an external copy for device-loss recovery.
- **Safer backup files:** Automatic snapshots are written through a temporary file and finalized only after the complete contents are synced, with retention limited to the five newest snapshots. Snapshots stay in app no-backup storage rather than silently entering Android cloud/device-transfer backups.
- **Backup size protection:** Preview, import, and export now enforce the same 32 MB limit. Oversized files are rejected while streaming instead of being fully loaded first, and exports too large for a later restore are refused.
- **Backup verification:** Added regression coverage for the exact size boundary, oversized input rejection, export-size validation, and atomic snapshot finalization. Credentials remain excluded from exported backups.
- **Local library:** Continued improvements to local favourites, listening history, playlists, and the Room database migration foundation.
- **Smart Playlists:** Create rule-based playlists from the current library. Filter by artist, liked status, recently added or played tracks, play count, and recent history; choose a sort order and result limit. Saved definitions remain separate from the generated track list and playback queue, and results update against current library/listening data.
- **Listening statistics:** Expanded listening summaries with monthly aggregates, track/artist/album statistics, hourly listening summaries, play counts, listening duration, and last-play information. Aggregates use bounded retention rather than growing indefinitely.
- **Android Auto:** Added a capability-aware Media3 browse library for car surfaces, including saved albums alongside recent items, favourites, and playlists. Android Auto uses the existing playback session and queue rather than creating a second player; unsupported actions remain constrained by the available media capabilities.
- **Typed playable media:** Introduced an explicit playback boundary for music and podcast episodes. Podcast enclosures can be represented as their own media type with stable feed/episode identity and direct HTTPS playback, without fabricating YouTube music IDs or treating episodes as `Song` objects.
- **Podcast network safeguards:** Added explicit podcast playback routing and redirect validation so podcast requests bypass the music resolver and redirects to loopback/private destinations are rejected.
- **Playback regression coverage:** Added Media3 playback and network-policy instrumentation coverage, including direct podcast enclosure playback and private-address redirect rejection.
- **Library and statistics UI refinement:** Tightened Library vertical spacing, clarified back-screen titles, and refined statistics card surfaces and hourly-chart heading contrast.
- **YouTube Music sync:** Account-scoped pending operations and safer synchronization handling from 1.8.10.
- **Updates:** A dedicated Updates settings category and improved update checking, download cancellation, and incomplete-download cleanup.
- **Appearance:** Progressive scroll-edge blur remains off by default, with an option to enable it in Appearance settings.
- **Phone onboarding:** Added a guided setup flow for profile name, optional Google sign-in, and the initial music setup, with a replay option in Settings.
- **Android TV onboarding:** Retains its TV-specific setup guide and Settings replay entry.
- **Wear OS onboarding:** Added a watch-specific first-run guide and a replay entry under More.
- **Consistent setup state:** Phone, desktop, and Wear OS persist onboarding completion so the welcome guide does not reappear on every launch.
- **Home discovery layout:** Reworked Quick Picks into compact, horizontally paged song groups. It uses two columns on wider screens and switches to one readable column on narrow screens.
- **Music video shelves:** Added dedicated horizontal video cards with landscape thumbnails displayed without cropping, plus title and artist/details beneath each thumbnail.
- **Listening and discovery rails:** Keep Listening now uses a horizontal carousel, while playlist, genre, mood, and other discovery shelves retain distinct artwork-led horizontal browsing.
- **Responsive home spacing:** Home shelf and card sizing adapts to the available content width, preserving readable song details and avoiding squeezed layouts on smaller screens.

This release does not claim that the Android local-first migration or YouTube Music synchronization is complete. Those areas remain under active development and need broader real-device and account-switching validation. The typed podcast playback foundation does not, by itself, mean that the full podcast subscription, feed discovery, and episode-management experience is complete. Android Auto emulator coverage is not a substitute for validation with the Desktop Head Unit and a compatible vehicle.

## Desktop availability

- **Windows x64:** `Audify-Setup.exe`
- **Linux and macOS:** The desktop codebase includes platform support, but downloadable builds are not yet part of this release.

## What's next for Android

Priorities under consideration include full podcast feed discovery and subscription management on the typed playback foundation, smarter two-way YouTube Music sync, conflict resolution for edits made on multiple devices, scheduled and user-controlled backup/restore, richer queue tools, broader Android Auto vehicle validation, and better offline download management. These are roadmap ideas, not features promised by this release.

## Report a bug or share feedback

- **Bug reports:** https://github.com/sachit1751-art/Audify/issues/new?template=bug_report.yml
- **General tester feedback:** https://github.com/sachit1751-art/Audify/issues/new?template=tester-feedback.yml
- **All releases:** https://github.com/sachit1751-art/Audify/releases

## Downloads

- **Android phones:** `Audify-1.9.0.apk` (universal; includes supported ABIs)
- **Android phones (ARM64):** `Audify-1.9.0_arm64-v8a.apk` (smaller device-specific option)
- **Android TV:** `AudifyTV-1.9.0.apk`
- **Wear OS:** `AudifyWear-1.9.0.apk`
- **Windows desktop:** `Audify-Setup.exe` (separate desktop installer asset)
