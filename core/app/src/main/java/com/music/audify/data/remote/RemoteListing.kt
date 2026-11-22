package com.music.audify.data.remote

import com.music.audify.data.model.UiState

/**
 * What a remote library page shows for a listing. A failed listing names
 * its cause: a share that answers with an error is not an empty share, and
 * "no audio files" would send the user hunting through folder paths for a
 * login or network problem.
 */
object RemoteListing {

    fun <T> state(
        listing: Result<List<T>>,
        emptyMessage: String,
        failureMessage: String,
    ): UiState<List<T>> =
        listing.fold(
            onSuccess = {
                items -> if (items.isEmpty()) UiState.Error(emptyMessage, retryable = false) else UiState.Success(items)
            },
            // Remote exceptions can contain SMB paths, server names, usernames,
            // or implementation details. Keep those out of user-visible state;
            // detailed diagnostics belong in the existing redacted log path.
            onFailure = { UiState.Error(failureMessage) },
        )
}
