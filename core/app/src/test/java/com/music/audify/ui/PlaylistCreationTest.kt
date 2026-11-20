package com.music.audify.ui

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import com.music.audify.data.spotify.persistBeforePublish
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
    fun signedOutWaitsForSuspendingLocalPersistenceBeforeReportingSuccess() = runTest {
        var persisted = false

        val result = createPlaylistWithLocalFallback(
            signedIn = false,
            createRemote = { error("Signed-out creation must not call the remote service") },
            saveLocal = {
                delay(10)
                persisted = true
                "local:playlist:durable"
            },
        )

        assertTrue(persisted)
        assertEquals(PlaylistCreationResult.SavedLocally("local:playlist:durable"), result)
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

    @Test
    fun chunkFailureRetainsExactFailedIdsAndCountsOnlySuccessfulChunks() = runTest {
        val ids = (1..125).map { "video-$it" }
        val attempted = mutableListOf<List<String>>()
        val result = addPlaylistSongsInChunks(ids) { chunk ->
            attempted += chunk
            if (chunk.first() == "video-51") Result.failure<Unit>(IllegalStateException("batch rejected"))
            else Result.success(Unit)
        }

        assertEquals(listOf(50, 50, 25), attempted.map { it.size })
        assertEquals(125, result.requestedSongCount)
        assertEquals(75, result.addedSongCount)
        assertEquals(ids.subList(50, 100), result.failedVideoIds)
        assertFalse(result.isComplete)
    }

    @Test
    fun failedPersistenceDoesNotPublishMutation() = runTest {
        val mutex = Mutex()
        var published = false

        val failure = runCatching {
            persistBeforePublish<List<String>, Unit>(
                mutex = mutex,
                persist = { throw IllegalStateException("commit failed") },
                publish = { published = true },
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertFalse(published)
    }

    @Test
    fun mutationsSerializePersistenceAndPublishInCommitOrder() = runTest {
        val mutex = Mutex()
        val events = mutableListOf<String>()
        val first = launch {
            persistBeforePublish<String, Unit>(
                mutex = mutex,
                persist = {
                    events += "first-commit-start"
                    delay(10)
                    events += "first-commit-end"
                    "first" to Unit
                },
                publish = { events += "$it-published" },
            )
        }
        val second = launch {
            persistBeforePublish<String, Unit>(
                mutex = mutex,
                persist = {
                    events += "second-commit"
                    "second" to Unit
                },
                publish = { events += "$it-published" },
            )
        }
        first.join()
        second.join()

        assertEquals(
            listOf("first-commit-start", "first-commit-end", "first-published", "second-commit", "second-published"),
            events,
        )
    }
}
