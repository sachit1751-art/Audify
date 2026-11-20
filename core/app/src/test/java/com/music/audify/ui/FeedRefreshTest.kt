package com.music.audify.ui

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedRefreshTest {
    @Test
    fun staleRefreshCannotPublishOverANewerHomeLoad() {
        assertEquals(true, shouldPublishHomeRefresh(true, 4L, 4L))
        assertEquals(false, shouldPublishHomeRefresh(true, 4L, 5L))
        assertEquals(false, shouldPublishHomeRefresh(false, 4L, 4L))
    }

    @Test
    fun failedRefreshAlwaysClearsItsOwnRefreshingFlag() = runBlocking {
        val state = MutableStateFlow(setOf("HOME", "LIBRARY"))

        runCatching {
            runFeedRefresh(state, "HOME") {
                throw IllegalStateException("simulated repository failure")
            }
        }

        assertEquals(setOf("LIBRARY"), state.value)
    }

    @Test
    fun successfulRefreshClearsItsOwnRefreshingFlag() = runBlocking {
        val state = MutableStateFlow(setOf("HOME"))

        runFeedRefresh(state, "HOME") { }

        assertEquals(emptySet<String>(), state.value)
    }
}
