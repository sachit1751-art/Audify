// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.stats

import android.content.Context
import android.content.SharedPreferences
import com.music.audify.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Durable, account-independent library data. Mutations commit before flows publish. */
object LocalLibraryStore {
    private const val PREFS = "audify_local_library"
    private const val FAVORITES = "favorites_v1"
    private const val HISTORY = "history_v1"
    private const val SCHEMA_VERSION_KEY = "schema_version"
    private const val SCHEMA_VERSION = 1
    private const val HISTORY_LIMIT = 500
    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites = _favorites.asStateFlow()
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history = _history.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(SCHEMA_VERSION_KEY, 0)
        require(storedVersion <= SCHEMA_VERSION) { "Local library data was created by a newer Audify build" }
        if (storedVersion < SCHEMA_VERSION) {
            check(prefs.edit().putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION).commit()) {
                "Could not finalize local library storage migration"
            }
        }
        _favorites.value = readSongs(FAVORITES)
        _history.value = readSongs(HISTORY)
    }

    @Synchronized
    fun setFavorite(song: Song, liked: Boolean) {
        checkInitialized()
        val next = if (liked) listOf(song) + _favorites.value.filterNot { it.videoId == song.videoId }
            else _favorites.value.filterNot { it.videoId == song.videoId }
        persist(FAVORITES, next)
        _favorites.value = next
    }

    @Synchronized
    fun recordPlay(song: Song) {
        if (!this::prefs.isInitialized || song.videoId.isBlank()) return
        val next = (listOf(song) + _history.value.filterNot { it.videoId == song.videoId })
            .take(HISTORY_LIMIT)
        // Playback sampling can run on a latency-sensitive service thread;
        // SharedPreferences.apply updates memory now and flushes the file off-thread.
        val array = encodeSongs(next)
        prefs.edit().putString(HISTORY, array).apply()
        _history.value = next
    }

    fun exportFavorites(): List<Song> = _favorites.value.toList()
    fun exportHistory(): List<Song> = _history.value.toList()

    @Synchronized
    fun importLibrary(favorites: List<Song>, history: List<Song>) {
        checkInitialized()
        val cleanFavorites = favorites.filter { it.videoId.isNotBlank() }.distinctBy { it.videoId }
        val cleanHistory = history.filter { it.videoId.isNotBlank() }.distinctBy { it.videoId }.take(HISTORY_LIMIT)
        persist(FAVORITES, cleanFavorites)
        persist(HISTORY, cleanHistory)
        _favorites.value = cleanFavorites
        _history.value = cleanHistory
    }

    private fun readSongs(key: String): List<Song> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Song(
                videoId = id,
                title = item.optString("title"),
                artist = item.optString("artist"),
                thumbnailUrl = item.optString("thumbnail").takeIf(String::isNotBlank),
                durationText = item.optString("duration").takeIf(String::isNotBlank),
                artistId = item.optString("artistId").takeIf(String::isNotBlank),
                albumId = item.optString("albumId").takeIf(String::isNotBlank),
                albumName = item.optString("album").takeIf(String::isNotBlank),
                isVideo = item.optBoolean("video", false),
            )
        }
    }.getOrDefault(emptyList())

    private fun persist(key: String, songs: List<Song>) {
        check(prefs.edit().putString(key, encodeSongs(songs)).commit()) {
            "Could not persist local library data"
        }
    }

    private fun encodeSongs(songs: List<Song>): String {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(JSONObject().apply {
                put("id", song.videoId)
                put("title", song.title)
                put("artist", song.artist)
                put("thumbnail", song.thumbnailUrl)
                put("duration", song.durationText)
                put("artistId", song.artistId)
                put("albumId", song.albumId)
                put("album", song.albumName)
                put("video", song.isVideo)
            })
        }
        return array.toString()
    }

    private fun checkInitialized() = check(this::prefs.isInitialized) {
        "LocalLibraryStore must be initialized before changing library data"
    }
}
