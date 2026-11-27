package com.music.audify.ui

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.net.UnknownHostException
import java.net.NoRouteToHostException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestSearchRequestGateTest {

    @Test
    fun `offline search failures are distinct from server and parsing failures`() {
        assertEquals(true, isSearchNetworkUnavailable(UnknownHostException("music.youtube.com")))
        assertEquals(true, isSearchNetworkUnavailable(IOException("Network is unreachable")))
        assertEquals(true, isSearchNetworkUnavailable(IOException("wrapped", NoRouteToHostException("offline"))))
        assertEquals(false, isSearchNetworkUnavailable(IOException("HTTP 503 Service Unavailable")))
        assertEquals(false, isSearchNetworkUnavailable(IllegalArgumentException("Malformed search response")))
    }

    @Test
    fun `overlapping searches are not cancelled and only the latest response is published`() = runTest {
        val gate = LatestSearchRequestGate(maxConcurrentRequests = 2)
        val latestId = AtomicLong(1L)
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstResponse = CompletableDeferred<String>()
        val secondResponse = CompletableDeferred<String>()
        val published = mutableListOf<String>()

        val first = async {
            gate.execute(
                requestId = 1L,
                latestRequestId = latestId::get,
                request = {
                    firstStarted.complete(Unit)
                    firstResponse.await()
                },
                publish = published::add,
            )
        }
        firstStarted.await()

        latestId.set(2L)
        val second = async {
            gate.execute(
                requestId = 2L,
                latestRequestId = latestId::get,
                request = {
                    secondStarted.complete(Unit)
                    secondResponse.await()
                },
                publish = published::add,
            )
        }
        secondStarted.await()
        assertTrue("the newer request must run without cancelling the older request", !first.isCompleted)

        secondResponse.complete("new result")
        second.await()
        firstResponse.complete("stale result")
        first.await()

        assertEquals(listOf("new result"), published)
    }

    @Test
    fun `obsolete request waiting for a permit never reaches the provider`() = runTest {
        val gate = LatestSearchRequestGate(maxConcurrentRequests = 1)
        val latestId = AtomicLong(1L)
        val firstResponse = CompletableDeferred<Unit>()
        var obsoleteProviderCalls = 0

        val first = async {
            gate.execute(1L, latestId::get, { firstResponse.await() }, {})
        }
        val obsolete = async {
            gate.execute(2L, latestId::get, { obsoleteProviderCalls++; Unit }, {})
        }
        latestId.set(3L)
        val latest = async {
            gate.execute(3L, latestId::get, { Unit }, {})
        }

        firstResponse.complete(Unit)
        first.await()
        obsolete.await()
        latest.await()

        assertEquals(0, obsoleteProviderCalls)
    }
}
