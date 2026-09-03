/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePerformanceTest {

    @Test
    fun `low-RAM devices get a smaller memory cache fraction`() {
        assertEquals(0.10, lowEndMemoryCacheFraction(16), 0.0)
        assertEquals(0.10, lowEndMemoryCacheFraction(64), 0.0)
        assertEquals(0.10, lowEndMemoryCacheFraction(127), 0.0)
        assertEquals(0.15, lowEndMemoryCacheFraction(128), 0.0)
        assertEquals(0.15, lowEndMemoryCacheFraction(256), 0.0)
    }

    @Test
    fun `low-RAM devices disable image crossfade`() {
        assertEquals(false, lowEndUseCrossfade(64))
        assertEquals(false, lowEndUseCrossfade(127))
        assertEquals(true, lowEndUseCrossfade(128))
        assertEquals(true, lowEndUseCrossfade(512))
    }

    @Test
    fun `low-RAM devices cap artwork decode at 480 regardless of density`() {
        assertEquals(480, artworkDecodeCapPx(1.0f, 64))
        assertEquals(480, artworkDecodeCapPx(1.5f, 64))
        assertEquals(480, artworkDecodeCapPx(3.0f, 64))
    }

    @Test
    fun `capable devices scale artwork decode with density`() {
        assertEquals(480, artworkDecodeCapPx(1.0f, 256))
        assertEquals(720, artworkDecodeCapPx(1.5f, 256))
        assertEquals(960, artworkDecodeCapPx(2.0f, 256))
        assertEquals(1080, artworkDecodeCapPx(3.0f, 256))
        assertEquals(1080, artworkDecodeCapPx(4.0f, 256))
    }
}