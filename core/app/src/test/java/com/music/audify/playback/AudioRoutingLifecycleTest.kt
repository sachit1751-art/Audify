package com.music.audify.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutingLifecycleTest {

    @Test
    fun forgetsOnlyTheSelectedDeviceWhenItIsRemoved() {
        assertTrue(AudioRouting.shouldForgetSelection(12, listOf(4, 12, 19)))
        assertFalse(AudioRouting.shouldForgetSelection(12, listOf(4, 19)))
        assertFalse(AudioRouting.shouldForgetSelection(null, listOf(12)))
        assertFalse(AudioRouting.shouldForgetSelection(12, emptyList()))
    }
}
