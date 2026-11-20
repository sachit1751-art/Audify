// Copyright (c) 2026 Sachit. All rights reserved.
package com.music.audify.wear.data

/** Bounded catalogue projection sent by the phone; media URLs never leave the phone. */
data class PodcastEpisodeRow(
    val feedId: String,
    val episodeId: String,
    val title: String,
    val published: String,
    val durationMs: Long,
    val positionMs: Long,
    val completed: Boolean,
)

data class PodcastShowRow(
    val id: String,
    val title: String,
    val description: String,
    val category: String = "Subscriptions",
    val episodes: List<PodcastEpisodeRow>,
)

internal enum class PodcastCatalogueState {
    LOADING,
    OFFLINE_EMPTY,
    EMPTY,
    CONTENT,
}

internal fun podcastCatalogueState(
    shows: List<PodcastShowRow>?,
    phoneDisconnected: Boolean,
): PodcastCatalogueState = when {
    shows != null && shows.isNotEmpty() -> PodcastCatalogueState.CONTENT
    shows != null -> PodcastCatalogueState.EMPTY
    phoneDisconnected -> PodcastCatalogueState.OFFLINE_EMPTY
    else -> PodcastCatalogueState.LOADING
}

/** A late cached Data Layer read must never replace a newer live snapshot. */
internal fun shouldApplyPodcastSnapshot(incomingAt: Long, currentAt: Long): Boolean =
    incomingAt >= currentAt
