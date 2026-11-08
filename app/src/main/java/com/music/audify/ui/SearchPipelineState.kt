package com.music.audify.ui

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
