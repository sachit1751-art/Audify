package com.music.audify.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayIntentGlyphTest {
    @Test
    fun `pause glyph is shown while play intent is buffering`() {
        assertTrue(shouldShowPauseGlyph(isPlaying = false, playWhenReady = true, awaitingPartyStart = false))
    }

    @Test
    fun `pause glyph is shown while a party start is pending`() {
        assertTrue(shouldShowPauseGlyph(isPlaying = false, playWhenReady = false, awaitingPartyStart = true))
    }

    @Test
    fun `play glyph is shown when playback is paused with no pending start`() {
        assertFalse(shouldShowPauseGlyph(isPlaying = false, playWhenReady = false, awaitingPartyStart = false))
    }
}
