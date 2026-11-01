// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import java.util.concurrent.atomic.AtomicBoolean

/** One-shot gate for work that must wait until the Activity has drawn a frame. */
internal class StartupFirstDrawGate(private val onFirstDraw: () -> Unit) {
    private val triggered = AtomicBoolean(false)

    fun onFirstDrawn() {
        if (triggered.compareAndSet(false, true)) onFirstDraw()
    }
}
