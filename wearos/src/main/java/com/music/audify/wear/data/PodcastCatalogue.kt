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
