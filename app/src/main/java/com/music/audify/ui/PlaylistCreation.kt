package com.music.audify.ui

import kotlinx.coroutines.CancellationException

/** The destination that actually received a newly created playlist. */
internal sealed interface PlaylistCreationResult {
    data class RemoteCreated(val playlistId: String) : PlaylistCreationResult
    data class SavedLocally(
        val browseId: String,
        val remoteFailure: Throwable? = null,
    ) : PlaylistCreationResult
    data class Failed(val cause: Throwable) : PlaylistCreationResult
}

/**
 * Try YouTube Music for signed-in listeners, retaining the playlist locally if
 * that write fails. Signed-out listeners save locally without making a remote
 * request. The callbacks keep the policy independently testable.
 */
internal suspend fun createPlaylistWithLocalFallback(
    signedIn: Boolean,
    createRemote: suspend () -> Result<String>,
    saveLocal: () -> String,
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
