package com.music.audify.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/**
 * A cover for a file on the device, as Coil sees it.
 *
 * Two shapes reach it: the legacy `content://media/external/audio/albumart/<id>`
 * URI MediaStore rows carry — which Android 10+ no longer serves for most
 * files, so every row read as "no artwork" — and [LocalArtFetcher.trackArtUrl],
 * for files found without a MediaStore row (the picked Local Music folder),
 * which used to have no cover URL at all.
 */
data class LocalArt(val raw: String) {
    val albumId: Long?
        get() = if (raw.startsWith(ALBUM_ART_PREFIX)) {
            raw.removePrefix(ALBUM_ART_PREFIX).substringBefore('/').toLongOrNull()
        } else {
            null
        }

    val trackUri: String?
        get() = if (raw.startsWith(TRACK_PREFIX)) raw.removePrefix(TRACK_PREFIX) else null

    companion object {
        const val ALBUM_ART_PREFIX = "content://media/external/audio/albumart/"
        const val TRACK_PREFIX = "audify-art:"

        fun parse(data: String): LocalArt? {
            val art = LocalArt(data)
            return if (art.albumId != null || art.trackUri != null) art else null
        }
    }
}

class LocalArtMapper : Mapper<String, LocalArt> {
    override fun map(data: String, options: Options): LocalArt? = LocalArt.parse(data)
}

class LocalArtKeyer : Keyer<LocalArt> {
    override fun key(data: LocalArt, options: Options): String? = data.raw
}

/**
 * Reads the picture the file itself carries — the tag's embedded cover — and
 * only then asks MediaStore for what it made of it. Registered in
 * [AudifyApplication][com.music.audify.AudifyApplication]; anything that is
 * not a local-art URL falls straight through to Coil's own fetchers.
 */
class LocalArtFetcher private constructor(
    private val context: Context,
    private val art: LocalArt,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = withContext(Dispatchers.IO) { read() }
            ?: throw FileNotFoundException("No artwork for ${art.raw}")
        return SourceFetchResult(
            source = ImageSource(bytes.inputStream().source().buffer(), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    private fun read(): ByteArray? {
        art.albumId?.let { return fromAlbum(it) }
        art.trackUri?.let { return fromTrack(Uri.parse(it)) }
        return null
    }

    /** The first track of the album that yields a picture, then the legacy URI. */
    private fun fromAlbum(albumId: Long): ByteArray? {
        val resolver = context.contentResolver
        val ids = ArrayList<Long>()
        runCatching {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.ALBUM_ID} = ?",
                arrayOf(albumId.toString()),
                "${MediaStore.Audio.Media.TRACK} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && ids.size < MAX_ALBUM_TRIES) ids += cursor.getLong(0)
            }
        }
        for (id in ids) {
            fromTrack(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                ?.let { return it }
        }
        return runCatching {
            resolver.openInputStream(
                ContentUris.withAppendedId(Uri.parse(LocalArt.ALBUM_ART_PREFIX.trimEnd('/')), albumId),
            )?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun fromTrack(uri: Uri): ByteArray? {
        val embedded = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (uri.scheme == "file") {
                    retriever.setDataSource(uri.path)
                } else {
                    retriever.setDataSource(context, uri)
                }
                retriever.embeddedPicture
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (embedded != null && embedded.isNotEmpty()) return embedded

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            return runCatching {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(THUMB_PX, THUMB_PX), null)
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.toByteArray()
                }
            }.getOrNull()
        }
        return null
    }

    class Factory : Fetcher.Factory<LocalArt> {
        override fun create(data: LocalArt, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalArtFetcher(options.context, data)
    }

    companion object {
        private const val MAX_ALBUM_TRIES = 8
        private const val THUMB_PX = 512

        /** The cover URL for a file that has no MediaStore album behind it. */
        fun trackArtUrl(uri: String): String = LocalArt.TRACK_PREFIX + uri
    }
}