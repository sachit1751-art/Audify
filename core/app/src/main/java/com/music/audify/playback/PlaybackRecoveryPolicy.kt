// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

/** Pure decisions for the service's stream-recovery and failed-start policy. */
internal object PlaybackRecoveryPolicy {

    data class SkipDecision(
        val reason: String?,
        val nextConsecutiveSkips: Int,
        val reachedConsecutiveSkipCap: Boolean = false,
    )

    fun nextAttempt(previousAttempts: Int): Int = previousAttempts + 1

    /** Keep repeated queue occurrences' retry budgets independent. */
    fun recoveryKey(mediaId: String, queueEntryId: String?): String =
        queueEntryId?.let { "entry:$it" } ?: "media:$mediaId"

    fun shouldGiveUp(attempts: Int, permanentVerdict: String?, maxRecoveries: Int): Boolean =
        permanentVerdict != null || attempts > maxRecoveries

    fun isCurrentItem(
        failedMediaId: String,
        currentMediaId: String?,
        failedQueueEntryId: String? = null,
        currentQueueEntryId: String? = null,
    ): Boolean = currentMediaId == failedMediaId &&
        (failedQueueEntryId == null || currentQueueEntryId == failedQueueEntryId)

    fun skipDecision(
        verdict: String?,
        neverStarted: Boolean,
        consecutiveSkips: Int,
        maxConsecutiveSkips: Int,
        genericFailureReason: String,
    ): SkipDecision {
        if (verdict != null) return SkipDecision(verdict, consecutiveSkips)
        if (!neverStarted) return SkipDecision(null, consecutiveSkips)
        if (consecutiveSkips >= maxConsecutiveSkips) {
            return SkipDecision(null, consecutiveSkips, reachedConsecutiveSkipCap = true)
        }
        return SkipDecision(genericFailureReason, consecutiveSkips + 1)
    }
}
