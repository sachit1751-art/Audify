package com.music.audify.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiStateRetryabilityTest {
    @Test
    fun existingErrorsRemainRetryableByDefault() {
        assertTrue(UiState.Error("Temporary provider failure").retryable)
    }

    @Test
    fun producersCanMarkEmptyOrPermanentErrorsAsNonRetryable() {
        assertFalse(UiState.Error("Nothing to show", retryable = false).retryable)
    }
}
