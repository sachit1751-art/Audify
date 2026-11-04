// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFallbackAggregationTest {
    @Test
    fun partialCategoryFailureKeepsShelvesFromSuccessfulCategories() {
        val result = combineHomeFallbackCategoryResults(
            listOf(
                Result.success<List<String>>(listOf("Pop")),
                Result.failure<List<String>>(IllegalStateException("Rock unavailable")),
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("Pop"), result.getOrThrow())
    }

    @Test
    fun totalCategoryFailureIsNotReportedAsAnEmptyCataloguePage() {
        val result = combineHomeFallbackCategoryResults(
            listOf(
                Result.failure<List<String>>(IllegalStateException("Pop unavailable")),
                Result.failure<List<String>>(IllegalStateException("Rock unavailable")),
            ),
        )

        assertTrue(result.isFailure)
        assertEquals(2, result.exceptionOrNull()?.suppressed?.size)
    }

    @Test
    fun successfulEmptyCategoriesAndExhaustedPageRemainValidEmptyResults() {
        val successfulEmpty = combineHomeFallbackCategoryResults(listOf(Result.success<List<String>>(emptyList())))
        val exhausted = combineHomeFallbackCategoryResults<String>(emptyList())

        assertTrue(successfulEmpty.isSuccess)
        assertTrue(successfulEmpty.getOrThrow().isEmpty())
        assertTrue(exhausted.isSuccess)
        assertTrue(exhausted.getOrThrow().isEmpty())
        assertFalse(exhausted.isFailure)
    }
}
