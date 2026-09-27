package com.sachit.music.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan 009: pure window math behind the smart auto-playlists. The DAO queries do the
 * data filtering; these tests pin down the "On repeat" lookback window boundaries.
 */
class SmartPlaylistsTest {

    @Test
    fun `on repeat window starts two months earlier in the same year`() {
        assertEquals(java.time.LocalDate.of(2026, 7, 1), SmartPlaylists.onRepeatStartMonth(2026, 9))
    }

    @Test
    fun `on repeat window rolls back into the previous year for January and February`() {
        assertEquals(java.time.LocalDate.of(2025, 11, 1), SmartPlaylists.onRepeatStartMonth(2026, 1))
        assertEquals(java.time.LocalDate.of(2025, 12, 1), SmartPlaylists.onRepeatStartMonth(2026, 2))
    }

    @Test
    fun `on repeat window for March stays in the same year`() {
        assertEquals(java.time.LocalDate.of(2026, 1, 1), SmartPlaylists.onRepeatStartMonth(2026, 3))
    }

    @Test
    fun `on repeat window always starts on day one`() {
        for (month in 1..12) {
            assertEquals(1, SmartPlaylists.onRepeatStartMonth(2026, month).dayOfMonth)
        }
    }

    @Test
    fun `smart list kind resolves from route id and rejects unknown ids`() {
        assertEquals(SmartListKind.MOST_PLAYED_MONTH, SmartListKind.fromId("MOST_PLAYED_MONTH"))
        assertEquals(SmartListKind.ON_REPEAT, SmartListKind.fromId("ON_REPEAT"))
        assertEquals(SmartListKind.RECENTLY_ADDED, SmartListKind.fromId("RECENTLY_ADDED"))
        assertEquals(SmartListKind.NEVER_PLAYED, SmartListKind.fromId("NEVER_PLAYED"))
        assertNull(SmartListKind.fromId("bogus"))
        assertNull(SmartListKind.fromId(null))
    }
}
