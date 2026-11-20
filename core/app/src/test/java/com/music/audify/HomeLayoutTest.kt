// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeSlot
import com.music.audify.data.model.homeShelfKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    @Test
    fun savedLayoutRoundTripsWithoutLosingHiddenRows() {
        val expected = HomeLayout(
            listOf(
                HomeSlot(HOME_SLOT_MOODS),
                HomeSlot("shelf:recents", hidden = true),
                HomeSlot("shelf:quick picks"),
            ),
        )

        assertEquals(expected, HomeLayout.decode(expected.encode()))
    }

    @Test
    fun customOrderMovesRowsAndAppendsNewRows() {
        val layout = HomeLayout(
            listOf(
                HomeSlot(HOME_SLOT_MOODS),
                HomeSlot("shelf:albums"),
                HomeSlot("shelf:quick picks"),
            ),
        )

        assertEquals(
            listOf(HOME_SLOT_MOODS, "shelf:albums", "shelf:quick picks", "shelf:new releases"),
            layout.arrange(
                listOf("shelf:quick picks", "shelf:albums", HOME_SLOT_MOODS, "shelf:new releases"),
            ) { it },
        )
    }

    @Test
    fun hiddenRowsAreFilteredOnlyWhenRequested() {
        val layout = HomeLayout(
            listOf(HomeSlot("shelf:recents", hidden = true), HomeSlot("shelf:albums")),
        )
        val rows = listOf("shelf:recents", "shelf:albums")

        assertEquals(listOf("shelf:albums"), layout.arrange(rows) { it })
        assertEquals(rows, layout.arrange(rows, dropHidden = false) { it })
        assertTrue(layout.isHidden("shelf:recents"))
        assertFalse(layout.isHidden("shelf:albums"))
    }

    @Test
    fun editorKeepsHiddenRowsAvailableForRestoring() {
        val layout = HomeLayout(
            listOf(HomeSlot("shelf:recents", hidden = true), HomeSlot("shelf:albums")),
        )

        assertEquals(
            listOf("shelf:recents", "shelf:albums", "shelf:new releases"),
            layout.editorOrder(listOf("shelf:albums", "shelf:new releases")),
        )
    }

    @Test
    fun shelfKeysNormalizeKnownLocalizedAliases() {
        assertEquals(homeShelfKey("Listen again"), homeShelfKey("もう一度聴く"))
        assertEquals(homeShelfKey("Quick picks"), homeShelfKey("Speed dial"))
    }
}
