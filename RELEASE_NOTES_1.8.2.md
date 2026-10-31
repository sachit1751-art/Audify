# Audify 1.8.2 — release notes draft

> **Status: validation build / not a final release sign-off.** This note documents the current uncommitted implementation being prepared for the `main` branch. The remaining limitations below are material and must not be represented as completed features.

## Fixes and improvements

- Tightened lyrics lookup matching so recording-version qualifiers such as Live, Remix, Acoustic and Remastered are retained during LRCLIB lookup instead of being stripped from the search identity.
- Added metadata checks before accepting Bini lyric-identification candidates: contradictory title, artist or duration candidates are rejected rather than blindly trusting the first ranked result.
- Expanded lyric-clock regression coverage around sampled playback timing and synchronization state.
- Added a configurable download-storage budget with measured existing-file sizes, bounded concurrent reservations, an explicit unknown-size block, oldest-first review, and a separate confirmation before any selected file is deleted. No automatic download eviction is performed.
- Added downloaded-file access checks that distinguish file readability from actual playback verification.
- Added a listening-summary CSV export based on retained monthly per-track aggregates. The export does not fabricate per-play timestamps and documents retention limits.
- Added Spotify CSV/TSV playlist-file parsing and import flow for exports containing Track Name and Artist columns.
- Added a weekly Home listening recap with a visibility preference and dismissal behavior.
- Improved onboarding, search, Replay and settings interactions, and corrected the DevDebug instrumentation package assertion.
- Added the WorkManager dependency required by the TV module's shared download worker source.

## Validation evidence

- DevDebug Kotlin compilation and APK assembly succeeded with the installed JDK 21.
- The latest full `:app:testDevDebugUnitTest` run succeeded.
- Earlier targeted lyric, download-store, storage-budget, offline-verification, CSV and instrumentation tests passed in their respective runs.
- Emulator smoke testing exercised onboarding, Home, Explore, Library, Search, Settings/Sources, download-quality selection, Adele search, playback and the lyrics panel. Media3 reached `PLAYING` at 1× speed.
- `git diff --check` passed after the latest source edits.

## Known limitations — do not call release-ready yet

- Lyric timing has not been independently validated against an actual recording waveform or a user-supplied problem track. Search matching and synthetic clock tests do not prove real-song alignment. A concrete affected track (title and credited artist, ideally album/version and video ID) is still required.
- Bini candidate filtering and LRCLIB query preservation reduce matching risk, but provider-side matching can still return incorrect lyrics. Real-track regression coverage remains outstanding.
- Plan 035 quality-upgrade detection/replacement is not implemented; the storage-budget portion is implemented.
- Plan 036 tag editing remains blocked because current tag writers and storage replacement paths cannot safely preserve all existing metadata/artwork with rollback.
- Plan 037 exports retained monthly per-track aggregates only. It is not per-play or complete lifetime history; older months and lower-ranked tracks may have been pruned.
- Plan 038 supports Spotify CSV/TSV imports with Track Name and Artist columns. JSON, M3U and richer column mapping remain unsupported.
- Plan 039 checks file access/readable headers; it does not prove that audio playback succeeds.
- Lint still has unresolved errors after the isolated Media3 opt-in remediation. Remaining known categories include Compose resource-context calls, missing plural quantities, StateFlow reads in composition, and API/flow/string-format issues. No blanket suppression was added.
- The latest connected instrumentation invocation could not run because no emulator/device was connected at that time. Earlier instrumentation passed, but a fresh post-integration device run is still required.
- The previously observed startup ANR has not been conclusively attributed or closed. Full button-by-button coverage, accessibility-node review, rotation, keyboard and smaller-screen testing remain incomplete.

## Compatibility and release integrity

- No Room schema or migration changes are included.
- `versionCode` and `versionName` remain unchanged (30 / 1.8.2).
- No translated resource files were edited.
- No signing credentials are included in this change set.
