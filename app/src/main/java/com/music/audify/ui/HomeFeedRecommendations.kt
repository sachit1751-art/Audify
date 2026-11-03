// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui

import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import java.util.Locale
import kotlin.random.Random

/**
 * Removes tracks already rendered earlier in the current Home session.
 * Browsable cards remain eligible because they represent collections, not
 * duplicate track playback candidates.
 */
internal fun deduplicateHomeTracks(
    shelves: List<HomeShelf>,
    seenVideoIds: MutableSet<String>,
): List<HomeShelf> = shelves.mapNotNull { shelf ->
    val items = shelf.items.filter { item ->
        val videoId = item.videoId
        videoId == null || seenVideoIds.add(videoId)
    }
    items.takeIf { it.isNotEmpty() }?.let { shelf.copy(items = it) }
}

/** Moves tracks from the previous Home session behind fresh candidates when available. */
internal fun prioritizeFreshHomeTracks(
    shelves: List<HomeShelf>,
    previouslySeenVideoIds: Set<String>,
): List<HomeShelf> = shelves.map { shelf ->
    val ordered = shelf.items.withIndex()
        .sortedBy { (_, item) -> if (item.videoId != null && item.videoId in previouslySeenVideoIds) 1 else 0 }
        .map { it.value }
    shelf.copy(items = ordered)
}

/**
 * Reorders a shelf so one credited artist is not repeatedly shown ahead of
 * other available artists. The seed keeps the result stable for a given feed
 * while allowing a different mix on a later Home session.
 *
 * Home cards do not expose a reliable genre field, so artist credits are the
 * strongest item-level diversity signal available here.
 */
internal fun diversifyHomeShelf(shelf: HomeShelf, seed: Long): HomeShelf {
    if (shelf.title.equals("Recents", ignoreCase = true) ||
        shelf.title.equals("Recently played", ignoreCase = true)
    ) return shelf
    if (shelf.items.size < 3) return shelf

    val buckets = LinkedHashMap<String, ArrayDeque<ShelfItem>>()
    shelf.items.shuffled(Random(seed)).forEach { item ->
        val artist = item.subtitle.substringBefore(" • ").trim()
            .lowercase(Locale.ROOT)
            .ifBlank { item.videoId ?: item.browseId ?: item.title }
        buckets.getOrPut(artist) { ArrayDeque() }.addLast(item)
    }

    val ordered = ArrayList<ShelfItem>(shelf.items.size)
    var previousArtist: String? = null
    while (buckets.isNotEmpty()) {
        val nextArtist = buckets.keys.firstOrNull { it != previousArtist } ?: buckets.keys.first()
        val bucket = buckets.getValue(nextArtist)
        ordered += bucket.removeFirst()
        if (bucket.isEmpty()) buckets.remove(nextArtist)
        previousArtist = nextArtist
    }
    return shelf.copy(items = ordered)
}
