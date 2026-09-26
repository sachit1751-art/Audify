/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens.wrapped

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Aggregated listening stats for the current calendar month, shown on the Home
 * "mini-Wrapped" card. [minutes] is 0 when nothing was played this month;
 * [topArtistName] and [topSongTitle] are null when there is no data for them.
 */
data class MonthlyStats(
    val minutes: Long,
    val topArtistName: String?,
    val topSongTitle: String?,
) {
    /** The card is only worth rendering when something was actually played. */
    val isEmpty: Boolean get() = minutes <= 0L
}

/**
 * Pure helpers for the monthly card so the window boundaries are unit-testable
 * without Robolectric. Reads go through the same DAO queries Wrapped uses
 * (mostPlayedSongsStats / mostPlayedArtists / getTotalPlayTimeInRange), just with
 * a "current calendar month" window instead of Wrapped's fixed year.
 */
object MonthlyStatsProvider {

    /** Inclusive window [start, end) covering the whole calendar month of [today]. */
    fun monthWindow(today: LocalDate = LocalDate.now()): Pair<LocalDateTime, LocalDateTime> {
        val start = today.withDayOfMonth(1).atStartOfDay()
        val end = today.withDayOfMonth(1).plusMonths(1).atStartOfDay()
        return start to end
    }

    /**
     * Derives the card model from raw query results. Ties on play time resolve
     * deterministically (alphabetical by name) so the UI never flickers between
     * equal candidates.
     */
    fun build(
        totalPlayTimeMs: Long,
        topArtistName: String?,
        topSongTitle: String?,
    ): MonthlyStats =
        MonthlyStats(
            minutes = totalPlayTimeMs / 1000 / 60,
            topArtistName = topArtistName?.takeIf { it.isNotBlank() },
            topSongTitle = topSongTitle?.takeIf { it.isNotBlank() },
        )
}
