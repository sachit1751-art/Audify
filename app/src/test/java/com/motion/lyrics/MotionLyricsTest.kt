package com.motion.lyrics

import com.music.audify.data.lyrics.LyricLine
import com.music.audify.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionLyricsTest {
    @Test
    fun adapterKeepsCharacterSpansAndProviderWordTimings() {
        val line = LyricLine(
            timeMs = 100,
            text = "Hello world",
            words = listOf(
                LyricWord(startMs = 100, endMs = 320, text = "Hello"),
                LyricWord(startMs = 420, endMs = 760, text = "world"),
            ),
        )

        assertEquals(
            listOf(
                MotionWord(0, 5, 100, 320),
                MotionWord(6, 11, 420, 760),
            ),
            line.toMotionWords(),
        )
    }

    @Test
    fun karaokeHeadMovesForwardAcrossWordsAndPauses() {
        val words = listOf(
            MotionWord(0, 5, 100, 320),
            MotionWord(6, 11, 420, 760),
        )
        val out = HeadOut()

        val positions = listOf(150L, 260L, 380L, 540L, 700L).map { position ->
            computeHead(words, position, out)
            out.head
        }

        assertTrue(positions.zipWithNext().all { (before, after) -> after >= before })
        assertTrue(positions.last() > positions.first())
    }

    @Test
    fun criticallyDampedGlyphMotionSettlesOnItsTarget() {
        val values = floatArrayOf(0f)
        val velocities = floatArrayOf(0f)

        repeat(90) { dampedStep(values, velocities, 0, 1f, 0.16f, 1f / 60f) }

        assertEquals(1f, values[0], 0.002f)
        assertTrue(kotlin.math.abs(velocities[0]) < 0.02f)
    }
}
