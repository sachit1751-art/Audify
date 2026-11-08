// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.ui.unit.dp
import com.music.audify.ui.player.fullBleedArtworkAvailable
import org.junit.Assert.assertEquals
import org.junit.Test

class TabletLayoutColumnsTest {
    @Test
    fun exploreUsesFourColumnsAtLandscapeTabletWidth() {
        assertEquals(4, moodColumns(800.dp))
    }

    @Test
    fun exploreKeepsThreeColumnsInNarrowerTabletWindow() {
        assertEquals(3, moodColumns(600.dp))
    }

    @Test
    fun exploreUsesOneColumnWhenTwoCardsWouldBeTooNarrow() {
        assertEquals(1, moodColumns(360.dp))
    }

    @Test
    fun exploreKeepsTwoColumnsWhenMinimumCardWidthFits() {
        assertEquals(2, moodColumns(400.dp))
    }

    @Test
    fun fullBleedSettingIsOnlyAvailableWhenPortraitPlayerCanFillWindow() {
        assertEquals(true, fullBleedArtworkAvailable(360.dp, 800.dp))
        assertEquals(true, fullBleedArtworkAvailable(620.dp, 900.dp))
        assertEquals(false, fullBleedArtworkAvailable(621.dp, 900.dp))
        assertEquals(false, fullBleedArtworkAvailable(800.dp, 1200.dp))
        assertEquals(false, fullBleedArtworkAvailable(600.dp, 360.dp))
        assertEquals(false, fullBleedArtworkAvailable(1200.dp, 800.dp))
    }

    @Test
    fun quickPicksAddsColumnsAsWindowGrows() {
        assertEquals(1, quickPickColumns(448.dp))
        assertEquals(3, quickPickColumns(800.dp))
        assertEquals(4, quickPickColumns(1040.dp))
        assertEquals(5, quickPickColumns(1280.dp))
    }

    @Test
    fun keepListeningKeepsAlbumTextReadableOnPhonesAndScalesForLargerWindows() {
        assertEquals(1, keepListeningColumns(360.dp))
        assertEquals(1, keepListeningColumns(400.dp))
        assertEquals(2, keepListeningColumns(448.dp))
        assertEquals(3, keepListeningColumns(800.dp))
        assertEquals(4, keepListeningColumns(1040.dp))
    }

    @Test
    fun keepListeningCardWidthsMatchTheWeightedShelfLayout() {
        assertEquals(328.dp, keepListeningCardWidth(360.dp))
        assertEquals(368.dp, keepListeningCardWidth(400.dp))
        assertEquals(201.dp, keepListeningCardWidth(448.dp))
        assertEquals(246.66667f, keepListeningCardWidth(800.dp).value, 0.001f)
        assertEquals(241.5f, keepListeningCardWidth(1040.dp).value, 0.001f)
    }
}
