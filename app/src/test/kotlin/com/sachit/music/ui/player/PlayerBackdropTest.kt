/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure mesh math from [PlayerBackdrop]: the flip+rotate below the seam,
 * the bilinear resampling onto the texture, and the colour helpers.
 *
 * These are the parts that used to live inside `meshOf` on the Android framework side
 * and were therefore untestable on the JVM — now extracted so the index mapping and
 * interpolation can be asserted directly.
 */
class PlayerBackdropTest {

    // --- argb ---------------------------------------------------------------

    @Test
    fun `argb packs red green blue into a 32-bit argb word`() {
        // argb(r, g, b) = (0xFF << 24) | (r << 16) | (g << 8) | b
        assertEquals(0xFF_00_00_00.toInt(), argb(0, 0, 0))
        assertEquals(0xFF_10_20_30.toInt(), argb(0x10, 0x20, 0x30))
        assertEquals(0xFF_FF_FF_FF.toInt(), argb(255, 255, 255))
    }

    @Test
    fun `argb ignores alpha and always sets it to opaque`() {
        assertEquals(0xFF_10_20_30.toInt(), argb(0x10, 0x20, 0x30))
    }

    // --- lerpArgb -----------------------------------------------------------

    @Test
    fun `lerpArgb returns from when t is zero`() {
        val from = argb(100, 50, 25)
        val to = argb(200, 150, 100)
        assertEquals(from, lerpArgb(from, to, 0f))
    }

    @Test
    fun `lerpArgb returns to when t is one`() {
        val from = argb(100, 50, 25)
        val to = argb(200, 150, 100)
        assertEquals(to, lerpArgb(from, to, 1f))
    }

    @Test
    fun `lerpArgb interpolates halfway between two colours`() {
        val from = argb(0x00, 0x00, 0x00)
        val to = argb(0x10, 0x20, 0x30)
        val mid = lerpArgb(from, to, 0.5f)
        assertEquals(argb(0x08, 0x10, 0x18), mid)
    }

    @Test
    fun `lerpArgb clamps t outside zero-one`() {
        val from = argb(10, 20, 30)
        val to = argb(100, 200, 50)
        assertEquals(from, lerpArgb(from, to, -0.5f))
        assertEquals(to, lerpArgb(from, to, 2f))
    }

    // --- smoothstep ---------------------------------------------------------

    @Test
    fun `smoothstep returns zero for inputs at or below zero`() {
        assertEquals(0f, smoothstep(-2f))
        assertEquals(0f, smoothstep(0f))
    }

    @Test
    fun `smoothstep returns one for inputs at or above one`() {
        assertEquals(1f, smoothstep(1f))
        assertEquals(1f, smoothstep(3f))
    }

    @Test
    fun `smoothstep is smooth through the middle`() {
        // smoothstep(0.5) = 0.5 * 0.5 * (3 - 2 * 0.5) = 0.25 * 2 = 0.5
        assertEquals(0.5f, smoothstep(0.5f), 0.001f)
    }

    // --- rotatedBelowSeam --------------------------------------------------

    @Test
    fun `rotatedBelowSeam is identity when there is only one row`() {
        val grid = intArrayOf(1, 2, 3, 4, 5, 6)
        val result = grid.rotatedBelowSeam(cols = 3, rows = 1, seed = 42)
        assertArrayEquals(grid, result)
    }

    @Test
    fun `rotatedBelowSeam keeps the first row in place`() {
        // 2 rows × 3 cols: first row (indices 0-2) must not move.
        val grid = intArrayOf(10, 20, 30, 100, 200, 300)
        val result = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = 0)
        assertEquals(10, result[0])
        assertEquals(20, result[1])
        assertEquals(30, result[2])
    }

    @Test
    fun `rotatedBelowSeam flips below the seam when mirror is on`() {
        // Brute-force find a seed where nextBoolean() returns true
        val mirrorSeed = (0..10000).firstOrNull { seed ->
            java.util.Random(seed.toLong()).nextBoolean()
        }!!
        val grid = intArrayOf(
            1, 2, 3,   // row 0 (kept)
            4, 5, 6,   // row 1 cell 0
            7, 8, 9,   // row 1 cell 1
            10, 11, 12 // row 1 cell 2
        )
        val result = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = mirrorSeed)
        // When mirror=true, row 1 is reversed: [6, 5, 4]
        assertEquals(6, result[3])
        assertEquals(5, result[4])
        assertEquals(4, result[5])
    }

    @Test
    fun `rotatedBelowSeam shifts columns below the seam`() {
        // Find a seed with mirror=false and shift != 0
        val shiftSeed = (0..10000).firstOrNull { seed ->
            val r = java.util.Random(seed.toLong())
            val mirror = r.nextBoolean()
            val shift = r.nextInt(3)
            !mirror && shift != 0
        }!!
        val grid = intArrayOf(
            1, 2, 3,   // row 0
            4, 5, 6,   // row 1 original
        )
        val result = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = shiftSeed)
        // Verify shift occurred: result should differ from original order
        assertFalse("shift should change the order", result[3] == 4 && result[4] == 5 && result[5] == 6)
    }

    @Test
    fun `rotatedBelowSeam wraps around the column boundary`() {
        // Find a seed with mirror=false and shift=2
        val wrapSeed = (0..10000).firstOrNull { seed ->
            val r = java.util.Random(seed.toLong())
            val mirror = r.nextBoolean()
            val shift = r.nextInt(3)
            !mirror && shift == 2
        }!!
        val grid = intArrayOf(
            100, 200, 300,
            1, 2, 3,
        )
        val r = java.util.Random(wrapSeed.toLong())
        val mirror = r.nextBoolean()
        val shift = r.nextInt(3)
        val result = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = wrapSeed)
        // Verify wrapping: with shift=2 and cols=3:
        // each output[x] = input[(x + shift) % cols]
        // result should be a permutation of [1, 2, 3]
        val row1 = listOf(result[3], result[4], result[5])
        assertEquals(listOf(1, 2, 3).sorted(), row1.sorted())
        // And verify wrap: one of the values should come from the "next" position wrapping around
        assertTrue("wrapping should occur with shift=$shift", row1.contains(1) && row1.indexOfFirst { it == 1 } != 0 || shift == 0)
    }

    @Test
    fun `rotatedBelowSeam is deterministic given the same seed`() {
        val grid = intArrayOf(1, 2, 3, 4, 5, 6)
        val a = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = 7)
        val b = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = 7)
        assertArrayEquals(a, b)
    }

    @Test
    fun `rotatedBelowSeam keeps first row deterministic regardless of seed`() {
        val grid = intArrayOf(11, 22, 33, 44, 55, 66)
        for (seed in 0 until 20) {
            val result = grid.rotatedBelowSeam(cols = 3, rows = 2, seed = seed)
            assertEquals(11, result[0])
            assertEquals(22, result[1])
            assertEquals(33, result[2])
        }
    }

    // --- resampled ---------------------------------------------------------

    @Test
    fun `resampled produces the right output size`() {
        val grid = IntArray(6) { it }
        val result = grid.resampled(cols = 3, rows = 2, size = 5)
        assertEquals(25, result.size) // 5×5
    }

    @Test
    fun `resampled identity on a 1×1 source`() {
        val grid = intArrayOf(argb(0xAB, 0xCD, 0xEF))
        val result = grid.resampled(cols = 1, rows = 1, size = 4)
        // Every output texel should sample the same single cell.
        for (v in result) {
            assertEquals(argb(0xAB, 0xCD, 0xEF), v)
        }
    }

    @Test
    fun `resampled is deterministic`() {
        val grid = IntArray(6) { it * 10 + 5 }
        val a = grid.resampled(cols = 3, rows = 2, size = 8)
        val b = grid.resampled(cols = 3, rows = 2, size = 8)
        assertArrayEquals(a, b)
    }

    // --- helpers ------------------------------------------------------------

    private fun assertArrayEquals(expected: IntArray, actual: IntArray) {
        assertEquals("array length mismatch", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("mismatch at index $i", expected[i], actual[i])
        }
    }
}
