package com.music.audify.ui.tv.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPodcastRefreshStateTest {
    @Test
    fun failedRefreshRetainsLastSuccessfulCatalogue() {
        val previous = listOf("Saved show")

        assertEquals(previous, retainLastSuccessful(previous, Result.failure(IllegalStateException("offline"))))
    }

    @Test
    fun successfulRefreshReplacesLastSuccessfulCatalogue() {
        val previous = listOf("Old show")
        val refreshed = listOf("New show")

        assertEquals(refreshed, retainLastSuccessful(previous, Result.success(refreshed)))
    }

    @Test
    fun firstRefreshFailureDoesNotInventCatalogueContent() {
        assertNull(retainLastSuccessful(null, Result.failure(IllegalStateException("offline"))))
    }
}
