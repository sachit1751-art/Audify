/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens.wrapped

import java.time.LocalDate
import java.time.LocalDateTime

/** The time range a recap slideshow covers. */
enum class RecapRange {
    /** Current calendar month. */
    MONTHLY,

    /** Current calendar year (the original Wrapped behavior). */
    YEARLY,
}

/**
 * Pure window mapping for the recap feature so the boundaries are unit-testable
 * without Robolectric. Windows are always computed at query time from [today] —
 * never cached — so month/year rollover mid-session is handled naturally.
 */
object RecapRangeResolver {

    /** Inclusive [start, end) window covering the calendar month of [today]. */
    fun monthlyWindow(today: LocalDate = LocalDate.now()): Pair<LocalDateTime, LocalDateTime> =
        MonthlyStatsProvider.monthWindow(today)

    /** Inclusive [start, end) window covering the calendar year of [today]. */
    fun yearlyWindow(today: LocalDate = LocalDate.now()): Pair<LocalDateTime, LocalDateTime> {
        val start = today.withDayOfYear(1).atStartOfDay()
        val end = LocalDate.of(today.year, 12, 31).plusDays(1).atStartOfDay()
        return start to end
    }

    fun resolve(range: RecapRange, today: LocalDate = LocalDate.now()): Pair<LocalDateTime, LocalDateTime> =
        when (range) {
            RecapRange.MONTHLY -> monthlyWindow(today)
            RecapRange.YEARLY -> yearlyWindow(today)
        }

    /** The calendar year a window belongs to, for headline text ("Your 2026 Wrapped"). */
    fun recapYear(range: RecapRange, today: LocalDate = LocalDate.now()): Int = today.year

    /** Label-safe numeric year for the playlist name ("Sachit 2026-09" for a monthly recap). */
    fun playlistLabel(range: RecapRange, today: LocalDate = LocalDate.now()): String =
        when (range) {
            RecapRange.MONTHLY -> "%04d-%02d".format(today.year, today.monthValue)
            RecapRange.YEARLY -> today.year.toString()
        }
}
