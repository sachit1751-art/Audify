// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui

import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedRecommendationsTest {
    @Test
    fun deduplicationRemovesTracksAcrossShelvesAndBatchesButKeepsBrowseCards() {
        val seen = mutableSetOf("already-shown")
        val shelves = listOf(
            HomeShelf("First", listOf(track("one", "Artist A"), track("already-shown", "Artist B"))),
            HomeShelf("Second", listOf(track("one", "Artist C"), track("two", "Artist A"))),
            HomeShelf("Collections", listOf(browseCard("album-1"))),
        )

        val result = deduplicateHomeTracks(shelves, seen)

        assertEquals(listOf("one"), result[0].items.mapNotNull { it.videoId })
        assertEquals(listOf("two"), result[1].items.mapNotNull { it.videoId })
        assertEquals("album-1", result[2].items.single().browseId)
        assertEquals(setOf("already-shown", "one", "two"), seen)
    }

    @Test
    fun emptyShelvesAreDroppedAfterDuplicateTracksAreRemoved() {
        val result = deduplicateHomeTracks(
            listOf(HomeShelf("Repeated", listOf(track("same", "Artist")))),
            mutableSetOf("same"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun artistOrderingAvoidsAdjacentRepeatsWhenOtherArtistsAreAvailable() {
        val shelf = HomeShelf(
            "For you",
            listOf(
                track("a1", "Artist A"),
                track("a2", "Artist A"),
                track("a3", "Artist A"),
                track("b1", "Artist B"),
                track("b2", "Artist B"),
                track("c1", "Artist C"),
            ),
        )

        val result = diversifyHomeShelf(shelf, seed = 42L)
        val artists = result.items.map { it.subtitle }

        assertEquals(shelf.items.size, result.items.size)
        assertEquals(shelf.items.mapNotNull { it.videoId }.toSet(), result.items.mapNotNull { it.videoId }.toSet())
        assertTrue(artists.zipWithNext().none { (first, second) -> first == second })
        assertEquals(result, diversifyHomeShelf(shelf, seed = 42L))
    }

    @Test
    fun historyShelvesKeepTheirChronologicalOrder() {
        val recents = HomeShelf("Recently played", listOf(
            track("newest", "Artist A"),
            track("middle", "Artist A"),
            track("oldest", "Artist B"),
        ))

        assertEquals(recents, diversifyHomeShelf(recents, seed = 42L))
    }

    @Test
    fun previousSessionTracksMoveBehindFreshCandidatesWithoutBeingDiscarded() {
        val shelf = HomeShelf(
            "For you",
            listOf(track("old-1", "Artist A"), track("fresh-1", "Artist B"), track("old-2", "Artist C")),
        )

        val result = prioritizeFreshHomeTracks(listOf(shelf), setOf("old-1", "old-2")).single()

        assertEquals(listOf("fresh-1", "old-1", "old-2"), result.items.mapNotNull { it.videoId })
    }

    @Test
    fun artistKeyUsesFirstExplicitCreditAndDoesNotInferFromTrackTitle() {
        assertEquals("artist a, artist b", homeArtistKey(track("song-title", "Artist A, Artist B • 3:21")))
        assertEquals("earth, wind & fire", homeArtistKey(track("song-title", "Earth, Wind & Fire")))
        assertEquals("song-title", homeArtistKey(track("song-title", " • 3:21")))
    }

    @Test
    fun categoryBackedShelvesAreInterleavedAndHistoryStaysFirst() {
        val history = HomeShelf("Recently played", listOf(track("recent", "Artist R")))
        val shelves = listOf(
            HomeShelf("Pop one", emptyList(), discoveryCategory = "Pop"),
            HomeShelf("Pop two", emptyList(), discoveryCategory = "Pop"),
            HomeShelf("Rock one", emptyList(), discoveryCategory = "Rock"),
            history,
        )

        val result = diversifyHomeShelves(shelves)

        assertEquals(history, result.first())
        assertEquals(listOf("Pop", "Rock", "Pop"), result.drop(1).map { it.discoveryCategory })
        assertEquals(shelves.size, result.size)
    }

    @Test
    fun unlabelledShelvesAreNotAssignedInventedCategories() {
        val shelves = listOf(
            HomeShelf("For you", listOf(track("one", "Artist A"))),
            HomeShelf("Listen again", listOf(track("two", "Artist B"))),
        )

        val result = diversifyHomeShelves(shelves)

        assertEquals(listOf(null, null), result.map { it.discoveryCategory })
        assertEquals(shelves, result)
    }

    @Test
    fun stalledPrimaryPagingSwitchesToFallbackAndFallbackPagingRemainsExplicit() {
        val prefix = "audify-home-fallback:"

        assertEquals("${prefix}0", continueHomeAfterEmptyPages("server-next-token", prefix))
        assertEquals("${prefix}4", continueHomeAfterEmptyPages("${prefix}4", prefix))
        assertEquals(null, continueHomeAfterEmptyPages(null, prefix))
    }

    @Test
    fun initialEmptyPrimaryPageKeepsItsContinuationBeforeFallingBack() {
        val prefix = "audify-home-fallback:"

        assertEquals("primary-next", initialHomeContinuation("primary-next", prefix))
        assertEquals("${prefix}0", initialHomeContinuation(null, prefix))
    }

    private fun track(id: String, artist: String) = ShelfItem(
        title = id,
        subtitle = artist,
        thumbnailUrl = null,
        videoId = id,
        browseId = null,
    )

    private fun browseCard(id: String) = ShelfItem(
        title = id,
        subtitle = "Album",
        thumbnailUrl = null,
        videoId = null,
        browseId = id,
    )
}
