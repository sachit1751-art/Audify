package com.music.audify.data.local

import com.music.audify.data.model.Song
import com.music.audify.data.stats.ListeningTrackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMirrorMappingTest {
    @Test
    fun preservesActualPlayCountsAndLastPlayedTimestamps() {
        val played = Song(videoId = "played", title = "Played", artist = "Artist", thumbnailUrl = null)
        val legacy = Song(videoId = "legacy", title = "Legacy", artist = "Artist", thumbnailUrl = null)
        val rows = historyRows(
            songs = listOf(played, legacy),
            snapshots = mapOf(
                "played" to ListeningTrackSnapshot(
                    videoId = "played",
                    song = played,
                    plays = 7,
                    listenedMs = 240_000L,
                    lastPlayedAtEpochMs = 1_234L,
                ),
            ),
            now = 9_000L,
        )

        assertEquals(7, rows[0].playCount)
        assertEquals(1_234L, rows[0].lastPlayedAt)
        // Old history without a matching stats aggregate has no defensible
        // qualified play count; do not invent one.
        assertEquals(0, rows[1].playCount)
        assertEquals(8_999L, rows[1].lastPlayedAt)
    }
}
