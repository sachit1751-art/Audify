package com.music.audify.playback

import com.music.audify.data.model.PlaybackSourceType
import com.music.audify.data.model.QueueTier
import com.music.audify.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueTimelinePlaylistOrderTest {
    private fun song(
        id: String,
        tier: QueueTier = QueueTier.CONTEXT,
        entryId: String? = null,
        setVideoId: String? = null,
    ) = Song(
        videoId = id,
        title = "Track $id",
        artist = "Artist",
        thumbnailUrl = null,
        setVideoId = setVideoId,
        queueTier = tier,
        queueEntryId = entryId,
    )

    @Test
    fun selectedPlaylistTrackKeepsOriginalOrderWithManualQueueBehindIt() {
        val result = QueueTimeline.buildContextQueue(
            currentTimeline = listOf(
                song("current"),
                song("manual-1", QueueTier.USER_QUEUE, "entry-1"),
                song("manual-2", QueueTier.USER_QUEUE, "entry-2"),
                song("old-autoplay", QueueTier.AUTOPLAY, "entry-auto"),
            ),
            currentIndex = 0,
            newContextSongs = listOf("p1", "p2", "p3", "p4", "p5").map { song(it) },
            selectedIndex = 2,
            contextSource = QueueSource("Playlist", PlaybackSourceType.BROWSE, "playlist-id"),
        )

        assertEquals(2, result.startIndex)
        assertEquals(
            listOf("p1", "p2", "p3", "manual-1", "manual-2", "p4", "p5"),
            result.timeline.map { it.videoId },
        )
        assertEquals(
            listOf(
                QueueTier.CONTEXT,
                QueueTier.CONTEXT,
                QueueTier.CONTEXT,
                QueueTier.USER_QUEUE,
                QueueTier.USER_QUEUE,
                QueueTier.CONTEXT,
                QueueTier.CONTEXT,
            ),
            result.timeline.map { it.queueTier },
        )
    }

    @Test
    fun removalFindsTheSameOccurrenceAfterItsQueueIndexMoves() {
        val queue = listOf(
            song("duplicate", entryId = "first"),
            song("other"),
            song("duplicate", entryId = "second"),
        )

        assertEquals(2, QueueTimeline.findQueueRemovalIndex(queue, song("duplicate", entryId = "second")))
    }

    @Test
    fun removalUsesPlaylistEntryIdentityWhenQueueEntryIdIsMissing() {
        val queue = listOf(
            song("duplicate", setVideoId = "set-first"),
            song("other"),
            song("duplicate", setVideoId = "set-second"),
        )

        assertEquals(2, QueueTimeline.findQueueRemovalIndex(queue, song("duplicate", setVideoId = "set-second")))
    }

    @Test
    fun ambiguousLegacyDuplicateRemovalFailsClosed() {
        val queue = listOf(song("duplicate"), song("other"), song("duplicate"))

        assertNull(QueueTimeline.findQueueRemovalIndex(queue, song("duplicate")))
    }

    @Test
    fun addingAnExistingQueueSongCreatesANewUniqueOccurrence() {
        val source = song("same-track", entryId = "existing-entry")

        val first = QueueTimeline.run { source.asNewQueueEntry(QueueTier.USER_QUEUE) }
        val second = QueueTimeline.run { source.asNewQueueEntry(QueueTier.USER_QUEUE) }

        assertEquals("existing-entry", source.queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, first.queueTier)
        assertEquals(QueueTier.USER_QUEUE, second.queueTier)
        kotlin.test.assertNotEquals(source.queueEntryId, first.queueEntryId)
        kotlin.test.assertNotEquals(first.queueEntryId, second.queueEntryId)
    }
}
