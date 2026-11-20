import test from 'node:test';
import assert from 'node:assert/strict';
import { isRoomExpired, nextCleanupAt } from './lifecycle.js';

const GRACE = 45_000, EMPTY_TTL = 120_000, MAX_AGE = 12 * 60 * 60 * 1000;
const room = overrides => ({ createdAtMs: 1_000, emptySinceMs: null,
  members: [{ connected: true, lastSeenMs: 1_000 }], ...overrides });

test('active rooms survive their empty-room TTL window', () => {
  assert.equal(isRoomExpired(room({ emptySinceMs: 1_000 }), 60_000, MAX_AGE, EMPTY_TTL), false);
});

test('empty rooms expire after the configured 120-second grace', () => {
  const empty = room({ emptySinceMs: 1_000, members: [] });
  assert.equal(isRoomExpired(empty, 121_000, MAX_AGE, EMPTY_TTL), false);
  assert.equal(isRoomExpired(empty, 121_001, MAX_AGE, EMPTY_TTL), true);
});

test('all rooms expire at the 12-hour maximum age', () => {
  assert.equal(isRoomExpired(room(), 1_000 + MAX_AGE + 1, MAX_AGE, EMPTY_TTL), true);
});

test('connected participants do not trigger empty-room expiry', () => {
  const active = room({ emptySinceMs: 1_000 });
  assert.equal(isRoomExpired(active, 1_000 + EMPTY_TTL + 1, MAX_AGE, EMPTY_TTL), false);
});

test('cleanup alarm selects the earliest disconnected-member deadline', () => {
  const pending = room({ members: [
    { connected: false, lastSeenMs: 10_000 },
    { connected: false, lastSeenMs: 20_000 },
  ], emptySinceMs: 20_000 });
  assert.equal(nextCleanupAt(pending, 30_000, GRACE, EMPTY_TTL, MAX_AGE), 55_001);
});

test('cleanup alarm schedules empty-room expiry when no disconnected members remain', () => {
  const empty = room({ members: [], emptySinceMs: 30_000 });
  assert.equal(nextCleanupAt(empty, 40_000, GRACE, EMPTY_TTL, MAX_AGE), 150_001);
});
