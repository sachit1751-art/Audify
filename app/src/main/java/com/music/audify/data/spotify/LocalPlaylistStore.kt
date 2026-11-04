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

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()
    private val mutationMutex = Mutex()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        load()
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
            _playlists.value = list
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
