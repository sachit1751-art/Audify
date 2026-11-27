package com.music.audify.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The destination that actually received a newly created playlist. */
internal sealed interface PlaylistCreationResult {
    data class RemoteCreated(val playlistId: String) : PlaylistCreationResult
    data class SavedLocally(
        val browseId: String,
        val remoteFailure: Throwable? = null,
    ) : PlaylistCreationResult
    data class Failed(val cause: Throwable) : PlaylistCreationResult
}

internal data class PlaylistChunkAddResult(
    val requestedSongCount: Int,
    val addedSongCount: Int,
    val failedVideoIds: List<String>,
    val failures: List<Throwable>,
) {
    val isComplete: Boolean get() = failedVideoIds.isEmpty()
}

/** Attempts every batch and retains the exact IDs that need a retry. */
internal suspend fun addPlaylistSongsInChunks(
    videoIds: List<String>,
    addChunk: suspend (List<String>) -> Result<*>,
): PlaylistChunkAddResult {
    val ids = videoIds.filter(String::isNotBlank).distinct()
    var added = 0
    val failedIds = mutableListOf<String>()
    val failures = mutableListOf<Throwable>()
    for (chunk in ids.chunked(50)) {
        val result = try {
            addChunk(chunk)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure<Any?>(failure)
        }
        result.fold(
            // A successful add response may omit per-entry set IDs; the
            // repository documents that as normal because the add still landed.
            onSuccess = { added += chunk.size },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                failedIds += chunk
                failures += failure
            },
        )
    }
    return PlaylistChunkAddResult(ids.size, added, failedIds, failures)
}

/**
 * Try YouTube Music for signed-in listeners, retaining the playlist locally if
 * that write fails. Signed-out listeners save locally without making a remote
 * request. The callbacks keep the policy independently testable.
 */
internal suspend fun createPlaylistWithLocalFallback(
    signedIn: Boolean,
    createRemote: suspend () -> Result<String>,
    saveLocal: suspend () -> String,
): PlaylistCreationResult {
    return if (signedIn) {
        val remote = try {
            createRemote()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        remote.fold(
            onSuccess = { PlaylistCreationResult.RemoteCreated(it) },
            onFailure = { remoteFailure ->
                if (remoteFailure is CancellationException) throw remoteFailure
                try {
                    PlaylistCreationResult.SavedLocally(saveLocal(), remoteFailure)
                } catch (localFailure: Exception) {
                    if (localFailure is CancellationException) throw localFailure
                    if (localFailure !== remoteFailure) localFailure.addSuppressed(remoteFailure)
                    PlaylistCreationResult.Failed(localFailure)
                }
            },
        )
    } else {
        try {
            PlaylistCreationResult.SavedLocally(saveLocal())
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            PlaylistCreationResult.Failed(failure)
        }
    }
}

/**
 * Creates a remote playlist in bounded batches. If a later batch fails, delete
 * the staged remote playlist before falling back locally; never report a
 * partially populated remote playlist as a successful import.
 */
internal suspend fun createPlaylistWithCompensatingRollback(
    videoIds: List<String>,
    createRemote: suspend (initialBatch: List<String>) -> Result<String>,
    addRemote: suspend (playlistId: String, batch: List<String>) -> Result<*>,
    deleteRemote: suspend (playlistId: String) -> Result<*>,
    saveLocal: suspend () -> String,
): PlaylistCreationResult {
    val ids = videoIds.filter(String::isNotBlank)
    if (ids.isEmpty()) return PlaylistCreationResult.Failed(
        IllegalArgumentException("There are no playable track IDs to import"),
    )

    suspend fun localFallback(remoteFailure: Throwable?): PlaylistCreationResult = try {
        PlaylistCreationResult.SavedLocally(saveLocal(), remoteFailure)
    } catch (localFailure: Exception) {
        if (localFailure is CancellationException) throw localFailure
        if (remoteFailure != null && remoteFailure !== localFailure) localFailure.addSuppressed(remoteFailure)
        PlaylistCreationResult.Failed(localFailure)
    }

    val created = try {
        createRemote(ids.take(50))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
    val playlistId = created.getOrElse {
        if (it is CancellationException) throw it
        return localFallback(it)
    }

    try {
        ids.drop(50).chunked(50).forEach { batch ->
            val result = try {
                addRemote(playlistId, batch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure<Any?>(failure)
            }
            result.getOrThrow()
        }
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) {
            try {
                deleteRemote(playlistId).onFailure(cancelled::addSuppressed)
            } catch (rollbackFailure: Exception) {
                cancelled.addSuppressed(rollbackFailure)
            }
        }
        throw cancelled
    } catch (failure: Exception) {
        val rollback = try {
            deleteRemote(playlistId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rollbackFailure: Exception) {
            Result.failure<Any?>(rollbackFailure)
        }
        if (rollback.isFailure) {
            val partial = IllegalStateException(
                "Playlist import failed and remote playlist $playlistId could not be rolled back",
                failure,
            )
            rollback.exceptionOrNull()?.let(partial::addSuppressed)
            return PlaylistCreationResult.Failed(partial)
        }
        return localFallback(failure)
    }

    return PlaylistCreationResult.RemoteCreated(playlistId)
}
