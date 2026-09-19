/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import com.sachit.music.db.MusicDatabase
import com.sachit.music.di.DownloadCache
import com.sachit.music.playback.DownloadUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The container/format of an exported audio file, decided from the stream's
 * MIME type.
 *
 * YouTube Music's audio tracks are progressive — one self-contained stream,
 * no separate init segment — so the bytes in the cache ARE a playable file;
 * they only need to be reassembled in order. AAC streams ride in an MP4
 * container (an .mp4/.m4a file), Opus streams in WebM.
 */
enum class AudioContainer(val extension: String, val mimeType: String) {
    MP4("mp4", "audio/mp4"),
    WEBM("webm", "audio/webm");

    companion object {
        fun fromMimeType(mimeType: String?): AudioContainer =
            when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
                "audio/webm", "video/webm" -> WEBM
                else -> MP4
            }
    }
}

/**
 * Reassembles downloaded songs into real, portable audio files.
 *
 * The in-app download is a Media3 `SimpleCache` — chunked, keyed blobs that
 * only ExoPlayer can read. That keeps offline playback fast, but it is not a
 * file a file manager can see. [exportAudio] bridges the two: it reads the
 * stored bytes back in order (straight from the cache when the song is fully
 * downloaded, otherwise from the network) and writes them to a single audio
 * file the user can share, save to Music, or copy off the device.
 *
 * No transcode: the stream is copied verbatim, so exports are as fast as a
 * file copy and lose nothing to re-encoding.
 */
@Singleton
class AudioExporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
    @DownloadCache private val downloadCache: androidx.media3.datasource.cache.Cache,
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(com.sachit.innertube.YouTube.proxy)
            .proxyAuthenticator { _, response ->
                com.sachit.innertube.YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
            .build()
    }

    /** Progress of an export, for a determinate progress UI. */
    data class Progress(val bytesWritten: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes > 0) (bytesWritten.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /** Why an export cannot proceed. */
    sealed class ExportError : Exception() {
        /** The song has never finished downloading, and the network path failed. */
        data object NotAvailable : ExportError() {
            private fun readResolve(): Any = NotAvailable
        }

        /** The stream metadata (container/length) is missing from the database. */
        data object UnknownFormat : ExportError() {
            private fun readResolve(): Any = UnknownFormat
        }
    }

    /**
     * Writes the song's audio to a real file and returns it.
     *
     * Reads the Media3 download cache when every byte is already stored, so a
     * downloaded song exports offline; otherwise resolves a fresh stream and
     * downloads it. The returned file lives in the app's cache — hand it to
     * [shareAudio] or [saveToMusic] to get it out of the sandbox.
     */
    suspend fun exportAudio(
        songId: String,
        title: String,
        artistName: String?,
        onProgress: (Progress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val format = database.format(songId).first()
            val container = AudioContainer.fromMimeType(format?.mimeType)

            cleanStaleExports()
            val target = File(exportDir(), safeFileName(title, artistName) + "." + container.extension)
            if (target.exists()) target.delete()

            val contentLength = format?.contentLength?.takeIf { it > 0L }
            val written = if (isFullyCached(songId, contentLength)) {
                writeFromCache(songId, target, contentLength, onProgress)
            } else {
                writeFromNetwork(songId, target, contentLength, onProgress)
            }

            if (written <= 0L) {
                target.delete()
                return@withContext Result.failure(ExportError.NotAvailable)
            }
            Result.success(target)
        } catch (e: ExportError) {
            Result.failure(e)
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Export failed for %s", songId)
            Result.failure(e)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Unexpected export failure for %s", songId)
            Result.failure(e)
        }
    }

    /** The directory exported files are staged in before being shared or saved. */
    fun exportDir(): File =
        File(context.cacheDir, "audio-exports").apply { mkdirs() }

    /**
     * Deletes staged exports older than a day. Sharing hands a copy to the
     * receiving app, so the staged file is only needed while the share sheet
     * is open — anything left behind a day later is litter.
     */
    private fun cleanStaleExports() {
        val cutoff = System.currentTimeMillis() - STALE_EXPORT_MS
        exportDir().listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun isFullyCached(songId: String, contentLength: Long?): Boolean {
        // Without a known length there is no cheap "is it all there" check; fall
        // back to the download manager's own state, which is authoritative for
        // completed downloads.
        if (contentLength == null) {
            val download = downloadUtil.downloads.value[songId]
            return download?.state == Download.STATE_COMPLETED
        }
        return runCatching { downloadCache.isCached(songId, 0, contentLength) }.getOrDefault(false)
    }

    /** Streams the cached chunks back in order into [target]. */
    private fun writeFromCache(
        songId: String,
        target: File,
        contentLength: Long?,
        onProgress: (Progress) -> Unit,
    ): Long {
        val source: DataSource = CacheDataSource.Factory()
            .setCache(downloadCache)
            // Cache-only: fail loudly rather than silently hitting the network.
            .setUpstreamDataSourceFactory(null)
            .createDataSource()
        val spec = DataSpec.Builder()
            .setUri(songId.toDataUri())
            .setKey(songId)
            .setPosition(0)
            .setLength(contentLength ?: C.LENGTH_UNSET.toLong())
            .build()
        source.open(spec)
        try {
            return copyStream(
                source,
                target,
                if (spec.length != C.LENGTH_UNSET.toLong()) spec.length else contentLength ?: -1L,
                onProgress,
            )
        } finally {
            source.close()
        }
    }

    /** Resolves a fresh stream and writes it to [target]. */
    private suspend fun writeFromNetwork(
        songId: String,
        target: File,
        contentLength: Long?,
        onProgress: (Progress) -> Unit,
    ): Long {
        val playbackData = com.sachit.music.utils.InnerTubeXPlayer
            .playerResponseForPlayback(
                videoId = songId,
                audioQuality = com.sachit.music.constants.AudioQuality.HIGH,
                connectivityManager = context.getSystemService(android.net.ConnectivityManager::class.java),
            )
            .getOrElse { throw ExportError.NotAvailable }

        val request = okhttp3.Request.Builder()
            .url(playbackData.streamUrl)
            .apply {
                playbackData.streamHeaders.forEach { (name, value) -> header(name, value) }
            }
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Stream returned HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty stream body")
            target.outputStream().use { out ->
                var written = 0L
                val total = contentLength ?: body.contentLength().takeIf { it > 0 } ?: -1L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(Progress(written, total))
                    }
                }
                out.flush()
                return written
            }
        }
    }

    private fun copyStream(
        source: DataSource,
        target: File,
        total: Long,
        onProgress: (Progress) -> Unit,
    ): Long {
        var written = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
        target.outputStream().use { out ->
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, read)
                written += read
                onProgress(Progress(written, total))
            }
            out.flush()
        }
        return written
    }

    private fun String.toDataUri(): Uri =
        Uri.parse("sachit-music://export/$this")

    /**
     * Shares [file] through the system share sheet as an audio attachment.
     * Returns false when no share target accepted the file.
     */
    fun shareAudio(context: Context, file: File, mimeType: String): Boolean =
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, file.name).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        }.getOrDefault(false)

    /**
     * Copies [file] into the public Music collection (Music/Audify) so any
     * file manager can reach it. Returns the destination [Uri] for MediaStore
     * copies on API 29+, or a file Uri for the app-scoped fallback below Q.
     */
    suspend fun saveToMusic(context: Context, file: File, mimeType: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Audify")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val destUri = resolver.insert(collection, values)
                        ?: throw IOException("MediaStore rejected the new audio entry")
                    resolver.openOutputStream(destUri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: throw IOException("Could not open MediaStore output stream")
                    resolver.update(destUri, android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                    destUri
                } else {
                    // Pre-Q scoped storage: park it in the app's external music
                    // folder, which is readable over USB/FTP without permissions.
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Audify")
                        .apply { mkdirs() }
                    val dest = File(dir, file.name)
                    file.copyTo(dest, overwrite = true)
                    FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", dest)
                }
            }.onFailure { e ->
                Timber.tag(TAG).w(e, "saveToMusic failed for %s", file.name)
            }
        }

    companion object {
        private const val TAG = "AudioExporter"
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
        private const val STALE_EXPORT_MS = 24L * 60 * 60 * 1000

        /** The MIME type of an exported file, decided from its extension. */
        fun mimeTypeForFile(file: File): String =
            if (file.extension.equals(WEBM_EXTENSION, ignoreCase = true)) {
                AudioContainer.WEBM.mimeType
            } else {
                AudioContainer.MP4.mimeType
            }

        private const val WEBM_EXTENSION = "webm"

        /**
         * A filesystem-safe "Artist - Title" name: separators, reserved
         * Windows characters and control characters are folded to underscores,
         * trailing dots are dropped (they hide extensions in some file
         * managers), and the result is capped so the container's ".mp4" never
         * gets truncated by path-length limits.
         */
        fun safeFileName(title: String, artistName: String?): String {
            val raw = listOfNotNull(artistName?.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() })
                .joinToString(" - ")
                .ifBlank { "Unknown song" }
            val cleaned = raw
                .replace(WHITESPACE_RUN, " ")
                .replace(RESERVED_CHARS, "_")
                .trim()
                .trimEnd('.')
            return cleaned.take(MAX_NAME_LENGTH).ifBlank { "Unknown song" }
        }

        private val RESERVED_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")
        private val WHITESPACE_RUN = Regex("\\s+")
        private const val MAX_NAME_LENGTH = 120
    }
}
