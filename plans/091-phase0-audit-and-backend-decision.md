<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
(Header copied from the other files in plans/. Confirm with the maintainer that it is the right one for a contributor's document.)
-->

# Plan 091 — Phase 0: Audit and Backend Decision (DRAFT)

**Status: draft for maintainer review. This document recommends; the maintainer decides.**
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
- [ ] Maintainer records the backend decision in this file or an ADR.
