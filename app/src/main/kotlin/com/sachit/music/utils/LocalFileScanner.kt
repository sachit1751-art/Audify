/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.sachit.music.db.MusicDatabase
import com.sachit.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans the device's MediaStore audio collection and syncs local files into
 * the app's song table with `isLocal = 1`.
 *
 * The scan is incremental: it compares existing local song IDs against the
 * MediaStore query and only inserts new files. A full re-scan can be forced
 * by deleting local songs first.
 */
@Singleton
class LocalFileScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    /** A single local audio file found in MediaStore. */
    data class LocalFile(
        val displayName: String,
        val artist: String?,
        val title: String?,
        val album: String?,
        val durationMs: Long,
        val uri: String,
        val dateAdded: Long,
    )

    /** Scan result: how many were new, how many already existed. */
    data class ScanResult(val inserted: Int, val skipped: Int, val total: Int)

    /**
     * Queries MediaStore for audio files and syncs new ones into the database.
     * Emits progress as it scans.
     */
    suspend fun scan(/* onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> } */): ScanResult =
        withContext(Dispatchers.IO) {
            val existingIds = database.existingLocalSongIds().toSet()
            val files = queryMediaStoreAudio()

            var inserted = 0
            var skipped = 0

            files.forEach { file ->
                val songId = makeLocalSongId(file.uri)
                if (songId in existingIds) {
                    skipped++
                } else {
                    val entity = makeSongEntity(file, songId)
                    database.insertLocalSong(entity)
                    inserted++
                }
            }

            ScanResult(inserted, skipped, files.size)
        }

    /**
     * Returns all local songs as a Flow, refreshed when the database changes.
     */
    fun localSongs(): Flow<List<com.sachit.music.db.entities.Song>> =
        database.localSongs()

    /**
     * Returns the count of local songs.
     */
    fun localSongsCount(): Flow<Int> = database.localSongsCount()

    // ---- Private helpers ----

    private fun queryMediaStoreAudio(): List<LocalFile> {
        val songs = mutableListOf<LocalFile>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val dateAddedIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx)
                val artist = cursor.getString(artistIdx)
                val title = cursor.getString(titleIdx)
                val album = cursor.getString(albumIdx)
                val durationMs = cursor.getLong(durationIdx)
                val path = cursor.getString(pathIdx)
                val dateAdded = cursor.getLong(dateAddedIdx)

                // Build a content URI for this file
                val uri = "local://audio/$id"

                songs.add(LocalFile(
                    displayName = name.orEmpty(),
                    artist = artist,
                    title = title,
                    album = album,
                    durationMs = durationMs,
                    uri = uri,
                    dateAdded = dateAdded,
                ))
            }
        }

        return songs
    }

    private fun makeLocalSongId(uri: String): String = "local:${stableHash(uri)}"

    private fun stableHash(s: String): String = java.util.zip.CRC32().apply { update(s.toByteArray()) }.value.toInt().toString(16)

    private fun makeSongEntity(file: LocalFile, songId: String): SongEntity {
        val durationSec = (file.durationMs / 1000).toInt().coerceAtLeast(1)
        val title = file.title?.takeIf { it.isNotBlank() } ?: file.displayName

        // Derive a stable local path-based ID if no URI is available
        val id = if (songId.startsWith("local:")) songId else songId

        return SongEntity(
            id = id,
            title = title,
            duration = durationSec,
            thumbnailUrl = null,
            albumId = file.album?.let { "local-album:${it.hashCode().toString(16)}" },
            albumName = file.album,
            explicit = false,
            liked = false,
            isLocal = true,
            dateDownload = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(file.dateAdded * 1000),
                ZoneId.systemDefault(),
            ),
            inLibrary = LocalDateTime.now(),
        )
    }
}
