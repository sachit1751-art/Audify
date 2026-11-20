// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.os.Bundle
import com.music.audify.data.model.QueueTier
import com.music.audify.data.model.Song
import com.music.audify.playback.PlayableMedia
import com.music.audify.playback.PodcastEpisodeRef
import com.music.audify.playback.QueueEntryMetadata
import com.music.audify.playback.isPodcastEpisode
import com.music.audify.playback.hasSupportedPlayableKind
import com.music.audify.playback.toMediaItem
import com.music.audify.playback.toPlayableMedia
import com.music.audify.playback.toSongOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PlayableMediaTest {

    @Test
    fun musicMediaItemKeepsExistingSongIdentityAndQueueMetadata() {
        val song = Song(
            videoId = "music-video-id",
            title = "A track",
            artist = "An artist",
            thumbnailUrl = "https://example.com/cover.jpg",
            durationText = "3:45",
            albumName = "An album",
            queueTier = QueueTier.USER_QUEUE,
            queueEntryId = "music-occurrence-1",
        )

        val item = PlayableMedia.Music(song).toMediaItem(
            QueueEntryMetadata("music-occurrence-1", QueueTier.USER_QUEUE, canonicalIndex = 4),
        )
        val decoded = item.toPlayableMedia() as PlayableMedia.Music

        assertEquals(song.videoId, decoded.song.videoId)
        assertEquals(song.title, decoded.song.title)
        assertEquals(song.artist, decoded.song.artist)
        assertEquals(song.albumName, decoded.song.albumName)
        assertEquals(QueueTier.USER_QUEUE, decoded.song.queueTier)
        assertEquals("music-occurrence-1", decoded.queueEntryId)
        assertEquals("music-video-id", item.toSongOrNull()?.videoId)
        assertFalse(item.isPodcastEpisode)
        assertTrue(item.hasSupportedPlayableKind)
    }

    @Test
    fun podcastMediaItemRoundTripsTypedIdentityAndNeverConvertsToSong() {
        val episode = PodcastEpisodeRef(
            feedId = "feed:one",
            episodeId = "episode/42",
            enclosureUri = Uri.parse("https://cdn.example.com/episode.mp3"),
            mimeType = "audio/mpeg",
            showTitle = "The Example Show",
            episodeTitle = "Episode 42",
            description = "An episode description",
            artworkUri = Uri.parse("https://cdn.example.com/show.jpg"),
            durationMs = 2_400_000L,
        )

        val item = PlayableMedia.Podcast(episode).toMediaItem(
            QueueEntryMetadata("podcast-occurrence-1", QueueTier.USER_QUEUE),
        )
        val decoded = item.toPlayableMedia() as PlayableMedia.Podcast

        assertEquals(episode.stableId, item.mediaId)
        assertEquals(episode.feedId, decoded.episode.feedId)
        assertEquals(episode.episodeId, decoded.episode.episodeId)
        assertEquals(episode.enclosureUri, decoded.episode.enclosureUri)
        assertEquals(episode.mimeType, decoded.episode.mimeType)
        assertEquals(episode.showTitle, decoded.episode.showTitle)
        assertEquals(episode.episodeTitle, decoded.episode.episodeTitle)
        assertEquals(episode.description, decoded.episode.description)
        assertEquals(episode.artworkUri, decoded.episode.artworkUri)
        assertEquals(episode.durationMs, decoded.episode.durationMs)
        assertEquals("podcast-occurrence-1", decoded.queueEntryId)
        assertTrue(item.isPodcastEpisode)
        assertNull(item.toSongOrNull())
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, item.mediaMetadata.mediaType)
    }

    @Test
    fun unknownExplicitMediaKindFailsClosedInsteadOfBecomingMusic() {
        val item = MediaItem.Builder()
            .setMediaId("future-item")
            .setUri("https://example.com/audio")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(Bundle().apply { putString("audify.mediaKind", "future-kind") })
                    .build(),
            )
            .build()

        assertNull(item.toSongOrNull())
        assertFalse(item.isPodcastEpisode)
        assertFalse(item.hasSupportedPlayableKind)
        try {
            item.toPlayableMedia()
            throw AssertionError("Unknown media kind must be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Unsupported Audify media kind"))
        }
    }

    @Test
    fun missingMediaKindIsRejectedRatherThanGuessedAsMusic() {
        val unknown = MediaItem.Builder()
            .setMediaId("untyped-media-id")
            .setUri("https://example.org/audio.mp3")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Unspecified media").build())
            .build()

        assertFalse(unknown.hasSupportedPlayableKind)
        assertNull(unknown.toSongOrNull())
        try {
            unknown.toPlayableMedia()
            throw AssertionError("Missing media kind must be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Unsupported Audify media kind"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun podcastEnclosureMustBeHttps() {
        PodcastEpisodeRef(
            feedId = "feed",
            episodeId = "episode",
            enclosureUri = Uri.parse("http://example.com/episode.mp3"),
            mimeType = "audio/mpeg",
            showTitle = "Show",
            episodeTitle = "Episode",
            description = null,
            artworkUri = null,
            durationMs = null,
        )
    }
}
