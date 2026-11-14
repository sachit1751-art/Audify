@file:OptIn(UnstableApi::class)

package com.music.audify.ui.replay

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.audify.data.Http
import com.music.audify.data.innertube.StreamResolver
import com.music.audify.data.model.Song
import com.music.audify.playback.ChunkedDataSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Short-lived disk store for the Replay's backsound clips.
 *
 * A clip used to be resolved and streamed live at the moment its turn came, so
 * whether the music was there depended on how fast that one fetch happened to
 * be: sometimes it started with the story, sometimes late, sometimes never.
 * Here each track is resolved and stored in full ahead of its turn, then played
 * from disk. Nothing in it is meant to last: a clip is removed as soon as the
 * Replay moves on to the next song, and whatever is left goes when the Replay
 * closes (or, after a crash, the next time the store is opened).
 */
object ReplayClipCache {

    private const val DIR_NAME = "replay_backsound"

    /** A track bigger than this is streamed as before rather than stored. */
    private const val MAX_CLIP_BYTES = 24L * 1024 * 1024

    /** Upper bound per range; [ChunkedDataSource] narrows it further per client. */
    private const val CHUNK_BYTES = 1L * 1024 * 1024

    /**
     * How long a clip is waited for while it is being stored. Past this it
     * streams while the copy carries on, so the music starts instead of the
     * Replay sitting silent behind a download.
     */
    const val STORE_GRACE_MS = 4_000L

    /** Storing runs here, apart from the caller, so a stalled read can never hold the caller up. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Copies in progress, by videoId. */
    private val storing = ConcurrentHashMap<String, Deferred<Boolean>>()

    private val lock = Any()
    private var cache: SimpleCache? = null
    private var session = 0

    private fun open(context: Context): SimpleCache = synchronized(lock) {
        cache ?: run {
            val app = context.applicationContext
            val dir = File(app.cacheDir, DIR_NAME)
            val database = StandaloneDatabaseProvider(app)
            // Whatever an earlier run left behind (killed mid-Replay) is dropped.
            runCatching { SimpleCache.delete(dir, database) }
            SimpleCache(dir, NoOpCacheEvictor(), database).also { cache = it }
        }
    }

    /**
     * The same fetch the app's own player makes: bounded ranges, because
     * googlevideo paces an open-ended read down to playback speed, and the
     * headers of whichever client minted the URL, because a stream is refused
     * without them. A bare OkHttp read had neither, which is what left the
     * Replay silent.
     */
    private fun upstream(context: Context): DataSource.Factory =
        ResolvingDataSource.Factory(
            ChunkedDataSource.Factory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(Http.client)),
                CHUNK_BYTES,
            ),
        ) { spec ->
            if (spec.uri.scheme == "http" || spec.uri.scheme == "https") {
                spec.buildUpon()
                    .setHttpRequestHeaders(StreamResolver.mediaHeadersFor(spec.uri.toString()))
                    .build()
            } else {
                spec
            }
        }

    /** Marks a new Replay as the owner of the store. */
    fun openSession(): Int = synchronized(lock) { ++session }

    /**
     * Empties the store when the Replay that owns it closes. If a newer Replay
     * has opened in the meantime it is left alone, and that one clears up.
     * Disk work: call off the main thread.
     */
    fun closeSession(id: Int) {
        val current = synchronized(lock) { if (id == session) cache else null } ?: return
        storing.values.toList().forEach { it.cancel() }
        storing.clear()
        current.keys.toList().forEach { runCatching { current.removeResource(it) } }
    }

    /** Removes one song's stored copy. Disk work: call off the main thread. */
    fun evict(song: Song) {
        if (!song.localUri.isNullOrBlank() || song.videoId.isBlank()) return
        storing.remove(song.videoId)?.cancel()
        val current = synchronized(lock) { cache } ?: return
        runCatching { current.removeResource(song.videoId) }
    }

    /**
     * Reads what [prepare] stored. Never writes: a track that could not be
     * stored just streams, as it always did, and local files pass straight through.
     */
    fun playbackFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(open(context))
            .setUpstreamDataSourceFactory(upstream(context))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Gets [song] ready to play: resolves it and stores it on disk, keyed by
     * its videoId. Returns the URI to hand to the player, or null when the track
     * cannot be played at all. If storing fails the URL is still returned, so
     * the clip falls back to streaming instead of being skipped.
     */
    suspend fun prepare(
        context: Context,
        song: Song,
        storeGraceMs: Long = STORE_GRACE_MS,
    ): String? = withContext(Dispatchers.IO) {
        song.localUri?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val videoId = song.videoId.takeIf { it.isNotBlank() } ?: return@withContext null
        val url = try {
            StreamResolver.resolve(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            val store = open(context)
            if (!isStored(store, videoId)) {
                val copy = storing.getOrPut(videoId) {
                    scope.async {
                        val ok = download(context, store, videoId, url) { isActive }
                        if (!ok) runCatching { store.removeResource(videoId) }
                        ok
                    }.also { job -> job.invokeOnCompletion { storing.remove(videoId, job) } }
                }
                // Waited for only a moment: a clip not stored by then streams
                // while the copy carries on.
                withTimeoutOrNull(storeGraceMs) { copy.join() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Audify", "Replay backsound: could not store $videoId: ${e.message}")
        }
        url
    }

    private fun isStored(store: SimpleCache, key: String): Boolean = runCatching {
        val length = ContentMetadata.getContentLength(store.getContentMetadata(key))
        length > 0L && store.isCached(key, 0L, length)
    }.getOrDefault(false)

    private fun download(
        context: Context,
        store: SimpleCache,
        key: String,
        url: String,
        active: () -> Boolean,
    ): Boolean {
        val source = CacheDataSource.Factory()
            .setCache(store)
            .setUpstreamDataSourceFactory(upstream(context))
            .createDataSource()
        val spec = DataSpec.Builder().setUri(url).setKey(key).build()
        var aborted = false
        lateinit var writer: CacheWriter
        writer = CacheWriter(
            source,
            spec,
            null,
            CacheWriter.ProgressListener { _, bytesCached, _ ->
                if (bytesCached > MAX_CLIP_BYTES || !active()) {
                    aborted = true
                    writer.cancel()
                }
            },
        )
        val finished = try {
            writer.cache()
            true
        } catch (e: Exception) {
            false
        }
        return finished && !aborted
    }
}