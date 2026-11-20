package com.music.audify.ui.tv.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDisplayCapabilitiesTest {
    @Test
    fun ultra120IsUnsupportedWhenNo120HzModeExists() {
        val capabilities = TvDisplayCapabilities(
            actualRefreshRateHz = 60f,
            supportedModes = listOf(TvDisplayMode(1, 1920, 1080, 60f)),
        )

        assertFalse(capabilities.is120HzSupported)
    }

    @Test
    fun ultra120IsSupportedWhenA120HzAlternativeRateExists() {
        val capabilities = TvDisplayCapabilities(
            actualRefreshRateHz = 60f,
            supportedModes = listOf(
                TvDisplayMode(1, 1920, 1080, 60f, alternativeRefreshRates = floatArrayOf(120f)),
            ),
        )

        assertTrue(capabilities.is120HzSupported)
    }
}
