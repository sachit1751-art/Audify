// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.ui.unit.dp
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenLayoutTest {
    @Test
    fun discoverySectionsFollowTheFirstEligibleRecommendationNotQuickPicks() {
        val shelves = listOf(
            HomeShelf("  Recently played  ", listOf(track("recent"))),
            HomeShelf("Quick picks", listOf(track("quick"))),
            HomeShelf("Featured mixes", listOf(track("featured"))),
            HomeShelf("Albums for you", listOf(track("album"))),
        )

        assertEquals(
            HomeShelfPositions(recentIndex = 0, leadIndex = 2, discoveryInsertIndex = 2),
            homeShelfPositions(shelves),
        )
    }

    @Test
    fun discoverySectionsFallBackAfterHistoryWhenNoEligibleRecommendationExists() {
        val shelves = listOf(
            HomeShelf("Quick picks", listOf(track("quick"))),
            HomeShelf("Recently played", listOf(track("recent"))),
            HomeShelf("Music videos", listOf(track("video"))),
        )

        assertEquals(
            HomeShelfPositions(recentIndex = 1, leadIndex = -1, discoveryInsertIndex = 1),
            homeShelfPositions(shelves),
        )
        assertEquals(
            HomeShelfPositions(recentIndex = -1, leadIndex = -1, discoveryInsertIndex = -1),
            homeShelfPositions(emptyList()),
        )
    }

    @Test
    fun heroArtworkScalesFromPhoneAndPortraitTabletToStandardLandscapeAndDesktopShelves() {
        assertTrue(homeLeadUsesHero(400.dp, enabled = true))
        assertTrue(homeLeadUsesHero(800.dp, enabled = true))
        assertFalse(homeLeadUsesHero(900.dp, enabled = true))
        assertFalse(homeLeadUsesHero(1440.dp, enabled = true))
        assertFalse(homeLeadUsesHero(400.dp, enabled = false))
    }

    private fun track(id: String) = ShelfItem(
        title = id,
        subtitle = "Artist",
        thumbnailUrl = null,
        videoId = id,
        browseId = null,
    )
}
