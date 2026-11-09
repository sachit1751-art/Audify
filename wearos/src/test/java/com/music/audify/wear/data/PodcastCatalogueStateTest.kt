package com.music.audify.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastCatalogueStateTest {
    @Test
    fun connectedNullCatalogueIsLoading() {
        assertEquals(PodcastCatalogueState.LOADING, podcastCatalogueState(null, phoneDisconnected = false))
    }

    @Test
    fun disconnectedNullCatalogueIsAnOfflineEmptyStateNotAnInfiniteLoader() {
        assertEquals(PodcastCatalogueState.OFFLINE_EMPTY, podcastCatalogueState(null, phoneDisconnected = true))
    }

    @Test
    fun cachedEmptyCatalogueRemainsAnHonestEmptyStateOffline() {
        assertEquals(PodcastCatalogueState.EMPTY, podcastCatalogueState(emptyList(), phoneDisconnected = true))
    }

    @Test
    fun cachedSubscriptionsRemainVisibleOffline() {
        val show = PodcastShowRow("feed", "Show", "", episodes = emptyList())
        assertEquals(PodcastCatalogueState.CONTENT, podcastCatalogueState(listOf(show), phoneDisconnected = true))
    }
}
