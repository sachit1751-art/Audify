# Plan 044 — Artist new-release notifications

**Status: not started.** Verified at commit `61778ab4` — no artist new-release notifications exist. The app 
has artist pages, artist subscriptions/bookmarks (the executor checks at implementation time whether the app 
tracks "followed" artists), and a sync worker, but no push/notification for new releases from followed artists.

## Objective

Let the user follow artists and get a notification when a followed artist releases a new song/album. The 
notification is a local check (the app checks followed artists for new releases on a schedule) → notification 
channel → tap → open the artist / new release. Privacy-safe, local-first — no third-party notification service.

## Why

- Matrix #12: SimpMusic and YTM have artist new-release alerts. Audify has artist pages and sync — adding 
notifications is a discovery feature for users who follow artists.
- It's a natural extension of artist following + the existing sync worker — the app already checks artists for 
sync; new-release detection is a small addition to that.
- Push notifications are a standard mobile feature — a local scheduled check + notification is the right 
implementation (no third-party push service needed).

## What exists today

- Artist pages exist (the executor checks at implementation time how artists are represented — artist entity, 
artist page screen, artist metadata).
- Artist following/bookmarking may exist (the executor checks at implementation time — some apps call it 
"bookmarked artists" or "followed artists"). The plan assumes a "followed artists" concept exists or can be 
added; if it doesn't, v1 can use "bookmarked artists" as the follow list.
- A sync worker exists (the executor checks at implementation time — the app syncs with YT for library/podcasts 
etc.). New-release detection can hook into or parallel the existing sync.
- Notifications: the executor checks at implementation time what notification infra exists. The plan assumes 
standard Android notifications (notification channel, notification builder). If the app has a notification 
utility, reuse it.

## Scope

**In scope:**
- A "followed artists" concept (if it doesn't exist, v1 uses bookmarked artists as the follow list — the 
executor decides at implementation time). The user follows artists (from the artist page / artist list) → those 
artists are the follow list.
- New-release detection: on a schedule (WorkManager periodic task, e.g. daily), for each followed artist, check 
if the artist has new releases since the last check (compare against the last-known release timestamp / the 
existing artist metadata). The check is a light lookup per artist — not a full artist page fetch (the executor 
picks the API at implementation time — e.g. a "new releases" endpoint, or a lightweight artist metadata fetch 
that includes recent releases).
- Notification: when a new release is detected for a followed artist, post a notification: "Artist X released a 
new [song/album] — [title]". Tap → open the artist page / the new release. The notification goes to a 
notification channel (e.g. "Artist new releases") so the user can control it.
- Notification settings: a toggle for "Artist new-release notifications" (on/off), and optionally per-artist 
opt-out (the user can unfollow / mute a specific artist's notifications). The executor picks the setting surface 
at implementation time.
- Deduplication: don't notify for the same release twice. Track the last-notified release per artist; don't 
re-notify until a newer release appears.

**Out of scope:**
- Real-time push (third-party push service) — defer; v1 is a local periodic check + notification. No third-party 
push service.
- New-release discovery for non-followed artists — defer; v1 is only followed artists. Discovery of new artists 
is a different feature.
- Social features (friends' new releases, etc.) — defer; v1 is personal, user's followed artists only.
- In-app badge / home-screen alert in addition to notification — defer; v1 is the notification. An in-app badge 
can be added later.
- Persisting every release as a library entry automatically — defer; v1 notifies, the user taps to open the 
artist/new release. Auto-adding to library is a different feature.

## Affected files

| File | Change |
|------|--------|
| `utils/ArtistReleaseCheckWorker.kt` (new) | WorkManager Worker: for each followed artist, check for new releases since last check. The executor picks the API at implementation time. On new release: post notification, update last-checked timestamp. |
| `utils/ArtistNotificationScheduler.kt` (new) | Schedule the periodic check (WorkManager). Enforce constraints (network, maybe periodic daily). |
| `db/entities/FollowedArtistEntity.kt` (new, or reuse existing) | Followed artist: artistId, name, lastChecked timestamp, lastNotifiedReleaseId/timestamp. The executor reuses an existing "followed/bookmarked artist" entity if it exists, or adds a new one. |
| `db/MusicDatabase.kt` | DAO for followed artists: list all, update last-checked, update last-notified. If a "followed artist" concept doesn't exist, add the entity + DAO. |
| `viewmodels/ArtistNotificationViewModel.kt` (new, or extend existing settings VM) | Bridge: toggle on/off, last-checked status, pending notifications count (optional). |
| `ui/screens/settings/integrations/ArtistNotificationsSettings.kt` (new) | Settings UI: enable/disable artist new-release notifications, notification channel setup, per-artist opt-out (if implemented), last-checked status. |
| `ui/screens/settings/integrations/IntegrationsSettings.kt` (or the settings host) | Add artist notifications section. |
| `sachit_strings.xml` | "Artist new releases", "New release from…", notification channel name/description, per-artist opt-out labels, last-checked labels. |

## Technical approach

1. **Followed artists** — The executor checks at implementation time whether a "followed artist" concept exists. 
If yes, reuse it. If not, v1 uses "bookmarked artists" as the follow list (the user bookmarks/follows artists 
from the artist page). The plan is agnostic — it needs a list of artists the user cares about.
2. **New-release check** — A WorkManager periodic task (e.g. daily) iterates followed artists and checks for 
new releases. The executor picks the API at implementation time: a "new releases" endpoint, a lightweight 
artist metadata fetch that includes recent releases, or a comparison against cached artist metadata (last-known 
release date / last-known release ID). The key is: a lightweight check per artist, not a full artist page fetch 
for every artist on every run.
3. **Deduplication** — Track per artist the last-notified release (release ID or timestamp). On a check, if the 
artist has a newer release than the last-notified, notify and update last-notified. This prevents duplicate 
notifications for the same release.
4. **Notification** — Post a notification to a "Artist new releases" channel (the executor creates the channel 
at implementation time with the right importance / sound / behavior). The notification: "Artist X released a new 
[song/album] — [title]". Tap → open the artist page / the new release (the executor picks the deep-link target 
at implementation time).
5. **Settings** — A toggle for "Artist new-release notifications" (on/off) and a notification channel. 
Optionally, per-artist opt-out (the user can mute a specific artist's notifications — the executor decides if 
this is v1). Last-checked status shown in settings (e.g. "Last checked: yesterday").
6. **Constraints** — The periodic task runs with reasonable constraints: network (the check needs network to 
fetch new releases), maybe Wi-Fi-only if the executor wants to be conservative. Battery-friendly — daily, not 
hourly.

## Testing

- Unit test for the new-release check logic (mock API): given a list of followed artists with known last-checked 
+ last-notified states, and a mock API returning new releases for some, assert notifications are posted only for 
new releases, dedup works (same release not re-notified), last-checked/last-notified updated.
- Unit test for the notification posting (mock NotificationManager): assert a notification is posted with the 
right content + channel when a new release is found.
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: follow a few artists, wait for / trigger the periodic check, verify notifications appear for new 
releases (use test artists with known new releases, or mock). Verify no notification for artists with no new 
releases. Verify tap opens the artist / new release. Test the on/off toggle (off → no notifications). Test 
guest / no-followed-artists edge case (no notifications if no followed artists).
- Regression: existing artist pages, sync, notifications (other channels) unchanged.

## Performance notes

- The check is a periodic task with a lightweight per-artist lookup — not a full artist page fetch for every 
artist. The executor picks the light API at implementation time. For a user with many followed artists, the 
executor may batch or throttle the checks — defer large-follower optimization until observed.
- WorkManager respects battery / constraints — not aggressive.

## Rollback

- Remove the check worker, scheduler, followed-artist entities (if new), notification posting, and settings UI 
→ no artist new-release notifications, existing artist pages / sync unchanged. Zero impact on the rest of the 
app.

## Anti-BitChord check

- Artist new-release notifications are a standard feature — YTM and SimpMusic have them. Implement as Audify: 
local periodic check + notification, followed-artist-based, opt-in. Don't copy a competitor's push-provider 
approach — use local scheduled checks + Android notifications.
- Privacy-safe: the check is local (the app fetches artist metadata on the user's behalf, as it does for sync). 
No third-party notification service. The user controls the toggle and follows.
