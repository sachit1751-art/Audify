// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricSubtitlePositionTest {

    @Test
    fun `positive offset delays the subtitle timeline`() {
        assertEquals(9_000L, adjustedLyricSubtitlePosition(10_000L, 1_000))
    }

    @Test
    fun `negative offset advances the subtitle timeline`() {
        assertEquals(11_000L, adjustedLyricSubtitlePosition(10_000L, -1_000))
    }

    @Test
    fun `positive offset clamps before track start`() {
        assertEquals(0L, adjustedLyricSubtitlePosition(500L, 1_000))
    }
}
