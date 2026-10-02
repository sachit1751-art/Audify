/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

/**
 * Session-scoped memory of queue rows the listener jumped over with a forward queue jump
 * (plan 020). Pure state: no Android imports, no persistence — marks only live as long as
 * the process, which matches what they mean ("skipped earlier this session").
 *
 * [markSkipped] deduplicates and keeps at most [maxEntries] mediaIds (FIFO eviction): beyond
 * that the rows are far enough off-screen that a mark would mislead.
 */
class QueueHistoryState(private val maxEntries: Int = 100) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val skipped = ArrayDeque<String>()

    /** Marks the given mediaIds as skipped, in order, ignoring duplicates. */
    @Synchronized
    fun markSkipped(mediaIds: Collection<String>) {
        for (id in mediaIds) {
            if (id in skipped) continue
            skipped.addLast(id)
            while (skipped.size > maxEntries) {
                skipped.removeFirst()
            }
        }
    }

    /** Stable snapshot of the currently marked mediaIds. */
    @Synchronized
    fun skippedIdsSnapshot(): Set<String> = skipped.toSet()

    @Synchronized
    fun clear() = skipped.clear()
}
