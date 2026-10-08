/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for StreamResolution core types.
 *
 * Plan 022 step 2.
 */
class StreamResolutionTest {

    @Test
    fun `StreamSourceHealth starts healthy`() {
        val health = StreamSourceHealth()
        assertNotNull(health)
        assertEquals(StreamHealth.HEALTHY, health.state("test"))
    }

    @Test
    fun `StreamSourceHealth tracks failures`() {
        val health = StreamSourceHealth(fallbackThreshold = 2, ttlMs = 60_000L)
        health.onFailure("test", "WEB_REMIX")
        assertEquals(StreamHealth.RETRYING, health.state("test"))
        health.onFailure("test", "WEB_REMIX")
        assertEquals(StreamHealth.FALLBACK, health.state("test"))
    }

    @Test
    fun `StreamSourceHealth clears on success`() {
        val health = StreamSourceHealth(fallbackThreshold = 2, ttlMs = 60_000L)
        health.onFailure("test", null)
        assertEquals(StreamHealth.RETRYING, health.state("test"))
        health.onSuccess("test")
        assertEquals(StreamHealth.HEALTHY, health.state("test"))
    }

    @Test
    fun `CachedStreamUrl contains all stream info`() {
        val cached = CachedStreamUrl(
            url = "https://example.com/stream",
            requestHeaders = mapOf("Auth" to "token"),
            clientName = "WEB_REMIX",
            requireBoundedRange = true,
            rangeChunkSizeBytes = 1024,
            useRangeChunks = false,
        )
        assertEquals("https://example.com/stream", cached.url)
        assertEquals("token", cached.requestHeaders["Auth"])
        assertEquals("WEB_REMIX", cached.clientName)
        assertEquals(true, cached.requireBoundedRange)
        assertEquals(1024L, cached.rangeChunkSizeBytes)
        assertEquals(false, cached.useRangeChunks)
    }
}
