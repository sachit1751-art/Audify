package com.music.audify.ui

import com.music.audify.data.model.SearchFilter
import java.util.Locale

/**
 * Infer a narrow, explicit media intent from ordinary leading phrases.
 * The query itself is deliberately left untouched: provider wording and
 * ranking semantics remain the provider's responsibility.
 */
internal fun inferSearchFilter(query: String): SearchFilter? {
    val normalized = query.trim().lowercase(Locale.ROOT)
    return when {
        normalized.matches(Regex("^(?:find\\s+|search\\s+for\\s+|show\\s+me\\s+)?(?:songs?|tracks?)\\s+by\\s+.+")) -> SearchFilter.SONGS
        normalized.matches(Regex("^(?:find\\s+|search\\s+for\\s+|show\\s+me\\s+)?albums?\\s+by\\s+.+")) -> SearchFilter.ALBUMS
        normalized.matches(Regex("^(?:find\\s+|search\\s+for\\s+|show\\s+me\\s+)?artists?\\s+.+")) -> SearchFilter.ARTISTS
        normalized.matches(Regex("^(?:find\\s+|search\\s+for\\s+|show\\s+me\\s+)?playlists?\\s+(?:by|for|with)\\s+.+")) -> SearchFilter.PLAYLISTS
        normalized.matches(Regex("^(?:find\\s+|search\\s+for\\s+|show\\s+me\\s+)?music\\s+videos?\\s+by\\s+.+")) -> SearchFilter.VIDEOS
        else -> null
    }
}

/** Separates a query-derived facet from a facet the listener chose explicitly. */
internal data class SearchFilterSelection(
    val filter: SearchFilter = SearchFilter.ALL,
    val inferred: Boolean = false,
) {
    fun onQueryChanged(): SearchFilterSelection =
        if (inferred) copy(filter = SearchFilter.ALL, inferred = false) else this

    fun select(filter: SearchFilter): SearchFilterSelection = copy(filter = filter, inferred = false)

    fun infer(query: String, source: SearchSource): SearchFilterSelection {
        if (source != SearchSource.YOUTUBE || filter != SearchFilter.ALL) return this
        val inferredFilter = inferSearchFilter(query) ?: return this
        return copy(filter = inferredFilter, inferred = true)
    }
}

/** A delayed YouTube typeahead answer is valid only while its query and source are still active. */
internal fun shouldPublishYouTubeTypeahead(
    currentQuery: String,
    requestedQuery: String,
    submitted: Boolean,
    source: SearchSource,
    requestId: Long,
    currentRequestId: Long,
): Boolean = source == SearchSource.YOUTUBE && !submitted &&
    currentQuery == requestedQuery && requestId == currentRequestId

/** A local scan must not overwrite a newer scan or a different search source. */
internal fun shouldPublishLibrarySearch(
    requestGeneration: Long,
    currentGeneration: Long,
    source: SearchSource,
): Boolean = requestGeneration == currentGeneration && source == SearchSource.LIBRARY
