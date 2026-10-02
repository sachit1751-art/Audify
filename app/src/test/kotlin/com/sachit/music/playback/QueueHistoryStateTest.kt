/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHistoryStateTest {

    @Test
    fun `marks and reports skipped ids`() {
        val state = QueueHistoryState()
        state.markSkipped(listOf("a", "b"))
        assertEquals(setOf("a", "b"), state.skippedIdsSnapshot())
    }

    @Test
    fun `duplicate marks are ignored`() {
        val state = QueueHistoryState()
        state.markSkipped(listOf("a", "b", "a"))
        assertEquals(setOf("a", "b"), state.skippedIdsSnapshot())
    }

    @Test
    fun `empty input is a no-op`() {
        val state = QueueHistoryState()
        state.markSkipped(emptyList())
        assertTrue(state.skippedIdsSnapshot().isEmpty())
    }

    @Test
    fun `clear removes all marks`() {
        val state = QueueHistoryState()
        state.markSkipped(listOf("a", "b"))
        state.clear()
        assertTrue(state.skippedIdsSnapshot().isEmpty())
    }

    @Test
    fun `at exactly maxEntries nothing is evicted`() {
        val state = QueueHistoryState(maxEntries = 3)
        state.markSkipped(listOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), state.skippedIdsSnapshot())
    }

    @Test
    fun `one past maxEntries evicts the oldest entry first`() {
        val state = QueueHistoryState(maxEntries = 3)
        state.markSkipped(listOf("a", "b", "c"))
        state.markSkipped(listOf("d"))
        assertEquals(setOf("b", "c", "d"), state.skippedIdsSnapshot())
        // Re-adding an evicted id marks it again; "e" then evicts "c" too.
        state.markSkipped(listOf("a", "e"))
        assertEquals(setOf("d", "a", "e"), state.skippedIdsSnapshot())
    }
}
