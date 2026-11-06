// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.data.model.QueueTier
import com.music.audify.data.model.Song
import com.music.audify.playback.LastPlayed
import com.music.audify.playback.PlayableMedia
import com.music.audify.playback.PodcastEpisodeRef
import com.music.audify.playback.PodcastPlaybackBridge
import com.music.audify.playback.PodcastPlaybackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastPlayedTypedSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy { context.getSharedPreferences("audify_last_played", Context.MODE_PRIVATE) }

    @Before
    fun setUp() {
        LastPlayed.init(context)
        LastPlayed.clearImmediately()
        PodcastPlaybackBridge.repository = null
    }

    @After
    fun tearDown() {
        LastPlayed.clearImmediately()
        PodcastPlaybackBridge.repository = null
    }

    @Test
    fun mixedPhysicalQueueRoundTripsThroughRepositoryCanonicalEpisode() {
        val episode = episode()
        PodcastPlaybackBridge.repository = repository(episode)
        val song = Song(
            videoId = "music-id",
            title = "Music",
            artist = "Artist",
            thumbnailUrl = null,
            fromAutoplay = false,
        )
        val podcast = PlayableMedia.Podcast(episode, "podcast-occurrence", QueueTier.USER_QUEUE)

        LastPlayed.savePlayableQueue(listOf(PlayableMedia.Music(song), podcast), index = 1)
        LastPlayed.savePlaybackState(index = 1, positionMs = 4_567L)
        assertTrue(preferences.getString("queue", null).orEmpty().contains("\"version\":1"))

        val restored = LastPlayed.load()
        assertNotNull(restored)
        assertEquals(1, restored!!.index)
        assertEquals(4_567L, restored.positionMs)
        assertEquals("music-id", (restored.playables[0] as PlayableMedia.Music).song.videoId)
        val restoredPodcast = restored.playables[1] as PlayableMedia.Podcast
        assertEquals(episode.stableId, restoredPodcast.episode.stableId)
        assertEquals("podcast-occurrence", restoredPodcast.queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, restoredPodcast.queueTier)
    }

    @Test
    fun legacySnapshotsWithoutKindDecodeAsMusic() {
        preferences.edit().putString(
            "queue",
            """{"tracks":[{"id":"legacy-id","title":"Legacy","artist":"Artist"}],"index":0,"positionMs":1234}""",
        ).putInt("index", 0).putLong("position", 1_234L).commit()

        val restored = LastPlayed.load()
        assertNotNull(restored)
        assertEquals(1, restored!!.playables.size)
        assertTrue(restored.playables.single() is PlayableMedia.Music)
        assertEquals("legacy-id", (restored.playables.single() as PlayableMedia.Music).song.videoId)
        assertEquals(1_234L, restored.positionMs)
    }

    @Test
    fun podcastEntryIsSkippedWhenCatalogueMissesIt() {
        val episode = episode()
        PodcastPlaybackBridge.repository = repository(null)
        LastPlayed.savePlayableQueue(
            listOf(PlayableMedia.Podcast(episode, "podcast-occurrence")),
            index = 0,
        )

        assertNull(LastPlayed.load())
    }

    @Test
    fun temporarilyUnavailablePodcastRepositoryPreservesMixedSnapshot() {
        val episode = episode()
        val song = Song(
            videoId = "music-id",
            title = "Music",
            artist = "Artist",
            thumbnailUrl = null,
            fromAutoplay = false,
        )
        PodcastPlaybackBridge.repository = repository(episode)
        LastPlayed.savePlayableQueue(
            listOf(PlayableMedia.Music(song), PlayableMedia.Podcast(episode, "podcast-occurrence")),
            index = 1,
        )

        PodcastPlaybackBridge.repository = null
        assertTrue(LastPlayed.hasPendingPodcastSnapshot())
        assertNull(LastPlayed.load())

        PodcastPlaybackBridge.repository = repository(episode)
        assertTrue(!LastPlayed.hasPendingPodcastSnapshot())
        val restored = LastPlayed.load()
        assertNotNull(restored)
        assertEquals(2, restored!!.playables.size)
        assertTrue(restored.playables[1] is PlayableMedia.Podcast)
        assertEquals("podcast-occurrence", restored.playables[1].queueEntryId)
    }

    private fun repository(ref: PodcastEpisodeRef?): PodcastPlaybackRepository =
        object : PodcastPlaybackRepository {
            override fun findEpisode(feedId: String, episodeId: String): PodcastEpisodeRef? =
                ref?.takeIf { it.feedId == feedId && it.episodeId == episodeId }
            override fun onPlaybackStarted(episode: PodcastEpisodeRef) = Unit
            override fun onPlaybackProgress(episode: PodcastEpisodeRef, positionMs: Long, durationMs: Long?) = Unit
            override fun onPlaybackCompleted(episode: PodcastEpisodeRef, positionMs: Long, durationMs: Long?) = Unit
        }

    private fun episode() = PodcastEpisodeRef(
        feedId = "feed-id",
        episodeId = "episode-id",
        enclosureUri = Uri.parse("https://cdn.example.org/episode.mp3"),
        mimeType = "audio/mpeg",
        showTitle = "Show",
        episodeTitle = "Episode",
        description = null,
        artworkUri = null,
        durationMs = 600_000L,
    )
}
