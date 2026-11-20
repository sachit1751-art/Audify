// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import androidx.media3.common.MediaItem
import com.music.audify.data.model.QueueTier
import com.music.audify.data.model.Song

/** App-layer adapter that preserves typed USER_QUEUE rows around music-only queue algorithms. */
object TypedQueueComposer {

    data class Result(val items: List<MediaItem>, val startIndex: Int)

    fun buildContextQueue(
        currentItems: List<MediaItem>,
        currentIndex: Int,
        newContextSongs: List<Song>,
        selectedIndex: Int,
        source: QueueSource,
    ): Result {
        val upcomingUserItems = upcomingUserItems(currentItems, currentIndex)
        val context = QueueCoordinator.buildContextQueue(
            currentTimeline = emptyList(),
            currentIndex = -1,
            newContextSongs = newContextSongs,
            selectedIndex = selectedIndex,
            contextSource = source,
        )
        val throughSelection = context.timeline.take(context.startIndex + 1).map { it.toMediaItem() }
        val afterSelection = context.timeline.drop(context.startIndex + 1).map { it.toMediaItem() }
        return Result(
            items = throughSelection + upcomingUserItems + afterSelection,
            startIndex = context.startIndex,
        )
    }

    fun buildOneOffQueue(
        currentItems: List<MediaItem>,
        currentIndex: Int,
        tappedSong: Song,
        source: QueueSource,
    ): Result {
        val userItems = upcomingUserItems(currentItems, currentIndex)
        val selected = QueueCoordinator.buildOneOffQueue(emptyList(), -1, tappedSong, source).first().toMediaItem()
        return Result(listOf(selected) + userItems, 0)
    }

    fun findUserQueueInsertionIndex(
        currentItems: List<MediaItem>,
        currentIndex: Int,
        isNext: Boolean,
    ): Int {
        val start = (currentIndex + 1).coerceIn(0, currentItems.size)
        if (isNext) return start
        for (index in start until currentItems.size) {
            if (currentItems[index].queueTier != QueueTier.USER_QUEUE) return index
        }
        return currentItems.size
    }

    private fun upcomingUserItems(currentItems: List<MediaItem>, currentIndex: Int): List<MediaItem> {
        val start = (currentIndex + 1).coerceIn(0, currentItems.size)
        return currentItems.drop(start).filter { it.queueTier == QueueTier.USER_QUEUE }
    }
}
