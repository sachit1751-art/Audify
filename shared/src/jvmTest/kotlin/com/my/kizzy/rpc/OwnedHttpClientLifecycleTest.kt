package com.my.kizzy.rpc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OwnedHttpClientLifecycleTest {
    @Test
    fun closesOwnedClientAfterSuccessfulRequest() = runBlocking {
        var closeCount = 0
        val result = withOwnedHttpClient({ closeCount++ }) { "ok" }
        assertEquals("ok", result)
        assertEquals(1, closeCount)
    }

    @Test
    fun closesOwnedClientWhenRequestFails() = runBlocking {
        var closeCount = 0
        assertFailsWith<IllegalStateException> {
            withOwnedHttpClient({ closeCount++ }) { error("request failed") }
        }
        assertEquals(1, closeCount)
    }
}
