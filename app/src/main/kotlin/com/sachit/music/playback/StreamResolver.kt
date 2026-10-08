/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import android.content.Context
import android.net.ConnectivityManager
import com.sachit.music.constants.AudioQuality
import com.sachit.music.utils.InnerTubeXPlayer

/**
 * Wired StreamResolution for MusicService.
 * Delegates to the extracted StreamResolution class with MusicService's dependencies.
 *
 * Plan 022 step 2.
 */
internal class StreamResolver(
    context: Context,
    connectivityManager: ConnectivityManager,
    streamUrlCache: StreamUrlCache,
    sourceHealth: StreamSourceHealth,
    private val audioQuality: () -> AudioQuality,
    private val bypassCacheForQualityChange: MutableSet<String>,
    private val getPlaybackData: suspend (
        mediaId: String,
        quality: AudioQuality,
        contentHints: com.metrolist.innertubex.extraction.ContentHints,
    ) -> InnerTubeXPlayer.PlaybackData?,
) {
    private val resolution = StreamResolution(
        context = context,
        connectivityManager = connectivityManager,
        streamUrlCache = streamUrlCache,
        sourceHealth = sourceHealth,
        audioQuality = audioQuality,
        bypassCacheForQualityChange = bypassCacheForQualityChange,
        onSuccess = { mediaId -> /* tracked by caller if needed */ },
        onFailure = { mediaId, client -> /* tracked by caller */ },
        getPlaybackData = getPlaybackData,
    )

    suspend fun resolve(mediaId: String): StreamResolution.StreamHandle =
        resolution.resolve(mediaId)

    fun invalidateCache(mediaId: String) = resolution.invalidateCache(mediaId)
}
