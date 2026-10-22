/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sachit.music.constants.SongFilter
import com.sachit.music.constants.SongSortDescendingKey
import com.sachit.music.constants.SongSortType
import com.sachit.music.constants.SongSortTypeKey
import com.sachit.music.db.MusicDatabase
import com.sachit.music.extensions.filterExplicit
import com.sachit.music.extensions.filterVideoSongs
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sachit.music.utils.LocalFileScanner
import com.sachit.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongWithStatus(
    val song: com.sachit.music.db.entities.Song,
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
)

@HiltViewModel
class LocalSongsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val localFileScanner: LocalFileScanner,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val debouncedSearchQuery = _searchQuery
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val sortType = context.dataStore.data
        .map { prefs -> prefs[SongSortTypeKey]?.let { SongSortType.valueOf(it) } ?: SongSortType.CREATE_DATE }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, SongSortType.CREATE_DATE)

    val sortDescending = context.dataStore.data
        .map { prefs -> prefs[SongSortDescendingKey] ?: true }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val filter = context.dataStore.data
        .map { prefs -> prefs[SongSortTypeKey]?.let { SongFilter.valueOf(it) } ?: SongFilter.LIKED }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, SongFilter.LIKED)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<LocalFileScanner.ScanResult?>(null)
    val scanResult: StateFlow<LocalFileScanner.ScanResult?> = _scanResult.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val hasScannedPreviously: Boolean
        get() = _scanResult.value != null

    val localSongs = combine(
        sortType,
        sortDescending,
    ) { sortType, descending ->
        Pair(sortType, descending)
    }.flatMapLatest { (sortType, descending) ->
        when (sortType) {
            SongSortType.CREATE_DATE -> database.localSongs()
            SongSortType.NAME -> database.localSongsByNameAsc()
            else -> database.localSongs()
        }.map { songs ->
            songs.filter { !it.song.explicit }
                .filter { !it.song.isVideo }
                .let { if (descending) it.reversed() else it }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSortType(type: SongSortType) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[SongSortTypeKey] = type.name
            }
        }
    }

    fun setSortDescending(descending: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[SongSortDescendingKey] = descending
            }
        }
    }

    fun setFilter(filter: SongFilter) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[SongSortTypeKey] = filter.name
            }
        }
    }

    fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED -> {
                scan()
            }
            else -> {
                // Don't block — the screen shows empty state with button
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val result = localFileScanner.scan()
                _scanResult.value = result
            } catch (e: Exception) {
                // Log and continue — partial scans are ok
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            scan()
            _isRefreshing.value = false
        }
    }
}
