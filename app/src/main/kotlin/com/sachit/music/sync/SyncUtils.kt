package com.sachit.music.sync

import androidx.datastore.preferences.core.Preferences
import com.sachit.music.db.entities.SongEntity
import com.sachit.innertube.models.SongItem
import com.sachit.music.utils.ArtistNameAliases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object SyncUtils {
    val podcastsFlow: Flow<List<String>> = flowOf(emptyList())

    suspend fun savePodcast(podcastId: String, saved: Boolean) {}
    suspend fun saveEpisode(songId: String, saved: Boolean, setVideoId: String?) {}
    suspend fun likeSong(song: SongEntity) {}
    suspend fun scheduleRemoveFromPlaylist(
        browseId: String,
        songId: String,
        playlistId: String,
        setVideoIdProvider: () -> String?,
    ) {}

    suspend fun addToPlaylist(
        browseId: String,
        playlistId: String,
        songId: String,
    ): Boolean = false

    suspend fun download(song: com.sachit.innertube.models.SongItem) {}

    suspend fun download(song: com.sachit.music.db.entities.SongEntity) {}

    val downloads: kotlinx.coroutines.flow.Flow<Map<String, Int>>
        get() = kotlinx.coroutines.flow.flowOf(emptyMap())

}
