// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.stats

import android.content.Context
import android.net.Uri
import com.music.audify.BuildConfig
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.SearchHistory
import com.music.audify.data.model.Song
import com.music.audify.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * The whole of what this app knows about you, as one JSON file you own.
 *
 * Two errands, and they are the same errand: moving to a new phone, and being
 * able to see what is being kept. Everything Replay counts lives only on this
 * device, which is the point of it — and the flip side of that is that a factory
 * reset takes it with no way back, so there has to be a way out.
 *
 * ## What is in it
 *
 * Every preference except credentials (see [AppSettings.exportPrefs]), and every
 * month of listening. Not the audio cache, the downloads or the artwork: those
 * are megabytes that re-fetch themselves, and a backup that is mostly cache is
 * one nobody keeps.
 *
 * ## Typed values rather than a JSON object
 *
 * Preferences come off Android as `Map<String, Any?>` and go back the same way,
 * where the *type* decides which `put` is called. Written as plain JSON, `0.5`
 * comes back a Double and lands in a Float preference as a class-cast crash the
 * first time it is read — weeks later, in a settings screen, with nothing
 * pointing at the import. So each value carries its type and is parsed back into
 * exactly the type it left as, and anything unrecognised is skipped rather than
 * guessed at.
 */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** A suggested filename, dated so successive exports don't collide. */
    fun suggestedName(): String =
        "audify-backup-${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )}.json"

    /** Writes a private, atomic snapshot for scheduled recovery. */
    suspend fun createAutomatic(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Keep scheduled snapshots on this install only. The user-owned
            // export is the portable copy; these recovery snapshots must not
            // silently enter Android cloud backup or device-transfer payloads.
            val directory = File(context.noBackupFilesDir, "automatic_backups")
            check(directory.exists() || directory.mkdirs()) { "Could not create backup directory" }
            val file = buildBackupFile()
            val bytes = json.encodeToString(BackupFile.serializer(), file).toByteArray()
            validateBackupSize(bytes.size)
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.now())
            val target = File(directory, "audify-auto-$stamp.json")
            writeBackupAtomically(directory, target.name, bytes)
            // Keep the newest five complete snapshots; never delete before the new one is safe.
            directory.listFiles { candidate -> candidate.name.endsWith(".json") }
                .orEmpty().sortedByDescending { it.lastModified() }.drop(5).forEach { it.delete() }
            context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST_BACKUP_AT, System.currentTimeMillis()).commit()
            target
        }
    }

    fun lastAutomaticBackupAt(context: Context): Long = context
        .getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getLong(LAST_BACKUP_AT, 0L)

    fun lastExternalBackupAt(context: Context): Long = context
        .getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getLong(LAST_EXTERNAL_BACKUP_AT, 0L)

    /** Parses and validates a selected backup without mutating app data. */
    suspend fun preview(context: Context, source: Uri): Result<Preview> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use(::readBoundedBackup)
                ?: error("Couldn't open that file")
            val file = decodeAndValidate(text)
            Preview(
                songs = file.favorites.distinctBy { it.videoId }.size,
                history = file.history.distinctBy { it.videoId }.size,
                playlists = if (file.localPlaylists.isBlank()) 0 else validatedPlaylistCount(file.localPlaylists),
                months = file.listening.size,
                settings = file.settings.size,
                from = file.versionName,
                at = file.exportedAt,
            )
        }
    }

    data class Preview(
        val songs: Int, val history: Int, val playlists: Int, val months: Int,
        val settings: Int, val from: String, val at: String,
    )

    /**
     * Writes a backup to [target], a document the user picked.
     *
     * Through the content resolver rather than a [java.io.File] because the
     * destination is wherever they chose — Drive, a USB stick, a folder this app
     * has no path to and no permission for. The picker grants access to that one
     * document and nothing else, which is the correct amount.
     */
    suspend fun exportTo(context: Context, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val file = buildBackupFile()
            val text = json.encodeToString(BackupFile.serializer(), file)
            validateBackupSize(text.toByteArray().size)
            context.contentResolver.openOutputStream(target, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open that file for writing")
            check(context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST_EXTERNAL_BACKUP_AT, System.currentTimeMillis()).commit()) {
                "Backup was written, but its reminder timestamp could not be saved"
            }
            file.listening.size
        }
    }

    /**
     * Reads [source] and replaces this device's settings and listening with it.
     *
     * Validated before anything is written: a file that isn't one of ours, or is
     * from a schema this build can't read, is refused whole. A half-applied
     * import is worse than a refused one — it leaves settings from two devices
     * mixed together with nothing to say which came from where.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)
                ?.use(::readBoundedBackup)
                ?: error("Couldn't open that file")
            val file = decodeAndValidate(text)

            // Keep a rollback image until every store has accepted the incoming
            // snapshot. These stores are separate files, so this is a
            // compensating transaction rather than a cross-file atomic commit.
            val oldFavorites = LocalLibraryStore.exportFavorites()
            val oldHistory = LocalLibraryStore.exportHistory()
            val oldPlaylists = LocalPlaylistStore.exportSnapshot()
            val oldListening = ListeningStats.exportAll()
            val oldSettings = AppSettings.exportPrefs()
            try {
                LocalLibraryStore.importLibrary(
                    file.favorites.map(BackupSong::toSong),
                    file.history.map(BackupSong::toSong),
                )
                if (file.localPlaylists.isNotBlank()) LocalPlaylistStore.importSnapshot(file.localPlaylists)
                LocalPlaylistStore.syncFavorites(LocalLibraryStore.favorites.value)
                ListeningStats.importAll(file.listening)
                AppSettings.importPrefs(file.settings.mapValues { it.value.decoded() })
                SearchHistory.reload()
            } catch (restoreFailure: Throwable) {
                runCatching { LocalLibraryStore.importLibrary(oldFavorites, oldHistory) }
                runCatching { LocalPlaylistStore.importSnapshot(oldPlaylists) }
                runCatching { LocalPlaylistStore.syncFavorites(oldFavorites) }
                runCatching { ListeningStats.importAll(oldListening) }
                runCatching { AppSettings.importPrefs(oldSettings) }
                runCatching { SearchHistory.reload() }
                throw restoreFailure
            }
            Summary(
                months = file.listening.size,
                settings = file.settings.size,
                from = file.versionName,
                at = file.exportedAt,
            )
        }
    }

    /** What an import turned out to contain, for the line shown afterwards. */
    data class Summary(
        val months: Int,
        val settings: Int,
        val from: String,
        val at: String,
    )

    private const val APP_TAG = "audify"
    private const val BACKUP_PREFS = "audify_backup_state"
    private const val LAST_BACKUP_AT = "last_automatic_backup_at"
    private const val LAST_EXTERNAL_BACKUP_AT = "last_external_backup_at"
    private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

    internal fun validateBackupSize(size: Int, maxBytes: Int = MAX_BACKUP_BYTES) {
        require(size in 0..maxBytes) { "Backup exceeds the ${maxBytes / (1024 * 1024)} MB restore limit" }
    }

    /** Read at most the accepted backup size, including on the restore path. */
    internal fun readBoundedBackup(input: InputStream, maxBytes: Int = MAX_BACKUP_BYTES): String {
        require(maxBytes > 0) { "Backup size limit must be positive" }
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= maxBytes) { "Backup file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().decodeToString()
    }

    /** Finalize a private snapshot only after its complete contents reach disk. */
    internal fun writeBackupAtomically(directory: File, targetName: String, bytes: ByteArray): File {
        require(targetName.isNotBlank() && '/' !in targetName && '\\' !in targetName)
        check(directory.exists() || directory.mkdirs()) { "Could not create backup directory" }
        val target = File(directory, targetName)
        val temporary = File(directory, "$targetName.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(temporary.renameTo(target)) { "Could not finalize automatic backup" }
            return target
        } finally {
            temporary.delete()
        }
    }

    /**
     * Bump when the shape below stops being readable by an older build. A file
     * from a *newer* schema is refused rather than partially read; one from an
     * older schema is read as-is, since every field has a default.
     */
    private const val SCHEMA_VERSION = 2

    private suspend fun buildBackupFile() = BackupFile(
        versionName = BuildConfig.VERSION_NAME,
        exportedAt = Instant.now().toString(),
        settings = AppSettings.exportPrefs().mapNotNull { (key, value) ->
            PrefValue.of(value)?.let { key to it }
        }.toMap(),
        listening = ListeningStats.exportAll(),
        favorites = LocalLibraryStore.exportFavorites().distinctBy { it.videoId }.map(BackupSong::from),
        history = LocalLibraryStore.exportHistory().distinctBy { it.videoId }.map(BackupSong::from),
        localPlaylists = LocalPlaylistStore.exportSnapshot(),
    )

    private fun decodeAndValidate(text: String): BackupFile {
        require(text.toByteArray().size <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { error("That doesn't look like an Audify backup") }
        require(file.app == APP_TAG) { "That backup is from another app" }
        require(file.version in 1..SCHEMA_VERSION) { "That backup version isn't supported" }
        require(file.favorites.size <= 100_000 && file.history.size <= 100_000) { "Backup contains too many songs" }
        if (file.localPlaylists.isNotBlank()) validatedPlaylistCount(file.localPlaylists)
        return file
    }

    private fun validatedPlaylistCount(raw: String): Int {
        val array = org.json.JSONArray(raw)
        require(array.length() <= 10_000) { "Backup contains too many playlists" }
        val ids = HashSet<String>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.getString("id")
            require(id.isNotBlank() && item.getString("title").isNotBlank())
            ids.add(id) // Repeated IDs are merged during restore, not discarded here.
            val songs = item.getJSONArray("songs")
            require(songs.length() <= 100_000)
        }
        return ids.size
    }

    @Serializable
    data class BackupFile(
        val app: String = APP_TAG,
        val version: Int = SCHEMA_VERSION,
        /** Which build wrote it — for the reader, not for any logic here. */
        val versionName: String = "",
        val exportedAt: String = "",
        val settings: Map<String, PrefValue> = emptyMap(),
        val listening: List<StoredBucket> = emptyList(),
        val favorites: List<BackupSong> = emptyList(),
        val history: List<BackupSong> = emptyList(),
        val localPlaylists: String = "",
    )

    @Serializable
    data class BackupSong(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String? = null,
        val durationText: String? = null,
        val artistId: String? = null,
        val albumId: String? = null,
        val albumName: String? = null,
        val isVideo: Boolean = false,
    ) {
        fun toSong() = Song(videoId, title, artist, thumbnailUrl, durationText, artistId,
            albumId = albumId, albumName = albumName, isVideo = isVideo)

        companion object {
            fun from(song: Song) = BackupSong(song.videoId, song.title, song.artist,
                song.thumbnailUrl, song.durationText, song.artistId, song.albumId,
                song.albumName, song.isVideo)
        }
    }

    /** One preference, with the type it has to be restored as. */
    @Serializable
    data class PrefValue(
        val type: String,
        val value: String? = null,
        val values: List<String> = emptyList(),
    ) {
        fun decoded(): Any? = when (type) {
            BOOLEAN -> value?.toBooleanStrictOrNull()
            INT -> value?.toIntOrNull()
            LONG -> value?.toLongOrNull()
            FLOAT -> value?.toFloatOrNull()
            STRING -> value
            STRING_SET -> values.toSet()
            else -> null
        }

        companion object {
            /**
             * Numbers go out as strings on purpose. JSON has one number type and
             * every parser picks its own Kotlin type back off it, which is
             * exactly the ambiguity this whole class exists to remove.
             */
            fun of(value: Any?): PrefValue? = when (value) {
                is Boolean -> PrefValue(BOOLEAN, value.toString())
                is Int -> PrefValue(INT, value.toString())
                is Long -> PrefValue(LONG, value.toString())
                is Float -> PrefValue(FLOAT, value.toString())
                is String -> PrefValue(STRING, value)
                is Set<*> -> PrefValue(STRING_SET, values = value.filterIsInstance<String>())
                else -> null
            }

            private const val BOOLEAN = "bool"
            private const val INT = "int"
            private const val LONG = "long"
            private const val FLOAT = "float"
            private const val STRING = "string"
            private const val STRING_SET = "stringSet"
        }
    }
}
