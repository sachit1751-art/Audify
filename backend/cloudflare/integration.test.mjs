import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import tls from 'node:tls';
import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const base = (process.env.WORKER_URL || 'http://127.0.0.1:8787').replace(/\/$/, '');
let host, guest, other, hostSocket, guestSocket, otherSocket;

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (headers.has('x-test-ip')) {
    headers.set('cf-connecting-ip', headers.get('x-test-ip'));
    headers.delete('x-test-ip');
  } else if (base.includes('127.0.0.1')) headers.set('cf-connecting-ip', crypto.randomUUID());
  return fetch(`${base}${path}`, { ...options, headers });
}
async function json(response) { return response.json(); }
function socket(code, token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${base.replace(/^http/, 'ws')}/ws/parties/${code}`, ['audify.v1', `audify.token.${token}`]);
    const timeout = setTimeout(() => reject(new Error('WebSocket welcome timed out')), 5000);
    ws.addEventListener('message', event => {
      const frame = JSON.parse(event.data);
      if (frame.type === 'welcome') { clearTimeout(timeout); resolve({ ws, welcome: frame }); }
    });
    ws.addEventListener('error', reject);
  });
}
function nativeHandshake(code, bearer) {
  return new Promise((resolve, reject) => {
    const target = new URL(base), secure = target.protocol === 'https:';
    const connection = secure ? tls.connect({ host: target.hostname, port: 443, servername: target.hostname, ALPNProtocols: ['http/1.1'] }) : net.connect(Number(target.port || 80), target.hostname);
    let response = '';
    const finish = status => { connection.destroy(); resolve(status); };
    connection.setTimeout(5000, () => { connection.destroy(); reject(new Error('Native WebSocket handshake timed out')); });
    connection.on('error', reject);
    connection.on(secure ? 'secureConnect' : 'connect', () => {
      const key = randomBytes(16).toString('base64');
      connection.write(`GET /ws/parties/${code} HTTP/1.1\r\nHost: ${target.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: ${key}\r\nAuthorization: Bearer ${bearer}\r\n\r\n`);
    });
    connection.on('data', chunk => {
      response += chunk.toString('latin1');
      if (response.includes('\r\n\r\n')) finish(Number(response.match(/^HTTP\/1\.1 (\d+)/)?.[1] || 0));
    });
  });
}
function send(ws, frame) { ws.send(JSON.stringify(frame)); }
function waitFor(ws, predicate, timeoutMs = 5000, label = 'Expected WebSocket frame timed out') {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { ws.removeEventListener('message', onMessage); reject(new Error(label)); }, timeoutMs);
    function onMessage(event) {
      const frame = JSON.parse(event.data);
      if (!predicate(frame)) return;
      clearTimeout(timer); ws.removeEventListener('message', onMessage); resolve(frame);
    }
    ws.addEventListener('message', onMessage);
  });
}

before(async () => {
  const response = await request('/api/parties', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'u-host', deviceId: 'd-host', displayName: 'Host', maxMembers: 5 }) });
  assert.equal(response.status, 201);
  host = await json(response);
  const joined = await request(`/api/parties/${host.code}/join`, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'u-guest', deviceId: 'd-guest', displayName: 'Guest' }) });
  assert.equal(joined.status, 200); guest = await json(joined);
  const third = await request(`/api/parties/${host.code}/join`, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'u-other', deviceId: 'd-other', displayName: 'Other' }) });
  assert.equal(third.status, 200); other = await json(third);
});

after(async () => { hostSocket?.close(); guestSocket?.close(); otherSocket?.close(); });

test('health, time, preview and authenticated snapshot REST contract', async () => {
  const overview = await json(await request('/'));
  assert.equal(overview.service, 'audify-listen-together'); assert.ok(overview.parties >= 1); assert.ok(overview.members >= 1);
  assert.equal((await request('/healthz')).status, 200);
  assert.equal(typeof (await json(await request('/api/time'))).serverMs, 'number');
  const preview = await json(await request(`/api/parties/${host.code}/preview`));
  assert.equal(preview.memberCount, 3); assert.equal(preview.hostName, 'Host');
  assert.equal((await request(`/api/parties/${host.code}`)).status, 401);
  assert.equal((await request(`/api/parties/${host.code}/join`)).status, 405);
  assert.equal((await request(`/api/parties/${host.code}/preview`, { method: 'POST' })).status, 405);
  const snapshot = await request(`/api/parties/${host.code}`, { headers: { authorization: `Bearer ${host.token}` } });
  assert.equal(snapshot.status, 200); assert.equal((await json(snapshot)).members.length, 3);
});

test('anonymous feedback validates submissions and fails closed until GitHub delivery is configured', async () => {
  assert.equal((await request('/api/feedback')).status, 405);
  const invalid = await request('/api/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' });
  assert.equal(invalid.status, 422);
  assert.equal((await json(invalid)).error, 'validation_error');

  const validPayload = {
    kind: 'bug', category: 'Playback', title: 'Playback stops',
    description: 'Playback stops after the current song finishes unexpectedly.',
    platform: 'Android', version: '1.9.2', website: '',
  };
  const unconfigured = await request('/api/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(validPayload) });
  assert.equal(unconfigured.status, 503);
  assert.equal((await json(unconfigured)).error, 'feedback_unavailable');

  const bot = await request('/api/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ ...validPayload, website: 'spam.example' }) });
  assert.equal(bot.status, 202);
  assert.deepEqual(await json(bot), { ok: true });
});

test('native WebSocket bearer-header authentication matches the Android client', async () => {
  assert.equal(await nativeHandshake(host.code, host.token), 101);
  assert.equal(await nativeHandshake(host.code, 'not-a-valid-token'), 401);
});

test('browser token subprotocol handshake and simultaneous participants', async () => {
  const [h, g] = await Promise.all([socket(host.code, host.token), socket(guest.code, guest.token)]);
  hostSocket = h.ws; guestSocket = g.ws;
  assert.equal(hostSocket.protocol, 'audify.v1'); assert.equal(guestSocket.protocol, 'audify.v1');
  assert.equal(h.welcome.type, 'welcome'); assert.equal(g.welcome.type, 'welcome');
  assert.equal(g.welcome.party.members.length, 3);
});

test('client ping returns pong and refreshes shared state for all participants', async () => {
  const statePromise = waitFor(guestSocket, f => f.type === 'state', 5000, 'Ping did not fan out a state heartbeat');
  send(hostSocket, { type: 'ping', clientMs: Date.now() });
  const pong = await waitFor(hostSocket, f => f.type === 'pong', 5000, 'Pong was not returned');
  assert.equal(typeof pong.serverMs, 'number');
  const state = await statePromise;
  assert.equal(state.playback.track?.videoId ?? null, null);
});

test('queue updates and duplicate event IDs are applied once', async () => {
  const eventId = crypto.randomUUID();
  const frame = { type: 'control', action: 'queueAdd', eventId, tracks: [{ videoId: 'video-1', title: 'One', artist: 'Test' }] };
  send(hostSocket, frame); await waitFor(guestSocket, f => f.type === 'queue' && f.queue.items?.length === 1);
  send(hostSocket, frame); await waitFor(hostSocket, f => f.type === 'queue' && f.queue.items?.length === 1);
  const state = await json(await request(`/api/parties/${host.code}`, { headers: { authorization: `Bearer ${host.token}` } }));
  assert.equal(state.queue.items.length, 1); assert.equal(state.queue.seq, 1);

  const autoplayTrack = { videoId: 'auto-1', title: 'Auto', fromAutoplay: true };
  send(hostSocket, { type: 'control', action: 'queueAdd', eventId: crypto.randomUUID(), track: autoplayTrack });
  await waitFor(guestSocket, f => f.type === 'queue' && f.queue.seq === 2);
  send(hostSocket, { type: 'control', action: 'queueAdd', eventId: crypto.randomUUID(), track: autoplayTrack });
  await waitFor(hostSocket, f => f.type === 'state');
  const deduped = await json(await request(`/api/parties/${host.code}`, { headers: { authorization: `Bearer ${host.token}` } }));
  assert.equal(deduped.queue.seq, 2); assert.equal(deduped.queue.items.length, 2);

  const fullQueue = Array.from({ length: 26 }, (_, i) => ({ videoId: `full-${i}`, title: `Full ${i}` }));
  fullQueue[0] = { videoId: 'video-1', title: 'One' };
  send(hostSocket, { type: 'control', action: 'setQueue', eventId: crypto.randomUUID(), queue: fullQueue, queueIndex: 0 });
  await waitFor(guestSocket, f => f.type === 'queue' && f.queue.items.length === 26);
  send(hostSocket, { type: 'control', action: 'queueAdd', eventId: crypto.randomUUID(), track: { ...fullQueue[1], fromAutoplay: true } });
  assert.equal((await waitFor(hostSocket, f => f.type === 'error' && f.error === 'queue_full')).error, 'queue_full');

  send(hostSocket, { type: 'control', action: 'queueRemove', eventId: crypto.randomUUID(), videoId: 'full-1' });
  await waitFor(guestSocket, f => f.type === 'queue' && f.queue.items.length === 25);
  send(hostSocket, { type: 'control', action: 'queueMove', eventId: crypto.randomUUID(), fromIndex: 1, toIndex: 2, videoId: 'full-2' });
  await waitFor(guestSocket, f => f.type === 'queue' && f.queue.items[1].videoId === 'full-3' && f.queue.items[2].videoId === 'full-2');
  send(hostSocket, { type: 'control', action: 'next', eventId: crypto.randomUUID() });
  await waitFor(guestSocket, f => f.type === 'state' && f.playback.track?.videoId === 'full-3');
  send(hostSocket, { type: 'control', action: 'previous', eventId: crypto.randomUUID() });
  await waitFor(guestSocket, f => f.type === 'state' && f.playback.track?.videoId === 'video-1');
  send(hostSocket, { type: 'control', action: 'queueClear', eventId: crypto.randomUUID() });
  await waitFor(guestSocket, f => f.type === 'queue' && f.queue.items.length === 1);
});

test('playback state synchronizes between participants', async () => {
  send(hostSocket, { type: 'control', action: 'setTrack', eventId: crypto.randomUUID(), track: { videoId: 'video-1', title: 'One' }, isPlaying: true });
  const frame = await waitFor(guestSocket, f => f.type === 'state' && f.playback.track?.videoId === 'video-1');
  assert.equal(frame.playback.isPlaying, true);
  send(guestSocket, { type: 'sync' });
  const synced = await waitFor(guestSocket, f => f.type === 'state');
  assert.equal(synced.playback.track.videoId, 'video-1');
});

test('Windows desktop host and Android member share YouTube playback, controls and queue', async () => {
  const created = await request('/api/parties', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'desktop:stable-device', deviceId: 'desktop-stable-device', displayName: 'Windows Desktop' }) });
  assert.equal(created.status, 201);
  const pc = await json(created);
  const pcConnection = await socket(pc.code, pc.token);
  const joined = await request(`/api/parties/${pc.code}/join`, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'android:account-hash', deviceId: 'android-stable-device', displayName: 'Android' }) });
  assert.equal(joined.status, 200);
  const android = await json(joined);
  const androidConnection = await socket(pc.code, android.token);
  assert.equal(pcConnection.welcome.you.isHost, true);
  assert.equal(androidConnection.welcome.you.isHost, false);

  const firstTrack = { videoId: 'yt-cross-device-1', title: 'Shared from Windows', artist: 'AUDIFY', durationMs: 180000 };
  const androidTrack = waitFor(androidConnection.ws, f => f.type === 'state' && f.playback.track?.videoId === firstTrack.videoId);
  send(pcConnection.ws, { type: 'control', action: 'setTrack', eventId: crypto.randomUUID(), track: firstTrack, isPlaying: true });
  assert.equal((await androidTrack).playback.isPlaying, true);

  const paused = waitFor(pcConnection.ws, f => f.type === 'state' && !f.playback.isPlaying);
  send(androidConnection.ws, { type: 'control', action: 'pause', eventId: crypto.randomUUID(), positionMs: 42000 });
  assert.equal((await paused).playback.positionMs, 42000);

  const resumed = waitFor(androidConnection.ws, f => f.type === 'state' && f.playback.isPlaying);
  send(pcConnection.ws, { type: 'control', action: 'play', eventId: crypto.randomUUID(), positionMs: 42000 });
  assert.equal((await resumed).playback.isPlaying, true);

  const sought = waitFor(androidConnection.ws, f => f.type === 'state' && f.playback.positionMs === 90000);
  send(pcConnection.ws, { type: 'control', action: 'seek', eventId: crypto.randomUUID(), positionMs: 90000 });
  assert.equal((await sought).playback.positionMs, 90000);

  const queued = waitFor(androidConnection.ws, f => f.type === 'queue' && f.queue.items.some(item => item.videoId === 'yt-cross-device-2'));
  send(pcConnection.ws, { type: 'control', action: 'queueAdd', eventId: crypto.randomUUID(), track: { videoId: 'yt-cross-device-2', title: 'Next from Windows' } });
  assert.ok((await queued).queue.items.some(item => item.videoId === 'yt-cross-device-2'));
  pcConnection.ws.close(); androidConnection.ws.close();
});

test('Android host and Windows desktop member share YouTube playback and queue changes', async () => {
  const created = await request('/api/parties', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'android:account', deviceId: 'android-host-device', displayName: 'Android Host' }) });
  assert.equal(created.status, 201);
  const android = await json(created);
  const androidConnection = await socket(android.code, android.token);
  const joined = await request(`/api/parties/${android.code}/join`, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'desktop:device', deviceId: 'windows-member-device', displayName: 'Windows Desktop' }) });
  assert.equal(joined.status, 200);
  const pc = await json(joined);
  const pcConnection = await socket(android.code, pc.token);
  assert.equal(androidConnection.welcome.you.isHost, true);
  assert.equal(pcConnection.welcome.you.isHost, false);

  const track = { videoId: 'yt-android-hosted', title: 'Shared from Android', artist: 'AUDIFY', durationMs: 210000 };
  const desktopPlayback = waitFor(pcConnection.ws, f => f.type === 'state' && f.playback.track?.videoId === track.videoId);
  send(androidConnection.ws, { type: 'control', action: 'setTrack', eventId: crypto.randomUUID(), track, isPlaying: true });
  assert.equal((await desktopPlayback).playback.track.videoId, track.videoId);

  const queueSync = waitFor(pcConnection.ws, f => f.type === 'queue' && f.queue.items.length === 2);
  send(androidConnection.ws, { type: 'control', action: 'setQueue', eventId: crypto.randomUUID(),
    queue: [track, { videoId: 'yt-android-next', title: 'Next from Android' }], queueIndex: 0 });
  assert.equal((await queueSync).queue.items[1].videoId, 'yt-android-next');
  androidConnection.ws.close(); pcConnection.ws.close();
});

test('host-only policy is enforced by the server', async () => {
  const connected = await socket(host.code, other.token);
  otherSocket = connected.ws;
  assert.equal(connected.welcome.type, 'welcome');
  send(hostSocket, { type: 'control', action: 'setMaxMembers', eventId: crypto.randomUUID(), maxMembers: 3 });
  await waitFor(guestSocket, f => f.type === 'members' && f.maxMembers === 3);
  send(guestSocket, { type: 'control', action: 'setMaxMembers', eventId: crypto.randomUUID(), maxMembers: 4 });
  assert.equal((await waitFor(guestSocket, f => f.type === 'error' && f.error === 'host_only')).error, 'host_only');
  send(hostSocket, { type: 'control', action: 'setHostOnlyControl', eventId: crypto.randomUUID(), enabled: true });
  await waitFor(guestSocket, f => f.type === 'members' && f.hostOnlyControl === true);
  send(guestSocket, { type: 'control', action: 'pause', eventId: crypto.randomUUID() });
  const refused = await waitFor(guestSocket, f => f.type === 'error' && f.error === 'host_only');
  assert.equal(refused.error, 'host_only');
  send(hostSocket, { type: 'control', action: 'setHostOnlyControl', eventId: crypto.randomUUID(), enabled: false });
  await waitFor(guestSocket, f => f.type === 'members' && f.hostOnlyControl === false);
  send(hostSocket, { type: 'control', action: 'kick', eventId: crypto.randomUUID(), memberId: other.you.memberId });
  assert.equal((await waitFor(otherSocket, f => f.type === 'bye')).reason, 'kicked');
  await waitFor(guestSocket, f => f.type === 'members' && f.members.length === 2);
});

test('a disconnected participant can reconnect with the same token and recover state', async () => {
  guestSocket.close();
  await new Promise(resolve => setTimeout(resolve, 150));
  const reconnected = await socket(guest.code, guest.token);
  guestSocket = reconnected.ws;
  assert.equal(reconnected.welcome.party.playback.track.videoId, 'video-1');
  assert.equal(reconnected.welcome.you.memberId, guest.you.memberId);
});

test('desktop reconnect can re-authenticate its stable device identity and rotate its token', async () => {
  const created = await request('/api/parties', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'desktop:reconnect-device', deviceId: 'desktop-reconnect-device', displayName: 'Windows' }) });
  assert.equal(created.status, 201);
  const first = await json(created);
  const firstSocket = await socket(first.code, first.token);
  const memberId = firstSocket.welcome.you.memberId;
  firstSocket.ws.close();
  await new Promise(resolve => setTimeout(resolve, 150));

  const joined = await request(`/api/parties/${first.code}/join`, { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'desktop:reconnect-device', deviceId: 'desktop-reconnect-device', displayName: 'Windows' }) });
  assert.equal(joined.status, 200);
  const renewed = await json(joined);
  assert.notEqual(renewed.token, first.token);
  const secondSocket = await socket(first.code, renewed.token);
  assert.equal(secondSocket.welcome.you.memberId, memberId);
  secondSocket.ws.close();
});

test('invalid party codes and tokens issued for another party cannot join', async () => {
  const invalid = await request('/api/parties/NOPE99/join', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'valid-user', deviceId: 'valid-device', displayName: 'Valid' }) });
  assert.equal(invalid.status, 404);
  const otherParty = await request('/api/parties', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'isolated-user', deviceId: 'isolated-device', displayName: 'Isolated' }) });
  assert.equal(otherParty.status, 201);
  const isolated = await json(otherParty);
  assert.equal(await nativeHandshake(host.code, isolated.token), 401);
});

test('presence, public stats, SVG badge and invite landing are compatible', async () => {
  const id = crypto.randomUUID();
  const ping = await request('/api/presence', { method: 'POST', headers: { 'content-type': 'application/json', origin: 'https://audify.in' },
    body: JSON.stringify({ id, platform: 'browser', open: true }) });
  assert.equal(ping.status, 200); assert.equal((await json(ping)).intervalSec, 300);
  const stats = await json(await request('/api/stats/live'));
  assert.ok(stats.online >= 1); assert.ok('updatedAt' in stats);
  const badgeResponse = await request('/api/stats/live/badge.svg');
  const badge = await badgeResponse.text();
  assert.equal(badgeResponse.headers.get('content-type'), 'image/svg+xml;charset=utf-8');
  const value = String(stats.online), valueWidth = 8.25 * value.length + 24;
  const expectedBadge = `<svg xmlns="http://www.w3.org/2000/svg" width="${124.25 + valueWidth}" height="28" role="img" aria-label="LISTENING NOW: ${value}"><title>LISTENING NOW: ${value}</title><g shape-rendering="crispEdges"><rect width="124.25" height="28" fill="#0d1117"/><rect x="124.25" width="${valueWidth}" height="28" fill="#fb4f67"/></g><g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="100"><text transform="scale(.1)" x="621.25" y="175" textLength="1002.5">LISTENING NOW</text><text transform="scale(.1)" x="${(124.25 + valueWidth / 2) * 10}" y="175" textLength="${82.5 * value.length}" font-weight="bold">${value}</text></g></svg>`;
  assert.equal(badge, expectedBadge, 'SVG badge geometry and markup must match the Go backend');
  const inviteResponse = await request(`/invite/${host.code}`);
  const invite = await inviteResponse.text();
  assert.equal(inviteResponse.status, 200);
  assert.equal(inviteResponse.headers.get('content-type'), 'text/html; charset=utf-8');
  assert.match(invite, /Listen Together • 2 in party/);
  assert.match(invite, /Join the Music Party/);
  assert.match(invite, /audify:\/\/party/);
  assert.match(invite, /intent:\/\/party/);
  assert.match(invite, /setTimeout\(launch,100\)/);
  const missing = await request('/invite/ZZZZZZ');
  assert.equal(missing.status, 404);
  assert.match(await missing.text(), /Party Not Found/);
  const malformed = await request('/invite/not-a-code');
  assert.equal(malformed.status, 400);
  assert.match(await malformed.text(), /Party Not Found/);
  await request('/api/presence', { method: 'POST', headers: { 'content-type': 'application/json', origin: 'https://audify.in' },
    body: JSON.stringify({ id, platform: 'browser', open: false }) });
});

test('presence validation and all supported platform counters match the Go contract', async () => {
  const androidId = crypto.randomUUID(), pcId = crypto.randomUUID();
  for (const [id, platform] of [[androidId, 'android'], [pcId, 'pc']]) {
    const response = await request('/api/presence', { method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id, platform, open: true }) });
    assert.equal(response.status, 200);
    assert.deepEqual(await json(response), { intervalSec: 300 });
  }
  assert.equal((await request('/api/presence', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' })).status, 422);
  assert.equal((await request('/api/presence', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ id: crypto.randomUUID(), platform: 'browser', open: true }) })).status, 403);
  const stats = await json(await request('/api/stats/live'));
  assert.ok(stats.android >= 1); assert.ok(stats.pc >= 1);
  assert.equal(stats.online, stats.android + stats.pc + stats.browser);
  for (const id of [androidId, pcId]) await request('/api/presence', { method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ id, platform: id === androidId ? 'android' : 'pc', open: false }) });
});

test('Android, web and Windows desktop share the authenticated Cloudflare party protocol', async () => {
  const android = await readFile(new URL('../../app/src/main/java/com/music/audify/data/listentogether/ListenTogether.kt', import.meta.url), 'utf8');
  const web = await readFile(new URL('../../web/app/src/party/listenTogether.ts', import.meta.url), 'utf8');
  const tv = await readFile(new URL('../../tv/build.gradle.kts', import.meta.url), 'utf8');
  const desktop = await readFile(new URL('../../desktop/crates/state/src/listening/link.rs', import.meta.url), 'utf8');
  assert.match(android, /put\("eventId",\s*UUID\.randomUUID\(\)\.toString\(\)\)/);
  assert.match(web, /eventId:\s*crypto\.randomUUID\(\)/);
  assert.match(tv, /kotlin\.directories \+= "\.\.\/app\/src\/main\/java"/);
  assert.match(desktop, /\/api\/parties/);
  assert.match(desktop, /\/ws\/parties\//);
  assert.match(desktop, /AUTHORIZATION/);
  assert.match(desktop, /Bearer \{token\}/);
  assert.match(desktop, /desktop:\{device_id\}/);
});

test('host departure hands host role to remaining participant', async () => {
  const left = await request(`/api/parties/${host.code}/leave`, { method: 'POST', headers: { authorization: `Bearer ${host.token}` } });
  assert.equal(left.status, 200);
  const snapshot = await json(await request(`/api/parties/${host.code}`, { headers: { authorization: `Bearer ${guest.token}` } }));
  assert.equal(snapshot.members.length, 1); assert.equal(snapshot.members[0].isHost, true);
});

test('creation validation and per-IP creation rate limit are enforced', async t => {
  if (!base.includes('127.0.0.1')) { t.skip('Cloudflare may route separate test requests through different egress IPs; verify the fixed-IP bucket locally.'); return; }
  const invalid = await request(`/api/parties/${host.code}/join`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' });
  assert.equal(invalid.status, 422);
  const payload = JSON.stringify({ userId: 'rate-user', deviceId: 'rate-device', displayName: 'Rate Test' });
  const headers = { 'content-type': 'application/json', 'x-test-ip': `rate-limit-suite-${crypto.randomUUID()}` };
  const first = await request('/api/parties', { method: 'POST', headers, body: payload });
  const second = await request('/api/parties', { method: 'POST', headers, body: payload });
  const third = await request('/api/parties', { method: 'POST', headers, body: payload });
  assert.equal(first.status, 201); assert.equal(second.status, 201); assert.equal(third.status, 429);
});

test('authentication and join attempts are rate limited by source IP', async t => {
  if (!base.includes('127.0.0.1')) { t.skip('Fixed-source-IP auth buckets are tested locally.'); return; }
  const authHeaders = { 'x-test-ip': `auth-limit-suite-${crypto.randomUUID()}` };
  for (let i = 0; i < 30; i++) assert.equal((await request(`/api/parties/${host.code}`, { headers: authHeaders })).status, 401);
  assert.equal((await request(`/api/parties/${host.code}`, { headers: authHeaders })).status, 429);

  const joinHeaders = { 'content-type': 'application/json', 'x-test-ip': `join-limit-suite-${crypto.randomUUID()}` };
  for (let i = 0; i < 10; i++) assert.equal((await request(`/api/parties/${host.code}/join`, { method: 'POST', headers: joinHeaders, body: '{}' })).status, 422);
  assert.equal((await request(`/api/parties/${host.code}/join`, { method: 'POST', headers: joinHeaders, body: '{}' })).status, 429);
});
