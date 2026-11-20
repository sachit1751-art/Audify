package com.music.audify

import com.music.audify.data.local.needsPlaylistSnapshotSync
import com.music.audify.ui.shouldQueueGuestLibraryMigration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSnapshotSyncTest {
    @Test
    fun guestLibraryMigratesOnFirstSignInButNotWhenSwitchingAccounts() {
        assertTrue(shouldQueueGuestLibraryMigration(wasSignedIn = false))
        assertFalse(shouldQueueGuestLibraryMigration(wasSignedIn = true))
    }

    @Test
    fun newGuestPlaylistIsQueuedForFirstAccountSync() {
        assertTrue(
            needsPlaylistSnapshotSync(
                status = null,
                syncedTitle = null,
                syncedVideoIds = null,
                title = "Road Trip",
                videoIds = listOf("track-a", "track-b"),
            ),
        )
    }

    @Test
    fun unchangedSyncedPlaylistIsNotQueuedAgainOnEveryAppLaunch() {
        assertFalse(
            needsPlaylistSnapshotSync(
                status = "synced",
                syncedTitle = "Road Trip",
                syncedVideoIds = listOf("track-a", "track-b"),
                title = "Road Trip",
                videoIds = listOf("track-a", "track-b"),
            ),
        )
    }

    @Test
    fun changedPlaylistTitleOrTrackOrderIsQueuedAgain() {
        assertTrue(
            needsPlaylistSnapshotSync(
                status = "synced",
                syncedTitle = "Road Trip",
                syncedVideoIds = listOf("track-a", "track-b"),
                title = "Summer Drive",
                videoIds = listOf("track-a", "track-b"),
            ),
        )
        assertTrue(
            needsPlaylistSnapshotSync(
                status = "synced",
                syncedTitle = "Road Trip",
                syncedVideoIds = listOf("track-a", "track-b"),
                title = "Road Trip",
                videoIds = listOf("track-b", "track-a"),
            ),
        )
    }

    @Test
    fun partialOrConflictedSyncIsNotMistakenForCompletedSync() {
        assertTrue(
            needsPlaylistSnapshotSync(
                status = "partial",
                syncedTitle = "Road Trip",
                syncedVideoIds = listOf("track-a"),
                title = "Road Trip",
                videoIds = listOf("track-a", "track-b"),
            ),
        )
        assertTrue(
            needsPlaylistSnapshotSync(
                status = "conflict",
                syncedTitle = "Road Trip",
                syncedVideoIds = listOf("track-a", "track-b"),
                title = "Road Trip",
                videoIds = listOf("track-a", "track-b"),
            ),
        )
    }
}
