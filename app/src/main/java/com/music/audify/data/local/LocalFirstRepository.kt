// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.music.audify.auth.GoogleAccountSession
import com.music.audify.data.local.LocalDatabase.FavoriteRow
import com.music.audify.data.local.LocalDatabase.FavoriteAccountSyncRow
import com.music.audify.data.local.LocalDatabase.HistoryRow
import com.music.audify.data.local.LocalDatabase.PlaylistAccountSyncRow
import com.music.audify.data.local.LocalDatabase.PlaylistRow
import com.music.audify.data.local.LocalDatabase.PlaylistSongRow
import com.music.audify.data.local.LocalDatabase.SongRow
import com.music.audify.data.local.LocalDatabase.SyncOperationRow
import com.music.audify.data.model.LikeStatus
import com.music.audify.data.model.Song
import com.music.audify.data.spotify.LocalPlaylistStore
import com.music.audify.data.stats.LocalLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Room-backed catalog and durable, account-scoped synchronization outbox. */
object LocalFirstRepository {
    private const val DATABASE_NAME = "audify-library.db"
    private const val LEGACY_IMPORTED = "legacy_json_imported_v1"
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS migration_state (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
        }
    }
    private val initMutex = Mutex()
    private val mirrorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var mirroringStarted = false
    @Volatile private var database: LocalDatabase? = null
    @Volatile private var contextForWork: Context? = null
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    private val _conflictCount = MutableStateFlow(0)
    val conflictCount: StateFlow<Int> = _conflictCount.asStateFlow()

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (database != null) return@withLock
            contextForWork = context.applicationContext
            val db = Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
            try {
                if (db.dao().migrationValue(LEGACY_IMPORTED) != "complete") {
                    db.runInTransaction {
                        val dao = db.dao()
                        val favorites = LocalLibraryStore.exportFavorites()
                        val history = LocalLibraryStore.exportHistory()
                        val playlists = LocalPlaylistStore.playlists.value
                        val songs = (favorites + history + playlists.flatMap { it.songs }).distinctBy { it.videoId }
                        if (songs.isNotEmpty()) dao.putSongs(songs.map(Song::toRow))
                        dao.clearFavorites()
                        if (favorites.isNotEmpty()) dao.putFavorites(favorites.distinctBy { it.videoId }
                            .map { FavoriteRow(it.videoId, System.currentTimeMillis()) })
                        dao.clearHistory()
                        if (history.isNotEmpty()) dao.putHistory(history.distinctBy { it.videoId }.mapIndexed { index, song ->
                            HistoryRow(song.videoId, System.currentTimeMillis() - index, 1)
                        })
                        playlists.forEach { playlist ->
                            val now = System.currentTimeMillis()
                            dao.putPlaylist(PlaylistRow(playlist.id, playlist.title, now, now, 1L))
                            dao.clearPlaylistSongs(playlist.id)
                            val rows = playlist.songs.distinctBy { it.videoId }
                                .mapIndexed { position, song -> PlaylistSongRow(playlist.id, song.videoId, position) }
                            if (rows.isNotEmpty()) dao.putPlaylistSongs(rows)
                        }
                        dao.putMigrationState(LocalDatabase.MigrationStateRow(LEGACY_IMPORTED, "complete"))
                    }
                }
                database = db
            } catch (failure: Throwable) {
                db.close()
                throw failure
            }
            refreshCounts()
            startLegacyStoreMirroring()
        }
    }

    /**
     * Compatibility bridge while callers are migrated from the old synchronous
     * JSON-store APIs. Room receives every committed local snapshot; this bridge
     * can be removed once the UI and playback writers use repository methods.
     */
    private fun startLegacyStoreMirroring() {
        if (mirroringStarted) return
        mirroringStarted = true
        mirrorScope.launch {
            LocalLibraryStore.favorites.collect { songs ->
                val dao = database?.dao() ?: return@collect
                runCatching {
                    val now = System.currentTimeMillis()
                    database?.runInTransaction {
                        if (songs.isNotEmpty()) dao.putSongs(songs.distinctBy { it.videoId }.map(Song::toRow))
                        dao.clearFavorites()
                        if (songs.isNotEmpty()) dao.putFavorites(songs.distinctBy { it.videoId }
                            .map { FavoriteRow(it.videoId, now) })
                    }
                }
            }
        }
        mirrorScope.launch {
            LocalLibraryStore.history.collect { songs ->
                val dao = database?.dao() ?: return@collect
                runCatching {
                    val now = System.currentTimeMillis()
                    database?.runInTransaction {
                        if (songs.isNotEmpty()) dao.putSongs(songs.distinctBy { it.videoId }.map(Song::toRow))
                        dao.clearHistory()
                        if (songs.isNotEmpty()) dao.putHistory(songs.distinctBy { it.videoId }.mapIndexed { index, song ->
                            HistoryRow(song.videoId, now - index, 1)
                        })
                    }
                }
            }
        }
        mirrorScope.launch {
            LocalPlaylistStore.playlists.collect { playlists ->
                val dao = database?.dao() ?: return@collect
                runCatching {
                    val now = System.currentTimeMillis()
                    database?.runInTransaction {
                        playlists.filter { it.id != LocalPlaylistStore.FAVORITES_PLAYLIST_ID }.forEach { playlist ->
                            val old = dao.playlist(playlist.id)
                            val ids = playlist.songs.map { it.videoId }.filter(String::isNotBlank).distinct()
                            val oldIds = dao.playlistSongs(playlist.id).sortedBy { it.position }.map { it.videoId }
                            val changed = old == null || old.title != playlist.title || oldIds != ids
                            val revision = (old?.localRevision ?: 0L) + if (changed) 1L else 0L
                            dao.putPlaylist(PlaylistRow(playlist.id, playlist.title, old?.createdAt ?: now, now, revision))
                            dao.clearPlaylistSongs(playlist.id)
                            val rows = ids.mapIndexed { position, videoId -> PlaylistSongRow(playlist.id, videoId, position) }
                            if (rows.isNotEmpty()) dao.putPlaylistSongs(rows)
                            val songs = playlist.songs.distinctBy { it.videoId }
                            if (songs.isNotEmpty()) dao.putSongs(songs.map(Song::toRow))
                        }
                    }
                }
            }
        }
    }

    suspend fun enqueueFavorite(
        accountId: String,
        profileId: String,
        videoId: String,
        status: LikeStatus,
        song: Song? = null,
    ) = withContext(Dispatchers.IO) {
        val dao = requireDatabase().dao()
        val now = System.currentTimeMillis()
        song?.let { dao.putSongs(listOf(it.toRow())) }
        if (status == LikeStatus.LIKE) dao.putFavorites(listOf(FavoriteRow(videoId, now)))
        else dao.deleteFavorite(videoId)
        dao.enqueue(SyncOperationRow(
            0, "favorite:$accountId:$profileId:$videoId", accountId, profileId,
            TYPE_FAVORITE, videoId, JSONObject().put("status", status.name).toString(),
            "pending", 0, 0L, null, now, now,
        ))
        refreshCounts()
        contextForWork?.let(SyncWorkScheduler::enqueue)
    }

    suspend fun enqueueLibrarySnapshot(session: GoogleAccountSession, profileId: String) = withContext(Dispatchers.IO) {
        val dao = requireDatabase().dao()
        val favorites = LocalLibraryStore.favorites.value
        val playlists = LocalPlaylistStore.playlists.value
        val allSongs = (favorites + playlists.flatMap { it.songs }).distinctBy { it.videoId }
        if (allSongs.isNotEmpty()) dao.putSongs(allSongs.map(Song::toRow))
        val now = System.currentTimeMillis()
        favorites.forEach { song ->
            dao.putFavorites(listOf(FavoriteRow(song.videoId, now)))
            val synced = dao.favoriteSync(song.videoId, session.accountId, profileId)
            if (synced?.status != LikeStatus.LIKE.name && synced?.status != "conflict") {
                dao.enqueue(SyncOperationRow(
                    0, "favorite:${session.accountId}:$profileId:${song.videoId}", session.accountId, profileId,
                    TYPE_FAVORITE, song.videoId, JSONObject().put("status", LikeStatus.LIKE.name).toString(),
                    "pending", 0, 0L, null, now, now,
                ))
            }
        }
        playlists.filter { it.id != LocalPlaylistStore.FAVORITES_PLAYLIST_ID }.forEach { playlist ->
            val existing = dao.playlist(playlist.id)
            val currentIds = playlist.songs.map { it.videoId }.filter(String::isNotBlank).distinct()
            val previousIds = dao.playlistSongs(playlist.id).sortedBy { it.position }.map { it.videoId }
            val changed = existing == null || existing.title != playlist.title || previousIds != currentIds
            val revision = (existing?.localRevision ?: 0L) + if (changed) 1L else 0L
            dao.putPlaylist(PlaylistRow(playlist.id, playlist.title, existing?.createdAt ?: now, now, revision))
            dao.clearPlaylistSongs(playlist.id)
            val songRows = currentIds.mapIndexed { position, videoId -> PlaylistSongRow(playlist.id, videoId, position) }
            if (songRows.isNotEmpty()) dao.putPlaylistSongs(songRows)
            val payload = JSONObject().put("title", playlist.title)
                .put("videoIds", JSONArray(currentIds)).put("revision", revision)
            val synced = dao.playlistSync(playlist.id, session.accountId, profileId)
            val alreadySynced = synced?.status == "synced" && synced.syncedTitle == playlist.title &&
                synced.syncedVideoIdsJson == JSONArray(currentIds).toString()
            if (!alreadySynced && synced?.status != "conflict") {
                dao.enqueue(SyncOperationRow(
                    0, "playlist:${session.accountId}:$profileId:${playlist.id}", session.accountId, profileId,
                    TYPE_PLAYLIST, playlist.id, payload.toString(), "pending", 0, 0L, null, now, now,
                ))
            }
            if (playlist.youtubePlaylistId != null &&
                dao.playlistSync(playlist.id, session.accountId, profileId) == null
            ) {
                // Older builds stored one unscoped remote ID. Never guess which
                // account owns it and accidentally create a duplicate.
                dao.putPlaylistSync(PlaylistAccountSyncRow(
                    playlist.id, session.accountId, profileId, null, "[]", playlist.title,
                    0L, "legacy_unscoped", "Legacy remote playlist ID was not associated with an account", now,
                ))
            }
        }
        refreshCounts()
        contextForWork?.let(SyncWorkScheduler::enqueue)
    }

    suspend fun dueOperations(accountId: String, profileId: String) =
        requireDatabase().dao().dueOperations(accountId, profileId, System.currentTimeMillis())

    suspend fun hasRetryingOperations(accountId: String, profileId: String) =
        requireDatabase().dao().retryingCount(accountId, profileId) > 0

    suspend fun playlistSync(playlistId: String, accountId: String, profileId: String) =
        requireDatabase().dao().playlistSync(playlistId, accountId, profileId)

    suspend fun savePlaylistSync(row: PlaylistAccountSyncRow) {
        requireDatabase().dao().putPlaylistSync(row)
    }

    suspend fun markPlaylistConflict(playlistId: String, accountId: String, profileId: String, message: String) {
        val dao = requireDatabase().dao()
        val row = dao.playlistSync(playlistId, accountId, profileId) ?: return
        dao.putPlaylistSync(PlaylistAccountSyncRow(
            row.playlistId, row.accountId, row.profileId, row.youtubePlaylistId,
            row.syncedVideoIdsJson, row.syncedTitle, row.syncedRevision,
            "conflict", message.take(500), System.currentTimeMillis(),
        ))
    }

    suspend fun saveFavoriteSync(videoId: String, accountId: String, profileId: String, status: LikeStatus) {
        requireDatabase().dao().putFavoriteSync(
            FavoriteAccountSyncRow(videoId, accountId, profileId, status.name, System.currentTimeMillis()),
        )
    }

    suspend fun markFavoriteConflict(videoId: String, accountId: String, profileId: String, message: String) {
        requireDatabase().dao().putFavoriteSync(
            FavoriteAccountSyncRow(videoId, accountId, profileId, "conflict", System.currentTimeMillis()),
        )
    }

    suspend fun favoriteSync(videoId: String, accountId: String, profileId: String) =
        requireDatabase().dao().favoriteSync(videoId, accountId, profileId)

    suspend fun syncStatusText(accountId: String, profileId: String): String = withContext(Dispatchers.IO) {
        val dao = requireDatabase().dao()
        val lines = mutableListOf<String>()
        dao.favoriteSyncRows(accountId, profileId).forEach { row ->
            val title = dao.song(row.videoId)?.title?.takeIf(String::isNotBlank) ?: row.videoId
            lines += "Favorite · $title — ${row.status}"
        }
        dao.playlistSyncRows(accountId, profileId).forEach { row ->
            val title = dao.playlist(row.playlistId)?.title ?: row.playlistId
            lines += "Playlist · $title — ${row.status}" +
                (row.lastError?.takeIf(String::isNotBlank)?.let { " ($it)" } ?: "")
        }
        dao.syncOperations(accountId, profileId).filter { it.state in setOf("pending", "retrying", "conflict") }
            .forEach { operation ->
                val title = when (operation.type) {
                    TYPE_PLAYLIST -> dao.playlist(operation.entityId)?.title ?: operation.entityId
                    else -> dao.song(operation.entityId)?.title ?: operation.entityId
                }
                lines += "${operation.type} · $title — ${operation.state}" +
                    (operation.lastError?.takeIf(String::isNotBlank)?.let { " ($it)" } ?: "")
            }
        if (lines.isEmpty()) "No YouTube Music sync records for this account/profile."
        else lines.distinct().take(40).joinToString("\n")
    }

    suspend fun complete(operation: SyncOperationRow) {
        requireDatabase().dao().deleteOperationIfUnchanged(
            operation.id, operation.payloadJson, operation.updatedAt,
        )
        refreshCounts()
    }

    suspend fun retry(operation: SyncOperationRow, failure: Throwable) {
        val attempts = operation.attempts + 1
        val delay = (30_000L * (1L shl attempts.coerceAtMost(8))).coerceAtMost(6 * 60 * 60 * 1000L)
        requireDatabase().dao().updateOperationIfUnchanged(
            operation.id, operation.payloadJson, operation.updatedAt, "retrying", attempts, System.currentTimeMillis() + delay,
            failure.message?.take(500), System.currentTimeMillis(),
        )
        refreshCounts()
    }

    suspend fun conflict(operation: SyncOperationRow, message: String) {
        requireDatabase().dao().updateOperationIfUnchanged(
            operation.id, operation.payloadJson, operation.updatedAt, "conflict", operation.attempts, Long.MAX_VALUE,
            message.take(500), System.currentTimeMillis(),
        )
        refreshCounts()
    }

    suspend fun resetDueRetries() {
        requireDatabase().dao().resetRetries(System.currentTimeMillis())
        refreshCounts()
    }

    private fun refreshCounts() {
        val dao = database?.dao() ?: return
        _pendingCount.value = dao.pendingCount()
        _conflictCount.value = dao.conflictCount()
    }

    private fun requireDatabase(): LocalDatabase = checkNotNull(database) {
        "LocalFirstRepository must be initialized before use"
    }

    private const val TYPE_FAVORITE = "favorite"
    private const val TYPE_PLAYLIST = "playlist"
}

private fun Song.toRow() = SongRow(
    videoId, title, artist, thumbnailUrl, durationText, artistId,
    JSONArray().apply {
        artists.forEach { artistRef ->
            put(JSONObject().put("name", artistRef.name).put("browseId", artistRef.browseId))
        }
    }.toString(),
    albumId, albumName, isVideo, isVideoOrigin, setVideoId,
)
