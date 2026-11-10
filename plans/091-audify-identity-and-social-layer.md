<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
Baseline: f2c7a6e4c37e8fd99ae20aa22965a71619da6b59 (2026-11-11)
-->

# Plan 091 — AUDIFY Identity and Lightweight Social Layer

**Priority: P0 — FIRST PRIORITY. This is the first plan to execute before other new feature work.**  
**Status: planned — architecture and implementation audit required; no implementation started.**  
**Related issue: [#23 — User Search & Following System](https://github.com/sachit1751-art/Audify/issues/23)**  
**Prerequisite for:** Plan 075 (Collaborative Playlists) and any feature that needs durable AUDIFY-owned user identity.

## 1. Product decision

AUDIFY needs its own lightweight identity and social graph. This is an AUDIFY account system, not a YouTube Music friends feature.

A user must be able to create and use an AUDIFY profile **without signing into or connecting YouTube Music, Spotify, or another music provider**. Provider accounts remain separate and optional. Connecting or disconnecting a music provider must never create, delete, rename, or change ownership of the AUDIFY identity.

The goal is a small, reliable social foundation—not a second Instagram. Ship identity, profile discovery, and follow relationships first. Do not start with messaging, a social feed, live listening, or recommendation algorithms.

## 2. Non-negotiable execution order

1. **Audit the existing backend and authentication architecture before selecting technology.** Inspect the current Go service under `backend/`, its deployment configuration, persistence layer, token issuance/validation, and all existing account/session code. Establish whether it is a production-capable, durable backend or only the transient Listen Together service.
2. **Write down the backend decision before implementation.** Prefer extending an existing production backend if it can safely provide durable storage, verified identity, migrations, monitoring, backups, and authorization. If it cannot, evaluate a managed backend such as Supabase. Do not add Supabase, Firebase, or another service merely because it is convenient; do not create a second backend without documenting why.
3. Implement a verified AUDIFY identity and profile foundation.
4. Implement username search and profile viewing.
5. Implement follow/unfollow and follower/following lists, counts, and mutual-follow state.
6. Integrate the same account and social data across supported AUDIFY clients.
7. Consider optional listening activity only after the core social layer is stable and privacy-reviewed.

**Do not begin Plan 075 collaborative-playlist implementation until the identity and durable-storage prerequisites in this plan are complete and verified.** Do not block unrelated playback or library work on this plan.

## 3. Existing architecture findings and audit caveats

The repository audit recorded in Plan 075 found:

- `app/src/main/java/com/music/audify/auth/AccountSessions.kt` models Google/YouTube provider sessions and account metadata.
- `app/src/main/java/com/music/audify/auth/AuthStore.kt` stores provider credentials and selects an active provider account. These are not first-party AUDIFY identities.
- `backend/` contains a Go Listen Together service. Its join protocol accepts client-supplied user identity fields; the recorded audit found that the service validates field shape but does not independently prove that the caller owns the submitted identity.
- The Listen Together party token is scoped to a live party/device membership. It is not a durable AUDIFY user credential.
- Plan 075 already identifies verified AUDIFY identity, durable transactional storage, and provider-independent track references as blockers for shared playlists.

This plan's baseline is the GitHub `main` commit shown in the header. The previous Plan 075 audit was read-only and explicitly did not verify local uncommitted changes, deployed infrastructure, or runtime behavior. The first implementation step must repeat the audit against the actual checkout and deployed environment. Do not treat the findings above as proof that the current deployment is unchanged.

## 4. Backend architecture decision gate

Create a short architecture decision record (or a clearly marked decision section in this plan) after inspecting the current code and deployment.

Evaluate these options:

| Option | Use when | Requirements |
|---|---|---|
| Extend the existing AUDIFY Go backend | It is actively deployed and can be made durable and secure without destabilizing Listen Together | Verified token issuer/validator, persistent transactional database, schema migrations, backups, monitoring, rate limits, and authenticated APIs |
| Supabase | The current Go service is transient or lacks a maintainable durable data/auth foundation | Supabase Auth, PostgreSQL, Row Level Security, migrations, storage policies, backup/restore plan, and secure client configuration |
| Another managed backend | A concrete repository or operational constraint makes both options unsuitable | Document the reason, cost/limits, portability, security model, and migration path before adoption |

Do not decide based on familiarity alone. Compare total operational burden, security, cross-platform SDK support, existing infrastructure, data portability, and cost at expected early-stage usage. Keep the social domain behind a repository/service boundary so the client UI is not coupled to one vendor's SDK.

**Security rule:** never trust a client-supplied `userId`, display name, avatar URL, YouTube cookie, provider profile ID, device ID, Discord token, or Listen Together party token as proof of identity. Every protected operation must derive the internal AUDIFY user ID from a verified access token/session.

## 5. MVP product scope

### 5.1 AUDIFY account and profile

- Sign up/sign in using the chosen AUDIFY identity provider.
- Create one stable AUDIFY profile per authenticated identity.
- Unique, normalized username with clear allowed-character and length rules.
- Display name, optional bio, and optional avatar.
- Profile creation and all social features work without any music provider connected.
- Account deletion and profile-data deletion behavior are defined before release.
- Provider connections are linked as separate integrations, never used as the primary social identity.

### 5.2 Discovery and profile

- Search by exact or prefix username with pagination and server-side limits.
- Open a profile showing avatar, display name, username, bio, follower count, following count, and follow state.
- Do not expose email, provider credentials, private identifiers, or other account metadata.
- Avoid contact-book uploads or invasive address-book matching.

### 5.3 Follow graph

- Follow and unfollow another AUDIFY user.
- Prevent self-follow and duplicate relationships.
- Return accurate follower/following counts and paginated lists.
- Compute mutual-follow state from the follow graph; do not store a redundant mutable mutual flag.
- Define block behavior: blocking must prevent follow/discovery interactions as specified by the privacy model and must not leak blocked users through counts or lists where prohibited.
- Make follow operations idempotent and protect them against races and abuse.

### 5.4 Explicitly out of scope for MVP

- Direct messages, chat, comments, likes, posts, and a general-purpose social feed.
- Real-time listening presence or public listening history.
- Collaborative playlists (owned by Plan 075).
- Friend/contact imports, automatic social graph scraping, and provider-specific friend discovery.
- Complex recommendation or people-you-may-know algorithms.

## 6. Initial data model

Use the chosen backend's equivalent schema; these are logical entities, not a mandate to create these exact tables before the audit.

| Entity | Required data and constraints |
|---|---|
| `profiles` | Immutable internal user ID, unique normalized username, display name, bio, avatar reference, discoverability/privacy setting, created/updated timestamps |
| `follows` | Follower ID, followed ID, created timestamp; unique pair, no self-follow, indexed in both directions |
| `blocks` | Blocker ID, blocked ID, created timestamp; unique pair, no self-block |
| `user_activity` | **Not part of MVP.** Revisit only with explicit opt-in, retention limits, and visibility controls |

Authentication credentials belong to the identity provider, not profile rows. Never store raw passwords or provider access tokens in the social tables. Use foreign keys/referential integrity and define deletion behavior for follows and blocks.

## 7. API and authorization requirements

The exact transport (Go HTTP API, Supabase client/RPC, or another option) follows the architecture decision. The logical operations must cover:

- Create/read/update own profile.
- Search discoverable profiles.
- Read a profile and its follow state relative to the authenticated caller.
- Follow/unfollow a target user.
- Read paginated followers and following lists.
- Read follower/following counts and mutual-follow state.
- Block/unblock a user.

Enforce ownership and authorization server-side or with verified Row Level Security policies. Add username normalization/uniqueness at the database boundary, pagination limits, request validation, rate limits for signup/search/follow actions, abuse logging, and safe error responses. Never ship service-role keys or backend secrets in any client.

## 8. Cross-platform integration

The social domain must have one source of truth and consistent semantics across:

- Android phone/tablet
- Desktop
- Android TV (adapted browsing/profile presentation; do not force phone-style workflows onto TV)
- Wear OS (compact profile/follow affordances only where practical)

Use shared domain models and a repository/service boundary where the existing architecture supports it. Cache only non-sensitive display data; handle sign-out, token expiry, account switching, offline states, and deleted/blocked profiles consistently. Do not duplicate social state independently on each device.

## 9. Privacy, safety, and operational requirements

- Choose and document public vs private profile behavior and discoverability defaults before schema/API freeze.
- Provide block and report pathways before broad public discovery is released; define moderation/abuse response ownership.
- Collect the minimum profile data required. Do not require real names, phone numbers, contacts, or music-provider credentials.
- Do not expose listening activity by default. Activity sharing is a separate opt-in feature and is excluded from MVP.
- Define account export/deletion and retention behavior.
- Protect endpoints against enumeration, scraping, spam follows, and username squatting; use pagination, throttling, and monitoring.
- Configure backups and test restoration before treating the social database as production data.

## 10. Implementation sequence and acceptance criteria

### Phase 0 — Repository and deployment audit (blocking)

- [ ] Read `docs/PRODUCT.md`, `docs/DESIGN.md`, `AGENTS.md`, and existing backend/deployment documentation.
- [ ] Inspect the current Go backend, storage, auth/session code, hosting, secrets/configuration model, CI, logs, and deployed service.
- [ ] Confirm whether a durable database already exists and whether any existing identity issuer can provide verifiable user subjects.
- [ ] Record a backend decision and data-flow diagram. Do not provision a new paid service until the decision and cost are understood.
- [ ] Record baseline commit and working-tree status; identify source drift before edits.

### Phase 1 — Identity and profile foundation

- [ ] Implement signup/sign-in/sign-out and verified access-token validation.
- [ ] Map verified external identity to an immutable internal AUDIFY user ID.
- [ ] Implement profile creation, username uniqueness/normalization, profile editing, and avatar storage.
- [ ] Verify a user can use AUDIFY social features without connecting YouTube Music or another provider.
- [ ] Add migration, auth, authorization, and account-deletion tests.

### Phase 2 — Search and profiles

- [ ] Implement bounded, paginated username search.
- [ ] Implement profile detail and caller-relative follow state.
- [ ] Add privacy and discoverability tests; ensure private fields never appear in responses.

### Phase 3 — Follow graph

- [ ] Implement idempotent follow/unfollow with database constraints.
- [ ] Implement paginated follower/following lists and accurate counts.
- [ ] Implement mutual-follow calculation.
- [ ] Implement block/unblock and verify blocked-user behavior.
- [ ] Add concurrency, authorization, pagination, abuse/rate-limit, and deletion tests.

### Phase 4 — Client integration

- [ ] Integrate shared account/profile/follow repositories across supported platforms.
- [ ] Add loading, empty, error, offline, expired-session, and signed-out states.
- [ ] Verify cross-device consistency after follow/unfollow and profile edits.
- [ ] Add UI/accessibility tests for search, profile, follow controls, and lists.

### Phase 5 — Release readiness

- [ ] Security review of token verification, authorization boundaries, database policies, secrets, and enumeration/rate-limit controls.
- [ ] Verify backup and restore procedure.
- [ ] Verify provider-independent signup and social flows on real supported clients.
- [ ] Document operational ownership, monitoring, incident response, and backend cost assumptions.
- [ ] Keep Issue #23 open until the shipped behavior is verified; do not claim completion from UI-only work.

## 11. Testing requirements

- Unit tests: username normalization, validation, follow state, mutual-follow logic, privacy rules.
- Backend integration tests: token verification, authorization, uniqueness, duplicate/racing follows, pagination, block behavior, deletion and referential integrity.
- Security tests: forged client user IDs, expired/wrong-audience tokens, private-field leakage, user enumeration, rate limits, and secret exposure.
- Cross-platform tests: account creation without provider login, provider connect/disconnect without identity changes, session expiry, sign-out, and cross-device consistency.
- Operational tests: migration from empty database, backup, restore, and service restart behavior.

## 12. Rollback and rejected shortcuts

- Keep provider authentication and playback untouched; social identity is additive.
- Roll back client entry points independently from backend data. Do not drop production tables as a rollback shortcut.
- Use forward-compatible migrations and retain a tested backup before destructive data changes.
- Reject using YouTube Music accounts/friend APIs as the AUDIFY social identity; they do not provide the required cross-provider AUDIFY graph.
- Reject reusing Listen Together party tokens or client-submitted identity fields as account authentication.
- Reject local-only SharedPreferences/Room data as the canonical cross-user social database.
- Reject building chat/feed/activity before identity, authorization, privacy, and moderation are stable.

## 13. Completion definition

This plan is complete only when the AUDIFY identity works independently of music providers, profile discovery and follow relationships are durable and secure, all supported clients share the same source of truth, privacy/blocking and account deletion are tested, operational recovery is documented, and Issue #23's acceptance criteria are verified. At that point, Plan 075 may proceed using the verified identity and durable backend rather than inventing a parallel account system.
