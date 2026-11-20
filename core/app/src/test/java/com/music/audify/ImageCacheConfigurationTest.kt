package com.music.audify

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCacheConfigurationTest {
    @Test
    fun normalDevicesUseTwentyFivePercentOfAvailableHeap() {
        assertEquals(0.25, imageMemoryCacheHeapFraction(isLowRamDevice = false), 0.0)
    }

    @Test
    fun lowRamDevicesUseReducedFifteenPercentHeapBudget() {
        assertEquals(0.15, imageMemoryCacheHeapFraction(isLowRamDevice = true), 0.0)
    }
}
