// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeSlot
import com.music.audify.data.model.canHideLayoutSlot
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
    fun pinnedRowsRoundTripAndAppearBeforeUnpinnedRows() {
        val expected = HomeLayout(
            listOf(
                HomeSlot("shelf:albums", hidden = true, pinned = true),
                HomeSlot("shelf:recents"),
                HomeSlot("shelf:mix", pinned = true),
            ),
        )

        assertEquals(expected, HomeLayout.decode(expected.encode()))
        assertEquals(
            listOf("shelf:albums", "shelf:mix", "shelf:recents"),
            expected.arrange(listOf("shelf:albums", "shelf:recents", "shelf:mix"), dropHidden = false) { it },
        )
        assertTrue(expected.isPinned("shelf:mix"))
        assertFalse(expected.isPinned("shelf:recents"))
    }

    @Test
    fun legacySavedFlagsDecodeWithoutCreatingPinnedRows() {
        val decoded = HomeLayout.decode("0shelf:albums\n1shelf:recents")
        assertFalse(decoded.isPinned("shelf:albums"))
        assertFalse(decoded.isPinned("shelf:recents"))
        assertTrue(decoded.isHidden("shelf:recents"))
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
    fun moveReordersSlotsAndPreservesVisibility() {
        val layout = HomeLayout(
            listOf(
                HomeSlot("shelf:one", pinned = true),
                HomeSlot("shelf:two", hidden = true, pinned = true),
                HomeSlot("shelf:three"),
            ),
        )

        assertEquals(
            HomeLayout(
                listOf(
                    HomeSlot("shelf:two", hidden = true, pinned = true),
                    HomeSlot("shelf:one", pinned = true),
                    HomeSlot("shelf:three"),
                ),
            ),
            layout.move("shelf:two", -1),
        )
    }

    @Test
    fun moveIgnoresUnknownKeysAndClampsAtBoundaries() {
        val layout = HomeLayout(listOf(HomeSlot("shelf:one"), HomeSlot("shelf:two")))

        assertEquals(layout, layout.move("missing", 1))
        assertEquals(layout, layout.move("shelf:one", -1))
        assertEquals(layout, layout.move("shelf:two", 1))
        assertEquals(layout, layout.move("shelf:one", 0))
    }

    @Test
    fun moveCannotCrossThePinnedAndUnpinnedBoundary() {
        val layout = HomeLayout(
            listOf(
                HomeSlot("shelf:pinned", pinned = true),
                HomeSlot("shelf:ordinary"),
            ),
        )

        assertFalse(layout.canMove("shelf:pinned", 1))
        assertFalse(layout.canMove("shelf:ordinary", -1))
        assertEquals(layout, layout.move("shelf:pinned", 1))
        assertEquals(layout, layout.move("shelf:ordinary", -1))
        val grouped = HomeLayout(
            listOf(
                HomeSlot("shelf:pinned-one", pinned = true),
                HomeSlot("shelf:pinned-two", pinned = true),
                HomeSlot("shelf:ordinary-one"),
                HomeSlot("shelf:ordinary-two"),
            ),
        )
        assertTrue(grouped.canMove("shelf:pinned-two", -1))
        assertTrue(grouped.canMove("shelf:ordinary-one", 1))
    }

    @Test
    fun togglingPinPreservesVisibilityOrderAndCanBeReversed() {
        val layout = HomeLayout(
            listOf(
                HomeSlot("shelf:one", hidden = true),
                HomeSlot("shelf:two", pinned = true),
            ),
        )

        val pinned = layout.togglePinned("shelf:one")
        assertEquals(
            listOf(HomeSlot("shelf:one", hidden = true, pinned = true), HomeSlot("shelf:two", pinned = true)),
            pinned.slots,
        )
        assertEquals(layout, pinned.togglePinned("shelf:one"))
        assertEquals(layout, layout.togglePinned("missing"))
    }

    @Test
    fun layoutCannotHideItsLastVisibleSection() {
        assertTrue(canHideLayoutSlot(isVisible = true, visibleCount = 2))
        assertFalse(canHideLayoutSlot(isVisible = true, visibleCount = 1))
        assertTrue(canHideLayoutSlot(isVisible = false, visibleCount = 1))
    }

    @Test
    fun shelfKeysNormalizeKnownLocalizedAliases() {
        assertEquals(homeShelfKey("Listen again"), homeShelfKey("もう一度聴く"))
        assertEquals(homeShelfKey("Quick picks"), homeShelfKey("Speed dial"))
    }
}
