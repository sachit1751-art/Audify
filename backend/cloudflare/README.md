# AUDIFY Listen Together — Cloudflare Durable Objects

This directory contains the Cloudflare replacement for the Go/Render Listen Together service.

## Design

- One SQLite-backed `PartyRoom` Durable Object per six-character party code.
- Room membership, bearer tokens, host assignment, queue and playback anchors are persisted together in a single SQLite row.
- The public REST routes and WebSocket frame names are kept compatible with the existing clients.
- A singleton Durable Object stores live-presence heartbeats and serializes create/presence rate limits in SQLite.
- WebSockets use Cloudflare's hibernation API. Per-socket member identity is stored in a serialized attachment; room state is reloaded from SQLite after an eviction or runtime restart.
- Existing clients ping every 15 seconds. Each ping returns the clock pong and fans out the authoritative state frame; a 45-second Durable Object alarm handles idle connections and lifecycle cleanup instead of running a five-second alarm loop.

## Deployment

From this directory, run `npx wrangler deploy`. The `wrangler.toml` declares the `PARTY_ROOMS` binding and SQLite-backed `PartyRoom` export. The Worker is deployed to the connected Cloudflare account's `workers.dev` subdomain; the resulting URL is printed by Wrangler.

Set the exact browser origins in `JAM_ALLOWED_ORIGINS` (the repository's production web origins are configured in `wrangler.toml`). Native clients send no `Origin` header. Do not use a wildcard. No paid Durable Object, D1, KV, R2, or third-party service is configured here.

## Production cutover

The account owner confirmed that the connected account is on Workers Free. Production Android, Android TV, CI builds and the web build workflow now use `https://audify-listen-together.sachit1751.workers.dev`. No plan or billing change was made. The connected account exposes only the unrelated `sachit-portfolio` Pages project, not AUDIFY's Pages project; set `VITE_PARTY_SERVER_URL` to this Worker URL in the actual AUDIFY Pages project's production and preview environments before publishing a web release. Until then, an independently deployed Pages app may retain its previous environment value.

## Client protocol coverage

- Android and web attach a fresh UUID `eventId` to every control frame. The Worker deduplicates `(memberId, eventId)`; older Go servers safely ignore the extra JSON field.
- Android TV compiles the Android app's shared `app/src/main/java` source tree, so it uses the same Listen Together transport and idempotency implementation. A regression assertion guards this source-set relationship.
- The desktop application currently implements Spotify Jam through Spotify Connect, not the AUDIFY `/api/parties` and `/ws/parties` protocol. It is deliberately not redirected to this Worker. A regression assertion guards that boundary; desktop Spotify Jam remains unchanged.
- `npm run test:integration` checks REST response shapes, presence platform counts and validation, exact SVG badge markup, active/missing/malformed invite pages, Android and browser WebSocket handshakes, multiple participants, duplicate event IDs, playback/queue updates, host controls, reconnect, and client-source boundaries.

For local protocol checks, use `npx wrangler dev`. Run `npm test` for lifecycle tests and `npm run test:integration` with a local Worker at `127.0.0.1:8787` for the REST/WebSocket suite. Set `WORKER_URL` to the deployed URL to run the remote suite; its fixed-source-IP rate-limit case is intentionally skipped remotely.

## Compatibility checklist

- REST: `/`, `/healthz`, `/api/time`, create, join, snapshot, preview, leave, presence, live stats, SVG badge and invite landing.
- WebSocket: bearer token for native clients; browser `audify.v1, audify.token.<token>` offer with `audify.v1` as the only selected subprotocol; welcome, state, queue, members, pong, error, bye and activity frames.
- State: room membership, host identity, queue, playback position and server-time anchor are stored in Durable Object SQLite.
- Lifecycle: 15-second client-ping state heartbeat; 45-second disconnected-member grace and cleanup alarm; 120-second empty-room TTL; 12-hour maximum room age.
- Abuse controls: 50 active rooms; two party creations per IP per minute; ten join attempts and thirty authenticated endpoint attempts per IP per minute; twenty presence pings per IP per minute; bounded presence/rate-limit key tables; per-socket frame/control token buckets.

These are implementation targets, not a claim that the full production test matrix has passed. Cross-client integration and live cutover remain gated on the tests listed in the release checklist.

## Free-plan capacity assumptions

This configuration uses SQLite Durable Objects and Workers only. Cloudflare's current Free limits include 100,000 Worker requests/day, 100,000 Durable Object requests/day (including WebSocket messages and alarm invocations), 13,000 GB-s/day of Durable Object compute, 100,000 SQL row writes/day and 5 GB total SQLite storage. Each participant ping is a Durable Object message; each REST operation and the global directory/rate-limit operations also consume requests. Monitor usage before broad rollout. Free-tier exhaustion causes requests to fail; this project does not configure an upgrade or paid fallback.

## Rollback

Rollback is an endpoint/configuration change, not a data migration. Restore the Android default and CI `LISTEN_TOGETHER_SERVER` values to `https://audify-listen-together-qdbd.onrender.com` (Android) and `https://audify-listen-together.onrender.com` (TV), restore `backend/tools/jam_probe.py` to its previous Render URL, and restore Pages `VITE_PARTY_SERVER_URL` to its previous production value. Rebuild/redeploy affected clients. Keep the Worker deployed during the rollback window. Rooms are not replicated between Render and Cloudflare; active rooms created on one backend do not move to the other.

## Runtime bindings

The Worker expects a SQLite-backed Durable Object namespace binding named `PARTY_ROOMS`, mapped to the `PartyRoom` class through the declarative `exports.PartyRoom` configuration.

Set `JAM_ALLOWED_ORIGINS` to the comma-separated exact web origins. Native clients send no `Origin` header. Do not use a wildcard for browser origins.

## Safety during cutover

Do not point the production app builds at this Worker until the compatibility suite passes. Existing Render rooms cannot be transferred automatically: the Go server's in-memory rooms are not exported, and the two services do not share a database. New rooms created after cutover will live in Durable Object storage.
