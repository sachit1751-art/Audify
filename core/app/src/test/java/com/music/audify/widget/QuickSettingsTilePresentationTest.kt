// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsTilePresentationTest {

    @Test
    fun `no active track offers app launch and stays inactive`() {
        val result = quickSettingsTilePresentation(hasTrack = false, playWhenReady = false, title = null)

        assertFalse(result.isActive)
        assertEquals("Tap to open Audify", result.subtitle)
        assertEquals("No active track", result.stateDescription)
    }

    @Test
    fun `playing session activates tile and displays track title`() {
        val result = quickSettingsTilePresentation(hasTrack = true, playWhenReady = true, title = "  Track A  ")

        assertTrue(result.isActive)
        assertEquals("Track A", result.subtitle)
        assertEquals("Playing", result.stateDescription)
    }

    @Test
    fun `paused session remains inactive and identifies current track`() {
        val result = quickSettingsTilePresentation(hasTrack = true, playWhenReady = false, title = "Track B")

        assertFalse(result.isActive)
        assertEquals("Paused · Track B", result.subtitle)
        assertEquals("Paused", result.stateDescription)
    }

    @Test
    fun `blank metadata uses stable fallback`() {
        val result = quickSettingsTilePresentation(hasTrack = true, playWhenReady = true, title = "  ")

        assertEquals("Now playing", result.subtitle)
    }
}
