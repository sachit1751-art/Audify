// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Implemented by Plan 14's local podcast repository; playback never owns canonical episode data. */
interface PodcastPlaybackRepository {
    fun findEpisode(feedId: String, episodeId: String): PodcastEpisodeRef?
    fun onPlaybackStarted(episode: PodcastEpisodeRef)
    fun onPlaybackProgress(episode: PodcastEpisodeRef, positionMs: Long, durationMs: Long?)
    fun onPlaybackCompleted(episode: PodcastEpisodeRef, positionMs: Long, durationMs: Long?)
}

/** Narrow integration seam so the playback foundation does not depend on feed/database code. */
object PodcastPlaybackBridge {
    private val repositoryState = MutableStateFlow<PodcastPlaybackRepository?>(null)

    var repository: PodcastPlaybackRepository?
        get() = repositoryState.value
        set(value) {
            repositoryState.value = value
        }

    /** Allows the playback service to retry a deferred restore when the local store becomes ready. */
    val repositoryFlow = repositoryState.asStateFlow()

    internal fun resolve(feedId: String, episodeId: String): PodcastEpisodeRef? =
        repository?.findEpisode(feedId, episodeId)
}
