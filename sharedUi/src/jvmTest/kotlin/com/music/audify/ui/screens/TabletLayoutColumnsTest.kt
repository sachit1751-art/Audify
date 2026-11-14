// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.ui.unit.dp
import com.music.audify.ui.player.fullBleedArtworkAvailable
import org.junit.Assert.assertEquals
import org.junit.Test

class TabletLayoutColumnsTest {
    @Test
    fun exploreGridKeepsTwoColumnsOnPhonesAndAddsColumnsOnWideWindows() {
        assertEquals(2, moodGenreColumns(360.dp))
        assertEquals(2, moodGenreColumns(400.dp))
        assertEquals(3, moodGenreColumns(600.dp))
        assertEquals(4, moodGenreColumns(800.dp))
    }

    @Test
    fun fullBleedAvailabilityFollowsTheCurrentPlayerWindowBreakpoints() {
        assertEquals(true, fullBleedArtworkAvailable(360.dp))
        assertEquals(true, fullBleedArtworkAvailable(620.dp))
        assertEquals(false, fullBleedArtworkAvailable(650.dp))
        assertEquals(true, fullBleedArtworkAvailable(700.dp))
        assertEquals(true, fullBleedArtworkAvailable(1200.dp))
    }
}
