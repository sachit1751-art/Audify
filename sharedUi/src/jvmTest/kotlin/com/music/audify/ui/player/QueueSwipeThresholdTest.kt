// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSwipeThresholdTest {
    @Test
    fun `removal requires a left swipe to reach the threshold`() {
        assertFalse(shouldRemoveQueueRowAfterSwipe(-71f, 72f))
        assertTrue(shouldRemoveQueueRowAfterSwipe(-72f, 72f))
        assertTrue(shouldRemoveQueueRowAfterSwipe(-120f, 72f))
        assertFalse(shouldRemoveQueueRowAfterSwipe(120f, 72f))
    }

    @Test
    fun `non-positive thresholds never remove a row`() {
        assertFalse(shouldRemoveQueueRowAfterSwipe(-100f, 0f))
        assertFalse(shouldRemoveQueueRowAfterSwipe(-100f, -1f))
    }
}
