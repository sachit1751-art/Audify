// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import android.net.Uri
import androidx.media3.common.Player
import com.music.audify.data.model.QueueTier
import com.music.audify.data.model.Song
import com.music.audify.data.model.durationMillis

/** A typed view of an item that can be presented by the playback session. */
sealed interface PlayableMedia {
    val stableId: String
    val title: String
    val artist: String
    val artworkUri: Uri?
    val durationMs: Long?
    val queueEntryId: String?
    val queueTier: QueueTier

    data class Music(val song: Song) : PlayableMedia {
        override val stableId: String get() = song.videoId
        override val title: String get() = song.title
        override val artist: String get() = song.artist
        override val artworkUri: Uri? get() = song.thumbnailUrl?.let(Uri::parse)
        override val durationMs: Long? get() = song.durationText.durationMillis().takeIf { it > 0L }
        override val queueEntryId: String? get() = song.queueEntryId
        override val queueTier: QueueTier get() = song.queueTier
    }

    data class Podcast(
        val episode: PodcastEpisodeRef,
        override val queueEntryId: String? = null,
        override val queueTier: QueueTier = QueueTier.USER_QUEUE,
    ) : PlayableMedia {
        override val stableId: String get() = episode.stableId
        override val title: String get() = episode.episodeTitle
        override val artist: String get() = episode.showTitle
        override val artworkUri: Uri? get() = episode.artworkUri
        override val durationMs: Long? get() = episode.durationMs
    }
}

/** Minimal, already URL-policy-validated episode data needed by the player. */
data class PodcastEpisodeRef(
    val feedId: String,
    val episodeId: String,
    val enclosureUri: Uri,
    val mimeType: String?,
    val showTitle: String,
    val episodeTitle: String,
    val description: String?,
    val artworkUri: Uri?,
    val durationMs: Long?,
) {
    init {
        require(feedId.isNotBlank()) { "Podcast feed ID must not be blank" }
        require(episodeId.isNotBlank()) { "Podcast episode ID must not be blank" }
        require(enclosureUri.scheme.equals("https", ignoreCase = true)) {
            "Podcast enclosure must use HTTPS"
        }
        require(!enclosureUri.host.isNullOrBlank()) { "Podcast enclosure must have a host" }
        require(showTitle.isNotBlank()) { "Podcast show title must not be blank" }
        require(episodeTitle.isNotBlank()) { "Podcast episode title must not be blank" }
        require(durationMs == null || durationMs >= 0L) { "Podcast duration cannot be negative" }
    }

    val stableId: String get() = "podcast:${feedId.length}:$feedId:${episodeId.length}:$episodeId"
}

/** Queue occurrence metadata is distinct from canonical media identity. */
data class QueueEntryMetadata(
    val queueEntryId: String,
    val tier: QueueTier = QueueTier.USER_QUEUE,
    val canonicalIndex: Int? = null,
) {
    init {
        require(queueEntryId.isNotBlank()) { "Queue occurrence ID must not be blank" }
        require(canonicalIndex == null || canonicalIndex >= 0) { "Canonical index cannot be negative" }
    }
}

/** Whether this physical Media3 queue contains any item outside the music-only algorithms. */
fun Player.hasPodcastInQueue(): Boolean =
    (0 until mediaItemCount).any { getMediaItemAt(it).isPodcastEpisode }
