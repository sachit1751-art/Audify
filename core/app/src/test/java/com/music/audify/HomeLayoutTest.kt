// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeSlot
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.homeShelfKey
import com.music.audify.data.model.moveHomeSlotWithinPinGroup
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

    @Test
    fun pinnedRowsStayAheadOfUnpinnedRowsAndSurviveSerialization() {
        val layout = HomeLayout(
            listOf(
                HomeSlot("shelf:albums", pinned = false),
                HomeSlot("shelf:recents", pinned = true),
                HomeSlot("shelf:mixes", hidden = true, pinned = true),
            ),
        )

        val restored = HomeLayout.decode(layout.encode())

        assertEquals(layout, restored)
        assertEquals(
            listOf("shelf:recents", "shelf:albums"),
            restored.arrange(listOf("shelf:albums", "shelf:recents")) { it },
        )
        assertTrue(restored.isPinned("shelf:recents"))
        assertFalse(restored.isPinned("shelf:albums"))
    }

    @Test
    fun legacyOneFlagLayoutsDecodeWithoutInventingPinnedRows() {
        val restored = HomeLayout.decode("0shelf:albums\n1shelf:recents")

        assertEquals(
            listOf(HomeSlot("shelf:albums"), HomeSlot("shelf:recents", hidden = true)),
            restored.slots,
        )
    }

    @Test
    fun browseableShelvesKeepIdentityWhenTheirDisplayTitleChanges() {
        val english = HomeShelf("New releases", emptyList(), moreBrowseId = "VL_RELEASES")
        val translated = HomeShelf("Nuevos lanzamientos", emptyList(), moreBrowseId = "VL_RELEASES")

        assertEquals(homeShelfKey(english), homeShelfKey(translated))
        assertEquals("shelf:browse:VL_RELEASES", homeShelfKey(english))
    }

    @Test
    fun legacyShelfKeysMigrateToBrowseIdsWithoutLosingHiddenOrPinnedFlags() {
        val shelf = HomeShelf("New releases", emptyList(), moreBrowseId = "VL_RELEASES")
        val legacy = HomeLayout(listOf(HomeSlot("shelf:new releases", hidden = true, pinned = true)))

        val migrated = legacy.remapShelfKeys(listOf(shelf))

        assertEquals(
            listOf(HomeSlot("shelf:browse:VL_RELEASES", hidden = true, pinned = true)),
            migrated.slots,
        )
    }

    @Test
    fun moveControlsReorderInsidePriorityGroupsWithoutCrossingPinnedRows() {
        val order = listOf("pinned-a", "pinned-b", "normal-a", "normal-b")
        val pinned = setOf("pinned-a", "pinned-b")

        assertEquals(
            listOf("pinned-b", "pinned-a", "normal-a", "normal-b"),
            moveHomeSlotWithinPinGroup(order, pinned, "pinned-b", -1),
        )
        assertEquals(
            listOf("pinned-a", "pinned-b", "normal-b", "normal-a"),
            moveHomeSlotWithinPinGroup(order, pinned, "normal-b", -1),
        )
        assertEquals(order, moveHomeSlotWithinPinGroup(order, pinned, "normal-a", -1))
    }
}
