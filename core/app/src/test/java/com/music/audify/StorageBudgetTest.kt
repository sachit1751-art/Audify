// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.download.StorageBudgetDecision
import com.music.audify.download.StorageBudgetFile
import com.music.audify.download.evaluateStorageBudget
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageBudgetTest {

    private fun file(uri: String, bytes: Long, added: Long) = StorageBudgetFile(
        uri = uri,
        videoIds = listOf(uri),
        title = uri,
        artist = "Artist",
        sizeBytes = bytes,
        addedAtMillis = added,
    )

    @Test
    fun `download is allowed when exact projected total stays within budget`() {
        val decision = evaluateStorageBudget(
            files = listOf(file("old", 400, 1)),
            projectedBytes = 500,
            budgetBytes = 1_000,
        )

        assertEquals(StorageBudgetDecision.Allowed, decision)
    }

    @Test
    fun `LRU selection chooses the smallest oldest prefix that frees enough bytes`() {
        val oldest = file("oldest", 400, 1)
        val middle = file("middle", 300, 2)
        val newest = file("newest", 900, 3)

        val decision = evaluateStorageBudget(
            files = listOf(newest, middle, oldest),
            projectedBytes = 300,
            budgetBytes = 1_300,
        )

        val plan = (decision as StorageBudgetDecision.RequiresConfirmation).plan
        assertEquals(1_600L, plan.usedBytes)
        assertEquals(600L, plan.requiredFreeBytes)
        assertEquals(listOf("oldest", "middle"), plan.oldestFiles.map { it.uri })
        assertEquals(listOf(400L, 300L), plan.oldestFiles.map { it.sizeBytes })
    }

    @Test
    fun `unknown existing or projected sizes never produce an estimate`() {
        assertEquals(
            StorageBudgetDecision.SizeUnknown,
            evaluateStorageBudget(files = null, projectedBytes = 100, budgetBytes = 1_000),
        )
        assertEquals(
            StorageBudgetDecision.SizeUnknown,
            evaluateStorageBudget(files = emptyList(), projectedBytes = null, budgetBytes = 1_000),
        )
    }

    @Test
    fun `reserved concurrent downloads count against budget but are not eviction candidates`() {
        val oldest = file("old", 500, 1)
        val decision = evaluateStorageBudget(
            files = listOf(oldest),
            projectedBytes = 300,
            budgetBytes = 1_000,
            reservedBytes = 400,
        )

        val plan = (decision as StorageBudgetDecision.RequiresConfirmation).plan
        assertEquals(500L, plan.usedBytes)
        assertEquals(200L, plan.requiredFreeBytes)
        assertEquals(listOf("old"), plan.oldestFiles.map { it.uri })
    }

    @Test
    fun `cannot fit is distinct from unknown size when no evictable bytes exist`() {
        assertEquals(
            StorageBudgetDecision.CannotFit(emptyList()),
            evaluateStorageBudget(files = emptyList(), projectedBytes = 1_200, budgetBytes = 1_000),
        )
    }
}
