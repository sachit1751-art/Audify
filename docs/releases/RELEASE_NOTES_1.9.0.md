# Audify 1.9.0

Audify 1.9.0 expands the project across Android phones, Android TV, Wear OS, and Windows desktop. It also introduces a consistent first-run setup experience and a way to replay the welcome guide from Settings.

## What's new

- **Windows desktop support:** Audify is now available as a native desktop app for Windows PCs, with a dedicated installer and desktop-sized interface.
- **One Audify project across devices:** The desktop app joins the Android phone, Android TV, and Wear OS builds. Each platform has an interface suited to its form factor.
- **Desktop app identity:** Added the Audify Windows application icon and aligned desktop package and installer versioning with the 1.9.0 release.

## Android

- **Backup and recovery:** Dedicated Backup settings, export/import, and automatic local-backup groundwork from 1.8.10.
- **Local library:** Continued improvements to local favourites, listening history, playlists, and the Room database migration foundation.
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

This release does not claim that the Android local-first migration or YouTube Music synchronization is complete. Those areas remain under active development and need broader real-device and account-switching validation.

## Desktop availability

- **Windows x64:** `Audify-Setup.exe`
- **Linux and macOS:** The desktop codebase includes platform support, but downloadable builds are not yet part of this release.

## What's next for Android

Priorities under consideration include smarter two-way YouTube Music sync, conflict resolution for edits made on multiple devices, scheduled and user-controlled backup/restore, richer queue and playlist tools, Android Auto integration, and better offline download management. These are roadmap ideas, not features promised by this release.

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
