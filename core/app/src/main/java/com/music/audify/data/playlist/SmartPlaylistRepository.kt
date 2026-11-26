// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.playlist

import com.music.audify.data.model.Song
import com.music.audify.data.stats.ListeningStats
import com.music.audify.data.stats.LocalLibraryStore
import java.util.Locale

/**
 * Builds a fresh result from the current library and listening aggregates.
 *
 * Rules are the durable data; generated tracks are deliberately not persisted.
 * This keeps a smart playlist current when the library or listening history
 * changes and prevents its generated queue from becoming a second source of
 * truth. The caller supplies the current browsable library snapshot; liked,
 * recent-history and listening signals are joined here.
 */
object SmartPlaylistRepository {

    /** Re-evaluate one saved definition when its page is opened or previewed. */
    suspend fun evaluate(
        definition: SmartPlaylistDefinition,
        library: List<Song>,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): List<Song> {
        val snapshots = ListeningStats.trackSnapshot()
        return evaluateWithSnapshot(definition, library, snapshots, nowEpochSeconds)
    }

    /** Re-evaluate every saved definition from one consistent listening snapshot. */
    suspend fun evaluateAll(
        library: List<Song>,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): Map<String, List<Song>> {
        val definitions = SmartPlaylistStore.definitions.value
        if (definitions.isEmpty()) return emptyMap()
        val snapshots = ListeningStats.trackSnapshot()
        return definitions.associate { definition ->
            definition.id to evaluateWithSnapshot(definition, library, snapshots, nowEpochSeconds)
        }
    }

    /**
     * Suggest tracks related to a playlist using one listening snapshot per batch.
     * Unrelated tracks are never presented as recommendations.
     */
    suspend fun recommend(
        definition: SmartPlaylistDefinition,
        currentSongs: List<Song>,
        library: List<Song>,
        limit: Int = 12,
    ): List<Song> {
        val listening = ListeningStats.trackSnapshot().associate { snapshot ->
            snapshot.videoId to SmartPlaylistListening(
                plays = snapshot.plays,
                listenedMs = snapshot.listenedMs,
                lastPlayedAtEpochMs = snapshot.lastPlayedAtEpochMs,
            )
        }
        return rankSmartPlaylistRecommendations(
            currentSongs = currentSongs,
            definitionArtists = definition.artists,
            library = library,
            listening = listening,
            excludedSongIds = definition.includedSongIds.toSet(),
            limit = limit,
        )
    }

    /** Build recommendations for every playlist from a single stats snapshot. */
    suspend fun recommendAll(
        definitions: List<SmartPlaylistDefinition>,
        currentSongsByPlaylist: Map<String, List<Song>>,
        library: List<Song>,
        limit: Int = 12,
    ): Map<String, List<Song>> {
        if (definitions.isEmpty() || library.isEmpty() || limit <= 0) return emptyMap()
        val listening = ListeningStats.trackSnapshot().associate { snapshot ->
            snapshot.videoId to SmartPlaylistListening(
                plays = snapshot.plays,
                listenedMs = snapshot.listenedMs,
                lastPlayedAtEpochMs = snapshot.lastPlayedAtEpochMs,
            )
        }
        return definitions.associate { definition ->
            definition.id to rankSmartPlaylistRecommendations(
                currentSongs = currentSongsByPlaylist[definition.id].orEmpty(),
                definitionArtists = definition.artists,
                library = library,
                listening = listening,
                excludedSongIds = definition.includedSongIds.toSet(),
                limit = limit,
            )
        }
    }

    private fun evaluateWithSnapshot(
        definition: SmartPlaylistDefinition,
        library: List<Song>,
        snapshots: List<com.music.audify.data.stats.ListeningTrackSnapshot>,
        nowEpochSeconds: Long,
    ): List<Song> {
        val likedIds = LocalLibraryStore.favorites.value.mapTo(mutableSetOf()) { it.videoId }
        val recentHistoryIds = LocalLibraryStore.history.value.mapTo(mutableSetOf()) { it.videoId }
        // Listening aggregates retain enough metadata to surface streamed
        // tracks even when they are not part of a device-local folder.
        val candidates = mergeSmartPlaylistCandidates(
            library = library,
            history = LocalLibraryStore.history.value,
            favorites = LocalLibraryStore.favorites.value,
            listeningTracks = snapshots.map { it.song },
        )
        val listening = snapshots.associate { snapshot ->
            snapshot.videoId to SmartPlaylistListening(
                plays = snapshot.plays,
                listenedMs = snapshot.listenedMs,
                lastPlayedAtEpochMs = snapshot.lastPlayedAtEpochMs,
            )
        }
        val generated = SmartPlaylistEngine.evaluate(
            definition = definition,
            library = candidates,
            likedVideoIds = likedIds,
            recentHistoryVideoIds = recentHistoryIds,
            nowEpochSeconds = nowEpochSeconds,
            listening = listening,
        )
        if (definition.paused) return emptyList()
        return mergeSmartPlaylistAdditions(
            generated = generated,
            library = candidates,
            includedSongIds = definition.includedSongIds,
            maxTracks = definition.maxTracks,
        )
    }
}

/**
 * Merge deliberate user additions with rule matches. User additions are placed
 * first so they remain present even when the generated result reaches its cap.
 */
internal fun mergeSmartPlaylistAdditions(
    generated: List<Song>,
    library: List<Song>,
    includedSongIds: List<String>,
    maxTracks: Int,
): List<Song> {
    val byId = library.associateBy { it.videoId }
    val included = includedSongIds.mapNotNull(byId::get)
    return (included + generated).distinctBy { it.videoId }.take(maxTracks.coerceAtLeast(0))
}


/**
 * Rank only genuine artist-affinity candidates. An empty anchor set produces no
 * recommendations rather than an arbitrary list of popular library tracks.
 */
internal fun rankSmartPlaylistRecommendations(
    currentSongs: List<Song>,
    definitionArtists: List<String>,
    library: List<Song>,
    listening: Map<String, SmartPlaylistListening>,
    excludedSongIds: Set<String> = emptySet(),
    limit: Int = 12,
): List<Song> {
    if (library.isEmpty() || limit <= 0) return emptyList()
    fun normalizedArtists(song: Song): Set<String> =
        (listOf(song.artist) + song.artists.map { it.name })
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter(String::isNotBlank)
            .toSet()

    val anchorArtists = (currentSongs.flatMap(::normalizedArtists) + definitionArtists)
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter(String::isNotBlank)
        .toSet()
    if (anchorArtists.isEmpty()) return emptyList()

    val excluded = currentSongs.mapTo(excludedSongIds.toMutableSet()) { it.videoId }
    return library.asSequence()
        .filter { it.videoId.isNotBlank() && it.videoId !in excluded }
        .distinctBy { it.videoId }
        .mapNotNull { song ->
            val affinity = normalizedArtists(song).count(anchorArtists::contains)
            if (affinity == 0) null else song to affinity
        }
        .sortedWith(
            compareByDescending<Pair<Song, Int>> { it.second }
                .thenByDescending { listening[it.first.videoId]?.plays ?: 0 }
                .thenByDescending { listening[it.first.videoId]?.lastPlayedAtEpochMs ?: 0L }
                .thenBy { it.first.title.lowercase(Locale.ROOT) }
                .thenBy { it.first.videoId },
        )
        .take(limit.coerceAtMost(50))
        .map { it.first }
        .toList()
}

/** Merge all device-owned sources once, preserving the first (richest) song record. */
internal fun mergeSmartPlaylistCandidates(
    library: List<Song>,
    history: List<Song>,
    favorites: List<Song>,
    listeningTracks: List<Song>,
): List<Song> = (library + history + favorites + listeningTracks)
    .asSequence()
    .filter { it.videoId.isNotBlank() }
    .distinctBy { it.videoId }
    .toList()