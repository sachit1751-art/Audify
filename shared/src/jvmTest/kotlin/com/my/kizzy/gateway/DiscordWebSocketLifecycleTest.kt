package com.my.kizzy.gateway

import kotlin.test.Test
import kotlin.test.assertFalse

class DiscordWebSocketLifecycleTest {
    @Test
    fun closingGatewayIsIdempotentAndPreventsReconnect() {
        val gateway = DiscordWebSocket(token = "test-token")
        gateway.close()
        gateway.connect()
        gateway.retryNow()
        assertFalse(gateway.isWebSocketConnected())
        gateway.close()
    }
}
