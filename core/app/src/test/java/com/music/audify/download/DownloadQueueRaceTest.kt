package com.music.audify.download

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueRaceTest {
    @Test
    fun staleCancelledWorkerCannotClearTheNewRetryClaim() {
        val oldJob = Job()
        val newJob = Job()
        val running = linkedMapOf("same-track" to RunningDownloadClaim(token = 2L))

        // The old worker's cancellation unwinds after a re-enqueue has already
        // claimed the same id. Both attachment and cleanup must be token-bound.
        assertFalse(attachRunningJobIfClaimMatches(running, "same-track", 1L, oldJob))
        assertTrue(attachRunningJobIfClaimMatches(running, "same-track", 2L, newJob))
        assertFalse(removeRunningClaimIfMatches(running, "same-track", 1L))
        assertSame(newJob, running["same-track"]?.job)
        assertTrue(removeRunningClaimIfMatches(running, "same-track", 2L))
        assertFalse("same-track" in running)
    }

    @Test
    fun workerFinishingBeforeNewJobIsAttachedCannotRemovePendingClaim() {
        val oldJob = Job()
        val running = linkedMapOf("same-track" to RunningDownloadClaim(token = 8L))

        assertFalse(attachRunningJobIfClaimMatches(running, "same-track", 7L, oldJob))
        assertTrue("same-track" in running)
    }

    @Test
    fun cancelledClaimBlocksSameTrackUntilItsWorkerHasUnwound() {
        val retiring = linkedMapOf("same-track" to mutableSetOf(11L))

        assertFalse(canStartDownloadClaim("same-track", retiring))
        assertFalse(removeRetiringDownloadClaim(retiring, "same-track", 10L))
        assertFalse(canStartDownloadClaim("same-track", retiring))
        assertTrue(removeRetiringDownloadClaim(retiring, "same-track", 11L))
        assertTrue(canStartDownloadClaim("same-track", retiring))
    }

    @Test
    fun enqueueRacingDrainShutdownRequestsAnotherDrain() {
        assertTrue(shouldRestartDownloadDrain(restartRequested = true, queueBusy = false))
        assertTrue(shouldRestartDownloadDrain(restartRequested = false, queueBusy = true))
        assertFalse(shouldRestartDownloadDrain(restartRequested = false, queueBusy = false))
    }
}
