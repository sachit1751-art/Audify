/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerLevelsTest {

    @Test
    fun `empty input yields zero levels`() {
        val levels = computeVisualizerLevels(ByteArray(0), 24)
        assertEquals(24, levels.size)
        assertTrue(levels.all { it == 0f })
    }

    @Test
    fun `input smaller than a full pair yields zero levels`() {
        val levels = computeVisualizerLevels(byteArrayOf(1, 2, 3), 8)
        assertEquals(8, levels.size)
        assertTrue(levels.all { it == 0f })
    }

    @Test
    fun `zero fft input yields zero levels`() {
        val levels = computeVisualizerLevels(ByteArray(128), 24)
        assertEquals(24, levels.size)
        assertTrue(levels.all { it == 0f })
    }

    @Test
    fun `non positive bar count yields empty array`() {
        assertTrue(computeVisualizerLevels(ByteArray(128), 0).isEmpty())
        assertTrue(computeVisualizerLevels(ByteArray(128), -3).isEmpty())
    }

    @Test
    fun `full scale signal saturates near one`() {
        // Every pair (2i, 2i+1) is re=im=127, skipping the DC/Nyquist entries at 0 and 1.
        val fft = ByteArray(258) { index -> if (index >= 2) 127.toByte() else 0 }
        val levels = computeVisualizerLevels(fft, 24)
        assertEquals(24, levels.size)
        assertTrue("expected near-full bars, got ${levels.toList()}", levels.all { it >= 0.95f })
    }

    @Test
    fun `output is bounded and non negative for noisy input`() {
        val fft = ByteArray(512)
        var seed = 42
        for (i in fft.indices) {
            seed = seed * 31 + 17
            fft[i] = (seed % 255 - 127).toByte()
        }
        val levels = computeVisualizerLevels(fft, 32)
        assertEquals(32, levels.size)
        assertTrue(levels.all { it in 0f..1f })
    }

    @Test
    fun `low frequency energy produces stronger leading bars`() {
        // Amplitude decreases linearly with bin index: bins 0.. (low frequencies)
        // dominate, so the leading bars (which cover the low bins) must be taller
        // than the trailing bars (which only see high, weak bins).
        val fft = ByteArray(258) { index ->
            if (index >= 2 && index % 2 == 0) {
                val bin = (index - 2) / 2
                (127 - bin).toByte()
            } else {
                0
            }
        }
        val levels = computeVisualizerLevels(fft, 24)
        assertTrue("expected leading bar above trailing bar, got ${levels.toList()}", levels.first() > levels.last())
    }

    @Test
    fun `same input yields identical output`() {
        val fft = ByteArray(256) { index -> (index * 3 - 100).toByte() }
        val first = computeVisualizerLevels(fft, 24)
        val second = computeVisualizerLevels(fft.copyOf(), 24)
        assertTrue(first.contentEquals(second))
    }
}
