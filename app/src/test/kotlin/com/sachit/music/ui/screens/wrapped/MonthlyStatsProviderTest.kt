/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens.wrapped

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MonthlyStatsProviderTest {

    @Test
    fun `month window starts at first day midnight`() {
        val (start, _) = MonthlyStatsProvider.monthWindow(LocalDate.of(2026, 9, 15))
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0, 0), start)
    }

    @Test
    fun `month window ends at first day of next month`() {
        val (_, end) = MonthlyStatsProvider.monthWindow(LocalDate.of(2026, 9, 15))
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0, 0), end)
    }

    @Test
    fun `january window crosses year boundary correctly`() {
        val (start, end) = MonthlyStatsProvider.monthWindow(LocalDate.of(2026, 1, 31))
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0, 0), end)
    }

    @Test
    fun `december window rolls into next year`() {
        val (start, end) = MonthlyStatsProvider.monthWindow(LocalDate.of(2026, 12, 31))
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0, 0), start)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0, 0), end)
    }

    @Test
    fun `leap year february window covers 29 days`() {
        val (start, end) = MonthlyStatsProvider.monthWindow(LocalDate.of(2028, 2, 10))
        assertEquals(LocalDateTime.of(2028, 2, 1, 0, 0, 0), start)
        assertEquals(LocalDateTime.of(2028, 3, 1, 0, 0, 0), end)
    }

    @Test
    fun `build converts milliseconds to minutes`() {
        val stats = MonthlyStatsProvider.build(totalPlayTimeMs = 90 * 60 * 1000L, topArtistName = null, topSongTitle = null)
        assertEquals(90L, stats.minutes)
    }

    @Test
    fun `build treats zero play time as empty`() {
        val stats = MonthlyStatsProvider.build(0L, null, null)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun `build with data is not empty`() {
        val stats = MonthlyStatsProvider.build(5 * 60 * 1000L, "Artist", "Song")
        assertFalse(stats.isEmpty)
        assertEquals("Artist", stats.topArtistName)
        assertEquals("Song", stats.topSongTitle)
    }

    @Test
    fun `build blanks out whitespace-only names`() {
        val stats = MonthlyStatsProvider.build(5 * 60 * 1000L, "   ", "Song")
        assertNull(stats.topArtistName)
        assertEquals("Song", stats.topSongTitle)
    }
}
