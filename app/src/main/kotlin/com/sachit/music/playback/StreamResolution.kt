/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import com.metrolist.innertubex.extraction.ContentHints
import com.sachit.music.utils.InnerTubeXPlayer
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a playable stream URL for a media item.
 *
 * Extracted from MusicService (plan 022 step 2) — owns the pipeline that today lives inline in
 * [MusicService.createDataSourceFactory] and [MusicService.getStreamUrl]:
 * cache lookup, per-network quality ceiling selection, InnerTubeXPlayer invocation,
 * cache population, and source-health-aware fallback.
 *
 * The class is suspend-based and takes its dependencies explicitly so the resolution pipeline
 * can be exercised on the JVM with fakes — without an ExoPlayer, a Service, or a Binder.
 */
internal class StreamResolution(
    private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val streamUrlCache: StreamUrlCache,
    private val sourceHealth: StreamSourceHealth,
    private val audioQuality: () -> com.sachit.music.constants.AudioQuality,
    private val bypassCacheForQualityChange: MutableSet<String>,
    private val onSuccess: (String) -> Unit,
    private val onFailure: (String, String?) -> Unit,
    private val getPlaybackData: suspend (
        mediaId: String,
        quality: com.sachit.music.constants.AudioQuality,
        contentHints: ContentHints,
    ) -> com.sachit.music.utils.InnerTubeXPlayer.PlaybackData?,
) {
    /**
     * A resolved stream ready to feed a DataSpec via [com.sachit.music.playback.withResolvedStream].
     */
    data class StreamHandle(
        val url: String,
        val requestHeaders: Map<String, String>,
        val clientName: String,
        val requireBoundedRange: Boolean,
        val rangeChunkSizeBytes: Long,
        val useRangeChunks: Boolean,
        val streamExpiresInSeconds: Int,
    )

    /**
     * Resolve a stream for [mediaId], applying cache, quality ceilings, and source-health fallback.
     *
     * Returns a [StreamHandle] for the resolved stream. When the cache has a valid entry and the
     * source is not in fallback state, the cached URL is returned directly. Otherwise a fresh
     * InnerTubeXPlayer resolution is performed and the cache is updated.
     */
    suspend fun resolve(mediaId: String): StreamHandle = withContext(Dispatchers.IO) {
        val shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

        if (!shouldBypassCache) {
            // Plan 021: after repeated source failures a cached URL is exactly the
            // thing that keeps failing — fall through to a fresh resolution instead.
            if (sourceHealth.state(mediaId) == StreamHealth.FALLBACK) {
                Timber.tag(TAG).i("BYPASSING URL CACHE for $mediaId (source health fallback)")
            } else {
                streamUrlCache[mediaId]?.let { cached ->
                    onSuccess(mediaId)
                    return@withContext cached.toStreamHandle()
                }
            }
        } else {
            Timber.tag(TAG).i("BYPASSING CACHE for $mediaId due to quality change")
        }

        val cacheGeneration = streamUrlCache.generation(mediaId)
        val resolvedQuality = resolveEffectiveQuality()

        Timber.tag(TAG).i(
            "FETCHING STREAM: $mediaId | quality=$resolvedQuality " +
                "(wifi=${prefWifiQuality()} metered=${prefMeteredQuality()})"
        )

        val playbackData = getPlaybackData(mediaId, resolvedQuality, ContentHints(false, false))

        val pd = playbackData
            ?: run {
                onFailure(mediaId, null)
                throw PlaybackException(
                    context.getString(com.sachit.music.R.string.error_unknown),
                    null,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR,
                )
            }

        val handle = pd.toStreamHandle()
        streamUrlCache.put(
            mediaId = mediaId,
            url = handle.url,
            requestHeaders = handle.requestHeaders,
            clientName = handle.clientName,
            expiresInSeconds = handle.streamExpiresInSeconds,
            requireBoundedRange = handle.requireBoundedRange,
            rangeChunkSizeBytes = handle.rangeChunkSizeBytes,
            useRangeChunks = handle.useRangeChunks,
            expectedGeneration = cacheGeneration,
        )

        onSuccess(mediaId)
        handle
    }

    /**
     * Resolve the effective audio quality for the current network.
     *
     * When the configured quality is AUTO, per-network ceilings (Plan 018) apply.
     * Otherwise the configured quality is used directly.
     */
    private fun resolveEffectiveQuality(): com.sachit.music.constants.AudioQuality {
        val configured = audioQuality()
        if (configured != com.sachit.music.constants.AudioQuality.AUTO) return configured

        val networkClass = connectivityManager.networkClass()
        return when (networkClass) {
            NetworkClass.WIFI -> prefWifiQuality() ?: configured
            NetworkClass.METERED -> prefMeteredQuality() ?: configured
            NetworkClass.OFFLINE -> configured
        }
    }

    private fun prefWifiQuality(): com.sachit.music.constants.AudioQuality? {
        return null // TODO: read from settings when wired
    }

    private fun prefMeteredQuality(): com.sachit.music.constants.AudioQuality? {
        return null // TODO: read from settings when wired
    }

    /** Invalidate the stream URL cache for a mediaId (called on quality change, etc.). */
    fun invalidateCache(mediaId: String) {
        streamUrlCache.invalidate(mediaId)
    }

    companion object {
        private const val TAG = "StreamResolution"
    }
}

// ── Extension helpers ──────────────────────────────────────────────────────────

private fun InnerTubeXPlayer.PlaybackData.toStreamHandle() = StreamResolution.StreamHandle(
    url = streamUrl,
    requestHeaders = streamHeaders,
    clientName = streamClient,
    requireBoundedRange = requireBoundedRange,
    rangeChunkSizeBytes = rangeChunkSizeBytes,
    useRangeChunks = useRangeChunks,
    streamExpiresInSeconds = streamExpiresInSeconds,
)

private fun CachedStreamUrl.toStreamHandle() = StreamResolution.StreamHandle(
    url = url,
    requestHeaders = requestHeaders,
    clientName = clientName,
    requireBoundedRange = requireBoundedRange,
    rangeChunkSizeBytes = rangeChunkSizeBytes,
    useRangeChunks = useRangeChunks,
    streamExpiresInSeconds = 0,
)
