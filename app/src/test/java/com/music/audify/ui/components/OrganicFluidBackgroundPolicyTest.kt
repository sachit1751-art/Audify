package com.music.audify.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganicFluidBackgroundPolicyTest {
    @Test
    fun firstFrameStartsTheClockAndSubsequentFramesAreCappedNearThirtyFps() {
        assertTrue(shouldAdvanceFluidFrame(0L, 1L))
        assertFalse(shouldAdvanceFluidFrame(1L, 16_666_667L))
        assertTrue(shouldAdvanceFluidFrame(1L, 33_333_334L))
    }

    @Test
    fun repeatedOrOutOfOrderFrameTimestampsNeverAdvanceTheClock() {
        assertFalse(shouldAdvanceFluidFrame(100L, 100L))
        assertFalse(shouldAdvanceFluidFrame(200L, 150L))
    }
}
