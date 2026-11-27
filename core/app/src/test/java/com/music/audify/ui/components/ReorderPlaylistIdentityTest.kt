package com.music.audify.ui.components

import com.music.audify.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderPlaylistIdentityTest {
    @Test
    fun duplicateMediaRowsReceiveDistinctStableOccurrenceKeys() {
        val repeated = Song(videoId = "same-track", title = "Same", artist = "Artist", thumbnailUrl = null)

        val entries = createReorderPlaylistEntries(listOf(repeated, repeated, repeated))

        assertEquals(3, entries.map { it.key }.distinct().size)
        assertTrue(entries.all { it.song == repeated })

        val moved = entries.toMutableList().apply { add(0, removeAt(2)) }
        assertEquals(entries[2].key, moved[0].key)
        assertNotEquals(moved[0].key, moved[1].key)
    }

    @Test
    fun cancelledDragRestoresOriginalOccurrenceOrder() {
        val repeated = Song(videoId = "same-track", title = "Same", artist = "Artist", thumbnailUrl = null)
        val original = createReorderPlaylistEntries(listOf(repeated, repeated, repeated))
        val order = original.toMutableList().apply { add(0, removeAt(2)) }

        restoreReorderPlaylistEntries(order, original)

        assertEquals(original.map { it.key }, order.map { it.key })
    }
}
