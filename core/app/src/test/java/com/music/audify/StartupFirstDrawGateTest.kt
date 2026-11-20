// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupFirstDrawGateTest {
    @Test
    fun prewarmWaitsForFirstDrawAndRunsOnlyOnce() {
        var prewarmCalls = 0
        val gate = StartupFirstDrawGate { prewarmCalls++ }

        assertEquals(0, prewarmCalls)
        gate.onFirstDrawn()
        assertEquals(1, prewarmCalls)
        gate.onFirstDrawn()
        assertEquals(1, prewarmCalls)
    }
}
