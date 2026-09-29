/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens.wrapped

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecapRangeResolverTest {

    @Test
    fun `monthly window reuses month window provider`() {
        val today = LocalDate.of(2026, 9, 28)
        val (start, end) = RecapRangeResolver.resolve(RecapRange.MONTHLY, today)
        assertEquals(MonthlyStatsProvider.monthWindow(today), start to end)
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), end)
    }

    @Test
    fun `yearly window starts jan first and ends next jan first`() {
        val (start, end) = RecapRangeResolver.resolve(RecapRange.YEARLY, LocalDate.of(2026, 9, 28))
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), start)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), end)
    }

    @Test
    fun `leap year february monthly window covers 29 days`() {
        val (start, end) = RecapRangeResolver.resolve(RecapRange.MONTHLY, LocalDate.of(2028, 2, 10))
        assertEquals(LocalDateTime.of(2028, 2, 1, 0, 0), start)
        assertEquals(LocalDateTime.of(2028, 3, 1, 0, 0), end)
    }

    @Test
    fun `december monthly window rolls into next year`() {
        val (start, end) = RecapRangeResolver.resolve(RecapRange.MONTHLY, LocalDate.of(2026, 12, 31))
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), start)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), end)
    }

    @Test
    fun `yearly window on leap year covers full year`() {
        val (start, end) = RecapRangeResolver.resolve(RecapRange.YEARLY, LocalDate.of(2028, 6, 15))
        assertEquals(LocalDateTime.of(2028, 1, 1, 0, 0), start)
        assertEquals(LocalDateTime.of(2029, 1, 1, 0, 0), end)
    }

    @Test
    fun `playlist label for monthly range is year-month`() {
        assertEquals("2026-09", RecapRangeResolver.playlistLabel(RecapRange.MONTHLY, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `playlist label for yearly range is the year`() {
        assertEquals("2026", RecapRangeResolver.playlistLabel(RecapRange.YEARLY, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `recap year is the window year`() {
        assertEquals(2026, RecapRangeResolver.recapYear(RecapRange.MONTHLY, LocalDate.of(2026, 9, 28)))
        assertEquals(2026, RecapRangeResolver.recapYear(RecapRange.YEARLY, LocalDate.of(2026, 9, 28)))
    }
}
