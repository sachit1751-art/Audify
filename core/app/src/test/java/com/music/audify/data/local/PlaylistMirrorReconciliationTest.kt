package com.music.audify.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistMirrorReconciliationTest {
    @Test
    fun removesOnlyRoomPlaylistsMissingFromTheCommittedSnapshot() {
        assertEquals(
            listOf("deleted-local", "deleted-smart"),
            stalePlaylistIds(
                existingIds = listOf("kept", "deleted-local", "deleted-smart"),
                activeIds = setOf("kept", "new"),
            ),
        )
    }

    @Test
    fun emptySnapshotRemovesEveryMirroredPlaylist() {
        assertEquals(
            listOf("one", "two"),
            stalePlaylistIds(listOf("one", "two"), emptySet()),
        )
    }
}
