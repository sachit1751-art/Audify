package com.music.audify.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevealSeenStateTest {
    @Test
    fun `seen keys are remembered and duplicate marks do not grow state`() {
        val state = RevealSeenState()

        state.markSeen("home:quick-picks")
        state.markSeen("home:quick-picks")

        assertTrue(state.hasSeen("home:quick-picks"))
        assertEquals(listOf("home:quick-picks"), state.savedKeys())
    }

    @Test
    fun `history stays bounded and evicts the oldest key`() {
        val state = RevealSeenState()
        repeat(170) { state.markSeen("section:$it") }

        assertEquals(160, state.savedKeys().size)
        assertFalse(state.hasSeen("section:0"))
        assertTrue(state.hasSeen("section:169"))
    }

    @Test
    fun `restored state preserves seen keys`() {
        val restored = RevealSeenState(listOf("home:recents", "artist:albums"))

        assertTrue(restored.hasSeen("home:recents"))
        assertTrue(restored.hasSeen("artist:albums"))
    }
}
