// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.ui.unit.dp
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
    fun exploreKeepsTwoColumnsOnPhone() {
        assertEquals(2, moodColumns(400.dp))
    }

    @Test
    fun quickPicksAddsColumnsAsWindowGrows() {
        assertEquals(1, quickPickColumns(448.dp))
        assertEquals(3, quickPickColumns(800.dp))
        assertEquals(4, quickPickColumns(1040.dp))
        assertEquals(5, quickPickColumns(1280.dp))
    }
}
