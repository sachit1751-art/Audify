/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import androidx.media3.common.MediaItem

/**
 * A single playback intent that collapses the 15 queue/radio/automix mutations
 * currently scattered across MusicService into one verb set.
 *
 * Plan 022 step 2.
 */
internal sealed class PlaybackIntent {
    /** Replace current queue with the given items and start playing. */
    data class Play(val items: List<MediaItem>) : PlaybackIntent()

    /** Insert items after the current track (play next). */
    data class EnqueueNext(val items: List<MediaItem>) : PlaybackIntent()

    /** Append items to the end of the queue. */
    data class Enqueue(val items: List<MediaItem>) : PlaybackIntent()

    /** Replace the current queue entirely (e.g., new search results, playlist). */
    data class Replace(val queue: QueueLike) : PlaybackIntent()

    /** Clear automix items. */
    data object ClearAutomix : PlaybackIntent()

    /** Start a radio based on the given media item. */
    data class StartRadio(val mediaId: String, val title: String?) : PlaybackIntent()
}

/** Minimal queue-like interface for PlaybackIntent.Replace. */
internal interface QueueLike {
    val items: List<MediaItem>
    val title: String?
}
