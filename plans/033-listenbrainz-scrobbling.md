# Plan 033 — ListenBrainz scrobbling

**Status: not started.** Verified at commit `4a3bee56` — no ListenBrainz integration exists. The app has
`ScrobbleManager.kt` + `LastFM.kt` + `LastFMSettings.kt` for Last.fm scrobbling. ListenBrainz is a
separate open protocol with its own API. The plan is to add a ListenBrainz track alongside the existing
Last.fm track.

## Objective

Add ListenBrainz scrobbling as a second scrobble backend alongside Last.fm. User tokens per service, both
can be on at once, both off, or either. Reuse the existing `ScrobbleManager` lifecycle where possible, add
a ListenBrainz REST client + token storage + settings UI.

## Why

- Matrix #6: BitChord has ListenBrainz; several competitors have it. Last.fm is not the only scrobble
  protocol, and some users prefer ListenBrainz (open, no commercial angle).
- The existing `ScrobbleManager` already handles the scrobble lifecycle — adding ListenBrainz is a new
  backend, not a new lifecycle.
- Low-to-medium cost — REST client + token storage + settings UI. The hard part (scrobble lifecycle) is
  already solved.

## What exists today

- `utils/ScrobbleManager.kt` — the scrobble lifecycle manager (when to scrobble, what to scrobble, error
  handling).
- `lastfm/LastFM.kt` — the Last.fm scrobble client.
- `constants/PreferenceKeys.kt` — Last.fm token + enabled pref.
- `ui/screens/settings/integrations/LastFMSettings.kt` — Last.fm auth + settings UI.
- No ListenBrainz client, no ListenBrainz token storage, no ListenBrainz settings UI.

## Scope

**In scope:**
- A ListenBrainz REST client: `user/create_scrobble`, `user/scrobble` (submit a scrobble), `user/` (verify
  token). ListenBrainz API is simple — a few endpoints.
- ListenBrainz token storage: a user token (API key or session token — check the current LB API at
  implementation time) stored in DataStore, with an enabled toggle.
- Scrobble submission: when `ScrobbleManager` decides to scrobble, submit to both Last.fm and ListenBrainz
  if both are enabled (fire-and-forget, with error handling per service — one failing doesn't block the
  other).
- Settings UI: a ListenBrainz section in the existing integrations settings (alongside Last.fm): enter
  token, test connection, enable/disable.
- Auth flow: ListenBrainz auth is simpler than Last.fm's — typically a token the user pastes or an OAuth
  flow if the API supports it. Check the current LB API at implementation time; paste-a-token is the simple
  path if OAuth isn't required.

**Out of scope:**
- Replacing Last.fm with ListenBrainz — add LB as a second backend, not a replacement.
- Scrobble lifecycle changes — reuse the existing `ScrobbleManager`; LB is a new submission target.
- Social features from ListenBrainz (recommendations, listening records display) — defer; this plan is
  scrobbling only.
- Last.fm features beyond scrobbling (loved tracks, etc.) — out of scope; this plan is LB scrobbling only.

## Affected files

| File | Change |
|------|--------|
| `lastfm/ListenBrainzClient.kt` (new) | REST client for ListenBrainz: verify token, submit scrobble, user info if needed |
| `utils/ListenBrainzTokenStore.kt` (new) | DataStore-backed token storage: token, enabled, last scrobble time (for LB-specific dedup if needed) |
| `constants/PreferenceKeys.kt` | Add `ListenBrainzTokenKey`, `ListenBrainzEnabledKey` |
| `utils/ScrobbleManager.kt` | Extend: when scrobbling, submit to LB if enabled (alongside Last.fm). Keep the existing Last.fm path unchanged. |
| `ui/screens/settings/integrations/ListenBrainzSettings.kt` (new) | Settings UI: token input, test connection, enable/disable toggle, status |
| `ui/screens/settings/integrations/IntegrationsSettings.kt` (or the host) | Add ListenBrainz section alongside Last.fm |
| `sachit_strings.xml` | ListenBrainz labels, "Enter your ListenBrainz token", "Test connection", "Scrobbling enabled", error strings |

## Technical approach

1. **ListenBrainz API** — At implementation time, check the current LB API (API root, auth method, scrobble
   endpoint, rate limits). The plan deliberately doesn't hard-code endpoints — the executor checks the API
   and implements the client against the current spec. The key is: a REST client, a token store, and a
   scrobble submission path.
2. **Scrobble submission** — When `ScrobbleManager` fires a scrobble, it submits to each enabled backend:
   Last.fm (existing path) + ListenBrainz (new path). Each backend is fire-and-forget with its own error
   handling — one failing doesn't block the other. This is the same pattern as the existing dual-backend
   design (if any) or the single-backend design extended to two.
3. **Token storage** — DataStore. Token + enabled toggle. Last scrobble time per service if LB needs
   dedup (check the API — LB may handle dedup server-side).
4. **Settings UI** — A section in the integrations settings. Token input (text field), "Test connection"
   button (hits a LB endpoint to verify the token), enable/disable toggle. Reuse the existing Last.fm
   settings UI pattern.
5. **Error handling** — LB scrobble failures are logged and non-fatal (same as Last.fm). If the token is
   invalid, the settings UI shows an error on "Test connection". Don't crash the player on scrobble failure.

## Testing

- Unit test for `ListenBrainzClient` (mock HTTP): verify token, submit scrobble → assert the correct
  endpoint + payload. Don't hit the real API in unit tests.
- Unit test for `ListenBrainzTokenStore`: save token, load token, toggle enabled.
- Unit test for `ScrobbleManager` extension: when LB is enabled, assert a scrobble submission reaches the LB
  client (mock the client).
- Build: `./gradlew :app:assembleFossDebug` must pass.
- Manual: enter a valid LB token, test connection, enable scrobbling, play a few songs, verify LB shows the
  scrobbles. Disable LB, verify scrobbles stop going to LB but still go to Last.fm (if enabled). Test
  invalid token → error on test connection.
- Regression: Last.fm scrobbling still works; existing scrobble lifecycle unchanged.

## Performance notes

- LB scrobble submission is a small REST call on track-change events — fire-and-forget, off the main thread.
  Cheap.
- Token storage is DataStore — cheap.

## Rollback

- Remove the LB client, token store, settings UI, and the LB submission path from `ScrobbleManager` →
  Last.fm-only scrobbling, as before. Zero impact on Last.fm.

## Anti-BitChord check

- ListenBrainz is an open protocol — using it is fine, not a copy of BitChord's integration. Implement
  Audify-native: own client, own token store, own settings UI. Don't copy BitChord's LB client code.
- Coexist with Last.fm — don't force a choice. Users can run both, either, or neither.
