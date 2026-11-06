<!--
Copyright (c) 2026 Sachit. All rights reserved.
Project: AUDIFY
Baseline: 2ac1c21eff38f718ab5127b6e99a42e4656d68ca (2026-11-06)
-->

# Plan 075 — Collaborative Playlists

**Status: planned — repository audit completed; implementation not started.**

**Product direction:** AUDIFY is a balanced music + social platform. Collaborative Playlists are the first persistent social layer: people build and maintain a playlist together without leaving the existing music-library and playback experience.

**Feature name in UI:** Shared Playlists  
**Tagline:** Your music. Our playlist.

**Scope boundary:** This plan creates AUDIFY-owned collaborative playlist objects. It does not turn YouTube Music playlists, Spotify playlists, device-local playlists, or Listen Together parties into the same storage object. Existing provider and local playlists must keep their current behavior.

## 1. Repository audit and current baseline

Audit performed against GitHub main at 2ac1c21eff38f718ab5127b6e99a42e4656d68ca on 2026-11-06. The local workspace tunnel was unavailable during this audit, so this is a read-only GitHub-source audit; no local build, runtime, or device verification was performed.

### Existing playlist ownership and storage

| Existing source | Current behavior | Consequence for this feature |
|---|---|---|
| app/src/main/java/com/music/audify/data/spotify/LocalPlaylistStore.kt | Device-local playlists are persisted in SharedPreferences JSON. The store exposes create, add, remove, reorder, rename, delete, import/export and a StateFlow. Mutations serialize through a Mutex and commit before publishing. | Reuse its user-facing concepts only. Do not use this store as the canonical shared database. Device-local URIs and SharedPreferences cannot provide cross-user persistence. |
| app/src/main/java/com/music/audify/ui/PlaylistCreation.kt | Creation tries the signed-in YouTube Music remote API and falls back to a local playlist on remote failure; guests save locally. Song additions are chunked in batches of 50 and report failed IDs. | Existing creation fallback is provider-specific. A failed shared-playlist request must not silently become a private local playlist, because that would falsely imply collaboration succeeded. |
| app/src/main/java/com/music/audify/ui/components/PlaylistPickerSheet.kt | Picker receives UserPlaylist values, supports multi-select, creates a playlist in the current account/local destination, and shows provider/local privacy choices. | Add an explicit Shared Playlists destination/section without conflating its permissions with YouTube privacy. Preserve current multi-select behavior for personal playlists. |
| shared/src/commonMain/kotlin/com/music/audify/data/model/Models.kt | UserPlaylist contains a raw playlist ID, title, subtitle and thumbnail; browseId adds the YouTube VL prefix for remote IDs and preserves local browse IDs. PlaylistPrivacy represents YouTube PRIVATE/UNLISTED/PUBLIC values. | Do not overload UserPlaylist or PlaylistPrivacy to represent collaboration. Introduce a distinct shared-playlist domain model and AUDIFY access policy. |
| app/src/test/java/com/music/audify/PlaylistOwnershipTest.kt | Tests distinguish owned YouTube playlists from saved/foreign playlists using Innertube response evidence. | Keep provider ownership detection separate from AUDIFY collaborative membership/ownership. |

### Identity and account audit

- app/src/main/java/com/music/audify/auth/AccountSessions.kt models Google login sessions, YouTube profiles, cookies, profile IDs and account metadata.
- app/src/main/java/com/music/audify/auth/AuthStore.kt stores provider credentials encrypted at rest and selects an active Google account/profile.
- These are provider sessions, not a first-party AUDIFY account identity service or a backend-issued user access token.
- app/src/main/java/com/music/audify/data/listentogether/ListenTogether.kt documents that the client requires a signed-in account, but also explicitly notes that the party server cannot verify the submitted identity claim.
- backend/protocol/protocol.go accepts userId, deviceId, displayName and avatarUrl in JoinRequest. Validation checks shape and size, not that userId belongs to the caller.
- backend/main.go mints a party membership token after create/join. That token authenticates a device's membership in one live party; it is not a durable user identity credential.
- Do not use a Google cookie, YouTube dataSyncId, Discord bearer token, display name, avatar URL, device ID, or existing party token as the authorization identity for shared playlists.

**Identity blocker:** Before implementation, choose and ship a verifiable AUDIFY user identity contract. Prefer a first-party account/token issuer or a separately verified OIDC identity. The backend must validate issuer, audience, signature, expiry and subject, and map the verified subject to an immutable internal user ID. Do not trust a client-supplied userId field. If no supported identity provider is approved, stop after the domain/API design; do not ship invitation or membership endpoints.

### Backend and persistence audit

- backend/main.go currently registers REST routes for Listen Together parties, health/time and presence, plus a WebSocket party endpoint. No persistent playlist endpoints are present in the inspected route table.
- backend/party/party.go stores parties, members, playback state and queues in process memory through PartyStore. NewPartyStore initializes a map; CreateWithLimits inserts into it; Sweep removes expired parties.
- backend/config/config.go contains party/presence limits and service configuration, not a durable playlist database connection.
- backend/README.md and backend/render.yaml describe the existing Go service deployment. No collaborative-playlist database or migration system was identified in the inspected backend tree.
- ListenTogether activity is documented as the last 100 actions for the current app session, not durable playlist history.
- A process restart or instance replacement must not erase shared playlists, membership, invitations or activity. The in-memory PartyStore is not a persistence layer.

**Persistence blocker:** Select and provision a durable transactional database before implementation. PostgreSQL is the recommended fit for playlist rows, membership constraints, invitation hashes, ordered entries and transactional authorization. The repository currently does not establish a PostgreSQL client, schema migration tool, or connection contract for this service. Do not assume Render's ephemeral filesystem is durable. Do not silently reuse an unrelated database or add Room migrations.

### Library, playback and product-design audit

- sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/LibraryScreen.kt already owns the Library composition and renders the provider Playlists shelf, device items, replay and library links. This is the correct discovery/entry surface; a new bottom-navigation destination is not justified.
- app/src/main/java/com/music/audify/ui/components/PlaylistPickerSheet.kt is the existing add-to-playlist interaction. It is the natural integration point for a distinct Shared Playlist destination after the backend contract exists.
- app/src/main/java/com/music/audify/playback/PlayerConnection.kt and PlaybackService.kt own the Media3 playback boundary and service behavior. Shared playlists must resolve to the existing playable Song/media path; do not introduce another player or mutate the playback engine to implement playlist collaboration.
- app/src/main/java/com/music/audify/data/listentogether/ListenTogether.kt and app/src/main/java/com/music/audify/playback/PartySync.kt implement synchronized party playback. This is a separate transient room feature and must remain separate from persistent playlist collaboration.
- docs/PRODUCT.md establishes current product/module ownership and hard project rules. docs/DESIGN.md establishes dark-first, artwork-led, compact hierarchy, progressive disclosure, existing Compose/Material 3 components, motion tokens, accessibility and adaptive-form-factor requirements.
- Existing design guidance requires explicit empty/loading/error/unavailable/permission states, stable keys for repeated media occurrences, destructive-action confirmation, system font scaling, TalkBack semantics and reduced-motion support.

### Track identity audit and blocker

The existing Song model represents a YouTube Music/catalogue track. LocalPlaylistStore can also store local content URIs and downloaded IDs. A raw videoId is therefore not a universally portable identity, and a device content URI is not a valid cross-user reference.

Before the API is finalized, define a namespaced, provider-aware track reference:
- provider/source key;
- stable provider track ID;
- optional canonical catalogue ID only where the resolver can prove it;
- display metadata as a non-authoritative snapshot;
- no expiring stream URL, signed URL, device content URI, filesystem path, cookie or provider credential.

For MVP, accept only tracks that the backend can resolve for every collaborator through an approved provider/catalogue integration. Local-only files must be visibly marked unavailable to collaborators and must not be uploaded or represented as playable remote tracks. The client must resolve a shared reference through its existing source-resolution path and handle unavailable/region-restricted/provider-auth-required states honestly.

### Audit conclusion

There is enough existing UI and playback infrastructure to integrate this feature without a parallel player or a new navigation tab. There is not yet enough backend infrastructure to implement secure, persistent collaboration. The critical prerequisites are verified user identity, durable transactional storage and a portable track-reference contract.

## 2. Product definition

A Shared Playlist is an AUDIFY-owned, server-persisted ordered collection of track references with an owner and invited members. All authorized members can view it. Members can contribute tracks and manage their own contributions. The owner controls membership, playlist metadata and destructive actions.

This is not:
- a YouTube Music playlist shared through YouTube privacy settings;
- a Spotify playlist;
- a device-local playlist synchronized by copying SharedPreferences;
- a Listen Together party or synchronized playback session;
- a public social feed, chat room or messaging system.

### MVP user stories

1. As a signed-in AUDIFY user, I can create a Shared Playlist from Library or the add-to-playlist flow.
2. As the owner, I can invite another verified AUDIFY user or create a revocable, expiring invite link.
3. As an invitee, I can preview the playlist and accept or decline an invitation while signed in.
4. As a member, I can add eligible tracks, see who added each entry, and remove entries I contributed.
5. As the owner, I can rename the playlist, update its description/cover, remove any entry, revoke invitations, remove members and delete the playlist.
6. As a member, I can leave without deleting the playlist for everyone.
7. As a listener, I can play or shuffle the playlist through the existing AUDIFY playback pipeline.
8. As a user without a valid AUDIFY identity or network connection, I receive an explicit sign-in/offline state; the app never pretends a local fallback is shared.

### Explicitly deferred

- Public playlist directory, search engine indexing or public profiles.
- Playlist comments, chat, reactions and messaging.
- Synchronized playback or shared queue control.
- Anonymous editing or permanent open invite links.
- Multiple custom roles/permission builders.
- Cross-provider automatic track matching without a reliable canonical ID.
- Offline mutation queue and conflict-free replicated data types.
- TV editing and Wear OS playlist management.

## 3. Information architecture and UI/UX

### Entry points

1. Library: add a distinct Shared Playlists section or shelf, with a clear empty state and Create Shared Playlist action. Keep personal/provider playlists in their current shelves.
2. Playlist picker: add a separate Shared Playlists destination or subsection. Label the destination clearly; never mix AUDIFY membership permissions with YouTube PRIVATE/UNLISTED/PUBLIC privacy.
3. Search results and track overflow: offer Add to playlist, then let the user select a personal or shared destination.
4. Home: add a Shared with You shelf only after the feature has meaningful data and the user can dismiss or hide it. Do not add placeholder recommendations.

Do not add a bottom-navigation tab for the MVP.

### Screen A — Shared Playlists list

Each row/card shows:
- artwork or a deterministic collage of the first available track artworks;
- playlist title;
- track count;
- member avatars/count;
- last activity or updated time;
- owner/member context where useful.

States:
- first-use empty state with a single Create action and concise explanation;
- loading skeleton that preserves layout;
- loaded content;
- offline/stale content with last-synced indicator;
- error with retry;
- invitation pending state if applicable.

Do not fabricate collaborators, artwork, counts or tracks.

### Screen B — Shared Playlist detail

Hierarchy:
1. Top app bar with back, playlist overflow and clear Shared label.
2. Artwork, title, optional description, owner attribution and member avatars/count.
3. Primary Play and Shuffle actions; Invite is visible only to the owner in MVP.
4. Track list with stable entry identity, title, artist, artwork, contributor and overflow actions.
5. Add tracks action for all members.
6. A secondary Activity view/section, not a competing primary panel.

Track rows must distinguish duplicate occurrences by entry ID, not track ID. Show a useful unavailable state if the current device cannot resolve a track. Never silently skip a track while claiming the full playlist is available.

### Screen C — Create Shared Playlist

Fields: title (required, bounded), optional description, optional cover selection from existing artwork. The creation destination is explicitly Shared Playlist. Do not expose YouTube privacy values on this form. Validate title/description both client-side and server-side.

Creation is not successful until the backend confirms persistence. A network failure must leave the form recoverable and must not create a misleading local-only shared playlist.

### Screen D — Invite collaborators

- Search/select verified AUDIFY users only if a supported user directory exists.
- Generate an invite link with a cryptographically random token; display the URL only after server creation succeeds.
- Show expiry and revoke controls.
- Preview recipient-visible playlist name, artwork, owner and member count before acceptance.
- Require authentication before accepting.
- Avoid exposing private playlist contents to unauthenticated visitors. An invite preview can reveal only minimal metadata until accepted.

### Screen E — Members and permissions

Show owner separately from members. Each member row can show display name/avatar and join date. Owner can remove members. Members can leave. Confirm removal/leave where it changes access. If the owner leaves, require an explicit ownership-transfer flow or prevent leaving until ownership is transferred/deleted.

### Screen F — Activity

Show durable, bounded activity such as playlist created/renamed, member joined/left/removed, track added/removed, invitation accepted/revoked. Each event includes actor, event time and relevant entry/member reference. Do not store message text or add a chat system.

### Interaction and visual rules

- Reuse current Material 3 components, typography, spacing, artwork treatment, sheet patterns and MotionTokens. No parallel design system.
- Maintain the current dark-first editorial music identity while supporting the existing light theme.
- Use progressive disclosure: playlist/member management belongs in overflow or a dedicated member sheet.
- Provide clear optimistic/pending/error feedback for every mutation.
- Disable actions only with an explanation when the user lacks permission or a track is unavailable.
- Preserve font scaling, TalkBack semantics, focus order, adequate touch targets and reduced-motion behavior.
- Use stable Compose keys based on playlist ID, member ID and playlist-entry ID.
- Design compact phone layout first; test large screens. TV is read-only in a later phase.

## 4. Domain model and persistence design

Do not extend UserPlaylist to represent shared membership. Add a separate domain model in shared/commonMain only if it remains platform-neutral; otherwise keep persistence/network DTOs in the owning app/backend layers and expose a narrow shared interface.

Recommended logical entities:

### SharedPlaylist
- id: opaque immutable UUID/ULID;
- ownerUserId: verified internal AUDIFY user ID;
- title and optional description;
- cover reference (existing artwork/track reference, not arbitrary untrusted file URL);
- createdAt, updatedAt;
- revision: monotonically increasing server revision;
- deletedAt or explicit deletion state if soft-delete is required for audit/recovery.

### PlaylistMember
- playlistId;
- userId;
- role: OWNER or MEMBER;
- joinedAt;
- unique constraint on (playlistId, userId);
- owner uniqueness/transfer invariant.

### PlaylistEntry
- id: immutable unique occurrence ID;
- playlistId;
- provider/source key and stable provider track ID;
- display snapshot (title, artist, artwork reference, duration) for rendering while resolving;
- addedByUserId and addedAt;
- order key/position;
- revision or mutation metadata.

Do not use track ID as the entry primary key. Repeated entries must remain addressable independently. Decide and document whether exact duplicate provider tracks are allowed; the recommended behavior is to allow duplicates and identify each by entry ID.

### PlaylistInvitation
- id;
- playlistId;
- createdByUserId;
- tokenHash only (never store raw token);
- optional intended recipient user ID;
- createdAt, expiresAt, revokedAt, acceptedAt;
- maxUses/uses only if reusable links are explicitly approved;
- one-time acceptance semantics for MVP.

### PlaylistActivity
- id;
- playlistId;
- actorUserId;
- eventType;
- target entry/member/invitation ID;
- createdAt;
- minimal event metadata with strict size limits.

### Database constraints and transactions

- All playlist reads and writes are scoped by verified membership.
- Foreign keys enforce playlist/member/entry relationships.
- Unique membership constraint prevents duplicate joins.
- A playlist has exactly one owner; ownership transfer is one transaction.
- Invitation acceptance locks/consumes the invitation and inserts membership atomically.
- Track addition inserts the entry and activity event in one transaction.
- Entry removal validates actor permissions and records activity atomically.
- Reorder uses a revision check and stable entry IDs; stale clients receive a conflict and refresh.
- Use database time for expiry checks and server-side timestamps for ordering.
- Add indexes for owner/member lookup, playlist entries ordered by playlist, active invitation hash lookup, and recent activity by playlist.
- Apply bounded pagination for entries and activity; never return unbounded collections.

**Schema approval gate:** This feature requires new durable schema. The project rules prohibit database/Room schema changes without explicit human approval. This plan is not that approval. Before implementation, present the selected database, migration approach, backup/restore implications, retention policy and schema diff for explicit approval. Do not add Room tables or migrations for server-owned shared data.

## 5. API and security contract

The exact route naming can follow existing Go conventions, but the contract must include equivalent operations:

| Operation | Authorization | Required behavior |
|---|---|---|
| Create playlist | Verified user | Create owner membership and playlist atomically |
| List my playlists | Verified user | Return only playlists where caller is a member |
| Get playlist | Verified member | Return metadata, ordered entries and member summary |
| Update metadata | Owner | Validate title/description/cover; increment revision |
| Delete playlist | Owner | Confirm/soft-delete according to approved retention policy |
| Add entry | Member | Validate portable track reference; record actor; increment revision |
| Remove entry | Owner or original contributor | Address entry by immutable entry ID |
| Reorder entries | Member or owner per approved policy | Compare expected revision; reject stale order writes |
| Create invitation | Owner | Return raw token once; persist only its hash |
| Preview invitation | Valid invite token | Return minimal metadata; do not disclose private track list |
| Accept invitation | Verified invitee | Validate expiry/revocation/recipient/use count; consume atomically |
| Revoke invitation | Owner | Make token unusable immediately |
| Remove member | Owner | Revoke access immediately; prevent removing owner |
| Leave playlist | Member | Remove own membership; owner must transfer or delete first |
| Read activity | Verified member | Return bounded, paginated activity |

### Identity verification

- Validate access tokens on every request; never accept a user ID as proof.
- Map token subject to immutable internal user ID.
- Do not use display names or avatars for authorization.
- Do not trust client-supplied ownerId, addedBy, role, createdAt or activity actor.
- Keep provider cookies and access tokens out of playlist rows, logs, analytics and invitation URLs.
- Use HTTPS for all network operations.
- Keep auth errors generic enough not to disclose private playlist existence to non-members.

### Invitation token handling

- Generate at least 256 bits of cryptographically secure random entropy.
- Persist a one-way cryptographic hash, not the raw token.
- Return raw token only in the successful create response.
- Apply expiration and immediate revocation.
- Rate-limit creation, preview and acceptance by account and IP.
- Prevent token reuse after one-time acceptance.
- Bind recipient-specific invites to the intended verified user ID.
- Avoid logging request URLs containing invitation tokens; prefer token-in-body or a redacted path/query contract.
- Do not treat an invite link as a permanent bearer credential.

### Abuse and data limits

- Bound playlist title/description, entries per playlist, members, invitations, page sizes and activity retention.
- Rate-limit playlist creation, entry additions, reorders and invite generation.
- Validate cover references against approved artwork sources; do not proxy arbitrary URLs.
- Prevent mass-invite enumeration and user-directory scraping.
- Add audit logs for owner/member/invitation actions.
- Define account deletion and data export behavior before launch.

## 6. Synchronization and conflict behavior

MVP should favor correctness over a premature realtime architecture.

1. Server is the source of truth.
2. Every mutation carries an expected playlist revision and an idempotency key.
3. Successful mutation returns the new revision and affected entry/member.
4. Clients refresh on screen entry, pull-to-refresh, and after a mutation.
5. Use conditional requests/ETag or revision polling to avoid downloading unchanged playlists.
6. A stale revision returns a conflict response; client reloads and asks the user to retry the intended action rather than overwriting unseen edits.
7. Reorder requests submit ordered entry IDs, not a whole client-owned playlist document.
8. Retry only idempotent operations with the same idempotency key.
9. Keep offline behavior read-only in MVP. Queueing offline edits is deferred until conflict and identity semantics are proven.

Realtime can be added after the durable API is stable. If implemented, use a separate authenticated playlist event channel or a documented event transport; do not overload Listen Together's party playback protocol. Events are hints to refetch authoritative state, not authoritative state themselves.

## 7. Playback integration

- Shared playlists produce ordered portable track references.
- The client resolves each reference using existing provider/source resolution and the current Song/MediaItem path.
- Do not create a second playback engine, queue manager or media service.
- Do not persist streaming URLs or provider credentials in the shared database.
- Keep player queue occurrence identity separate from playlist entry identity. A track can be queued multiple times; each queue occurrence remains distinct.
- On partial resolution, show which tracks are unavailable and offer Play available tracks only with explicit user acknowledgement; never silently claim the full playlist played.
- If a provider requires the listener's own sign-in, show that requirement locally. Never share one user's provider cookie with collaborators.
- Shared playlist editing must not mutate the current playback queue unless the user explicitly chooses to refresh/replace it.

## 8. Implementation phases and gates

Do not combine these phases into one patch. Stop at any failed gate and update this plan with evidence.

### Phase 0 — Architecture decisions and prerequisites

- Select a first-party/verified identity issuer and token-validation contract.
- Select and provision durable PostgreSQL (or an explicitly approved equivalent).
- Select a migration tool and backup/restore strategy.
- Define portable provider track references and eligibility rules.
- Approve server-owned schema and account/data-retention policy.
- Confirm deployment environment, secrets, health checks and rollback strategy.

**Gate:** no implementation of member/invite endpoints until identity and durable storage are approved and testable.

### Phase 1 — Domain, repository and API foundation

- Add server-side playlist/member/entry/invitation/activity models.
- Add database migrations, indexes, constraints and repository transactions only after schema approval.
- Add authenticated API middleware and verified internal user identity.
- Add create/list/get/update/delete endpoints and authorization tests.
- Add client repository interfaces and typed DTO/error mapping.
- Keep existing local/provider playlist models and stores unchanged.

**Gate:** integration tests prove persistence across process restart and cross-user isolation.

### Phase 2 — Shared playlist UI and track contribution

- Add Shared Playlists section to Library.
- Add create flow and detail screen.
- Add the shared destination to the playlist picker/search overflow.
- Add and remove entries with contributor identity.
- Resolve portable tracks through existing playback/source infrastructure.
- Add loading, empty, offline, permission, unavailable-track, stale and error states.
- Preserve personal playlist creation and editing behavior.

**Gate:** two verified test accounts can create, view and edit the same playlist; personal playlist regression suite passes.

### Phase 3 — Invitations and membership lifecycle

- Add invite creation, preview, acceptance, revocation and expiry.
- Add member list, remove member and leave playlist.
- Add ownership transfer or enforce owner transfer/delete before leaving.
- Add rate limits, enumeration protections and token redaction.
- Test revoked/expired/replayed/wrong-recipient invitations.

**Gate:** a removed/left member loses access on the next request; no stale token or client cache restores access.

### Phase 4 — Ordering, activity and synchronization

- Add stable playlist entry IDs and reorder with revision checks.
- Add durable activity events.
- Add conditional refresh and stale-write handling.
- Add bounded pagination.
- Add duplicate-entry behavior and concurrent edit tests.
- Add retry/idempotency handling for network interruptions.

**Gate:** concurrent edits cannot silently lose entries or reorder based on stale client state; process restart preserves playlist and activity.

### Phase 5 — Polish and optional realtime

- Add member avatars/count and Shared with You shelf.
- Add activity UI and last-updated state.
- Add realtime event hints only if polling is measurably inadequate.
- Validate light/dark, large fonts, TalkBack, keyboard/focus and adaptive widths.
- Consider read-only TV support only after Android phone flow is stable.

**Gate:** UX, accessibility and device verification are recorded; no unsupported platform is advertised as editable.

## 9. Exact likely code areas

This is a candidate inventory, not blanket authorization to modify every file. Re-run repository-wide searches and reconcile against the actual HEAD before implementation.

### Existing integration points

- sharedUi/src/jvmSharedMain/kotlin/com/music/audify/ui/screens/LibraryScreen.kt — Library entry point and shelf layout.
- app/src/main/java/com/music/audify/ui/components/PlaylistPickerSheet.kt — add-to-playlist destination.
- app/src/main/java/com/music/audify/ui/PlaylistCreation.kt — provider/local creation policy; do not reuse its fallback semantics for shared creation.
- app/src/main/java/com/music/audify/data/spotify/LocalPlaylistStore.kt — preserve as device-local storage; do not repurpose.
- shared/src/commonMain/kotlin/com/music/audify/data/model/Models.kt — existing Song/UserPlaylist/PlaylistPrivacy; add separate domain types only if their semantics are truly cross-platform.
- app/src/main/java/com/music/audify/playback/PlayerConnection.kt — existing media identity conversion boundary.
- app/src/main/java/com/music/audify/playback/PlaybackService.kt — existing playback ownership; shared playlist should feed existing queue only.
- app/src/main/java/com/music/audify/data/listentogether/ListenTogether.kt and app/src/main/java/com/music/audify/playback/PartySync.kt — inspect for separation; do not couple persistent playlists to party membership.
- app/src/main/java/com/music/audify/auth/ — identity integration only after a supported issuer is selected; provider credentials are not first-party identity.
- backend/main.go — route registration and HTTP middleware.
- backend/config/config.go — durable database and identity configuration, after approved deployment design.
- backend/protocol/ — only if a shared API DTO genuinely belongs here; avoid changing the party wire protocol.
- backend/ — new playlist service/repository/migration packages and integration tests.

### Tests to add or extend

- backend unit tests for playlist state, entry ordering, invitation lifecycle and permission matrix.
- backend database integration tests for transactions, constraints and restart persistence.
- backend HTTP tests for verified identity, authorization, validation, rate limits and error contracts.
- client unit tests for DTO mapping, repository state, retry/idempotency and unavailable track handling.
- Compose/UI tests for Library entry, creation, picker, detail, member actions, empty/error/offline/permission states and back behavior.
- playback regression tests proving shared entries resolve through existing Media3 path and do not alter party playback.
- existing playlist creation/ownership/local store tests must remain green.

## 10. Acceptance criteria

The feature is not done until all are true:

- [ ] Two distinct verified AUDIFY users can access the same persistent playlist.
- [ ] A client cannot impersonate another user by changing userId/displayName/avatar fields.
- [ ] Playlist and membership survive backend process restart and deployment replacement.
- [ ] Non-members cannot read private playlist contents or mutate anything.
- [ ] Owner/member permissions are enforced by the backend on every mutation.
- [ ] Invite tokens are hashed, expiring, revocable, rate-limited and one-time for MVP.
- [ ] Removed/left members lose access immediately on subsequent requests.
- [ ] Track references are portable and do not contain stream URLs, cookies or device-local paths.
- [ ] Unavailable tracks are represented honestly and are not silently fabricated or skipped.
- [ ] Entries have immutable IDs so duplicate tracks can be reordered/removed independently.
- [ ] Concurrent edits and retries cannot silently duplicate or overwrite entries.
- [ ] Activity records the actor and server time without becoming a chat system.
- [ ] Existing local, YouTube Music and Spotify playlist behavior remains unchanged.
- [ ] Shared playlist playback uses the existing playback service and queue.
- [ ] Phone UI passes light/dark, large-font, TalkBack, focus and error-state checks.
- [ ] Backend, client, integration and regression tests pass; physical-device verification is recorded.

## 11. Performance and operations

- Paginate playlist entries and activity; cap page sizes.
- Fetch member summaries in bounded queries; avoid one query per row.
- Index membership lookup and ordered entry retrieval.
- Use transactions for all multi-row authorization/mutation operations.
- Use revision/ETag conditional refresh to limit polling cost.
- Set explicit request and response size limits.
- Add metrics for create/add/remove/invite/accept failures, conflict rate, resolution failures and API latency without logging tokens or private track metadata.
- Add health checks that distinguish process liveness from database readiness.
- Back up the durable database and test restore before production rollout.
- Keep migration rollout backward-compatible with the previous app version; do not require an app update to preserve already-created shared data.

## 12. Rollback

- Disable shared-playlist creation/invitations through a server-side feature flag while keeping existing personal playlists fully functional.
- Keep the database data intact during rollback; do not drop tables or delete member/playlist rows as emergency cleanup.
- Revert client entry points independently from the backend.
- Keep API changes additive until the minimum supported app version is known.
- If a migration is faulty, restore from a verified backup or apply a forward repair migration; do not assume destructive down-migrations are safe.
- Do not change existing local playlist JSON, YouTube Music playlists, Spotify imports, playback queue persistence or party state during rollback.

## 13. Rejected alternatives

- **Reuse Listen Together PartyStore:** rejected because it is in-memory, expires transient rooms, is playback-oriented and has no verified durable user identity.
- **Use Google/YouTube cookies as AUDIFY identity:** rejected because they are provider credentials, account/profile selection can change, and the backend has no safe validation contract for them.
- **Store shared playlists in LocalPlaylistStore:** rejected because SharedPreferences is device-local and cannot coordinate users or survive reinstall/device loss.
- **Represent shared playlists as UserPlaylist:** rejected because UserPlaylist encodes provider/local playlist IDs and browse semantics, not AUDIFY ownership, membership or permissions.
- **Use YouTube PlaylistPrivacy as collaboration roles:** rejected because PRIVATE/UNLISTED/PUBLIC is provider visibility, not member authorization.
- **Build a new playback engine or party protocol:** rejected because existing Media3 and Listen Together have separate ownership and should remain the playback foundation.
- **Start with public discovery, chat or synchronized queue:** rejected because each expands moderation, privacy, abuse and synchronization scope before the core collaboration contract is proven.

## 14. Constraints and approval boundary

- This plan is product/architecture planning only. It does not authorize implementation.
- Explicit approval is required before adding a database schema, Room migration, new database service, identity provider, paid infrastructure, or changing account/authentication behavior.
- Never bump app versionCode/versionName as part of this feature unless separately authorized.
- English Android strings go only in app/src/main/res/values/sachit_strings.xml; do not edit translated resources.
- Preserve all existing personal/provider/local playlist data and behavior.
- Do not alter signing assets, keystore properties or release credentials.
- No unrelated UI redesign, party rewrite, playback-engine rewrite, release workflow changes or deployment activation.
- Follow repository plan rules: inspect git status and current HEAD before implementation, read docs/PRODUCT.md and docs/DESIGN.md, reconcile file drift, use JDK 21, and do not commit/push/merge unless separately authorized for the implementation work.

## 15. Audit limitations and next required decisions

This audit was performed from public GitHub source at the stated baseline. The local workspace tunnel was unavailable, so the auditor could not inspect uncommitted local changes, run Gradle/Go tests, inspect deployed database infrastructure, verify production environment variables, or test two real accounts. The repository's public source does not prove that no external identity/database service exists outside the repository; deployment configuration must be inspected before selecting the final architecture.

Before Phase 0 can close, answer:
1. Which verified identity issuer will represent an AUDIFY user across devices?
2. Which durable database and hosting/backup arrangement is approved?
3. Which catalogue providers can supply stable, portable track IDs for the first release?
4. Should all members be allowed to reorder entries, or should reordering be owner-only in MVP?

Until these decisions are approved, keep the plan in **planned** status and do not begin feature implementation.
