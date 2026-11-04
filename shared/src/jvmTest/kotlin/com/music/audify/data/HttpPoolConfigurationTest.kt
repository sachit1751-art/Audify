package com.music.audify.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HttpPoolConfigurationTest {
    @Test
    fun sharedOkHttpClientUsesBoundedConnectionPoolAndDispatcher() {
        val client = Http.client
        assertEquals(16, HTTP_POOL_MAX_IDLE_CONNECTIONS)
        assertEquals(5L, HTTP_POOL_KEEP_ALIVE_MINUTES)
        assertEquals(16, HTTP_MAX_REQUESTS_PER_HOST)
        assertEquals(HTTP_MAX_REQUESTS_PER_HOST, client.dispatcher.maxRequestsPerHost)
        assertEquals(64, client.dispatcher.maxRequests)
        assertSame(client.connectionPool, client.newBuilder().build().connectionPool)
    }
}
