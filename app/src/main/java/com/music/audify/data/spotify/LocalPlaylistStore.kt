package com.music.audify.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.audify.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Serialize durable playlist writes and publish only after persistence commits. */
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
    val coverUrl: String? = null,
    val youtubePlaylistId: String? = null,
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    private const val PREF_NAME = "audify_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"
    const val FAVORITES_PLAYLIST_ID = "audify_favorites"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

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
                        thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    songs.add(song)
                }
                val coverUrl = obj.optString("coverUrl", null).takeIf { !it.isNullOrBlank() && it != "null" }
                val youtubePlaylistId = obj.optString("youtubePlaylistId", null)
                    .takeIf { !it.isNullOrBlank() && it != "null" }
                list.add(LocalPlaylist(id, title, songs, coverUrl, youtubePlaylistId))
            }
            _playlists.value = list
        }
    }

    fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist {
        val id = "sp_local_" + System.currentTimeMillis()
        val playlist = LocalPlaylist(id = id, title = title, songs = songs)
        val updated = listOf(playlist) + _playlists.value.filterNot { it.id == id }
        _playlists.value = updated
        persist(updated)
        return playlist
    }

    fun deletePlaylist(id: String) {
        val updated = _playlists.value.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") }
        _playlists.value = updated
        persist(updated)
    }

    fun renamePlaylist(id: String, newTitle: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                it.copy(title = newTitle)
            } else it
        }
        _playlists.value = updated
        persist(updated)
    }

    /** Adds [song] unless the playlist already holds it; true when it went in. */
    fun addSong(id: String, song: Song): Boolean = addSongs(id, listOf(song)) > 0

    /** Appends the tracks the playlist does not hold yet and returns how many went in. */
    fun addSongs(id: String, songs: List<Song>): Int {
        val current = getPlaylist(id) ?: return 0
        val known = current.songs.mapTo(HashSet()) { it.videoId }
        val fresh = songs.filter { known.add(it.videoId) }
        if (fresh.isEmpty()) return 0
        replace(current.id) { it.copy(songs = it.songs + fresh) }
        return fresh.size
    }

    fun removeSong(id: String, videoId: String) {
        val current = getPlaylist(id) ?: return
        replace(current.id) { it.copy(songs = it.songs.filterNot { song -> song.videoId == videoId }) }
    }

    /** Sets the custom cover; null goes back to the first track's artwork. */
    fun setCover(id: String, coverUrl: String?) {
        val current = getPlaylist(id) ?: return
        replace(current.id) { it.copy(coverUrl = coverUrl) }
    }

    private fun replace(id: String, edit: (LocalPlaylist) -> LocalPlaylist) {
        val updated = _playlists.value.map { if (it.id == id) edit(it) else it }
        _playlists.value = updated
        persist(updated)
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    /** Raw snapshot used by Audify's explicit, validated backup format. */
    fun exportSnapshot(): String {
        check(this::prefs.isInitialized) { "Local playlists have not been initialized" }
        return prefs.getString(KEY_PLAYLISTS, "[]") ?: "[]"
    }

    /** Validates the complete playlist document before publishing it. */
    fun importSnapshot(raw: String) {
        check(this::prefs.isInitialized) { "Local playlists have not been initialized" }
        val array = JSONArray(raw)
        require(array.length() <= 10_000) { "Backup contains too many local playlists" }
        val imported = buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id").takeIf(String::isNotBlank)
                    ?: error("Local playlist has no id")
                val title = obj.getString("title").takeIf(String::isNotBlank)
                    ?: error("Local playlist has no title")
                val songsArray = obj.getJSONArray("songs")
                require(songsArray.length() <= 100_000) { "Local playlist contains too many songs" }
                val songs = buildList {
                    for (j in 0 until songsArray.length()) {
                        val song = songsArray.getJSONObject(j)
                        val videoId = song.getString("videoId").takeIf(String::isNotBlank) ?: continue
                        add(Song(
                            videoId = videoId,
                            title = song.optString("title", ""),
                            artist = song.optString("artist", ""),
                            thumbnailUrl = song.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            durationText = song.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            artistId = song.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            albumId = song.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            albumName = song.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                            isVideo = song.optBoolean("isVideo", false),
                        ))
                    }
                }.distinctBy { it.videoId }
                add(LocalPlaylist(
                    id = id,
                    title = title,
                    songs = songs,
                    coverUrl = obj.optString("coverUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                    youtubePlaylistId = obj.optString("youtubePlaylistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                ))
            }
        }.distinctBy { it.id }
        persist(imported)
        _playlists.value = imported
    }

    /** Keeps the device's Favorites collection reachable through the normal playlist UI. */
    fun syncFavorites(songs: List<Song>) {
        val favorite = LocalPlaylist(
            id = FAVORITES_PLAYLIST_ID,
            title = "Favorites",
            songs = songs.distinctBy { it.videoId },
        )
        val updated = listOf(favorite) + _playlists.value.filterNot { it.id == FAVORITES_PLAYLIST_ID }
        persist(updated)
        _playlists.value = updated
    }

    private fun persist(list: List<LocalPlaylist>) {
        if (!this::prefs.isInitialized) return
        runCatching {
            val jsonArray = JSONArray()
            for (playlist in list) {
                val pObj = JSONObject()
                pObj.put("id", playlist.id)
                pObj.put("title", playlist.title)
                pObj.put("coverUrl", playlist.coverUrl)
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
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
        }
    }
}
