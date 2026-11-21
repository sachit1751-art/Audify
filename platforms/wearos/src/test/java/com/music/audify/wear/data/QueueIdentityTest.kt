package com.music.audify.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueIdentityTest {
    @Test
    fun `row key prefers immutable occurrence id and falls back to index for old phone builds`() {
        assertEquals("entry:two", QueueRow(4, "Song", "Artist", null, "two", true).stableKey(emptySet()))
        assertEquals("index:4", QueueRow(4, "Song", "Artist", null).stableKey(emptySet()))
        val duplicate = QueueRow(5, "Song", "Artist", null, "duplicate")
        assertEquals("index:5", duplicate.stableKey(setOf("duplicate")))
        assertNull(duplicate.safeJumpEntryId(setOf("duplicate")))
        val outsideWindowDuplicate = QueueRow(6, "Song", "Artist", null, "duplicate", false)
        assertEquals("index:6", outsideWindowDuplicate.stableKey(emptySet()))
        assertNull(outsideWindowDuplicate.safeJumpEntryId(emptySet()))
        assertEquals("unique", QueueRow(7, "Song", "Artist", null, "unique", true).safeJumpEntryId(emptySet()))
    }

    @Test
    fun `queue snapshot freshness distinguishes duplicate occurrences of the same media item`() {
        val snapshot = Queue(emptyList(), currentIndex = 5, mediaId = "same-track", currentEntryId = "occurrence-a")

        assertTrue(snapshot.isCurrentFor("same-track", "occurrence-a", 5))
        assertFalse(snapshot.isCurrentFor("same-track", "occurrence-b", 5))
        assertFalse(snapshot.isCurrentFor("same-track", "occurrence-a", 6))
        assertFalse(snapshot.isCurrentFor("other-track", "occurrence-a", 5))
    }

    @Test
    fun `queue with a current track is rejected when phone explicitly has no current item`() {
        val stale = Queue(emptyList(), currentIndex = 5, mediaId = "old-track", currentEntryId = "old-entry")
        val empty = Queue(emptyList(), currentIndex = -1, mediaId = "")
        assertFalse(stale.isCurrentFor(null, null, null))
        assertTrue(empty.isCurrentFor(null, null, null))
    }

    @Test
    fun `legacy queue snapshots remain usable when occurrence ids are absent`() {
        val legacy = Queue(emptyList(), currentIndex = 2, mediaId = "track")
        assertTrue(legacy.isCurrentFor("track", null, null))
    }
}
