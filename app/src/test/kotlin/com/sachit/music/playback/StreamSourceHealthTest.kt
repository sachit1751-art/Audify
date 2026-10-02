/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourceHealthTest {

    private class FakeClock {
        var now: Long = 0L
        fun time(): Long = now
    }

    private fun trackerWith(clock: FakeClock) =
        StreamSourceHealth(fallbackThreshold = 2, ttlMs = 5 * 60 * 1000L, clock = clock::time)

    @Test
    fun `one failure means recovering`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", "WEB_REMIX")
        assertEquals(StreamHealth.RETRYING, tracker.state("a"))
        assertEquals(1, tracker.status("a").failureCount)
    }

    @Test
    fun `two failures reach fallback threshold`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", "WEB_REMIX")
        clock.now += 1000
        tracker.onFailure("a", "WEB_REMIX")
        assertEquals(StreamHealth.FALLBACK, tracker.state("a"))
        assertEquals(2, tracker.status("a").failureCount)
    }

    @Test
    fun `success clears the entry`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", null)
        tracker.onSuccess("a")
        assertEquals(StreamHealth.HEALTHY, tracker.state("a"))
        assertEquals(0, tracker.status("a").failureCount)
    }

    @Test
    fun `entries expire after ttl without events`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", null)
        clock.now += 5 * 60 * 1000L - 1
        assertEquals(StreamHealth.RETRYING, tracker.state("a"))
        clock.now += 1
        assertEquals(StreamHealth.HEALTHY, tracker.state("a"))
    }

    @Test
    fun `failure refreshes the ttl window`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", null)
        clock.now += 4 * 60 * 1000L
        tracker.onFailure("a", null)
        clock.now += 4 * 60 * 1000L
        // Second failure is still inside its own TTL window.
        assertEquals(StreamHealth.FALLBACK, tracker.state("a"))
    }

    @Test
    fun `failed client is tracked and kept across clientless failures`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        assertNull(tracker.failedClient("a"))
        tracker.onFailure("a", "WEB_REMIX")
        assertEquals("WEB_REMIX", tracker.failedClient("a"))
        tracker.onFailure("a", null)
        assertEquals("WEB_REMIX", tracker.failedClient("a"))
    }

    @Test
    fun `clear and clearAll reset state`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", null)
        tracker.onFailure("b", null)
        tracker.clear("a")
        assertEquals(StreamHealth.HEALTHY, tracker.state("a"))
        assertEquals(StreamHealth.RETRYING, tracker.state("b"))
        tracker.clearAll()
        assertEquals(StreamHealth.HEALTHY, tracker.state("b"))
    }

    @Test
    fun `independent media ids do not affect each other`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)
        tracker.onFailure("a", null)
        assertTrue(tracker.status("b").health == StreamHealth.HEALTHY)
    }
}
