// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.audify.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal suspend fun <S, R> persistBeforePublish(
    mutex: Mutex,
    persist: suspend () -> Pair<S, R>,
    publish: (S) -> Unit,
): R = mutex.withLock {
    val (state, result) = persist()
    publish(state)
    result
}

data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
    val youtubePlaylistId: String? = null,
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    private const val PREF_NAME = "audify_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val SCHEMA_VERSION = 1

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()
    private val mutationMutex = Mutex()
    const val FAVORITES_PLAYLIST_ID = "audify_favorites"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        migrateStorageIfNeeded()
        load()
    }

    /** Stable SharedPreferences name plus an explicit schema marker for future format migrations. */
    private fun migrateStorageIfNeeded() {
        val version = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        require(version <= SCHEMA_VERSION) { "Local playlist data was created by a newer Audify build" }
        if (version < SCHEMA_VERSION) {
            check(prefs.edit().putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION).commit()) {
                "Could not finalize local playlist storage migration"
            }
        }
    }

    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return
        runCatching {
            val list = mutableListOf<LocalPlaylist>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val songsArray = obj.getJSONArray("songs")
                val songs = mutableListOf<Song>()
                for (j in 0 until songsArray.length()) {
                    val sObj = songsArray.getJSONObject(j)
                    val song = Song(
                        videoId = sObj.optString("videoId", ""),
                        title = sObj.optString("title", ""),
                        artist = sObj.optString("artist", ""),
                        thumbnailUrl = sObj.optString("thumbnailUrl", "").takeIf { it.isNotBlank() && it != "null" },
                        durationText = sObj.optString("durationText", "").takeIf { it.isNotBlank() && it != "null" },
                        artistId = sObj.optString("artistId", "").takeIf { it.isNotBlank() && it != "null" },
                        albumId = sObj.optString("albumId", "").takeIf { it.isNotBlank() && it != "null" },
                        albumName = sObj.optString("albumName", "").takeIf { it.isNotBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    songs.add(song)
                }
                list.add(LocalPlaylist(id, title, songs, obj.optString("youtubePlaylistId").takeIf { it.isNotBlank() && it != "null" }))
            }
            val normalized = list.distinctBy { it.id }.map { it.copy(songs = it.songs
                .filter { song -> song.videoId.isNotBlank() }.distinctBy { song -> song.videoId }) }
            if (normalized != list) persistDurably(normalized)
            _playlists.value = normalized
        }
    }

    suspend fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist =
        withContext(Dispatchers.IO) {
            persistBeforePublish(
                mutex = mutationMutex,
                persist = {
                    checkInitialized()
                    val playlist = LocalPlaylist(
                        id = "sp_local_${UUID.randomUUID()}",
                        title = title,
                        songs = songs,
                    )
                    val updated = listOf(playlist) + _playlists.value.filterNot { it.id == playlist.id }
                    persistDurably(updated)
                    updated to playlist
                },
                publish = { _playlists.value = it },
            )
        }

    /** Keeps the device's Favorites collection reachable through the normal playlist UI. */
    suspend fun syncFavorites(songs: List<Song>) = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanSongs = songs.distinctBy { it.videoId }
                val updated = if (cleanSongs.isEmpty()) {
                    _playlists.value.filterNot { it.id == FAVORITES_PLAYLIST_ID }
                } else {
                    listOf(LocalPlaylist(FAVORITES_PLAYLIST_ID, "Favorites", cleanSongs)) +
                        _playlists.value.filterNot { it.id == FAVORITES_PLAYLIST_ID }
                }
                if (updated != _playlists.value) persistDurably(updated)
                updated to Unit
            },
            publish = { _playlists.value = it },
        )
    }

    suspend fun addSongs(id: String, songs: List<Song>): Int = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanId = id.removePrefix("local:playlist:")
                val current = _playlists.value.firstOrNull { it.id == cleanId }
                val known = current?.songs?.mapTo(mutableSetOf()) { it.videoId } ?: mutableSetOf()
                val additions = if (current == null) emptyList() else songs.filter {
                    it.videoId.isNotBlank() && known.add(it.videoId)
                }
                val updated = if (additions.isEmpty()) _playlists.value else _playlists.value.map { playlist ->
                    if (playlist.id == cleanId) playlist.copy(songs = playlist.songs + additions) else playlist
                }
                if (additions.isNotEmpty()) persistDurably(updated)
                updated to additions.size
            },
            publish = { _playlists.value = it },
        )
    }

    suspend fun removeSong(id: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanId = id.removePrefix("local:playlist:")
                val updated = _playlists.value.map { playlist ->
                    if (playlist.id == cleanId) playlist.copy(songs = playlist.songs.filterNot { it.videoId == videoId })
                    else playlist
                }
                val changed = updated != _playlists.value
                if (changed) persistDurably(updated)
                updated to changed
            },
            publish = { _playlists.value = it },
        )
    }

    suspend fun reorderSongs(id: String, songs: List<Song>): Boolean = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanId = id.removePrefix("local:playlist:")
                val updated = _playlists.value.map { playlist ->
                    if (playlist.id == cleanId) playlist.copy(songs = songs) else playlist
                }
                val changed = updated != _playlists.value
                if (changed) persistDurably(updated)
                updated to changed
            },
            publish = { _playlists.value = it },
        )
    }

    suspend fun markSynced(id: String, youtubePlaylistId: String): Boolean = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanId = id.removePrefix("local:playlist:")
                val exists = _playlists.value.any { it.id == cleanId }
                val updated = if (exists) _playlists.value.map { playlist ->
                    if (playlist.id == cleanId) playlist.copy(youtubePlaylistId = youtubePlaylistId) else playlist
                } else _playlists.value
                if (exists) persistDurably(updated)
                updated to exists
            },
            publish = { _playlists.value = it },
        )
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanId = id.removePrefix("local:playlist:")
                val updated = _playlists.value.filterNot {
                    it.id == id || it.browseId == id || it.id == cleanId
                }
                if (updated.size != _playlists.value.size) persistDurably(updated)
                updated to Unit
            },
            publish = { _playlists.value = it },
        )
    }

    suspend fun renamePlaylist(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                checkInitialized()
                val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
                val updated = _playlists.value.map {
                    if (it.id == cleanId || it.id == id || it.browseId == id) it.copy(title = newTitle) else it
                }
                if (updated != _playlists.value) persistDurably(updated)
                updated to Unit
            },
            publish = { _playlists.value = it },
        )
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    /** Raw, versioned-by-shape snapshot included in the user's explicit backup. */
    fun exportSnapshot(): String {
        checkInitialized()
        return prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
    }

    /** Validates the complete document before replacing the current local playlists. */
    fun importSnapshot(raw: String) {
        checkInitialized()
        val array = JSONArray(raw)
        val merged = linkedMapOf<String, LocalPlaylist>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.getString("id").trim()
            val title = item.getString("title").trim()
            require(id.isNotBlank() && title.isNotBlank())
            val songsArray = item.getJSONArray("songs")
            require(songsArray.length() <= 100_000)
            val songs = (0 until songsArray.length()).mapNotNull { index ->
                val song = songsArray.optJSONObject(index) ?: return@mapNotNull null
                val videoId = song.optString("videoId").takeIf(String::isNotBlank) ?: return@mapNotNull null
                Song(
                    videoId = videoId,
                    title = song.optString("title"),
                    artist = song.optString("artist"),
                    thumbnailUrl = song.optString("thumbnailUrl").takeIf { it.isNotBlank() && it != "null" },
                    durationText = song.optString("durationText").takeIf { it.isNotBlank() && it != "null" },
                    artistId = song.optString("artistId").takeIf { it.isNotBlank() && it != "null" },
                    albumId = song.optString("albumId").takeIf { it.isNotBlank() && it != "null" },
                    albumName = song.optString("albumName").takeIf { it.isNotBlank() && it != "null" },
                    isVideo = song.optBoolean("isVideo", false),
                )
            }.distinctBy { it.videoId }
            val previous = merged[id]
            merged[id] = if (previous == null) {
                LocalPlaylist(id, title, songs, item.optString("youtubePlaylistId").takeIf { it.isNotBlank() && it != "null" })
            } else {
                previous.copy(songs = (previous.songs + songs).distinctBy { it.videoId })
            }
        }
        val normalized = JSONArray().apply {
            merged.values.forEach { playlist ->
                put(JSONObject().apply {
                    put("id", playlist.id)
                    put("title", playlist.title)
                    put("youtubePlaylistId", playlist.youtubePlaylistId)
                    put("songs", JSONArray().apply {
                        playlist.songs.forEach { song -> put(JSONObject().apply {
                            put("videoId", song.videoId); put("title", song.title); put("artist", song.artist)
                            put("thumbnailUrl", song.thumbnailUrl); put("durationText", song.durationText)
                            put("artistId", song.artistId); put("albumId", song.albumId)
                            put("albumName", song.albumName); put("isVideo", song.isVideo)
                        }) }
                    })
                })
            }
        }
        check(prefs.edit().putString(KEY_PLAYLISTS, normalized.toString()).commit()) {
            "Could not restore local playlists"
        }
        load()
    }

    private fun checkInitialized() {
        check(this::prefs.isInitialized) {
            "LocalPlaylistStore must be initialized before changing playlists"
        }
    }

    /** Every mutation commits the complete snapshot before publishing it. */
    private fun persistDurably(list: List<LocalPlaylist>) {
        val jsonArray = JSONArray()
        for (playlist in list) {
            val pObj = JSONObject()
            pObj.put("id", playlist.id)
            pObj.put("title", playlist.title)
            pObj.put("youtubePlaylistId", playlist.youtubePlaylistId)
            val songsArray = JSONArray()
            for (song in playlist.songs) {
                val sObj = JSONObject()
                sObj.put("videoId", song.videoId)
                sObj.put("title", song.title)
                sObj.put("artist", song.artist)
                sObj.put("thumbnailUrl", song.thumbnailUrl)
                sObj.put("durationText", song.durationText)
                sObj.put("artistId", song.artistId)
                sObj.put("albumId", song.albumId)
                sObj.put("albumName", song.albumName)
                sObj.put("isVideo", song.isVideo)
                songsArray.put(sObj)
            }
            pObj.put("songs", songsArray)
            jsonArray.put(pObj)
        }
        val saved = prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).commit()
        check(saved) { "Could not write the local playlist to device storage" }
    }
}
