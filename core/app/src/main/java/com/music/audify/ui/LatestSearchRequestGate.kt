package com.music.audify.ui

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Bounds overlapping search I/O without cancelling a request on the shared HTTP client. */
internal class LatestSearchRequestGate(maxConcurrentRequests: Int = 2) {
    private val permits = Semaphore(maxConcurrentRequests.also { require(it > 0) })

    suspend fun <T> execute(
        requestId: Long,
        latestRequestId: () -> Long,
        request: suspend () -> T,
        publish: (T) -> Unit,
    ) {
        permits.withPermit {
            if (requestId != latestRequestId()) return@withPermit
            val result = request()
            if (requestId == latestRequestId()) publish(result)
        }
    }
}
