package com.music.audify.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A refresh may publish only while the load it started against is still current. */
internal fun shouldPublishHomeRefresh(
    identityMatches: Boolean,
    requestGeneration: Long,
    currentGeneration: Long,
): Boolean = identityMatches && requestGeneration == currentGeneration

/** Runs one feed refresh and always releases its loading indicator. */
internal suspend fun <T> runFeedRefresh(
    refreshing: MutableStateFlow<Set<T>>,
    feed: T,
    action: suspend () -> Unit,
) {
    try {
        action()
    } finally {
        refreshing.update { it - feed }
    }
}
