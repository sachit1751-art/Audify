package com.music.audify.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuationPagingGuardTest {
    @Test
    fun repeatedContinuationTokenStopsTheWalk() {
        val paging = ContinuationPagingGuard(maxPages = 10)

        assertTrue(paging.shouldFetch("page-2"))
        assertTrue(paging.shouldFetch("page-3"))
        assertFalse(paging.shouldFetch("page-2"))
    }

    @Test
    fun pageLimitAndMissingTokensStopTheWalk() {
        val paging = ContinuationPagingGuard(maxPages = 2)

        assertTrue(paging.shouldFetch("page-2"))
        assertFalse(paging.shouldFetch("page-3"))
        assertFalse(paging.shouldFetch(null))
        assertFalse(paging.shouldFetch(""))
    }
}
