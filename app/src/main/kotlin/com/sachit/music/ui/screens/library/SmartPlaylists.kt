/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens.library

import androidx.annotation.StringRes
import com.sachit.music.R
import java.time.LocalDate

/**
 * The smart auto-playlists (plan 009). The enum name doubles as the navigation
 * route argument ("smart_playlist/{list}") and is stable for saveable state.
 */
enum class SmartListKind {
    MOST_PLAYED_MONTH,
    ON_REPEAT,
    RECENTLY_ADDED,
    NEVER_PLAYED,
    ;

    @get:StringRes
    val titleRes: Int
        get() =
            when (this) {
                MOST_PLAYED_MONTH -> R.string.smart_most_played_month
                ON_REPEAT -> R.string.smart_on_repeat
                RECENTLY_ADDED -> R.string.smart_recently_added
                NEVER_PLAYED -> R.string.smart_never_played
            }

    companion object {
        fun fromId(id: String?): SmartListKind? = entries.firstOrNull { it.name == id }
    }
}

/**
 * Pure helpers behind the smart auto-playlists. The heavy lifting lives in
 * read-only DAO queries; this object only owns the window math so it can be
 * unit-tested without Robolectric.
 */
object SmartPlaylists {

    /** "On repeat" looks back this many calendar months, inclusive of the current one. */
    const val ON_REPEAT_MONTHS = 3

    /**
     * First month of the "On repeat" lookback window ending with the given month.
     * January/February roll back into the previous year.
     */
    fun onRepeatStartMonth(year: Int, month: Int): LocalDate =
        LocalDate.of(year, month, 1).minusMonths(ON_REPEAT_MONTHS - 1L)
}
