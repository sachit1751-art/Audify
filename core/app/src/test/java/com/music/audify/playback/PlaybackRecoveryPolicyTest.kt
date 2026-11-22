// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryPolicyTest {

    @Test
    fun `retry budget allows two retries and gives up on third failure`() {
        val firstAttempt = PlaybackRecoveryPolicy.nextAttempt(previousAttempts = 0)
        val secondAttempt = PlaybackRecoveryPolicy.nextAttempt(previousAttempts = firstAttempt)
        val thirdAttempt = PlaybackRecoveryPolicy.nextAttempt(previousAttempts = secondAttempt)

        assertEquals(1, firstAttempt)
        assertEquals(2, secondAttempt)
        assertEquals(3, thirdAttempt)
        assertFalse(PlaybackRecoveryPolicy.shouldGiveUp(firstAttempt, null, maxRecoveries = 2))
        assertFalse(PlaybackRecoveryPolicy.shouldGiveUp(secondAttempt, null, maxRecoveries = 2))
        assertTrue(PlaybackRecoveryPolicy.shouldGiveUp(thirdAttempt, null, maxRecoveries = 2))
    }

    @Test
    fun `permanent verdict gives up immediately`() {
        assertTrue(
            PlaybackRecoveryPolicy.shouldGiveUp(
                attempts = 1,
                permanentVerdict = "unavailable in this region",
                maxRecoveries = 2,
            ),
        )
    }

    @Test
    fun `delayed retry is current only for the same non-null media id`() {
        assertTrue(PlaybackRecoveryPolicy.isCurrentItem("track-a", "track-a"))
        assertFalse(PlaybackRecoveryPolicy.isCurrentItem("track-a", "track-b"))
        assertFalse(PlaybackRecoveryPolicy.isCurrentItem("track-a", null))
    }

    @Test
    fun `delayed recovery rejects another occurrence of the same media`() {
        assertTrue(
            PlaybackRecoveryPolicy.isCurrentItem(
                failedMediaId = "track-a",
                currentMediaId = "track-a",
                failedQueueEntryId = "occurrence-1",
                currentQueueEntryId = "occurrence-1",
            ),
        )
        assertFalse(
            PlaybackRecoveryPolicy.isCurrentItem(
                failedMediaId = "track-a",
                currentMediaId = "track-a",
                failedQueueEntryId = "occurrence-1",
                currentQueueEntryId = "occurrence-2",
            ),
        )
    }

    @Test
    fun `retry budgets are independent for repeated queue occurrences`() {
        assertNotEquals(
            PlaybackRecoveryPolicy.recoveryKey("track-a", "occurrence-1"),
            PlaybackRecoveryPolicy.recoveryKey("track-a", "occurrence-2"),
        )
        assertEquals(
            PlaybackRecoveryPolicy.recoveryKey("track-a", null),
            PlaybackRecoveryPolicy.recoveryKey("track-a", null),
        )
        assertNotEquals(
            PlaybackRecoveryPolicy.recoveryKey("entry:track-a", null),
            PlaybackRecoveryPolicy.recoveryKey("other", "track-a"),
        )
    }

    @Test
    fun `permanent verdict skips regardless of start state and cap without consuming allowance`() {
        val decision = PlaybackRecoveryPolicy.skipDecision(
            verdict = "permanently unavailable",
            neverStarted = false,
            consecutiveSkips = 4,
            maxConsecutiveSkips = 4,
            genericFailureReason = "generic failure",
        )

        assertEquals("permanently unavailable", decision.reason)
        assertEquals(4, decision.nextConsecutiveSkips)
        assertFalse(decision.reachedConsecutiveSkipCap)
    }

    @Test
    fun `generic failed starts consume exactly four consecutive skips`() {
        var consecutiveSkips = 0
        repeat(4) { index ->
            val decision = PlaybackRecoveryPolicy.skipDecision(
                verdict = null,
                neverStarted = true,
                consecutiveSkips = consecutiveSkips,
                maxConsecutiveSkips = 4,
                genericFailureReason = "failed to start after ${index + 1} attempts (IO)",
            )
            assertTrue(decision.reason != null)
            assertEquals(consecutiveSkips + 1, decision.nextConsecutiveSkips)
            assertFalse(decision.reachedConsecutiveSkipCap)
            consecutiveSkips = decision.nextConsecutiveSkips
        }

        val capped = PlaybackRecoveryPolicy.skipDecision(
            verdict = null,
            neverStarted = true,
            consecutiveSkips = consecutiveSkips,
            maxConsecutiveSkips = 4,
            genericFailureReason = "must not be used",
        )
        assertEquals(null, capped.reason)
        assertEquals(4, capped.nextConsecutiveSkips)
        assertTrue(capped.reachedConsecutiveSkipCap)
    }

    @Test
    fun `mid-song generic failure neither skips nor consumes allowance`() {
        val decision = PlaybackRecoveryPolicy.skipDecision(
            verdict = null,
            neverStarted = false,
            consecutiveSkips = 2,
            maxConsecutiveSkips = 4,
            genericFailureReason = "generic failure",
        )

        assertEquals(null, decision.reason)
        assertEquals(2, decision.nextConsecutiveSkips)
        assertFalse(decision.reachedConsecutiveSkipCap)
    }
}
