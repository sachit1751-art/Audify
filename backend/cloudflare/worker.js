// AUDIFY Listen Together — Cloudflare Workers + SQLite Durable Objects.
// The public REST and WebSocket wire contract intentionally matches backend/main.go.
import { DurableObject } from 'cloudflare:workers';
import { isRoomExpired, nextCleanupAt } from './lifecycle.js';

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTUVWXYZ';
const CODE_LENGTH = 6;
const MAX_BODY = 16 * 1024;
const MAX_FEEDBACK_BODY = 8 * 1024;
const MAX_UPCOMING = 25;
const PLAY_LEAD_MS = 350;
const DISCONNECT_GRACE_MS = 45_000;
const EMPTY_TTL_MS = 120_000;
const PARTY_MAX_AGE_MS = 12 * 60 * 60 * 1000;
const CONNECTION_IDLE_MS = 15 * 60 * 1000;
const FRAME_RATE = 30;
const CONTROL_RATE = 25;

const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', ...headers },
});
const err = (status, code, message) => json({ error: code, message }, status);
const now = () => Date.now();
const token = (bytes = 24) => [...crypto.getRandomValues(new Uint8Array(bytes))].map(x => x.toString(16).padStart(2, '0')).join('');
const normalise = value => [...String(value ?? '').trim().toUpperCase()].filter(c => /[A-Z0-9]/.test(c)).map(c => c === 'O' ? '0' : (c === 'I' || c === 'L') ? '1' : c).join('');
const makeCode = () => [...crypto.getRandomValues(new Uint8Array(CODE_LENGTH))].map(x => ALPHABET[x % ALPHABET.length]).join('');
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const clampText = (value, max) => typeof value === 'string' ? value.slice(0, max) : '';
const FEEDBACK_CATEGORIES = new Set(['Playback', 'Playlists', 'Discovery', 'Library', 'Downloads', 'Lyrics', 'Appearance', 'Accessibility', 'Performance', 'Android Auto', 'Android TV', 'Wear OS', 'Other']);

async function feedbackIpKey(ip) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(ip || 'unknown'));
  return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
}

function validateFeedback(body) {
  if (!isObject(body)) return 'Malformed JSON body.';
  if (!['bug', 'feature'].includes(body.kind)) return 'Choose a bug report or feature suggestion.';
  if (!FEEDBACK_CATEGORIES.has(body.category)) return 'Choose a valid category.';
  const title = clampText(body.title, 121).trim();
  const description = clampText(body.description, 6001).trim();
  if (title.length < 4 || title.length > 120) return 'Title must be between 4 and 120 characters.';
  if (/[\r\n\u0000-\u001f]/.test(title)) return 'Title must be a single line.';
  if (description.length < 20 || description.length > 6000) return 'Description must be between 20 and 6000 characters.';
  if (body.diagnostics != null && !isObject(body.diagnostics)) return 'Diagnostic details are invalid.';
  return null;
}

function feedbackIssueBody(body) {
  const kind = body.kind === 'bug' ? 'Bug report' : 'Feature suggestion';
  const diagnostics = isObject(body.diagnostics) ? body.diagnostics : {};
  const oneLine = (value, max) => clampText(value, max).replace(/[\r\n\t]+/g, ' ').trim();
  const lines = [
    '## Description', '', clampText(body.description, 6000).trim(), '',
    '## Category', '', clampText(body.category, 40), '',
    '## App details', '',
    `- Platform: ${oneLine(body.platform, 30) || 'Android'}`,
    `- Audify version: ${oneLine(body.version, 40) || 'Not provided'}`,
  ];
  if (Object.keys(diagnostics).length) {
    lines.push('', '## Optional diagnostics', '');
    for (const [label, value] of [['OS', diagnostics.os], ['Device', diagnostics.device]]) {
      if (typeof value === 'string' && value.trim()) lines.push(`- ${label}: ${oneLine(value, 120)}`);
    }
  }
  lines.push('', '---', '*Submitted anonymously from Audify. Do not post passwords, tokens, account details, private listening history, or personal files.*');
  return { title: `[${kind}] ${clampText(body.title, 120).trim()}`, body: lines.join('\n'), labels: body.kind === 'bug' ? ['bug'] : ['enhancement'] };
}

function inviteHtml({ code, origin, active, members = 0, title = '', artist = '' }) {
  const safe = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
  const deep = `audify://party/${encodeURIComponent(code)}?server=${encodeURIComponent(origin)}`;
  const intent = `intent://party/${encodeURIComponent(code)}?server=${encodeURIComponent(origin)}#Intent;scheme=audify;end`;
  const activeContent = `<div class="badge"><span class="badge-dot"></span><span>Listen Together • ${members} in party</span></div>
    <h1>Join the Music Party</h1><p class="subtitle">Opening Audify to sync playback in real time.</p>
    <div class="code-box"><div class="code-label">Party Code</div><div class="code-val">${safe(code)}</div>
    ${title ? `<div class="now-playing">🎵 ${safe(title)}${artist ? ` - ${safe(artist)}` : ''}</div>` : ''}</div>
    <a id="joinBtn" href="${safe(intent)}" class="btn">Join Party in Audify</a>
    <p class="footer-note">Didn't open automatically? Tap the button above.<br>Don't have Audify yet? <a href="https://github.com/kushagrasinghx/Audify/releases" target="_blank" rel="noopener">Download it here</a>.</p>
    <script>var intentUri=${JSON.stringify(intent)};var deepLink=${JSON.stringify(deep)};function launch(){window.location.href=/Android/i.test(navigator.userAgent)?intentUri:deepLink;}setTimeout(launch,100);</script>`;
  const inactiveContent = `<div class="badge"><span class="badge-dot offline"></span><span>Party Inactive</span></div>
    <h1>Party Not Found</h1><p class="subtitle">This party code has expired or does not exist on this server.</p>
    <div class="code-box"><div class="code-label">Party Code</div><div class="code-val" style="color:#a1a1aa">${safe(code)}</div></div>
    <p class="footer-note">Please ask the host for a new invite link or check your server configuration.</p>`;
  return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Audify Listen Together - Party ${safe(code)}</title><style>
*{box-sizing:border-box;margin:0;padding:0}body{background:#0b0b0e;color:#f3f3f7;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}.container{background:rgba(22,22,30,.85);backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);border:1px solid rgba(255,255,255,.08);border-radius:28px;max-width:440px;width:100%;padding:36px 28px;text-align:center;box-shadow:0 20px 50px rgba(0,0,0,.5),0 0 40px rgba(124,77,255,.1)}.badge{display:inline-flex;align-items:center;gap:6px;background:rgba(124,77,255,.15);color:#b388ff;font-size:13px;font-weight:600;padding:6px 14px;border-radius:9999px;margin-bottom:20px;letter-spacing:.5px}.badge-dot{width:8px;height:8px;background:#00e676;border-radius:50%;box-shadow:0 0 8px #00e676}.badge-dot.offline{background:#ff5252;box-shadow:0 0 8px #ff5252}h1{font-size:24px;font-weight:700;margin-bottom:8px;letter-spacing:-.5px}.subtitle{color:#9e9ea7;font-size:15px;line-height:1.5;margin-bottom:28px}.code-box{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.1);border-radius:18px;padding:16px 20px;margin-bottom:28px}.code-label{font-size:12px;color:#71717a;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:6px}.code-val{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:32px;font-weight:800;letter-spacing:6px;color:#fff}.now-playing{font-size:14px;color:#d1d1d6;margin-top:10px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.btn{display:block;width:100%;padding:16px 24px;background:linear-gradient(135deg,#7c4dff,#3d5afe);color:#fff;font-size:16px;font-weight:600;text-decoration:none;border-radius:16px;border:none;cursor:pointer;transition:transform .15s ease,opacity .15s ease;box-shadow:0 8px 24px rgba(124,77,255,.35)}.btn:hover{transform:translateY(-1px);opacity:.95}.btn:active{transform:scale(.98)}.footer-note{margin-top:24px;font-size:13px;color:#71717a;line-height:1.5}.footer-note a{color:#b388ff;text-decoration:none}.footer-note a:hover{text-decoration:underline}
</style></head><body><div class="container">${active ? activeContent : inactiveContent}</div></body></html>`;
}

function validJoin(body) {
  if (!isObject(body)) return 'Malformed JSON body.';
  body.userId = clampText(body.userId, 129).trim();
  body.deviceId = clampText(body.deviceId, 129).trim();
  body.displayName = clampText(body.displayName, 81).trim();
  if (!body.userId || body.userId.length > 128) return 'userId must be between 1 and 128 characters';
  if (!body.deviceId || body.deviceId.length > 128) return 'deviceId must be between 1 and 128 characters';
  if (!body.displayName || body.displayName.length > 80) return 'displayName must be between 1 and 80 characters';
  if (body.avatarUrl != null && body.avatarUrl !== '') {
    try { const u = new URL(body.avatarUrl); if (!['http:', 'https:'].includes(u.protocol) || body.avatarUrl.length > 1000) throw 0; }
    catch { return 'avatarUrl must be an http(s) URL'; }
  } else body.avatarUrl = null;
  if (body.maxMembers != null && (!Number.isInteger(body.maxMembers) || body.maxMembers < 2 || body.maxMembers > 10)) return 'maxMembers must be between 2 and 10';
  return null;
}

function trackOf(raw) {
  if (!isObject(raw) || typeof raw.videoId !== 'string' || !raw.videoId) return null;
  return {
    videoId: raw.videoId.slice(0, 128), title: clampText(raw.title, 300), artist: clampText(raw.artist, 300),
    ...(typeof raw.thumbnailUrl === 'string' && raw.thumbnailUrl ? { thumbnailUrl: raw.thumbnailUrl.slice(0, 1000) } : {}),
    ...(Number.isFinite(raw.durationMs) && raw.durationMs > 0 ? { durationMs: Math.trunc(raw.durationMs) } : {}),
    fromAutoplay: raw.fromAutoplay === true,
  };
}

function newRoom(code, maxMembers = 5, autoplayEnabled = false) {
  const t = now();
  return { code, createdAtMs: t, touchedAtMs: t, maxMembers, hostOnlyControl: false, emptySinceMs: t,
    members: [], playback: { seq: 0, track: null, queueIndex: -1, isPlaying: false, positionMs: 0, anchorMs: t,
      queue: [], queueSeq: 0, updatedBy: null, updatedAtMs: t, startedBy: null, startedByName: null, autoplayEnabled },
    recentEvents: [] };
}

function positionAt(p, t = now()) {
  if (!p.isPlaying) return p.positionMs;
  const value = p.positionMs + Math.max(0, t - p.anchorMs);
  return p.track?.durationMs ? Math.min(value, p.track.durationMs) : value;
}
function touch(p, memberId, queue = false) {
  if (queue) p.playback.queueSeq++;
  else p.playback.seq++;
  p.playback.updatedBy = memberId;
  p.playback.updatedAtMs = now();
  p.touchedAtMs = now();
}
function memberWire(m) {
  return { memberId: m.memberId, userId: m.userId, displayName: m.displayName, avatarUrl: m.avatarUrl,
    isHost: m.isHost, connected: m.connected, joinedAtMs: m.joinedAtMs, lastSeenMs: m.lastSeenMs };
}
function playbackWire(room, t = now()) {
  const p = room.playback;
  return { seq: p.seq, track: p.track, queueSeq: p.queueSeq, queueIndex: p.queueIndex, queueLength: p.queue.length,
    isPlaying: p.isPlaying, positionMs: p.positionMs, anchorMs: p.anchorMs, effectivePositionMs: positionAt(p, t),
    updatedBy: p.updatedBy, startedBy: p.startedBy, startedByName: p.startedByName,
    autoplayEnabled: p.autoplayEnabled, updatedAtMs: p.updatedAtMs };
}
function queueWire(room) { const p = room.playback; return { seq: p.queueSeq, index: p.queueIndex, items: p.queue }; }
function snapshot(room) {
  const t = now();
  return { code: room.code, createdAtMs: room.createdAtMs, maxMembers: room.maxMembers, hostOnlyControl: room.hostOnlyControl,
    members: [...room.members].sort((a, b) => a.joinedAtMs - b.joinedAtMs).map(memberWire),
    playback: playbackWire(room, t), queue: queueWire(room), serverMs: t };
}
function membersFrame(room) { return { type: 'members', members: room.members.map(memberWire), maxMembers: room.maxMembers, hostOnlyControl: room.hostOnlyControl, serverMs: now() }; }
function stateFrame(room) { return { type: 'state', playback: playbackWire(room), serverMs: now() }; }
function queueFrame(room) { return { type: 'queue', queue: queueWire(room), serverMs: now() }; }
function randomMember(room, body) {
  const t = now();
  const existing = room.members.find(m => m.deviceId === body.deviceId);
  if (existing) {
    Object.assign(existing, { userId: body.userId, displayName: body.displayName, avatarUrl: body.avatarUrl, token: token(), lastSeenMs: t });
    return existing;
  }
  if (room.members.length >= room.maxMembers) return null;
  const member = { memberId: token(8), userId: body.userId, deviceId: body.deviceId, displayName: body.displayName,
    avatarUrl: body.avatarUrl, token: token(), isHost: room.members.length === 0, connected: false,
    joinedAtMs: t, lastSeenMs: t, frameTokens: FRAME_RATE, frameAt: t, controlTokens: CONTROL_RATE, controlAt: t };
  room.members.push(member);
  return member;
}
function constantTimeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  let difference = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let i = 0; i < length; i++) difference |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return difference === 0;
}
function auth(room, bearer) { return room.members.find(m => m.token && bearer && constantTimeEqual(m.token, bearer)) ?? null; }
function bucket(member, kind) {
  const t = now(), isFrame = kind === 'frame', rate = isFrame ? FRAME_RATE : CONTROL_RATE;
  const key = isFrame ? 'frameTokens' : 'controlTokens', at = isFrame ? 'frameAt' : 'controlAt';
  member[key] = Math.min(rate, member[key] + Math.max(0, t - member[at]) / 1000 * rate);
  member[at] = t;
  if (member[key] < 1) return false;
  member[key] -= 1;
  return true;
}
function errorFrame(code, message) { return { type: 'error', error: code, message }; }
function nearestIndex(queue, videoId, around) {
  if (queue[around + 1]?.videoId === videoId) return around + 1;
  if (queue[around - 1]?.videoId === videoId) return around - 1;
  let best = -1, distance = Infinity;
  queue.forEach((item, i) => { if (item.videoId === videoId) { const d = i >= around ? i - around : (around - i) * 2 + 1; if (d < distance) { best = i; distance = d; } } });
  return best;
}
function activity(member, action, frame) {
  let detail = action;
  const title = frame.track?.title;
  if (action === 'setTrack' && title) detail = `Changed the song to "${title}"`;
  else if (action === 'queueAdd') { const tracks = frame.tracks ?? (frame.track ? [frame.track] : []); if (tracks.length && tracks[0]?.title) detail = tracks.length === 1 ? `Added "${tracks[0].title}" to the queue` : `Added "${tracks[0].title}" and ${tracks.length - 1} more to the queue`; }
  else if (action === 'setQueue') detail = `Replaced the queue with ${(frame.queue ?? []).length} tracks`;
  else if (action === 'queueRemove') detail = 'Removed a track from the queue';
  else if (action === 'queueClear') detail = 'Cleared upcoming tracks';
  else if (action === 'queueMove') detail = 'Reordered the upcoming queue';
  else if (action === 'next') detail = 'Skipped to the next song';
  else if (action === 'previous') detail = 'Went back to the previous song';
  else if (action === 'play') detail = 'Started playback';
  else if (action === 'pause') detail = 'Paused playback';
  else if (action === 'seek') detail = 'Changed the playback position';
  else if (action === 'kick') detail = 'Removed a listener from the party';
  else if (action === 'setMaxMembers') detail = 'Changed the party size';
  else if (action === 'setAutoplay') detail = 'Changed AutoPlay';
  return { type: 'activity', action, by: member.displayName, detail: detail.slice(0, 400), atMs: now() };
}

export class PartyRoom extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.env = env;
    this.ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS room_state (id INTEGER PRIMARY KEY CHECK (id = 1), data TEXT NOT NULL)');
      this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS presence (id TEXT PRIMARY KEY, platform TEXT NOT NULL, last_ms INTEGER NOT NULL)');
      this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS rate_limits (key TEXT PRIMARY KEY, window_ms INTEGER NOT NULL, count INTEGER NOT NULL)');
      this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS room_directory (code TEXT PRIMARY KEY, members INTEGER NOT NULL, connected INTEGER NOT NULL)');
      this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS room_reservations (code TEXT PRIMARY KEY, created_ms INTEGER NOT NULL)');
    });
  }
  load() { const row = this.ctx.storage.sql.exec('SELECT data FROM room_state WHERE id = 1').toArray()[0]; return row ? JSON.parse(row.data) : null; }
  save(room) { this.ctx.storage.sql.exec('INSERT INTO room_state (id, data) VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data', JSON.stringify(room)); }
  async updateDirectory(room) {
    if (room.code === '__audify_global__') return;
    const global = this.env.PARTY_ROOMS.getByName('__audify_global__');
    await global.fetch('https://room.internal/internal/directory', { method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ code: room.code, members: room.members.length, connected: room.members.filter(m => m.connected).length }) });
  }
  sockets() { return this.ctx.getWebSockets(); }
  send(ws, frame) { try { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(frame)); } catch {} }
  broadcast(frame, skipMemberId = null) {
    for (const ws of this.sockets()) {
      const attachment = ws.deserializeAttachment();
      if (attachment?.memberId !== skipMemberId) this.send(ws, frame);
    }
  }
  async fetch(request) {
    const url = new URL(request.url), path = url.pathname;
    // A single named Durable Object provides persistent, serialized global
    // rate limits and presence counts without adding a paid external service.
    if (path === '/internal/presence' && request.method === 'POST') {
      let body; try { body = await request.json(); } catch { return err(422, 'invalid_json', 'Malformed JSON body.'); }
      if (!isObject(body) || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.id ?? '')) return err(422, 'invalid_id', 'id must be a UUID.');
      if (!['android', 'pc', 'browser'].includes(body.platform)) return err(422, 'invalid_platform', 'platform must be android, pc or browser.');
      if (typeof body.open !== 'boolean') return err(422, 'invalid_open', 'open must be true or false.');
      const origin = request.headers.get('origin');
      if ((body.platform === 'browser') !== !!origin || (origin && !(this.env.JAM_ALLOWED_ORIGINS ?? '').split(',').map(x => x.trim()).includes(origin))) return err(403, 'origin_not_allowed', 'Presence is only accepted from the apps and the web player.');
      const ip = request.headers.get('cf-connecting-ip') ?? 'unknown', t = now();
      this.ctx.storage.sql.exec('DELETE FROM rate_limits WHERE window_ms < ?', t - 86_400_000);
      const key = `presence:${ip}`, row = this.ctx.storage.sql.exec('SELECT window_ms, count FROM rate_limits WHERE key = ?', key).toArray()[0];
      if (row && t - row.window_ms < 60_000 && row.count >= 20) return err(429, 'rate_limited', 'Too many presence pings from this address.');
      if (!row && this.ctx.storage.sql.exec("SELECT COUNT(*) AS count FROM rate_limits WHERE key LIKE 'presence:%'").toArray()[0].count >= Number(this.env.JAM_PRESENCE_RATE_LIMIT_MAX_IPS || 100000)) return err(429, 'rate_limited', 'Too many presence sources are being tracked.');
      this.ctx.storage.sql.exec('INSERT INTO rate_limits (key, window_ms, count) VALUES (?, ?, 1) ON CONFLICT(key) DO UPDATE SET window_ms = CASE WHEN ? - window_ms >= 60000 THEN ? ELSE window_ms END, count = CASE WHEN ? - window_ms >= 60000 THEN 1 ELSE count + 1 END', key, t, t, t, t);
      this.ctx.storage.sql.exec('DELETE FROM presence WHERE last_ms < ?', t - 390_000);
      if (body.open) {
        const id = body.id.toLowerCase(), existing = this.ctx.storage.sql.exec('SELECT 1 AS found FROM presence WHERE id = ?', id).toArray().length > 0;
        const count = this.ctx.storage.sql.exec('SELECT COUNT(*) AS count FROM presence').toArray()[0].count;
        if (existing || count < Number(this.env.JAM_PRESENCE_MAX_ENTRIES || 200000)) this.ctx.storage.sql.exec('INSERT INTO presence (id, platform, last_ms) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET platform = excluded.platform, last_ms = excluded.last_ms', id, body.platform, t);
      }
      else this.ctx.storage.sql.exec('DELETE FROM presence WHERE id = ?', body.id.toLowerCase());
      return json({ intervalSec: 300 });
    }
    if (path === '/internal/stats' && request.method === 'GET') {
      const t = now(); this.ctx.storage.sql.exec('DELETE FROM presence WHERE last_ms < ?', t - 390_000);
      const rows = this.ctx.storage.sql.exec('SELECT platform, COUNT(*) AS count FROM presence GROUP BY platform').toArray();
      const counts = { online: 0, android: 0, pc: 0, browser: 0, updatedAt: t };
      for (const row of rows) { counts[row.platform] = row.count; counts.online += row.count; }
      return json(counts, 200, { 'access-control-allow-origin': '*', 'cache-control': 'public, max-age=15' });
    }
    if (path === '/internal/directory' && request.method === 'POST') {
      const body = await request.json();
      if (body.remove === true) this.ctx.storage.sql.exec('DELETE FROM room_directory WHERE code = ?', body.code);
      else this.ctx.storage.sql.exec('INSERT INTO room_directory (code, members, connected) VALUES (?, ?, ?) ON CONFLICT(code) DO UPDATE SET members = excluded.members, connected = excluded.connected', body.code, body.members, body.connected);
      this.ctx.storage.sql.exec('DELETE FROM room_reservations WHERE code = ?', body.code);
      return json({ ok: true });
    }
    if (path === '/internal/overview' && request.method === 'GET') {
      this.ctx.storage.sql.exec('DELETE FROM room_directory WHERE code IN (SELECT code FROM room_reservations WHERE created_ms < ?)', now() - 60_000);
      this.ctx.storage.sql.exec('DELETE FROM room_reservations WHERE created_ms < ?', now() - 60_000);
      const row = this.ctx.storage.sql.exec('SELECT COUNT(*) AS parties, COALESCE(SUM(members), 0) AS members FROM room_directory').toArray()[0];
      return json({ service: 'audify-listen-together', parties: row.parties, members: row.members, serverMs: now() });
    }
    if (path === '/internal/create-limit' && request.method === 'POST') {
      const body = await request.json(), ip = clampText(body.ip, 128) || 'unknown', t = now(), key = `create:${ip}`;
      this.ctx.storage.sql.exec('DELETE FROM rate_limits WHERE window_ms < ?', t - 86_400_000);
      const row = this.ctx.storage.sql.exec('SELECT window_ms, count FROM rate_limits WHERE key = ?', key).toArray()[0];
      if (row && t - row.window_ms < 60_000 && row.count >= 2) return err(429, 'create_rate_limited', 'You can create up to two parties per minute. Please try again shortly.');
      if (!row && this.ctx.storage.sql.exec("SELECT COUNT(*) AS count FROM rate_limits WHERE key LIKE 'create:%' OR key LIKE 'auth:%' OR key LIKE 'join:%'").toArray()[0].count >= Number(this.env.JAM_RATE_LIMIT_MAX_ENTRIES || 10000)) return err(429, 'create_rate_limited', 'Too many source addresses are being tracked.');
      this.ctx.storage.sql.exec('INSERT INTO rate_limits (key, window_ms, count) VALUES (?, ?, 1) ON CONFLICT(key) DO UPDATE SET window_ms = CASE WHEN ? - window_ms >= 60000 THEN ? ELSE window_ms END, count = CASE WHEN ? - window_ms >= 60000 THEN 1 ELSE count + 1 END', key, t, t, t, t);
      return json({ ok: true });
    }
    if (path === '/internal/feedback-limit' && request.method === 'POST') {
      const body = await request.json(), ipKey = clampText(body.ipKey, 64), t = now(), key = `feedback:${ipKey}`;
      if (!/^[a-f0-9]{64}$/.test(ipKey)) return err(422, 'invalid_rate_key', 'Invalid rate-limit key.');
      this.ctx.storage.sql.exec('DELETE FROM rate_limits WHERE window_ms < ?', t - 86_400_000);
      const row = this.ctx.storage.sql.exec('SELECT window_ms, count FROM rate_limits WHERE key = ?', key).toArray()[0];
      if (row && t - row.window_ms < 3_600_000 && row.count >= 3) return err(429, 'feedback_rate_limited', 'You have sent several reports recently. Please try again later.');
      const active = this.ctx.storage.sql.exec("SELECT COUNT(*) AS count FROM rate_limits WHERE key LIKE 'feedback:%'").toArray()[0].count;
      if (!row && active >= Number(this.env.JAM_FEEDBACK_RATE_LIMIT_MAX_ENTRIES || 10000)) return err(429, 'feedback_rate_limited', 'Feedback is temporarily busy. Please try again later.');
      this.ctx.storage.sql.exec('INSERT INTO rate_limits (key, window_ms, count) VALUES (?, ?, 1) ON CONFLICT(key) DO UPDATE SET window_ms = CASE WHEN ? - window_ms >= 3600000 THEN ? ELSE window_ms END, count = CASE WHEN ? - window_ms >= 3600000 THEN 1 ELSE count + 1 END', key, t, t, t, t);
      return json({ ok: true });
    }
    if (path === '/internal/reserve-room' && request.method === 'POST') {
      const body = await request.json(), code = normalise(body.code);
      if (code.length !== CODE_LENGTH || [...code].some(c => !ALPHABET.includes(c))) return err(422, 'bad_code', 'Invalid party code.');
      this.ctx.storage.sql.exec('DELETE FROM room_directory WHERE code IN (SELECT code FROM room_reservations WHERE created_ms < ?)', now() - 60_000);
      this.ctx.storage.sql.exec('DELETE FROM room_reservations WHERE created_ms < ?', now() - 60_000);
      const existing = this.ctx.storage.sql.exec('SELECT 1 AS found FROM room_directory WHERE code = ?', code).toArray().length > 0;
      if (existing) return err(503, 'code_collision', 'Could not allocate a party code. Retry.');
      const active = this.ctx.storage.sql.exec('SELECT COUNT(*) AS count FROM room_directory').toArray()[0].count;
      if (active >= Number(this.env.JAM_MAX_PARTIES || 50)) return err(503, 'server_full', 'The party server is busy. Please try again in a few minutes.');
      this.ctx.storage.sql.exec('INSERT INTO room_directory (code, members, connected) VALUES (?, 0, 0)', code);
      this.ctx.storage.sql.exec('INSERT INTO room_reservations (code, created_ms) VALUES (?, ?)', code, now());
      return json({ ok: true });
    }
    if (path === '/internal/auth-limit' && request.method === 'POST') {
      const body = await request.json(), ip = clampText(body.ip, 128) || 'unknown';
      const scope = body.scope === 'join' ? 'join' : 'auth', limit = scope === 'join' ? 10 : 30;
      const t = now(), key = `${scope}:${ip}`;
      this.ctx.storage.sql.exec('DELETE FROM rate_limits WHERE window_ms < ?', t - 86_400_000);
      const row = this.ctx.storage.sql.exec('SELECT window_ms, count FROM rate_limits WHERE key = ?', key).toArray()[0];
      if (row && t - row.window_ms < 60_000 && row.count >= limit) return err(429, 'auth_rate_limited', 'Too many authentication or join attempts. Please wait before trying again.');
      if (!row && this.ctx.storage.sql.exec("SELECT COUNT(*) AS count FROM rate_limits WHERE key LIKE 'create:%' OR key LIKE 'auth:%' OR key LIKE 'join:%'").toArray()[0].count >= Number(this.env.JAM_RATE_LIMIT_MAX_ENTRIES || 10000)) return err(429, 'auth_rate_limited', 'Too many source addresses are being tracked.');
      this.ctx.storage.sql.exec('INSERT INTO rate_limits (key, window_ms, count) VALUES (?, ?, 1) ON CONFLICT(key) DO UPDATE SET window_ms = CASE WHEN ? - window_ms >= 60000 THEN ? ELSE window_ms END, count = CASE WHEN ? - window_ms >= 60000 THEN 1 ELSE count + 1 END', key, t, t, t, t);
      return json({ ok: true });
    }
    if (path === '/internal/invite' && request.method === 'GET') {
      const room = this.load();
      if (!room) {
        const code = normalise(request.headers.get('x-invite-code'));
        const origin = this.env.JAM_PUBLIC_ORIGIN || request.headers.get('x-public-origin') || 'https://audify.app';
        return new Response(inviteHtml({ code, origin, active: false }), { status: 404, headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
      }
      const p = room.playback;
      const code = room.code, origin = this.env.JAM_PUBLIC_ORIGIN || request.headers.get('x-public-origin') || 'https://audify.app';
      return new Response(inviteHtml({ code, origin, active: true, members: room.members.length,
        title: p.track?.title ?? '', artist: p.track?.artist ?? '' }), { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
    }
    if (path === '/internal/create' && request.method === 'POST') {
      if (this.load()) return err(409, 'code_collision', 'Party code already exists.');
      const body = await request.json();
      const room = newRoom(body.code, body.maxMembers, body.autoplayEnabled);
      const member = randomMember(room, body);
      this.save(room);
      await this.updateDirectory(room);
      await this.ctx.storage.setAlarm(now() + DISCONNECT_GRACE_MS);
      return json({ code: room.code, token: member.token, you: memberWire(member), party: snapshot(room) }, 201);
    }
    let room = this.load();
    if (!room) return err(404, 'no_such_party', 'No party with that code.');
    if (isRoomExpired(room, now(), PARTY_MAX_AGE_MS, EMPTY_TTL_MS)) {
      const global = this.env.PARTY_ROOMS.getByName('__audify_global__');
      await global.fetch('https://room.internal/internal/directory', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: room.code, remove: true }) });
      this.ctx.storage.deleteAll(); return err(404, 'no_such_party', 'No party with that code.');
    }
    if (path === '/internal/join' && request.method === 'POST') {
      const body = await request.json();
      const member = randomMember(room, body);
      if (!member) return err(409, 'party_full', `This party is full (${room.maxMembers} devices).`);
      room.touchedAtMs = now(); this.save(room);
      await this.updateDirectory(room);
      await this.ctx.storage.setAlarm(now() + DISCONNECT_GRACE_MS);
      return json({ code: room.code, token: member.token, you: memberWire(member), party: snapshot(room) });
    }
    if (path === '/internal/preview' && request.method === 'GET') {
      const members = [...room.members].sort((a, b) => a.joinedAtMs - b.joinedAtMs);
      return json({ code: room.code, hostName: members.find(m => m.isHost)?.displayName ?? '', memberCount: members.length,
        maxMembers: room.maxMembers, isFull: members.length >= room.maxMembers,
        members: members.map(m => ({ displayName: m.displayName, avatarUrl: m.avatarUrl, isHost: m.isHost })) });
    }
    const bearer = request.headers.get('authorization')?.match(/^Bearer\s+(.+)$/i)?.[1] ?? '';
    const member = auth(room, bearer);
    if (path === '/internal/snapshot' && request.method === 'GET') return member ? json(snapshot(room)) : err(401, 'unauthorized', bearer ? 'Bad token.' : 'Missing bearer token.');
    if (path === '/internal/leave' && request.method === 'POST') {
      if (!member) return err(401, 'unauthorized', bearer ? 'Bad token.' : 'Missing bearer token.');
      room.members = room.members.filter(m => m.memberId !== member.memberId);
      if (member.isHost && room.members.length) room.members[0].isHost = true;
      room.touchedAtMs = now();
      if (!room.members.some(m => m.connected)) room.emptySinceMs = now();
      this.save(room);
      await this.updateDirectory(room);
      for (const ws of this.sockets()) if (ws.deserializeAttachment()?.memberId === member.memberId) ws.close(1000, 'left');
      this.broadcast(membersFrame(room));
      return json({ ok: true });
    }
    if (path === '/ws' && request.headers.get('Upgrade')?.toLowerCase() === 'websocket') {
      const protocols = (request.headers.get('sec-websocket-protocol') ?? '').split(',').map(x => x.trim());
      const socketToken = bearer || protocols.find(x => x.startsWith('audify.token.'))?.slice('audify.token.'.length) || '';
      const wsMember = auth(room, socketToken);
      if (!wsMember) return new Response('bad token', { status: 401 });
      const pair = new WebSocketPair(), [client, server] = Object.values(pair);
      for (const old of this.sockets()) if (old.deserializeAttachment()?.memberId === wsMember.memberId) old.close(4001, 'replaced');
      this.ctx.acceptWebSocket(server);
      server.serializeAttachment({ memberId: wsMember.memberId });
      wsMember.connected = true; wsMember.lastSeenMs = now(); room.emptySinceMs = null; this.save(room);
      await this.updateDirectory(room);
      this.send(server, { type: 'welcome', you: memberWire(wsMember), party: snapshot(room), serverMs: now() });
      this.broadcast(membersFrame(room));
      // A 45s alarm is enough for idle-session and lifecycle cleanup. Clients
      // already ping every 15s; those pings carry the live state heartbeat so
      // we do not burn a Durable Object alarm every five seconds on Free.
      await this.ctx.storage.setAlarm(now() + DISCONNECT_GRACE_MS);
      return new Response(null, { status: 101, webSocket: client,
        headers: protocols.includes('audify.v1') ? { 'Sec-WebSocket-Protocol': 'audify.v1' } : {} });
    }
    return err(404, 'not_found', 'Not found.');
  }
  async webSocketMessage(ws, message) {
    if (typeof message !== 'string' || message.length > MAX_BODY) { ws.close(1009, 'message too large'); return; }
    const attachment = ws.deserializeAttachment(), room = this.load();
    if (!room || !attachment) { ws.close(4004, 'party expired'); return; }
    const member = room.members.find(m => m.memberId === attachment.memberId);
    if (!member) { this.send(ws, { type: 'bye', reason: 'kicked', message: 'The host removed you from this party.' }); ws.close(4003, 'not a member'); return; }
    let frame;
    try { frame = JSON.parse(message); } catch { this.send(ws, errorFrame('invalid_json', 'Malformed JSON frame.')); return; }
    if (!isObject(frame) || typeof frame.type !== 'string') return;
    if (!bucket(member, 'frame')) { this.send(ws, errorFrame('rate_limited', 'Too many messages at once.')); this.save(room); return; }
    member.lastSeenMs = now();
    if (frame.type === 'ping') {
      this.send(ws, { type: 'pong', clientMs: frame.clientMs, serverMs: now() });
      this.broadcast(stateFrame(room));
    }
    else if (frame.type === 'sync') this.send(ws, stateFrame(room));
    else if (frame.type === 'syncQueue') this.send(ws, queueFrame(room));
    else if (frame.type === 'report') { /* reports are diagnostic only; server state remains authoritative */ }
    else if (frame.type === 'control') {
      if (!bucket(member, 'control')) { this.send(ws, errorFrame('rate_limited', 'Too many controls at once.')); this.save(room); return; }
      const eventId = typeof frame.eventId === 'string' ? frame.eventId : '';
      if (eventId && (eventId.length > 128 || eventId.length < 8)) { this.send(ws, errorFrame('invalid_event_id', 'eventId must be between 8 and 128 characters.')); return; }
      const eventKey = eventId ? `${member.memberId}:${eventId}` : '';
      if (eventKey && room.recentEvents.includes(eventKey)) {
        this.send(ws, stateFrame(room)); this.send(ws, queueFrame(room)); return;
      }
      const before = room.playback.queueSeq;
      const [ok, code, messageText] = this.applyControl(room, member, frame);
      if (!ok) this.send(ws, errorFrame(code, messageText));
      else {
        if (eventKey) room.recentEvents = [...room.recentEvents, eventKey].slice(-256);
        this.save(room);
        if (room.playback.queueSeq !== before) this.broadcast(queueFrame(room));
        this.broadcast(stateFrame(room));
        if (['kick', 'setMaxMembers', 'setHostOnlyControl'].includes(frame.action)) this.broadcast(membersFrame(room));
        if (frame.action === 'kick') await this.updateDirectory(room);
        this.broadcast(activity(member, frame.action, frame));
        return;
      }
    }
    this.save(room);
  }
  applyControl(room, member, f) {
    const p = room.playback, a = f.action, t = now();
    const restricted = ['play','pause','seek','setTrack','setQueue','queueAdd','queueRemove','queueClear','queueMove','next','previous','setAutoplay'];
    if (restricted.includes(a) && room.hostOnlyControl && !member.isHost) return [false, 'host_only', 'Only the host can control the music in this party.'];
    const position = Number.isFinite(f.positionMs) ? Math.max(0, Math.trunc(f.positionMs)) : null;
    const setTrack = (track, pos, playing, index) => {
      p.track = track; p.positionMs = pos; p.isPlaying = playing && !!track; p.anchorMs = t + (p.isPlaying ? PLAY_LEAD_MS : 0);
      if (Number.isInteger(index) && index >= 0 && index < p.queue.length && track && p.queue[index].videoId === track.videoId) p.queueIndex = index;
      else if (track) p.queueIndex = nearestIndex(p.queue, track.videoId, p.queueIndex);
      else if (Number.isInteger(index)) p.queueIndex = index;
      p.startedBy = member.memberId; p.startedByName = member.displayName; touch(room, member.memberId);
    };
    switch (a) {
      case 'play': p.positionMs = position ?? positionAt(p, t); p.isPlaying = true; p.anchorMs = t + PLAY_LEAD_MS; touch(room, member.memberId); break;
      case 'pause': p.positionMs = position ?? positionAt(p, t); p.isPlaying = false; p.anchorMs = t; touch(room, member.memberId); break;
      case 'seek': if (position == null) return [false, 'missing_position', 'seek requires positionMs']; p.positionMs = position; p.anchorMs = t + (p.isPlaying ? PLAY_LEAD_MS : 0); touch(room, member.memberId); break;
      case 'setTrack': { const tr = trackOf(f.track); if (!tr) return [false, 'invalid_track', 'Track must include videoId']; setTrack(tr, position ?? 0, f.isPlaying !== false, f.queueIndex); break; }
      case 'setQueue': {
        const q = Array.isArray(f.queue) ? f.queue.map(trackOf).filter(Boolean) : [];
        let idx = Number.isInteger(f.queueIndex) ? f.queueIndex : -1;
        if (p.track && (idx < 0 || idx >= q.length || q[idx].videoId !== p.track.videoId)) idx = nearestIndex(q, p.track.videoId, idx);
        if (idx >= 0 && idx < q.length) p.queue = [...q.slice(0, idx + 1), ...q.slice(idx + 1, idx + 1 + MAX_UPCOMING)];
        else { p.queue = q.slice(0, 26); idx = -1; }
        p.queueIndex = idx; touch(room, member.memberId, true); break;
      }
      case 'queueAdd': {
        const input = Array.isArray(f.tracks) ? f.tracks : (f.track ? [f.track] : []), tracks = input.map(trackOf).filter(Boolean);
        const count = p.queueIndex >= 0 ? p.queue.length - p.queueIndex - 1 : p.queue.length;
        const slots = MAX_UPCOMING - Math.max(0, count);
        if (slots <= 0) return [false, 'queue_full', `Queue is full (maximum ${MAX_UPCOMING} upcoming songs).`];
        const waiting = new Set(p.queue.slice(p.queueIndex + 1).map(x => x.videoId));
        const add = tracks.filter(x => { if (!x.fromAutoplay) return true; if (waiting.has(x.videoId)) return false; waiting.add(x.videoId); return true; });
        if (!add.length && tracks.length) break;
        const clipped = add.slice(0, slots);
        if (!clipped.length) return [false, 'no_tracks', 'Could not add track to queue.'];
        if (f.playNext && p.queueIndex >= 0) p.queue.splice(p.queueIndex + 1, 0, ...clipped); else p.queue.push(...clipped);
        touch(room, member.memberId, true); break;
      }
      case 'queueRemove': {
        let vid = typeof f.videoId === 'string' ? f.videoId : '';
        if (!vid && Number.isInteger(f.index)) vid = p.queue[f.index]?.videoId ?? '';
        if (!vid) return [false, 'missing_track', 'queueRemove requires videoId or index'];
        const i = p.queue.findIndex(x => x.videoId === vid);
        if (i < 0 || i === p.queueIndex) return [false, 'not_found', 'Track is not in the upcoming queue'];
        p.queue.splice(i, 1); if (p.queueIndex > i) p.queueIndex--; touch(room, member.memberId, true); break;
      }
      case 'queueClear': if (p.queueIndex >= 0 ? p.queue.length <= p.queueIndex + 1 : !p.queue.length) return [false, 'no_upcoming', 'No upcoming tracks to clear'];
        p.queue = p.queue.slice(0, p.queueIndex + 1); touch(room, member.memberId, true); break;
      case 'queueMove': {
        let from = f.fromIndex, to = f.toIndex;
        if (!Number.isInteger(from) || !Number.isInteger(to)) return [false, 'missing_indices', 'queueMove requires fromIndex and toIndex'];
        if (f.videoId) from = p.queue.findIndex(x => x.videoId === f.videoId);
        if (from < 0 || from >= p.queue.length || to < 0 || to >= p.queue.length || from <= p.queueIndex || to <= p.queueIndex) return [false, 'invalid_move', 'Invalid queue move indices'];
        if (from !== to) { const [item] = p.queue.splice(from, 1); p.queue.splice(to, 0, item); touch(room, member.memberId, true); } break;
      }
      case 'next': case 'previous': { const idx = p.queueIndex + (a === 'next' ? 1 : -1); if (idx < 0 || idx >= p.queue.length) return [false, a === 'next' ? 'end_of_queue' : 'start_of_queue', a === 'next' ? 'Already at the end of the queue.' : 'Already at the beginning of the queue.']; setTrack(p.queue[idx], 0, true, idx); break; }
      case 'setMaxMembers': if (!member.isHost) return [false, 'host_only', 'Only the host can change the party size.'];
        if (!Number.isInteger(f.maxMembers) || f.maxMembers < 2 || f.maxMembers > 10) return [false, 'invalid_capacity', 'Choose a party size between 2 and 10.'];
        if (f.maxMembers < room.members.length) return [false, 'party_too_small', 'Party size cannot be smaller than the current member count.']; room.maxMembers = f.maxMembers; room.touchedAtMs = t; break;
      case 'setAutoplay': if (typeof f.enabled !== 'boolean') return [false, 'invalid_autoplay', 'AutoPlay must be enabled or disabled.']; if (p.autoplayEnabled !== f.enabled) { p.autoplayEnabled = f.enabled; p.updatedBy = member.memberId; p.updatedAtMs = t; } break;
      case 'setHostOnlyControl': if (!member.isHost) return [false, 'host_only', 'Only the host can change who controls the music.']; if (typeof f.enabled !== 'boolean') return [false, 'invalid_control_policy', 'Host-only control must be enabled or disabled.']; room.hostOnlyControl = f.enabled; room.touchedAtMs = t; break;
      case 'kick': {
        if (!member.isHost) return [false, 'host_only', 'Only the host can remove listeners.'];
        if (!f.memberId || f.memberId === member.memberId) return [false, 'invalid_member', 'Choose another listener to remove.'];
        const target = room.members.find(m => m.memberId === f.memberId); if (!target) return [false, 'not_found', 'That listener is no longer in this party.'];
        room.members = room.members.filter(m => m !== target); if (target.isHost && room.members.length) room.members[0].isHost = true;
        for (const ws of this.sockets()) if (ws.deserializeAttachment()?.memberId === target.memberId) { this.send(ws, { type: 'bye', reason: 'kicked', message: 'The host removed you from this party.' }); ws.close(4003, 'kicked'); }
        room.touchedAtMs = t; break;
      }
      default: return [false, 'unknown_action', `Unknown control action '${a ?? ''}'`];
    }
    return [true, '', ''];
  }
  async webSocketClose(ws) { await this.onDisconnect(ws); }
  async webSocketError(ws) { await this.onDisconnect(ws); }
  async onDisconnect(ws) {
    const attachment = ws.deserializeAttachment(), room = this.load();
    if (!attachment || !room) return;
    const member = room.members.find(m => m.memberId === attachment.memberId);
    if (!member) return;
    const stillConnected = this.sockets().some(s => s !== ws && s.deserializeAttachment()?.memberId === member.memberId && s.readyState === WebSocket.OPEN);
    member.connected = stillConnected; member.lastSeenMs = now();
    if (!room.members.some(m => m.connected)) room.emptySinceMs = now();
    this.save(room);
    await this.updateDirectory(room);
    this.broadcast(membersFrame(room));
    await this.ctx.storage.setAlarm(now() + DISCONNECT_GRACE_MS);
  }
  async alarm() {
    const room = this.load(); if (!room) return;
    const t = now();
    const liveSockets = this.sockets().filter(ws => ws.readyState === WebSocket.OPEN);
    for (const ws of liveSockets) {
      const attachment = ws.deserializeAttachment();
      const member = room.members.find(m => m.memberId === attachment?.memberId);
      if (member && t - member.lastSeenMs > CONNECTION_IDLE_MS) {
        member.connected = false;
        this.send(ws, { type: 'error', error: 'connection_idle', message: 'Connection timed out. Reconnect to continue.' });
        ws.close(4008, 'idle timeout');
      } else if (member) member.connected = true;
    }
    room.members = room.members.filter(m => m.connected || t - m.lastSeenMs <= DISCONNECT_GRACE_MS);
    if (room.members.length && !room.members.some(m => m.isHost)) room.members[0].isHost = true;
    if (room.members.some(m => m.connected)) room.emptySinceMs = null;
    else if (room.emptySinceMs == null) room.emptySinceMs = t;
    const expired = isRoomExpired(room, t, PARTY_MAX_AGE_MS, EMPTY_TTL_MS);
    if (expired) {
      const global = this.env.PARTY_ROOMS.getByName('__audify_global__');
      await global.fetch('https://room.internal/internal/directory', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: room.code, remove: true }) });
      this.ctx.storage.deleteAll(); for (const ws of this.sockets()) ws.close(4004, 'party expired'); return;
    }
    this.save(room);
    await this.updateDirectory(room);
    if (liveSockets.some(ws => ws.readyState === WebSocket.OPEN)) {
      this.broadcast(stateFrame(room));
      await this.ctx.storage.setAlarm(t + DISCONNECT_GRACE_MS);
    } else {
      const next = nextCleanupAt(room, t, DISCONNECT_GRACE_MS, EMPTY_TTL_MS, PARTY_MAX_AGE_MS);
      if (next != null) await this.ctx.storage.setAlarm(next);
    }
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url), path = url.pathname;
    const origin = request.headers.get('Origin');
    const allowed = (env.JAM_ALLOWED_ORIGINS ?? '').split(',').map(x => x.trim()).filter(Boolean);
    if (origin && !allowed.includes(origin)) return err(403, 'origin_not_allowed', 'Origin is not allowed.');
    const cors = origin ? { 'access-control-allow-origin': origin, vary: 'Origin', 'access-control-allow-methods': 'GET, POST, OPTIONS', 'access-control-allow-headers': 'Content-Type, Authorization' } : {};
    const fail = (status, code, message) => json({ error: code, message }, status, cors);
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    const global = env.PARTY_ROOMS.getByName('__audify_global__');
    if (['/', '/healthz', '/api/time'].includes(path) && request.method !== 'GET') return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: 'GET' } });
    if (path === '/') {
      const response = await global.fetch('https://room.internal/internal/overview');
      return new Response(response.body, { status: response.status, headers: { ...Object.fromEntries(response.headers), ...cors } });
    }
    if (path === '/healthz') return json({ ok: true, serverMs: now() }, 200, cors);
    if (path === '/api/time') return json({ serverMs: now() }, 200, cors);
    if (path === '/api/presence') {
      if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: 'POST' } });
      const raw = await request.text(); if (raw.length > 2048) return fail(413, 'request_too_large', 'Request body is too large.');
      const response = await global.fetch('https://room.internal/internal/presence', { method: 'POST', headers: request.headers, body: raw });
      return new Response(response.body, { status: response.status, headers: { ...Object.fromEntries(response.headers), ...cors } });
    }
    if (path === '/api/feedback') {
      if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: 'POST' } });
      const raw = await request.text();
      if (raw.length > MAX_FEEDBACK_BODY) return fail(413, 'request_too_large', 'Feedback is too long.');
      let body;
      try { body = JSON.parse(raw); } catch { return fail(422, 'invalid_json', 'Malformed JSON body.'); }
      const invalid = validateFeedback(body);
      if (invalid) return fail(422, 'validation_error', invalid);
      if (clampText(body.website, 200).trim()) return json({ ok: true }, 202, cors);
      if (!env.GITHUB_ISSUES_TOKEN) return fail(503, 'feedback_unavailable', 'Feedback submission is not configured yet.');
      const ipKey = await feedbackIpKey(request.headers.get('cf-connecting-ip') ?? 'unknown');
      const limited = await global.fetch('https://room.internal/internal/feedback-limit', {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ ipKey }),
      });
      if (!limited.ok) return new Response(limited.body, { status: limited.status, headers: { ...Object.fromEntries(limited.headers), ...cors } });
      const issue = feedbackIssueBody(body);
      let response;
      try {
    response = await fetch('https://api.github.com/repos/sachit1751-art/Audify/issues', {
          method: 'POST',
          headers: {
            authorization: `Bearer ${env.GITHUB_ISSUES_TOKEN}`,
            accept: 'application/vnd.github+json',
            'content-type': 'application/json',
            'user-agent': 'Audify-Feedback',
            'x-github-api-version': '2022-11-28',
          },
          body: JSON.stringify(issue),
        });
      } catch {
        return fail(502, 'feedback_upstream_unavailable', 'Could not reach the issue tracker. Your report was not submitted.');
      }
      if (!response.ok) return fail(502, 'feedback_upstream_rejected', 'The issue tracker could not accept this report. Please try again later.');
      let created;
      try { created = await response.json(); } catch { return fail(502, 'feedback_upstream_invalid', 'The issue tracker returned an invalid response. Please try again later.'); }
      if (!Number.isInteger(created.number) || typeof created.html_url !== 'string' || !created.html_url.startsWith('https://github.com/')) {
        return fail(502, 'feedback_upstream_invalid', 'The issue tracker returned an invalid response. Please try again later.');
      }
      return json({ ok: true, number: created.number, url: created.html_url }, 201, cors);
    }
    if (path === '/api/stats/live' || path === '/api/stats/live/badge.svg') {
      if (request.method !== 'GET') return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: 'GET' } });
    }
    if (path === '/api/stats/live') return global.fetch('https://room.internal/internal/stats');
    if (path === '/api/stats/live/badge.svg') {
      const stats = await global.fetch('https://room.internal/internal/stats').then(r => r.json()), value = String(stats.online);
      const valueWidth = 8.25 * value.length + 24, width = 124.25 + valueWidth;
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="28" role="img" aria-label="LISTENING NOW: ${value}"><title>LISTENING NOW: ${value}</title><g shape-rendering="crispEdges"><rect width="124.25" height="28" fill="#0d1117"/><rect x="124.25" width="${valueWidth}" height="28" fill="#fb4f67"/></g><g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="100"><text transform="scale(.1)" x="621.25" y="175" textLength="1002.5">LISTENING NOW</text><text transform="scale(.1)" x="${(124.25 + valueWidth / 2) * 10}" y="175" textLength="${82.5 * value.length}" font-weight="bold">${value}</text></g></svg>`;
      return new Response(svg, { headers: { ...cors, 'access-control-allow-origin': '*', 'content-type': 'image/svg+xml;charset=utf-8', 'cache-control': 'no-cache, no-store, must-revalidate, max-age=0' } });
    }
    const invite = path.match(/^\/invite\/([^/]+)$/);
    if (invite) {
      if (request.method !== 'GET') return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: 'GET' } });
      const code = normalise(invite[1]);
      if (code.length !== CODE_LENGTH) return new Response(inviteHtml({ code: invite[1], origin: env.JAM_PUBLIC_ORIGIN || url.origin, active: false }), { status: 400, headers: { ...cors, 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
      const response = await env.PARTY_ROOMS.getByName(code).fetch('https://room.internal/internal/invite', { headers: { 'x-public-origin': env.JAM_PUBLIC_ORIGIN || url.origin, 'x-invite-code': code } });
      return new Response(response.body, { status: response.status, headers: { ...Object.fromEntries(response.headers), ...cors } });
    }
    const match = path.match(/^\/(?:api\/parties\/([^/]+)(?:\/(join|preview|leave))?|ws\/parties\/([^/]+))$/);
    if (path === '/api/parties' && request.method !== 'POST') return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: 'POST' } });
    if (path === '/api/parties' && request.method === 'POST') {
      const limited = await global.fetch('https://room.internal/internal/create-limit', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ ip: request.headers.get('cf-connecting-ip') ?? 'unknown' }) });
      if (!limited.ok) return new Response(limited.body, { status: limited.status, headers: { ...Object.fromEntries(limited.headers), ...cors } });
      let body; try { const raw = await request.text(); if (raw.length > MAX_BODY) return fail(413, 'request_too_large', 'Request body is too large.'); body = JSON.parse(raw); } catch { return fail(422, 'invalid_json', 'Malformed JSON body.'); }
      const invalid = validJoin(body); if (invalid) return fail(422, 'validation_error', invalid);
      const code = makeCode();
      const reservation = await global.fetch('https://room.internal/internal/reserve-room', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code }) });
      if (!reservation.ok) return new Response(reservation.body, { status: reservation.status, headers: { ...Object.fromEntries(reservation.headers), ...cors } });
      const room = env.PARTY_ROOMS.getByName(code);
      let response;
      try {
        response = await room.fetch('https://room.internal/internal/create', { method: 'POST', body: JSON.stringify({ ...body, code, maxMembers: body.maxMembers ?? 5, autoplayEnabled: body.autoplayEnabled === true }) });
      } catch (error) {
        await global.fetch('https://room.internal/internal/directory', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code, remove: true }) });
        throw error;
      }
      if (!response.ok) await global.fetch('https://room.internal/internal/directory', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code, remove: true }) });
      if (response.status === 409) return fail(503, 'code_collision', 'Could not allocate a party code. Retry.');
      return new Response(response.body, { status: response.status, headers: { ...Object.fromEntries(response.headers), ...cors } });
    }
    if (!match) return fail(404, 'not_found', 'Not found.');
    const requiredMethod = match[3] || match[2] === 'preview' || !match[2] ? 'GET' : 'POST';
    if (request.method !== requiredMethod) return new Response('Method Not Allowed', { status: 405, headers: { ...cors, allow: requiredMethod } });
    const code = normalise(match[1] ?? match[3]);
    if (code.length !== CODE_LENGTH || [...code].some(c => !ALPHABET.includes(c))) return fail(404, 'no_such_party', 'No party with that code.');
    const authScope = match[2] === 'join' ? 'join' : 'auth';
    const limitedAuth = await global.fetch('https://room.internal/internal/auth-limit', { method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ ip: request.headers.get('cf-connecting-ip') ?? 'unknown', scope: authScope }) });
    if (!limitedAuth.ok) return new Response(limitedAuth.body, { status: limitedAuth.status, headers: { ...Object.fromEntries(limitedAuth.headers), ...cors } });
    const room = env.PARTY_ROOMS.getByName(code);
    let internalPath = '';
    if (match[3]) internalPath = '/ws';
    else if (match[2] === 'join') internalPath = '/internal/join';
    else if (match[2] === 'preview') internalPath = '/internal/preview';
    else if (match[2] === 'leave') internalPath = '/internal/leave';
    else internalPath = '/internal/snapshot';
    if (internalPath === '/internal/join') {
      let body; try { const raw = await request.text(); if (raw.length > MAX_BODY) return fail(413, 'request_too_large', 'Request body is too large.'); body = JSON.parse(raw); } catch { return fail(422, 'invalid_json', 'Malformed JSON body.'); }
      const invalid = validJoin(body); if (invalid) return fail(422, 'validation_error', invalid);
      request = new Request('https://room.internal' + internalPath, { method: 'POST', headers: request.headers, body: JSON.stringify(body) });
    } else request = new Request('https://room.internal' + internalPath, request);
    const response = await room.fetch(request);
    if (response.status === 101) return response;
    return new Response(response.body, { status: response.status, headers: { ...Object.fromEntries(response.headers), ...cors } });
  },
};
