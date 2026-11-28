package com.music.audify.data.spotify

import androidx.core.content.edit
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

internal fun mergeImportedSongs(existing: LocalPlaylist, imported: List<Song>): LocalPlaylist {
    val known = existing.songs.mapTo(HashSet()) { it.videoId }
    val additions = imported.filter { it.videoId.isNotBlank() && known.add(it.videoId) }
    return existing.copy(songs = existing.songs + additions)
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
                val coverUrl = obj.optString("coverUrl", "").takeIf { it.isNotBlank() && it != "null" }
                val youtubePlaylistId = obj.optString("youtubePlaylistId", "")
                    .takeIf { it.isNotBlank() && it != "null" }
                list.add(LocalPlaylist(id, title, songs, coverUrl, youtubePlaylistId))
            }
            _playlists.value = list
        }
    }

    suspend fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist {
        checkInitialized()
        return mutate { current ->
            val id = "sp_local_" + System.currentTimeMillis()
            val playlist = LocalPlaylist(id = id, title = title, songs = songs)
            (listOf(playlist) + current.filterNot { it.id == id }) to playlist
        }
    }

    /** Creates or refreshes a stable local playlist identity (used by Smart Playlists). */
    suspend fun saveOrReplacePlaylist(id: String, title: String, songs: List<Song>): LocalPlaylist {
        checkInitialized()
        require(id.isNotBlank() && !id.contains(':')) { "Invalid local playlist id" }
        val playlist = LocalPlaylist(id = id, title = title, songs = songs.distinctBy { it.videoId })
        return mutate { current ->
            (listOf(playlist) + current.filterNot { it.id == id }) to playlist
        }
    }

    /**
     * Reconcile the generated Smart Playlist snapshots as one durable update.
     * Smart rules remain the source of truth; these local entries are only the
     * normal-playlist UI projection. Removing missing IDs also clears orphaned
     * snapshots after a Smart Playlist is deleted.
     */
    suspend fun syncSmartPlaylists(playlists: List<LocalPlaylist>) {
        checkInitialized()
        mutate { current ->
            val currentSmart = current.filter { it.id.startsWith("smart_") }.associateBy { it.id }
            val synchronizedSmart = playlists
                .filter { it.id.startsWith("smart_") }
                .distinctBy { it.id }
                .map { playlist ->
                    require(!playlist.id.contains(':')) { "Invalid Smart Playlist id" }
                    playlist.copy(coverUrl = currentSmart[playlist.id]?.coverUrl)
                }
            (synchronizedSmart + current.filterNot { it.id.startsWith("smart_") }) to Unit
        }
    }

    suspend fun deletePlaylist(id: String) {
        mutate { current ->
            current.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") } to Unit
        }
    }

    suspend fun renamePlaylist(id: String, newTitle: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        mutate { current ->
            current.map {
                if (it.id == cleanId || it.id == id || it.browseId == id) it.copy(title = newTitle) else it
            } to Unit
        }
    }

    /** Adds [song] unless the playlist already holds it; true when it went in. */
    suspend fun addSong(id: String, song: Song): Boolean = addSongs(id, listOf(song)) > 0

    /** Appends the tracks the playlist does not hold yet and returns how many went in. */
    suspend fun addSongs(id: String, songs: List<Song>): Int = mutate { current ->
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val existing = current.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
            ?: return@mutate current to 0
        val merged = mergeImportedSongs(existing, songs)
        val added = merged.songs.size - existing.songs.size
        if (added <= 0) current to 0
        else current.map { if (it.id == existing.id) merged else it } to added
    }

    suspend fun removeSong(id: String, videoId: String) {
        mutate { current ->
            val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
            val existing = current.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
                ?: return@mutate current to Unit
            val updated = current.map {
                if (it.id == existing.id) it.copy(songs = it.songs.filterNot { song -> song.videoId == videoId }) else it
            }
            updated to Unit
        }
    }

    /** Sets the custom cover; null goes back to the first track's artwork. */
    suspend fun setCover(id: String, coverUrl: String?) {
        mutate { current ->
            val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
            val existing = current.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
                ?: return@mutate current to Unit
            current.map { if (it.id == existing.id) it.copy(coverUrl = coverUrl) else it } to Unit
        }
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
    suspend fun importSnapshot(raw: String) {
        checkInitialized()
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
                            thumbnailUrl = song.optString("thumbnailUrl", "").takeIf { it.isNotBlank() && it != "null" },
                            durationText = song.optString("durationText", "").takeIf { it.isNotBlank() && it != "null" },
                            artistId = song.optString("artistId", "").takeIf { it.isNotBlank() && it != "null" },
                            albumId = song.optString("albumId", "").takeIf { it.isNotBlank() && it != "null" },
                            albumName = song.optString("albumName", "").takeIf { it.isNotBlank() && it != "null" },
                            isVideo = song.optBoolean("isVideo", false),
                        ))
                    }
                }.distinctBy { it.videoId }
                add(LocalPlaylist(
                    id = id,
                    title = title,
                    songs = songs,
                    coverUrl = obj.optString("coverUrl", "").takeIf { it.isNotBlank() && it != "null" },
                    youtubePlaylistId = obj.optString("youtubePlaylistId", "").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }.distinctBy { it.id }
        mutate { imported to Unit }
    }

    /** Keeps the device's Favorites collection reachable through the normal playlist UI. */
    suspend fun syncFavorites(songs: List<Song>) {
        checkInitialized()
        val favorite = LocalPlaylist(
            id = FAVORITES_PLAYLIST_ID,
            title = "Favorites",
            songs = songs.distinctBy { it.videoId },
        )
        mutate { current ->
            (listOf(favorite) + current.filterNot { it.id == FAVORITES_PLAYLIST_ID }) to Unit
        }
    }

    private suspend fun <R> mutate(
        transform: (List<LocalPlaylist>) -> Pair<List<LocalPlaylist>, R>,
    ): R {
        checkInitialized()
        return persistBeforePublish(
            mutex = mutationMutex,
            persist = {
                val current = _playlists.value
                val (updated, result) = transform(current)
                if (updated != current) withContext(Dispatchers.IO) { persist(updated, commit = true) }
                updated to result
            },
            publish = { _playlists.value = it },
        )
    }

    private fun checkInitialized() = check(this::prefs.isInitialized) {
        "Local playlists have not been initialized"
    }

    private fun persist(list: List<LocalPlaylist>, commit: Boolean = true) {
        if (!this::prefs.isInitialized) return
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
        if (commit) {
            @Suppress("UseKtx")
            check(prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).commit()) {
                "Could not save playlist on this device"
            }
        } else {
            prefs.edit { putString(KEY_PLAYLISTS, jsonArray.toString()) }
        }
    }
}
