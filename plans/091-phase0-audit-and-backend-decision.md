<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
(Header copied from the other files in plans/. Confirm with the maintainer that it is the right one for a contributor's document.)
-->

# Plan 091 — Phase 0: Audit and Backend Decision (DRAFT)

**Status: decisions recorded in sections 9 and 13 (November 2026). Section 14 lists conflicts with the existing Cloudflare setup that must be resolved before Phase 2. Sections 1 to 8 are the original audit; sections 10 and 11 are the findings and proposals the maintainer responded to.**
**Parent plan:** [091 — AUDIFY Identity and Lightweight Social Layer](091-audify-identity-and-social-layer.md)
**Related issue:** #23 — User Search & Following System

## 0. Scope and limits of this audit

- **Baseline:** `upstream/main` at commit `4c0828ce` (tag `v1.9.5`), checked 2026-11-22. The repository now keeps the Android app and the backend under `core/`; paths below use that layout. Android source paths without a prefix (such as `auth/AccountSessions.kt`) are relative to `core/app/src/main/java/com/music/audify/`.
- **Read-only.** Nothing was deployed or modified. This is a code and documentation review only.
- **Not verified:** the live Cloudflare account and its usage dashboards, whether the Render and Oracle deployments are still running, runtime behaviour on a device, and anything in files I did not open. Where something was not examined it says so.
- Hosting prices and limits in §4 come from public pricing pages and third-party summaries searched in November 2026. **Re-check them on the vendors' own pages before relying on them.**

## 1. Findings

### F1. No AUDIFY identity exists on the client

- `auth/AccountSessions.kt` models a `GoogleAccountSession`: a captured **web cookie**, plus name, email and a list of `YouTubeProfile`s. `sessionId()` derives the account id from `dataSyncId`, or from a SHA-256 of the cookie.
- `auth/AuthStore.kt` selects the active session and stores it locally (via `EncryptedPrefs.kt` / `androidx.security:security-crypto`; storage internals not examined in detail).
- **Consequence:** a cookie is a bearer credential for Google/YouTube, not proof of identity that a third-party server can verify. It must never be sent to an AUDIFY server. Any server-verifiable login therefore has to be **new**, which agrees with Plan 091 §1 and §12.
- **Naming hazard:** "profile" already means a YouTube profile in the code (`YouTubeProfile`, `AccountProfileSelector.kt`) and a local statistics screen (`ProfileScreen.kt`). A social profile needs a distinct name in code and UI (for example `AudifyProfile`) to avoid confusion.

### F2. The Go backend is transient and trusts client-supplied identity

- `core/backend/README.md`: "Go + a WebSocket per device, **no database**." Parties live in memory (`core/backend/party/party.go`, `core/backend/hub/hub.go`) and are swept on a timer.
- The README states that `userId`, `deviceId` and `displayName` are taken from the client and **cannot be verified by the server**. Only the per-party token is server-held.
- Documented deployments are an Oracle Always Free VM (Caddy + systemd) and Render. The Cloudflare README calls Render the rollback backend. Whether either is still live was not checked.

### F3. The production backend is a Cloudflare Worker with SQLite Durable Objects

- `core/backend/cloudflare/wrangler.toml` binds one Durable Object class, `PartyRoom` (SQLite-backed). **No D1, KV or R2 binding is configured.** `core/app/build.gradle.kts` reads `LISTEN_TOGETHER_SERVER` from `local.properties` or the environment, with a built-in fallback that I believe is the workers.dev URL (re-verify the fallback value).
- Durable, but per party: state is kept in one SQLite row per room code and cleaned up by lifecycle alarms (empty-room TTL, 12-hour maximum age per the Cloudflare README). It is not a user database.
- `worker.js` validates only the **shape** of `userId`, `displayName` and `avatarUrl`. The join token is a random per-member string. Nothing proves who the caller is.
- The Cloudflare README says the account is on **Workers Free**, "No paid Durable Object, D1, KV, R2, or third-party service is configured", and that free-tier exhaustion makes requests fail with no paid fallback.
- Reusable patterns exist: a singleton Durable Object serialises rate limits (per-IP, bounded tables), and `FEEDBACK_SETUP.md` shows secrets handled with `wrangler secret put` rather than committed.

### F4. Client stack is ready for a new API client

- `shared/` is Kotlin Multiplatform (Android + JVM targets) with Ktor 3.6.1 (core, OkHttp, websockets, content-negotiation) and kotlinx-serialization; see `shared/build.gradle.kts`. Wire models for Listen Together already live in `shared/src/commonMain/.../listentogether/PartyModels.kt`.
- The Android app uses the same Ktor stack in `data/listentogether/ListenTogether.kt`. The Cloudflare README states Android TV compiles the Android app's source tree, so code added there reaches TV too.
- **Desktop is a separate Rust app** and does not use the AUDIFY party protocol (it uses Spotify Connect for Jam). A desktop social client would be separate work in Rust.
- Room is used locally (`core/app/schemas/.../LocalDatabase/1.json`, `2.json`). Plan 091 §12 rejects local-only storage as the social source of truth, and the plans README forbids Room schema changes without approval. Social data should not touch Room beyond non-sensitive caching, and any such cache needs separate approval.

### F5. Privacy and operations gaps

- A filename search of the repository found `desktop/PRIVACY.md` but **no privacy policy covering the Android app** (to be confirmed in the live repo). Storing usernames, avatars and a follow graph likely needs one, plus the account deletion and export behaviour that Plan 091 §9 already requires.
- `DiscordSocialPresence.kt` exists (Discord Social SDK integration). It was **not examined**. It is a different system from an AUDIFY social graph, but a maintainer should confirm there is no overlap in user-facing concepts.

## 2. Requirements taken from Plan 091

1. Verified identity, independent of any music provider; one immutable internal user id per account.
2. Durable transactional storage with database-level uniqueness (normalised username), foreign keys, and tested backup/restore.
3. Authorization enforced server-side from the verified session, never from client-supplied ids.
4. Rate limits and abuse logging on signup, search, and follow.
5. Avatar storage; account deletion and data export.
6. One shared source of truth for Android, TV, Wear OS and desktop.

## 3. Identity method (a sub-decision inside the backend decision)

Cookie-based provider login cannot be the identity (F1). Candidates:

| Method | Notes |
|---|---|
| Google Sign-In via Android Credential Manager (ID token) | Server verifies the token's signature, audience and expiry against Google's published keys. No password handling. Requires a Google account, and must be a **separate, standard OAuth flow**, not the existing cookie capture. |
| Email + password | Familiar, but means owning password hashing, email verification, reset flows and abuse protection. Highest security burden if self-built. |
| Email magic link / one-time code | No passwords, but needs reliable email delivery. |
| Passkeys | Strong and passwordless; more client and server complexity. |

If a managed backend is chosen, its auth service covers most of this. If the Cloudflare Worker is extended, the maintainer must accept writing and maintaining this code.

## 4. Backend options

Figures below come from public pricing pages and summaries (November 2026). **Verify before deciding.**

| | A. Extend the Cloudflare Worker (D1, plus R2 for avatars) | B. Extend the Go service | C. Supabase |
|---|---|---|---|
| Durable storage | D1 (SQLite). Free plan lists 5M rows read/day, 100K rows written/day, 5 GB total storage; free queries fail once a daily limit is hit | Needs a database added; the Go service has none, and the Render free plan spins down | Managed Postgres. Free plan: 500 MB DB, 1 GB files |
| Auth | **None built in.** Must be built (see §3) | **None built in.** Same | Built in (email, OAuth providers and more). Free plan: 50,000 monthly active users |
| Row-level authorization | Hand-written in Worker code | Hand-written | Postgres Row Level Security, but policies must be written and tested carefully |
| Existing infrastructure | Same platform, tooling (`wrangler`), and secrets pattern already in the repo | Documented as rollback/alternate, not production | New vendor and account |
| Cost at launch | $0 on Free, but see risks | $0 on Oracle/Render free, with weak durability | Free tier **pauses projects after 7 days of inactivity**; Pro is about $25/month |
| Backups | D1 advertises point-in-time recovery (verify on current plan) | Self-managed | Daily backups are listed under Pro, not Free |
| Lock-in | Moderate (Workers/D1 APIs); SQL is portable | Low | Low-moderate (standard Postgres; auth/SDK coupling) |
| Main risk | Self-built auth; shared free quota (below) | Same self-built auth plus database operations on a small VM | Cost and a new vendor; free tier unsuitable for production because of the pause |

**Shared-quota risk for option A.** Cloudflare's Workers Free request limit (100,000/day) is, to my reading, per account, not per Worker. Listen Together already spends Durable Object requests on a 15-second ping per participant. Social traffic (search, profile views, follow lists) would compete with it. Free-plan exhaustion causes failures. A launch probably needs the **Workers Paid plan (about $5/month)** whichever way the identity work goes. Verify against the account's dashboard.

**Sources to re-check:** Cloudflare D1 pricing and limits (developers.cloudflare.com/d1/platform/pricing/), Durable Objects pricing (developers.cloudflare.com/durable-objects/platform/pricing), Supabase pricing (supabase.com/pricing).

### Contributor's leaning (non-binding)

The project currently runs everything on free tiers and the maintainer has documented "no paid service configured." Option A (D1 + R2, same platform) fits that best **if** the maintainer is comfortable with option A's identity trade-off, ideally a standard ID-token login so the Worker never handles passwords. If self-built auth is judged too risky, option C moves that risk to a maintained service at the price of a paid plan before public launch. Option B looks weakest: it adds a database and operations burden to a service that is not the production path.

Whichever is chosen, keep the social domain behind a repository interface (Plan 091 §4) so the UI is not coupled to one vendor.

## 5. Proposed shape (for discussion, not decided)

- **Shared domain layer** in `shared/commonMain`: `AudifyProfile`, `FollowState`, `SocialRepository` (interface), and request/response models. Ktor implementation behind the interface.
- **Token storage** reuses the encrypted-storage approach already in `auth/`. Sign-out and account switching are handled consistently.
- **Android UI first** (search, profile, follow, followers/following), then TV, Wear OS and desktop as Plan 091 §8 describes.
- Phases follow Plan 091 §10: identity and profile, search, follow graph, client integration, release readiness. One focused PR per phase.

## 6. Idea for a later phase: profile showcase with per-item visibility

**Not part of the MVP.** Plan 091 keeps listening activity out of scope; this section only suggests leaving room for it in the privacy model so it need not be retrofitted.

- Each shareable item carries its own visibility: `everyone`, `followers`, or `nobody`. Candidates: follower list, pinned playlist, statistics summary.
- **Enforced on the server.** The UI hiding a field is not a privacy control.
- **Statistics:** publish an explicit **aggregate snapshot** (for example top artists and total time), chosen and refreshed by the user, not a live feed of raw listening history. This matches Plan 06's principle of not inventing per-play precision. Default: nobody.
- **Pinned playlist:** a snapshot of title and provider-independent track references. This overlaps Plan 075's open problem of portable track references and should wait for it.
- Blocked users never see these items, and counts and lists must not leak blocked users (Plan 091 §5.3).

## 7. Open questions for the maintainer

1. Which backend direction (A, B, C or other)? What monthly cost, if any, is acceptable before launch?
2. Which sign-in methods should AUDIFY offer (§3)? Is a Google account acceptable as one option, given the plan's provider-independence goal?
3. Separate Worker for social, or the existing one? How should free-plan quota be monitored?
4. Username rules (length, characters, reserved names) and the default discoverability setting for new profiles.
5. Who owns moderation, reports and abuse response?
6. Is a privacy policy for the Android app planned, and who writes it?
7. Is the desktop app in scope for the first release, given it is a separate Rust codebase?
8. Is the file header on this document (copied from the other plans) correct for contributions?
9. Is the Phase 6 showcase idea in §6 welcome as a separate issue?

## 8. Verification still required before this can be accepted

- [x] Record the baseline commit (§0).
- [x] Re-check file locations and the key claims: no database in the Go service, client-supplied `userId`, and only a Durable Object binding in `wrangler.toml`.
- [ ] Re-check the remaining details in `worker.js` (validation, token handling, rate limiting) and the Cloudflare README claims against this commit.
- [ ] Confirm live deployment state (Cloudflare usage, Render/Oracle status) with the account owner.
- [ ] Re-verify all pricing and limit figures on vendor pages.
- [ ] Read `DiscordSocialPresence.kt` and `EncryptedPrefs.kt` in full.
- [x] Maintainer records the backend decision in this file or an ADR (section 9).

## 9. Decisions

Recorded from the maintainer's direction for Phase 1. Source: the maintainer's direction on Telegram; please confirm in this PR's comments

### 9.1 Usernames

- 3 to 20 characters: letters, digits and underscores only.
- Matching is case-insensitive; the person's capitalization is kept for display.
- A username can be changed once every 30 days.
- System names and official AUDIFY names are reserved.

### 9.2 Backend and sign-in

- Reuse the existing Cloudflare setup wherever possible. No second database or duplicated service.
- Cloudflare Workers for the API. D1 is acceptable if it fits the database setup already in use (see 10.1).
- Start with Google sign-in. The design stays open to email sign-in and other providers later.
- Every account has its own permanent internal id. The username and the email address are never the identity.
- People can browse public content without signing in.

### 9.3 Shared test vectors

One set of examples, `shared/testvectors/social-v1.json`, is run by both the Android code and the backend. It covers username rules, text limits, Unicode, page-size limits and invalid cursors. Expected results are written by hand, not generated by either side. The Kotlin runner is `SocialVectorsTest`. The backend runner is part of the Worker work and runs in CI (see 10.3).

## 10. What the existing Cloudflare setup supports

Checked against `upstream/main` before proposing any architecture change.

- **One Worker, `audify-listen-together`** (`core/backend/cloudflare/wrangler.toml`), a single `worker.js` of about 770 lines plus `lifecycle.js`. Bindings: one SQLite-backed Durable Object class (`PartyRoom`). **No D1, KV or R2 binding exists.**
- **Existing database setup = SQLite inside Durable Objects.** Room state is one row per room. A singleton Durable Object holds the party directory, presence and the rate limiters (create, join, feedback, authentication). There is no user table and no relational data outside room rows.
- **Reusable pieces:** CORS and origin handling, per-client rate limiting through the singleton Durable Object, bounded key tables, the `wrangler secret` pattern (the feedback endpoint), and the `node --test` test setup (`lifecycle.test.mjs`, `integration.test.mjs`).
- **Cloudflare plan:** the README states the account is on Workers Free. It lists, as current Free limits, 100,000 Worker requests per day, 100,000 Durable Object requests per day, 100,000 SQL row writes per day and 5 GB of SQLite storage. Re-verify against the account's dashboard.
- **Deployment:** the Worker is deployed by hand (`npx wrangler deploy`). The only deploy workflow in CI targets the Oracle VM for the Go service and is disabled by default.
- **CI does not run the Worker's tests.** `check.yml` runs the Android build and the Go service; `web.yml` covers the web app. Nothing runs `npm test` or `npm run test:integration` for the Worker.
- **The Go service has no database.** The Cloudflare README names it as the rollback target.

## 11. Technical issues raised before implementation

Each item has a proposal. The maintainer decides.

1. **D1 is not part of the existing setup.** The existing database is SQLite in Durable Objects. *Proposal:* add one D1 binding to the same Worker, same account, same `wrangler` tooling; it is a new binding, not a new service or vendor. Reasons not to use a Durable Object instead: username prefix search and follower lists are global queries over all users, which fit an indexed relational database and not one object per room; a single social Durable Object would share the 100,000-requests-per-day and row-write budgets with Listen Together and serialise every request through one thread; and D1 has migration tooling. A singleton Durable Object remains possible if no new binding is wanted, accepting those limits. D1's own free-tier limits are separate and must be re-verified.
2. **Same Worker or a new one.** The direction is to reuse what exists, so the proposal is to add the social routes to the existing Worker under a `/api/social/` prefix, in separate source files, and to keep the code behind the shared contract. The cost is that one deploy affects both features, which makes issue 3 important. Free-plan request limits are per account in any case.
3. **Worker tests are not in CI.** *Proposal:* before any social code merges, add a CI job that runs the Worker's `npm test` and the shared vectors against the Worker's rules module. Without it, a change to the Worker can break Listen Together, which is in production, with no check.
4. **Free-plan quota.** Anonymous browsing and search-as-you-type each cost requests, and the pool is shared with Listen Together, whose clients already ping every 15 seconds. *Proposal:* a minimum search prefix length and client-side debouncing; per-client rate limits using the existing limiter pattern; short caching of public profile reads; and a decision on whether to move to a paid Workers plan before wide release.
5. **Public browsing and blocks.** A block cannot apply to someone who is not signed in, so a blocked person can sign out and still view a public profile. This is normal for services with public profiles, but it limits what blocking can promise. *Proposal:* document it, say it in the interface, and treat "who can see this" for sensitive items (a pinned playlist, statistics) as an audience setting that defaults to nobody, not as a block.
6. **Google sign-in setup.** The app needs Google Cloud OAuth clients: a web client id (the audience the Worker checks) and Android clients registered per signing certificate, which means the debug, release and CI keystores each need their SHA-1 registered. The Worker needs only the public client id and Google's public signing keys, not a client secret. It should require a nonce, verify issuer, audience and expiry, then issue its own revocable session. Accounts are stored as an internal id plus rows in an identities table keyed by provider and the provider's stable subject id, so email and other providers can be added without changing the account model. This is a separate standard sign-in flow; it does not use the existing cookie login.
7. **Username policy gaps.** (a) When someone renames, is the old name held for a while before anyone else can take it? Without a hold, a name can be taken the day it is released to impersonate its former owner, and two accounts can swap names to bypass the cooldown. *Proposal:* hold released names for the same 30 days. (b) Names such as `audify_support` are not covered by exact-match reservation. *Proposal:* decide whether to reserve names that start with `audify`. (c) The first choice at sign-up does not start the cooldown, and a change of capitalization alone counts as a change. These are the simplest readings of "once every 30 days"; confirm them.
8. **Rollback.** The Go service has no database, so rolling back to it removes the social features. *Proposal:* accept this and say so in the rollback notes.

## 12. Updated acceptance criteria

Every item is a requirement. A backend implementation of the social layer is not done until all of them are tested.

1. The acting user comes from a verified session, never from client-supplied ids.
2. Every account has a permanent, server-assigned, never-reused internal id; username and email are attributes.
3. Privacy settings, blocks and relationship state are computed and enforced on the server on every request.
4. Username uniqueness is enforced by the database on the lowercase key, including names that are being held (item 14); the cooldown uses the server clock.
5. Public reads work without a session and expose only content that the audience rules make public from discoverable profiles. They are rate limited per client.
6. Page limits are clamped to 1..50 independently of any client; cursors are validated, and every bad cursor returns `INVALID_INPUT`.
7. Text and username rules, including the code-point length unit, the explicit trim set, and rejection of control, invisible and unpaired-surrogate characters, are applied on the server.
8. The server passes every case in `shared/testvectors/social-v1.json`, in CI, and so does the Kotlin code.
9. The Worker's tests, including the existing Listen Together regression suite, run in CI, so a social change cannot silently break Listen Together.
10. Sign-in is an extensible identity model (provider plus subject), starting with Google.
11. Account deletion removes the profile, follows, blocks, sessions and avatar, and has a test.
12. Public profiles may be viewed signed out. The limits of blocking for signed-out visitors are documented here and stated in the interface.
13. Sensitive content, including pinned playlists and statistics, has an explicit audience control and defaults to private (`Audience.NOBODY`).
14. A released username is held for 30 days before anyone else can take it. Every name that starts with `audify`, and the other reserved names, is unavailable. Registration does not start the change cooldown, and a capitalization-only change is not a change.
15. The server verifies Google's token signature, issuer, audience, expiry and a server-issued nonce. No OAuth secret, signing key or other credential is committed to the repository.
16. Search requires a prefix of at least 2 characters, is debounced in the app, is rate limited per client **without** routing through a single global object or sharing Listen Together's limiter tables, and public reads may be cached briefly.
17. Session tokens are stored only as hashes. Tokens, ID tokens and search queries are never written to logs.
18. Rollback never discards social data: a documented, tested export and restore procedure exists, and migrations are forward-only.
19. Listen Together stays isolated: social code does not change the existing party routes, limits or storage, and the regression suite for them passes unchanged.

## 13. The maintainer's resolutions of section 11

1. **Database.** D1 is not required. Evaluate D1 in the existing account and Worker, with its own migrations and quota checks. If Durable Objects alone are kept, do not send all social traffic through one global object.
2. **Worker structure and CI.** Keep the existing Worker if practical. Isolate the social routes and modules under `/api/social/`. Worker tests and the shared vectors must run in CI before any social code merges.
3. **Quota.** Debounced search, a sensible minimum prefix length, per-client rate limits and short-lived caching. No move to a paid plan without reviewing actual usage and cost first.
4. **Privacy and blocks.** Public profiles stay publicly viewable; document the limits of blocking for signed-out visitors. Sensitive content has explicit audience controls and defaults to private.
5. **Authentication.** Permanent internal ids independent of username and email. Verify issuer, audience, expiry and nonce on the server. Never commit OAuth secrets or signing credentials.
6. **Usernames.** Hold released names for 30 days. Reserve the `audify` namespace. Registration does not start the cooldown. Capitalization-only changes do not count.
7. **Rollback.** Document that reverting to the Go service makes social features unavailable. Social data is never silently discarded; define how it is preserved and restored.
8. **Acceptance criteria.** Every item in section 12 is a requirement, especially server-side authorization, database-enforced username uniqueness, privacy enforcement and shared-vector parity.

## 14. Conflicts with the existing Cloudflare setup (resolve before Phase 2)

Found by reading `core/backend/cloudflare/` on `upstream/main`. Each has a proposal; none is implemented yet.

1. **CORS allows only GET, POST and OPTIONS** (`access-control-allow-methods` in `worker.js`, applied to every route). The social API needs PATCH and DELETE for browser clients. *Proposal:* the social router builds its own CORS headers and the Listen Together ones stay untouched.
2. **All rate limiting runs through one global object.** Create, join, authentication, feedback and presence limits all go to the single Durable Object named `__audify_global__`, and its `rate_limits` table has a shared entry cap (`JAM_RATE_LIMIT_MAX_ENTRIES`, 10,000 by default) for create, join and auth keys. Social traffic through it would both create the single hot object the maintainer wants to avoid and use up Listen Together's cap, which returns "too many source addresses are being tracked". *Proposal:* social rate limiting uses a separate mechanism, such as Cloudflare's Workers rate limiting binding or counters spread over many keys, and never the same object or table. Check availability and limits on the Free plan before choosing.
3. **There is no staging environment.** `wrangler.toml` has one configuration, so any social deploy goes straight to the Worker that serves Listen Together in production. *Proposal:* add a staging environment with its own D1 database, and run migrations there first.
4. **Tooling is not pinned.** `package.json` has no dependencies and there is no lockfile, and the README says to run `npx wrangler deploy`, so the Wrangler version is whatever `npx` fetches. D1 migrations, the declarative `[exports.PartyRoom]` configuration and local D1 development all depend on the Wrangler version. *Proposal:* add Wrangler as a pinned dev dependency with a lockfile, used by CI and by contributors.
5. **The Worker's integration suite needs a running server.** It expects a local Worker at `127.0.0.1:8787`, and some cases are skipped against a remote one. *Proposal:* a CI job that installs the pinned Wrangler, starts `wrangler dev`, waits for `/healthz`, runs `npm test` and `npm run test:integration`, and also runs the vectors suite, which needs no server. Today none of these run in CI (section 10).
6. **Routing is one long chain inside `fetch`.** The social router must be dispatched first on `/api/social/`, before any party logic, and must leave Listen Together's 404, 405 and CORS behaviour unchanged. The Worker also has observability enabled, so no tokens, ID tokens or search queries may be logged.
7. **Configuration naming and secrets.** Existing variables are prefixed `JAM_`; social ones should be prefixed `SOCIAL_`. A cursor-signing key and a token pepper are secrets set with `wrangler secret put`, which only the account owner can do. Google's client id is public and is a normal variable. A D1 `database_id` in `wrangler.toml` ties the repository to one account; check that contributors can still run `wrangler dev` with local D1 and that CI works without the production id.
8. **`wrangler.toml` starts with a UTF-8 byte-order mark.** Harmless to Wrangler today, but it can confuse other TOML tools. *Proposal:* remove it in the same change that adds the D1 binding.
9. **Android configuration has one server URL.** The build field is `LISTEN_TOGETHER_SERVER`, set in `core/app/build.gradle.kts`, in CI's `local.properties`, and shared by the TV build through the same source tree. *Proposal:* a `SOCIAL_SERVER` field that defaults to the same value, so there is one place to point at a different Worker.
10. **Rollback needs a procedure, not only a statement.** Reverting clients to the Go service makes social features unavailable (section 11, item 8) while the data stays in D1. *Proposal:* migrations are forward-only; before any rollback, export the database (`wrangler d1 export`) and note the Time Travel window, which depends on the plan and must be checked; the Worker stays deployed so data is intact; clients show social as unavailable when the endpoint is missing; restore is a re-point, not a data migration. Test the export and restore on staging.
11. **Listen Together isolation.** The party code is a single file with the routing chain above. *Proposal:* social pull requests do not refactor or reformat existing party code, the existing integration suite is the regression guard, and it must pass unchanged in CI.
