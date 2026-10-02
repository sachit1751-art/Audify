/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

/**
 * Health of a media item's stream source this session (plan 021).
 *
 * [HEALTHY] — no recent failures. [RETRYING] — at least one recoverable I/O failure; the
 * normal retry path is handling it. [FALLBACK] — failures reached the threshold: the next
 * resolution must skip the stream-URL cache and fetch a genuinely fresh source instead of
 * re-trying a possibly poisoned URL/client combination.
 */
enum class StreamHealth { HEALTHY, RETRYING, FALLBACK }

/** Immutable snapshot exposed to the UI (plan 019's Signal sheet renders this). */
data class StreamHealthStatus(
    val health: StreamHealth = StreamHealth.HEALTHY,
    val failureCount: Int = 0,
)

/**
 * Per-mediaId failure memory with TTL, mirroring the spirit of InnerTubeXPlayer's
 * WEB_REMIX failure exclusion but at the service layer and client-agnostic.
 *
 * Pure JVM state (injectable clock) so the rules stay unit-testable. Entries expire after
 * [ttlMs] without events and are swept lazily on access — no background work anywhere.
 */
class StreamSourceHealth(
    private val fallbackThreshold: Int = 2,
    private val ttlMs: Long = 5 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(fallbackThreshold >= 1) { "fallbackThreshold must be at least 1" }
    }

    private data class Entry(
        val failureCount: Int,
        val lastFailureAt: Long,
        val lastFailedClient: String?,
    )

    private val entries = HashMap<String, Entry>()

    /** Records a recoverable I/O failure for [mediaId]. Only call for source-side errors. */
    @Synchronized
    fun onFailure(
        mediaId: String,
        clientName: String?,
    ) {
        sweepExpired()
        val previous = entries[mediaId]
        entries[mediaId] =
            Entry(
                failureCount = (previous?.failureCount ?: 0) + 1,
                lastFailureAt = clock(),
                lastFailedClient = clientName ?: previous?.lastFailedClient,
            )
    }

    /** Clears the entry after a successful stream start. */
    @Synchronized
    fun onSuccess(mediaId: String) {
        entries.remove(mediaId)
    }

    @Synchronized
    fun state(mediaId: String): StreamHealth = status(mediaId).health

    @Synchronized
    fun status(mediaId: String): StreamHealthStatus {
        val entry = liveEntry(mediaId)
            ?: return StreamHealthStatus(StreamHealth.HEALTHY, 0)
        val health =
            when {
                entry.failureCount >= fallbackThreshold -> StreamHealth.FALLBACK
                else -> StreamHealth.RETRYING
            }
        return StreamHealthStatus(health, entry.failureCount)
    }

    /** The client that served the most recent failed URL, for exclusion hints. */
    @Synchronized
    fun failedClient(mediaId: String): String? = liveEntry(mediaId)?.lastFailedClient

    @Synchronized
    fun clear(mediaId: String) {
        entries.remove(mediaId)
    }

    @Synchronized
    fun clearAll() {
        entries.clear()
    }

    private fun liveEntry(mediaId: String): Entry? {
        val entry = entries[mediaId] ?: return null
        if (clock() - entry.lastFailureAt >= ttlMs) {
            entries.remove(mediaId)
            return null
        }
        return entry
    }

    private fun sweepExpired() {
        val now = clock()
        entries.entries.removeIf { now - it.value.lastFailureAt >= ttlMs }
    }
}
