package com.music.audify.ui

import com.music.audify.data.LocalMediaRepository
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState

internal fun localLibraryUiState(
    scan: LocalMediaRepository.LocalMusicScanResult,
    noAudioMessage: String,
    partialMessage: String,
    failedMessage: String,
    accessMessage: String,
): UiState<List<Song>> = when (scan) {
    is LocalMediaRepository.LocalMusicScanResult.Complete ->
        if (scan.songs.isEmpty()) UiState.Error(noAudioMessage, retryable = false) else UiState.Success(scan.songs)
    is LocalMediaRepository.LocalMusicScanResult.Partial ->
        if (scan.songs.isEmpty()) UiState.Error(failedMessage)
        else UiState.Success(scan.songs, warning = partialMessage)
    is LocalMediaRepository.LocalMusicScanResult.Failed -> UiState.Error(
        if (scan.issue == LocalMediaRepository.LocalMusicScanIssue.ACCESS_UNAVAILABLE) accessMessage
        else failedMessage,
        retryable = scan.issue != LocalMediaRepository.LocalMusicScanIssue.ACCESS_UNAVAILABLE,
    )
}
