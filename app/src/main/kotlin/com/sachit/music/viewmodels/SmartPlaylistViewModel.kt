/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sachit.music.db.MusicDatabase
import com.sachit.music.db.entities.Song
import com.sachit.music.ui.screens.library.SmartListKind
import com.sachit.music.ui.screens.library.SmartPlaylists
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmartPlaylistViewModel
@Inject
constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val listKind = SmartListKind.fromId(savedStateHandle.get<String>("list"))
        ?: SmartListKind.MOST_PLAYED_MONTH

    /**
     * Emits the current (year, month) once and again whenever the month rolls over while
     * collected (it sleeps until the first day of the next month, no polling), so list
     * windows are always computed at query time.
     */
    private val currentMonthKey: Flow<Pair<Int, Int>> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.year to now.monthValue)
            val nextMonthStart = now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay()
            delay(Duration.between(LocalDateTime.now(), nextMonthStart).toMillis() + 1_000)
        }
    }

    val mostPlayedThisMonth: StateFlow<List<Song>> =
        currentMonthKey
            .flatMapLatest { (year, month) -> database.mostPlayedSongsInMonth(year, month) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    val onRepeat: StateFlow<List<Song>> =
        currentMonthKey
            .flatMapLatest { (year, month) ->
                val start = SmartPlaylists.onRepeatStartMonth(year, month)
                database.songsPlayedFrequentlySince(fromYear = start.year, fromMonth = start.monthValue)
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    val recentlyAdded: StateFlow<List<Song>> =
        database.recentlyAddedSongs()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    val neverPlayed: StateFlow<List<Song>> =
        database.neverPlayedSongs()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun songsFor(kind: SmartListKind): StateFlow<List<Song>> =
        when (kind) {
            SmartListKind.MOST_PLAYED_MONTH -> mostPlayedThisMonth
            SmartListKind.ON_REPEAT -> onRepeat
            SmartListKind.RECENTLY_ADDED -> recentlyAdded
            SmartListKind.NEVER_PLAYED -> neverPlayed
        }
}
