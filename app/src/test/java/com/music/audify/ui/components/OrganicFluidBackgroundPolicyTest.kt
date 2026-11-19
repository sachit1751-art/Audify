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

    @Test
    fun unknownWindowSizeDoesNotPauseAnAttachedWallpaper() {
        assertTrue(
            isFluidBoundsVisible(
                left = 0f,
                top = 0f,
                right = 1080f,
                bottom = 2400f,
                windowWidth = 0f,
                windowHeight = 0f,
            ),
        )
    }

    @Test
    fun knownWindowSizeStillRejectsOffscreenWallpaperBounds() {
        assertFalse(
            isFluidBoundsVisible(
                left = 1200f,
                top = 0f,
                right = 1600f,
                bottom = 2400f,
                windowWidth = 1080f,
                windowHeight = 2400f,
            ),
        )
    }
}
