// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem

class TimeAwareDiscoveryTest {
    @Test
    fun hourBlocksCoverEveryHourAndRespectBoundaries() {
        assertEquals(ListeningHourBlock.LATE_NIGHT, ListeningHourBlock.at(0))
        assertEquals(ListeningHourBlock.LATE_NIGHT, ListeningHourBlock.at(5))
        assertEquals(ListeningHourBlock.MORNING, ListeningHourBlock.at(6))
        assertEquals(ListeningHourBlock.MORNING, ListeningHourBlock.at(10))
        assertEquals(ListeningHourBlock.AFTERNOON, ListeningHourBlock.at(11))
        assertEquals(ListeningHourBlock.AFTERNOON, ListeningHourBlock.at(16))
        assertEquals(ListeningHourBlock.EVENING, ListeningHourBlock.at(17))
        assertEquals(ListeningHourBlock.EVENING, ListeningHourBlock.at(21))
        assertEquals(ListeningHourBlock.LATE_NIGHT, ListeningHourBlock.at(22))
        assertEquals(ListeningHourBlock.LATE_NIGHT, ListeningHourBlock.at(23))
    }

    @Test
    fun currentBlockWinsWhenItHasAnyHistory() {
        val counts = MutableList(24) { 0 }
        counts[7] = 1
        counts[19] = 20

        assertEquals(
            ListeningHourBlock.MORNING,
            selectTimeAwareHourBlock(LocalTime.of(8, 30), counts),
        )
    }

    @Test
    fun fallsBackToMostListenedBlockWhenCurrentBlockIsEmpty() {
        val counts = MutableList(24) { 0 }
        counts[13] = 3
        counts[23] = 8
        counts[2] = 4

        assertEquals(
            ListeningHourBlock.LATE_NIGHT,
            selectTimeAwareHourBlock(LocalTime.of(9, 0), counts),
        )
    }

    @Test
    fun returnsNoBlockWhenThereIsNoHistory() {
        assertNull(selectTimeAwareHourBlock(LocalTime.NOON, List(24) { 0 }))
    }

    @Test
    fun choosesDiscoveryShelfMatchingTheSelectedTimeBlock() {
        val generic = HomeShelf("Listen again", listOf(shelfItem("again")))
        val commute = HomeShelf("Commute mix", listOf(shelfItem("commute")))

        assertEquals(
            commute,
            selectTimeAwareDiscoveryShelf(listOf(generic, commute), ListeningHourBlock.MORNING),
        )
    }

    @Test
    fun discoveryShelfFallsBackWithoutUsingRecents() {
        val recents = HomeShelf("Recents", listOf(shelfItem("recent")))
        val fallback = HomeShelf("Listen again", listOf(shelfItem("again")))

        assertEquals(
            fallback,
            selectTimeAwareDiscoveryShelf(listOf(recents, fallback), ListeningHourBlock.LATE_NIGHT),
        )
        assertNull(selectTimeAwareDiscoveryShelf(listOf(recents), ListeningHourBlock.MORNING))
    }

    @Test
    fun timeAwareDiscoveryExcludesBothChronologicalHistoryTitlesRegardlessOfCaseOrPadding() {
        val recents = HomeShelf("  rEcEnTs  ", listOf(shelfItem("recent")))
        val recentlyPlayed = HomeShelf("RECENTLY PLAYED", listOf(shelfItem("played")))
        val discovery = HomeShelf("Night mix", listOf(shelfItem("night")))

        assertEquals(
            discovery,
            selectTimeAwareDiscoveryShelf(
                listOf(recents, recentlyPlayed, discovery),
                ListeningHourBlock.LATE_NIGHT,
            ),
        )
        assertNull(
            selectTimeAwareDiscoveryShelf(
                listOf(recents, recentlyPlayed),
                ListeningHourBlock.MORNING,
            ),
        )
    }

    @Test
    fun chronologicalHistoryShelvesRemainFirstAndInOriginalOrder() {
        val recents = HomeShelf("Recents", listOf(shelfItem("recent")))
        val recentlyPlayed = HomeShelf("Recently played", listOf(shelfItem("played")))
        val discovery = HomeShelf("Mixes", listOf(shelfItem("mix")))

        val result = varyHomeFeed(listOf(recents, recentlyPlayed, discovery), 1234L)

        assertEquals(listOf(recents, recentlyPlayed), result.take(2))
    }

    @Test
    fun homeFeedVariationIsStablePerInstallAndDifferentAcrossInstallSeeds() {
        val shelves = listOf(
            HomeShelf("Recently played", listOf(shelfItem("history"))),
            HomeShelf("Mixes", (1..8).map { shelfItem("mix-$it") }),
            HomeShelf("Fresh playlists", (1..8).map { shelfItem("playlist-$it") }),
            HomeShelf("New albums", (1..8).map { shelfItem("album-$it") }),
            HomeShelf("Discover", (1..8).map { shelfItem("discover-$it") }),
        )

        val firstInstall = varyHomeFeed(shelves, 1234L)
        assertEquals(firstInstall, varyHomeFeed(shelves, 1234L))
        assertEquals("Recently played", firstInstall.first().title)
        assertEquals("Recently played", varyHomeFeed(shelves, 9876L).first().title)
        assertNotEquals(firstInstall, varyHomeFeed(shelves, 9876L))
    }

    private fun shelfItem(id: String) = ShelfItem(
        title = id,
        subtitle = "",
        thumbnailUrl = null,
        videoId = id,
        browseId = null,
    )

    @Test
    fun aggregatesOnlyTheInclusiveDateWindowAndIgnoresMalformedValues() {
        val counts = aggregateHourOfDayPlays(
            dailyCounts = mapOf(
                "2026-10-01" to List(24) { 100 },
                "2026-10-02" to listOf(2, -5, 4),
                "2026-10-03" to List(24) { 3 },
                "2026-10-04" to List(24) { 200 },
                "not-a-date" to List(24) { 500 },
            ),
            firstDay = LocalDate.parse("2026-10-02"),
            lastDay = LocalDate.parse("2026-10-03"),
        )

        assertEquals(5, counts[0])
        assertEquals(3, counts[1])
        assertEquals(7, counts[2])
        assertEquals(3, counts[23])
    }

    @Test
    fun invertedDateWindowProducesZeroCounts() {
        assertEquals(
            List(24) { 0 },
            aggregateHourOfDayPlays(
                dailyCounts = mapOf("2026-10-03" to List(24) { 10 }),
                firstDay = LocalDate.parse("2026-10-04"),
                lastDay = LocalDate.parse("2026-10-03"),
            ),
        )
    }
}
