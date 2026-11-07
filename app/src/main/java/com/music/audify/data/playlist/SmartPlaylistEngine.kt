// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.playlist

import com.music.audify.data.model.Song
import java.util.Locale

enum class SmartPlaylistSort {
    LIBRARY_ORDER,
    MOST_PLAYED,
    RECENTLY_PLAYED,
    RECENTLY_ADDED,
    TITLE,
}

/** The listening fields used by Smart Playlists; sourced from ListeningStats' monthly aggregates. */
data class SmartPlaylistListening(
    val plays: Int,
    val listenedMs: Long,
    val lastPlayedAtEpochMs: Long?,
)

/** A deliberately small rule set backed only by signals AUDIFY currently has. */
data class SmartPlaylistDefinition(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val likedOnly: Boolean = false,
    val addedWithinDays: Int? = null,
    val excludeRecentHistory: Boolean = false,
    val playedWithinDays: Int? = null,
    val notPlayedWithinDays: Int? = null,
    val minimumPlays: Int? = null,
    val sortBy: SmartPlaylistSort = SmartPlaylistSort.LIBRARY_ORDER,
    val maxTracks: Int = 100,
    val paused: Boolean = false,
)

object SmartPlaylistEngine {
    const val MAX_PLAYLISTS = 100
    const val MAX_TRACKS = 500
    const val MAX_ARTISTS = 20
    const val MAX_AGE_DAYS = 3650

    /** Validate before persistence; blank artist filters are never meaningful. */
    fun validate(definition: SmartPlaylistDefinition): String? = when {
        definition.id.isBlank() -> "Playlist ID is required"
        definition.title.isBlank() -> "Playlist name is required"
        definition.title.length > 80 -> "Playlist name must be 80 characters or fewer"
        definition.artists.size > MAX_ARTISTS -> "Choose at most $MAX_ARTISTS artists"
        definition.artists.any(String::isBlank) -> "Artist names cannot be blank"
        definition.addedWithinDays != null && definition.addedWithinDays !in 1..MAX_AGE_DAYS ->
            "Added-within period must be between 1 and $MAX_AGE_DAYS days"
        definition.playedWithinDays != null && definition.playedWithinDays !in 1..MAX_AGE_DAYS ->
            "Played-within period must be between 1 and $MAX_AGE_DAYS days"
        definition.notPlayedWithinDays != null && definition.notPlayedWithinDays !in 1..MAX_AGE_DAYS ->
            "Not-played-within period must be between 1 and $MAX_AGE_DAYS days"
        definition.minimumPlays != null && definition.minimumPlays !in 1..10_000 ->
            "Minimum plays must be between 1 and 10,000"
        definition.maxTracks !in 1..MAX_TRACKS -> "Track limit must be between 1 and $MAX_TRACKS"
        definition.artists.isEmpty() && !definition.likedOnly &&
            definition.addedWithinDays == null && !definition.excludeRecentHistory &&
            definition.playedWithinDays == null && definition.notPlayedWithinDays == null &&
            definition.minimumPlays == null && definition.sortBy == SmartPlaylistSort.LIBRARY_ORDER ->
            "Add at least one rule"
        else -> null
    }

    /**
     * Evaluate against a caller-provided snapshot. Results are stable, unique by
     * video ID, and never mutate the source collection or an ordinary playlist.
     * A null local date-added value simply cannot satisfy that filter.
     */
    fun evaluate(
        definition: SmartPlaylistDefinition,
        library: List<Song>,
        likedVideoIds: Set<String>,
        recentHistoryVideoIds: Set<String>,
        nowEpochSeconds: Long,
        listening: Map<String, SmartPlaylistListening> = emptyMap(),
    ): List<Song> {
        if (definition.paused || validate(definition) != null) return emptyList()
        val cutoff = definition.addedWithinDays?.let { nowEpochSeconds - it * 86_400L }
        val playedCutoff = definition.playedWithinDays?.let { (nowEpochSeconds - it * 86_400L) * 1000L }
        val notPlayedCutoff = definition.notPlayedWithinDays?.let { (nowEpochSeconds - it * 86_400L) * 1000L }
        val artists = definition.artists.mapTo(mutableSetOf()) { it.trim().lowercase(Locale.ROOT) }
        val filtered = library.asSequence()
            .filter { it.videoId.isNotBlank() }
            .distinctBy { it.videoId }
            .filter { song ->
                val addedAt = song.localDateAddedSeconds
                val stats = listening[song.videoId]
                val lastPlayed = stats?.lastPlayedAtEpochMs
                (artists.isEmpty() || sequenceOf(song.artist).plus(song.artists.asSequence().map { it.name })
                    .any { it.trim().lowercase(Locale.ROOT) in artists }) &&
                    (!definition.likedOnly || song.videoId in likedVideoIds) &&
                    (cutoff == null || (addedAt != null && addedAt >= cutoff)) &&
                    (!definition.excludeRecentHistory || song.videoId !in recentHistoryVideoIds) &&
                    (playedCutoff == null || (lastPlayed != null && lastPlayed >= playedCutoff)) &&
                    (notPlayedCutoff == null || lastPlayed == null || lastPlayed < notPlayedCutoff) &&
                    (definition.minimumPlays == null || (stats?.plays ?: 0) >= definition.minimumPlays)
            }
            .toList()
        val sorted = when (definition.sortBy) {
            SmartPlaylistSort.LIBRARY_ORDER -> filtered
            SmartPlaylistSort.MOST_PLAYED -> filtered.sortedWith(
                compareByDescending<Song> { listening[it.videoId]?.plays ?: 0 }
                    .thenByDescending { listening[it.videoId]?.listenedMs ?: 0L }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            SmartPlaylistSort.RECENTLY_PLAYED -> filtered.sortedWith(
                compareByDescending<Song> { listening[it.videoId]?.lastPlayedAtEpochMs ?: 0L }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            SmartPlaylistSort.RECENTLY_ADDED -> filtered.sortedWith(
                compareByDescending<Song> { it.localDateAddedSeconds ?: 0L }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            SmartPlaylistSort.TITLE -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
        }
        return sorted.take(definition.maxTracks)
    }
}
