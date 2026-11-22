package com.music.audify.ui

import com.music.audify.data.LocalMediaRepository
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalLibraryStateTest {
    private val song = Song(videoId = "local-1", title = "Local track", artist = "Artist", thumbnailUrl = null)

    @Test
    fun `complete empty scan is not confused with provider failure`() {
        val state = localLibraryUiState(
            LocalMediaRepository.LocalMusicScanResult.Complete(emptyList()),
            "empty", "partial", "failed", "access required",
        )
        assertEquals(UiState.Error("empty", retryable = false), state)
    }

    @Test
    fun `complete scan has no warning`() {
        val state = localLibraryUiState(
            LocalMediaRepository.LocalMusicScanResult.Complete(listOf(song)),
            "empty", "partial", "failed", "access required",
        ) as UiState.Success
        assertEquals(listOf(song), state.data)
        assertNull(state.warning)
    }

    @Test
    fun `partial scan keeps available rows and exposes warning`() {
        val state = localLibraryUiState(
            LocalMediaRepository.LocalMusicScanResult.Partial(
                listOf(song), LocalMediaRepository.LocalMusicScanIssue.NESTING_LIMIT,
            ),
            "empty", "partial", "failed", "access required",
        ) as UiState.Success
        assertEquals(listOf(song), state.data)
        assertEquals("partial", state.warning)
    }

    @Test
    fun `partial scan without readable rows uses failure guidance`() {
        val state = localLibraryUiState(
            LocalMediaRepository.LocalMusicScanResult.Partial(
                emptyList(), LocalMediaRepository.LocalMusicScanIssue.QUERY_FAILED,
            ),
            "empty", "partial", "failed", "access required",
        )
        assertEquals(UiState.Error("failed"), state)
    }

    @Test
    fun `failed scan is not reported as an empty library`() {
        val state = localLibraryUiState(
            LocalMediaRepository.LocalMusicScanResult.Failed(LocalMediaRepository.LocalMusicScanIssue.QUERY_FAILED),
            "empty", "partial", "failed", "access required",
        )
        assertEquals(UiState.Error("failed"), state)
    }

    @Test
    fun `access revoked during scan is reported as an access failure`() {
        val state = localLibraryUiState(
            LocalMediaRepository.LocalMusicScanResult.Failed(LocalMediaRepository.LocalMusicScanIssue.ACCESS_UNAVAILABLE),
            "empty", "partial", "failed", "access required",
        )
        assertEquals(UiState.Error("access required", retryable = false), state)
    }
}
