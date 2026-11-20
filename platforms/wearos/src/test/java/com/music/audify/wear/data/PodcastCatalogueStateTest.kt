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

    @Test
    fun lateCachedSnapshotCannotReplaceNewerLiveCatalogue() {
        assertEquals(false, shouldApplyPodcastSnapshot(incomingAt = 100L, currentAt = 200L))
    }

    @Test
    fun newerLiveSnapshotReplacesCachedCatalogue() {
        assertEquals(true, shouldApplyPodcastSnapshot(incomingAt = 201L, currentAt = 200L))
    }

    @Test
    fun equalTimestampSnapshotCanBeReapplied() {
        assertEquals(true, shouldApplyPodcastSnapshot(incomingAt = 200L, currentAt = 200L))
    }
}
