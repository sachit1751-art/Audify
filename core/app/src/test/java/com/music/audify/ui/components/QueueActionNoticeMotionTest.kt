package com.music.audify.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueActionNoticeMotionTest {
    @Test
    fun `reduced motion keeps a short opacity confirmation`() {
        assertEquals(120, queueNoticeTransitionDurationMs(reduceAnimation = true))
        assertEquals(200, queueNoticeTransitionDurationMs(reduceAnimation = false))
    }
}
