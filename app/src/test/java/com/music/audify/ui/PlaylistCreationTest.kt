package com.music.audify.ui

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCreationTest {
    @Test
    fun signedInRemoteSuccessDoesNotCreateLocalCopy() = runTest {
        var localSaveCalled = false

        val result = createPlaylistWithLocalFallback(
            signedIn = true,
            createRemote = { Result.success("playlist-123") },
            saveLocal = { localSaveCalled = true; "local:playlist:1" },
        )

        assertEquals(PlaylistCreationResult.RemoteCreated("playlist-123"), result)
        assertFalse(localSaveCalled)
    }

    @Test
    fun signedInRemoteFailureSavesLocally() = runTest {
        val remoteFailure = IllegalStateException("remote unavailable")
        val result = createPlaylistWithLocalFallback(
            signedIn = true,
            createRemote = { Result.failure(remoteFailure) },
            saveLocal = { "local:playlist:1" },
        )

        val savedLocally = result as PlaylistCreationResult.SavedLocally
        assertEquals("local:playlist:1", savedLocally.browseId)
        assertSame(remoteFailure, savedLocally.remoteFailure)
    }

    @Test
    fun signedOutSkipsRemoteAndSavesLocally() = runTest {
        var remoteCalled = false

        val result = createPlaylistWithLocalFallback(
            signedIn = false,
            createRemote = { remoteCalled = true; Result.success("unexpected") },
            saveLocal = { "local:playlist:2" },
        )

        assertEquals(PlaylistCreationResult.SavedLocally("local:playlist:2"), result)
        assertFalse(remoteCalled)
    }

    @Test
    fun failedLocalFallbackPreservesBothFailures() = runTest {
        val remoteFailure = IllegalStateException("remote unavailable")
        val localFailure = IllegalArgumentException("storage unavailable")

        val result = createPlaylistWithLocalFallback(
            signedIn = true,
            createRemote = { Result.failure(remoteFailure) },
            saveLocal = { throw localFailure },
        )

        assertTrue(result is PlaylistCreationResult.Failed)
        assertEquals(localFailure, (result as PlaylistCreationResult.Failed).cause)
        assertEquals(listOf(remoteFailure), localFailure.suppressed.toList())
    }

    @Test
    fun cancelledRemoteCreationDoesNotCreateLocalPlaylist() = runTest {
        var localSaveCalled = false

        val result = runCatching {
            createPlaylistWithLocalFallback(
                signedIn = true,
                createRemote = { Result.failure(CancellationException("cancelled")) },
                saveLocal = { localSaveCalled = true; "local:playlist:3" },
            )
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(localSaveCalled)
    }
}
