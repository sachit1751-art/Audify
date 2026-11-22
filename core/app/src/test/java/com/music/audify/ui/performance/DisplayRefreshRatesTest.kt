package com.music.audify.ui.performance

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRefreshRatesTest {
    @Test
    fun maximumDefaultSelectsTheHighestAvailablePanelRate() {
        val supported = listOf(60, 90, 120, 144, 165)
        assertEquals(165, resolvePerformanceRefreshRate(240, supported))
    }

    @Test
    fun explicitSupportedRateRemainsSelected() {
        val supported = listOf(60, 90, 120, 144, 165)
        assertEquals(120, resolvePerformanceRefreshRate(120, supported))
    }

    @Test
    fun nearestRateWinsAndTiesPreferHigherRefreshRate() {
        assertEquals(120, resolvePerformanceRefreshRate(110, listOf(90, 120)))
        assertEquals(120, resolvePerformanceRefreshRate(105, listOf(90, 120)))
    }

    @Test
    fun emptyDisplayListUsesSafeFallback() {
        assertEquals(60, resolvePerformanceRefreshRate(60, emptyList()))
    }
}
