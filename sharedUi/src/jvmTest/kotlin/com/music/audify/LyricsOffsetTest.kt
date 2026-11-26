// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.ui.player.adjustedLyricsPosition
import com.music.audify.ui.player.adjustedLyricsSeekTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsOffsetTest {
    @Test
    fun `positive offset delays lyrics`() {
        assertEquals(8_500L, adjustedLyricsPosition(positionMs = 10_000L, offsetMs = 1_500))
    }

    @Test
    fun `negative offset advances lyrics`() {
        assertEquals(11_500L, adjustedLyricsPosition(positionMs = 10_000L, offsetMs = -1_500))
    }

    @Test
    fun `offset never produces a negative lyric clock`() {
        assertEquals(0L, adjustedLyricsPosition(positionMs = 500L, offsetMs = 1_500))
    }

    @Test
    fun `seeking a lyric includes its offset`() {
        assertEquals(11_500L, adjustedLyricsSeekTarget(lineTimeMs = 10_000L, offsetMs = 1_500))
        assertEquals(8_500L, adjustedLyricsSeekTarget(lineTimeMs = 10_000L, offsetMs = -1_500))
    }

    @Test
    fun `lyrics speed scales the clock and reverses when seeking`() {
        assertEquals(12_000L, adjustedLyricsPosition(10_000L, 0, 1.2f))
        assertEquals(8_000L, adjustedLyricsPosition(10_000L, 0, 0.8f))
        assertEquals(10_000L, adjustedLyricsSeekTarget(12_000L, 0, 1.2f))
        assertEquals(10_000L, adjustedLyricsSeekTarget(8_000L, 0, 0.8f))
    }
}
