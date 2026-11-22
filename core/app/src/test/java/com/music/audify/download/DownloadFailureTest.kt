package com.music.audify.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFailureTest {
    @Test
    fun storageExhaustionIsNotReportedAsAConnectionFailure() {
        assertEquals(
            STORAGE_MESSAGE,
            friendlyDownloadFailure(java.io.IOException("No space left on device")),
        )
        assertEquals(
            STORAGE_MESSAGE,
            friendlyDownloadFailure(IllegalStateException("write failed", java.io.IOException("ENOSPC"))),
        )
    }

    @Test
    fun unrelatedFailuresKeepTheirExistingUserFacingMeaning() {
        assertEquals(
            "Download failed — check your connection",
            friendlyDownloadFailure(java.io.IOException("connection reset")),
        )
        assertEquals(
            "Android won't store WebM files in Music",
            friendlyDownloadFailure(IllegalStateException("Android won't store WebM files in Music")),
        )
    }

    private companion object {
        const val STORAGE_MESSAGE =
            "Not enough storage space to save this download. Free up space and try again."
    }
}
