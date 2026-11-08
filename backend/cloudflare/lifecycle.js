/** Shared room-expiry and next-alarm calculations. Kept pure for Node tests. */
export function isRoomExpired(room, nowMs, maxAgeMs, emptyTtlMs) {
  return nowMs - room.createdAtMs > maxAgeMs ||
    (!room.members.some(member => member.connected) && room.emptySinceMs != null && nowMs - room.emptySinceMs > emptyTtlMs);
}

export function nextCleanupAt(room, nowMs, disconnectGraceMs, emptyTtlMs, maxAgeMs) {
  const deadlines = [room.createdAtMs + maxAgeMs];
  for (const member of room.members) {
    if (!member.connected) deadlines.push(member.lastSeenMs + disconnectGraceMs + 1);
  }
  if (room.emptySinceMs != null) deadlines.push(room.emptySinceMs + emptyTtlMs + 1);
  const future = deadlines.filter(deadline => deadline > nowMs);
  return future.length ? Math.min(...future) : null;
}
