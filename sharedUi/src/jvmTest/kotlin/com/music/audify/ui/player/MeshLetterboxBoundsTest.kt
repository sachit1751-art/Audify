package com.music.audify.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshLetterboxBoundsTest {
    @Test
    fun `trims uniform black top and bottom letterbox bars`() {
        val width = 128
        val height = 128
        val pixels = IntArray(width * height) { index ->
            val y = index / width
            if (y < 16 || y >= 112) 0xFF000000.toInt() else 0xFF336699.toInt()
        }

        assertEquals(MeshSampleBounds(0, 16, width, 112), detectMeshSampleBounds(pixels, width, height))
    }

    @Test
    fun `trims uniform black side letterbox bars`() {
        val width = 128
        val height = 128
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            if (x < 12 || x >= 116) 0xFF000000.toInt() else 0xFFB06030.toInt()
        }

        assertEquals(MeshSampleBounds(12, 0, 116, height), detectMeshSampleBounds(pixels, width, height))
    }

    @Test
    fun `does not crop a uniformly dark frame`() {
        val pixels = IntArray(64 * 64) { 0xFF080808.toInt() }

        assertEquals(MeshSampleBounds(0, 0, 64, 64), detectMeshSampleBounds(pixels, 64, 64))
    }

    @Test
    fun `leaves full frame unchanged when no letterbox bars exist`() {
        val pixels = IntArray(64 * 64) { index ->
            val x = index % 64
            val y = index / 64
            (0xFF shl 24) or ((x * 3) shl 16) or ((y * 3) shl 8) or 0x30
        }

        assertEquals(MeshSampleBounds(0, 0, 64, 64), detectMeshSampleBounds(pixels, 64, 64))
    }
}
