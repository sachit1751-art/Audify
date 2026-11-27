package com.music.audify.download

import com.music.audify.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistSyncTest {
    private fun song(id: String) = Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null)

    @Test
    fun `liked sync discovers only ids absent from its saved collection`() {
        val record = SavedCollection(
            id = "LM",
            title = "Liked songs",
            playlist = true,
            videoIds = listOf("saved-a", "saved-b"),
        )

        assertEquals(listOf("new-c"), PlaylistSync.newTracks(listOf(song("saved-a"), song("new-c")), record)
            .map { it.videoId })
    }

    @Test
    fun `failed downloads stay absent from the saved playlist record`() {
        val record = SavedCollection(
            id = "LM",
            title = "Liked songs",
            playlist = true,
            videoIds = listOf("saved-a"),
        )

        assertEquals(
            listOf("saved-a", "downloaded-b"),
            PlaylistSync.listingToRecord(
                listOf(song("saved-a"), song("downloaded-b"), song("failed-c")),
                record,
                handled = setOf("downloaded-b"),
            ).map { it.videoId },
        )
    }

    @Test
    fun `only YouTube playlist collections are eligible for regular sync`() {
        assertEquals("VLPL123", PlaylistSync.syncableBrowseId(SavedCollection("PL123", "Playlist", playlist = true)))
        assertEquals("VLPL123", PlaylistSync.syncableBrowseId(SavedCollection("VLPL123", "Playlist", playlist = true)))
        assertNull(PlaylistSync.syncableBrowseId(SavedCollection("spotify:123", "Spotify", playlist = true)))
        assertNull(PlaylistSync.syncableBrowseId(SavedCollection("MPRE123", "Album", playlist = false)))
    }
}
