package com.music.audify.ui.tv.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TvMotionTest {
    @Test
    fun reducedMotionUsesShortOpacityDuration() {
        assertEquals(120, tvMotionDuration(reduceMotion = true, normalMillis = 260))
    }

    @Test
    fun normalMotionNeverExceedsThreeHundredMilliseconds() {
        assertEquals(260, tvMotionDuration(reduceMotion = false, normalMillis = 260))
        assertEquals(300, tvMotionDuration(reduceMotion = false, normalMillis = 520))
    }

    @Test
    fun normalMotionDoesNotTurnAnInstantTransitionIntoAnAnimation() {
        assertEquals(0, tvMotionDuration(reduceMotion = false, normalMillis = 0))
    }
}
