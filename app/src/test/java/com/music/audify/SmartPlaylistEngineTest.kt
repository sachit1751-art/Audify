// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.data.model.ArtistRef
import com.music.audify.data.model.Song
import com.music.audify.data.playlist.SmartPlaylistDefinition
import com.music.audify.data.playlist.SmartPlaylistEngine
import com.music.audify.data.playlist.SmartPlaylistListening
import com.music.audify.data.playlist.SmartPlaylistSort
import com.music.audify.data.playlist.mergeSmartPlaylistCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistEngineTest {
    private val now = 2_000_000L
    private fun song(id: String, artist: String = "Artist", added: Long? = null) = Song(
        videoId = id, title = id, artist = artist, thumbnailUrl = null,
        localDateAddedSeconds = added,
    )

    @Test fun `artist rule matches lead and credited artists case insensitively`() {
        val guest = song("guest", artist = "Lead").copy(artists = listOf(ArtistRef("Guest Artist")))
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Guests", artists = listOf("guest artist")),
            listOf(song("other"), guest), emptySet(), emptySet(), now,
        )
        assertEquals(listOf("guest"), result.map { it.videoId })
    }

    @Test fun `liked rule intersects with other filters`() {
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Liked artist", artists = listOf("Artist"), likedOnly = true),
            listOf(song("a"), song("b")), setOf("b"), emptySet(), now,
        )
        assertEquals(listOf("b"), result.map { it.videoId })
    }

    @Test fun `recently added requires real local date metadata`() {
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "New", addedWithinDays = 7),
            listOf(song("new", added = now - 100), song("old", added = now - 8 * 86_400), song("unknown")),
            emptySet(), emptySet(), now,
        )
        assertEquals(listOf("new"), result.map { it.videoId })
    }

    @Test fun `recent history exclusion and duplicate handling are deterministic`() {
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Fresh", excludeRecentHistory = true),
            listOf(song("a"), song("a"), song("b")), emptySet(), setOf("a"), now,
        )
        assertEquals(listOf("b"), result.map { it.videoId })
    }

    @Test fun `paused and empty library produce no results`() {
        val paused = SmartPlaylistDefinition("1", "Paused", likedOnly = true, paused = true)
        assertTrue(SmartPlaylistEngine.evaluate(paused, listOf(song("a")), setOf("a"), emptySet(), now).isEmpty())
        assertTrue(SmartPlaylistEngine.evaluate(paused.copy(paused = false), emptyList(), emptySet(), emptySet(), now).isEmpty())
    }

    @Test fun `validation requires a rule and bounds result size`() {
        assertFalse(SmartPlaylistEngine.validate(SmartPlaylistDefinition("1", "No rules")).isNullOrBlank())
        assertNull(SmartPlaylistEngine.validate(SmartPlaylistDefinition("1", "Valid", likedOnly = true)))
        assertFalse(SmartPlaylistEngine.validate(SmartPlaylistDefinition("1", "Too many", likedOnly = true, maxTracks = 501)).isNullOrBlank())
    }

    @Test fun `most played sorting uses aggregate play counts with stable tie breaks`() {
        val library = listOf(song("low"), song("high"), song("tie"))
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Most played", sortBy = SmartPlaylistSort.MOST_PLAYED),
            library, emptySet(), emptySet(), now,
            mapOf(
                "low" to SmartPlaylistListening(2, 120_000, now * 1_000),
                "high" to SmartPlaylistListening(8, 300_000, now * 1_000),
                "tie" to SmartPlaylistListening(8, 180_000, now * 1_000),
            ),
        )
        assertEquals(listOf("high", "tie", "low"), result.map { it.videoId })
    }

    @Test fun `recent and not recently played rules use actual last played timestamps`() {
        val library = listOf(song("recent"), song("old"), song("never"))
        val stats = mapOf(
            "recent" to SmartPlaylistListening(3, 90_000, (now - 2 * 86_400) * 1_000),
            "old" to SmartPlaylistListening(7, 240_000, (now - 40 * 86_400) * 1_000),
        )
        val recentlyPlayed = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Recent", playedWithinDays = 7),
            library, emptySet(), emptySet(), now, stats,
        )
        val notRecentlyPlayed = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("2", "Rediscover", notPlayedWithinDays = 30),
            library, emptySet(), emptySet(), now, stats,
        )
        assertEquals(listOf("recent"), recentlyPlayed.map { it.videoId })
        assertEquals(listOf("old", "never"), notRecentlyPlayed.map { it.videoId })
    }

    @Test fun `minimum plays filters tracks without history and validates bounds`() {
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Familiar", minimumPlays = 3),
            listOf(song("a"), song("b")), emptySet(), emptySet(), now,
            mapOf("a" to SmartPlaylistListening(3, 60_000, null), "b" to SmartPlaylistListening(2, 30_000, null)),
        )
        assertEquals(listOf("a"), result.map { it.videoId })
        assertFalse(SmartPlaylistEngine.validate(SmartPlaylistDefinition("2", "Invalid", minimumPlays = 0)).isNullOrBlank())
    }

    @Test fun `recently played sort puts unplayed tracks last`() {
        val result = SmartPlaylistEngine.evaluate(
            SmartPlaylistDefinition("1", "Recently played", sortBy = SmartPlaylistSort.RECENTLY_PLAYED),
            listOf(song("never"), song("older"), song("newer")), emptySet(), emptySet(), now,
            mapOf(
                "older" to SmartPlaylistListening(1, 30_000, (now - 10) * 1_000),
                "newer" to SmartPlaylistListening(1, 30_000, (now - 1) * 1_000),
            ),
        )
        assertEquals(listOf("newer", "older", "never"), result.map { it.videoId })
    }
    @Test fun `candidate merge includes played and liked streamed tracks without duplicates`() {
        val local = song("local")
        val played = song("played")
        val liked = song("liked")
        val aggregateOnly = song("aggregate")
        val duplicateHistory = played.copy(title = "Older title")
        val result = mergeSmartPlaylistCandidates(
            library = listOf(local, played),
            history = listOf(duplicateHistory),
            favorites = listOf(liked, local),
            listeningTracks = listOf(aggregateOnly, liked),
        )
        assertEquals(listOf("local", "played", "liked", "aggregate"), result.map { it.videoId })
        assertEquals("played", result.first { it.videoId == "played" }.title)
    }
}