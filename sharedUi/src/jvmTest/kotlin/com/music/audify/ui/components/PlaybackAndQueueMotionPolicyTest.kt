package com.music.audify.ui.components

import com.music.audify.ui.player.queueRowPlacementDurationMs
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAndQueueMotionPolicyTest {
    @Test
    fun `track identity transition stays within the documented motion budget`() {
        assertEquals(220, trackTextTransitionDurationMs(reduceAnimation = false))
        assertEquals(150, trackTextTransitionDurationMs(reduceAnimation = true))
    }

    @Test
    fun `queue row placement snaps when reduced animation is enabled`() {
        assertEquals(200, queueRowPlacementDurationMs(reduceAnimation = false))
        assertEquals(0, queueRowPlacementDurationMs(reduceAnimation = true))
    }
}
