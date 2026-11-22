// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.data.model.UiState
import com.music.audify.data.remote.RemoteListing
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteListingTest {

    @Test
    fun listsWhatTheServerReturned() {
        assertEquals(
            UiState.Success(listOf("a", "b")),
            RemoteListing.state(Result.success(listOf("a", "b")), "empty", "failed"),
        )
    }

    @Test
    fun emptyListingReadsAsEmpty() {
        assertEquals(
            UiState.Error("empty", retryable = false),
            RemoteListing.state(Result.success(emptyList<String>()), "empty", "failed"),
        )
    }

    @Test
    fun failedListingDoesNotExposeRemotePathsOrRawExceptionDetails() {
        val denied = IllegalStateException("STATUS_ACCESS_DENIED (0xc0000022): Could not connect to \\\\nas\\Music")
        assertEquals(
            UiState.Error("failed"),
            RemoteListing.state(Result.failure<List<String>>(denied), "empty", "failed"),
        )
    }

    @Test
    fun failuresWithoutUsefulMessagesStillUseTheSafeFallback() {
        assertEquals(
            UiState.Error("failed"),
            RemoteListing.state(Result.failure<List<String>>(NullPointerException()), "empty", "failed"),
        )
        assertEquals(
            UiState.Error("failed"),
            RemoteListing.state(Result.failure<List<String>>(IllegalStateException("  ")), "empty", "failed"),
        )
    }
}
