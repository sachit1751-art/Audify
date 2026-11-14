package com.music.audify.data.canvas

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.music.audify.data.settings.AppSettings
import com.music.audify.playback.DynamicLruCacheEvictor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Disk cache for canvas clips — the looping video some releases publish
 * alongside a track, played over the cover art by
 * [CanvasArtworkPlayer][com.music.audify.ui.player.CanvasArtworkPlayer].
 *
 * Without this, that player fetched a clip straight off the network through
 * a bare OkHttp data source, repeating for as long as the track playing over
 * it does ([androidx.media3.common.Player.REPEAT_MODE_ONE]). ExoPlayer frees
 * a sample's buffer as soon as the renderer has consumed it — there is no
 * back buffer configured, nor should there be one sized for an entire clip —
 * so once a loop finishes, nothing of it is left in memory to loop back to,
 * and reaching position zero again means the data source is asked for those
 * bytes again. A five-second clip behind a four-minute track loops around
 * fifty times, which without this cache was fifty downloads of the same few
 * seconds of video rather than one: the "40MB for one song" and multi-
 * gigabyte-day reports trace to exactly this repeat, not to audio bitrate.
 *
 * Wrapping the upstream in [CacheDataSource] means only the first loop of a
 * clip ever reaches the network; every loop after it, and every replay of
 * the same track later in the session, is served from disk instead.
 */
@UnstableApi
object CanvasCache {

    /**
     * The disk budget comes from [AppSettings.canvasCacheLimitBytes] and can
     * be changed at any time from Settings. Same evictor as the song cache so
     * the ceiling can move without reopening the [SimpleCache]; the head
     * protection is turned off (0 / 0) because a clip is only a few seconds
     * long and has no analysis pass that needs its opening kept.
     */
    private val evictor = DynamicLruCacheEvictor(
        maxBytes = AppSettings.DEFAULT_CANVAS_CACHE_LIMIT_BYTES,
        headBytes = 0L,
        headBudgetBytes = 0L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var cache: SimpleCache

    /** Opened once per process, alongside [com.music.audify.playback.AudioCache.init]. */
    fun init(context: Context) {
        CanvasIndex.init(context)
        // Must run after AppSettings.init: opening the cache replays every
        // stored span through the evictor, and a ceiling still at its
        // default would evict clips the listener's larger limit allows.
        evictor.maxBytes = AppSettings.canvasCacheLimitBytes.value
        cache = SimpleCache(
            File(context.cacheDir, "canvas"),
            evictor,
            StandaloneDatabaseProvider(context),
        )
        scope.launch {
            AppSettings.canvasCacheLimitBytes.collect { maxBytes ->
                evictor.maxBytes = maxBytes
                evictor.applyNow(cache)
            }
        }
    }

    /** Bytes the canvas clips occupy on disk right now. */
    fun usedBytes(): Long = if (::cache.isInitialized) cache.cacheSpace else 0L

    /** How many clips are stored. */
    fun clipCount(): Int = if (::cache.isInitialized) cache.keys.size else 0

    /**
     * Whether the clip at [url] is stored on disk in full, so playing it needs
     * no network at all. Key = URL, same as the default CacheDataSource key.
     * A partly downloaded clip counts as not cached: finishing it would spend
     * data.
     */
    fun isClipCached(url: String): Boolean {
        if (!::cache.isInitialized) return false
        return runCatching {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(url))
            length > 0L && cache.isCached(url, 0L, length)
        }.getOrDefault(false)
    }

    /** Whether any rendition of [artwork] is fully on disk. */
    fun isCached(artwork: CanvasArtwork): Boolean =
        isClipCached(artwork.url) ||
            artwork.portraitUrl?.let(::isClipCached) == true ||
            artwork.fallbackUrl?.let(::isClipCached) == true

    /** Deletes every stored clip. Disk work: call off the main thread. */
    fun clear() {
        if (!::cache.isInitialized) return
        cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
        CanvasIndex.clear()
    }

    /**
     * [upstream] wrapped so a clip already on disk never touches the
     * network again — see the class doc for why that is the whole point.
     * A cache write that fails (full disk, evicted mid-write) drops back to
     * plain streaming rather than surfacing as a playback error, the same
     * choice [com.music.audify.playback.AudioCache] makes for audio.
     */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
