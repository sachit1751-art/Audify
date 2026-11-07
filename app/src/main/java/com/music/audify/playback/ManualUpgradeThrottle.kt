// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

/**
 * App-side gate for explicit quality retries.
 *
 * Automatic upgrades never pass through this object. Returning the remaining
 * wait rather than a Boolean gives the log enough information to distinguish
 * a deliberate throttle from a search which failed to start.
 */
internal class ManualUpgradeThrottle(private val cooldownMs: Long) {
    private var lastAcceptedAtMs: Long? = null

    @Synchronized
    fun tryAcquire(nowMs: Long): Long {
        val previous = lastAcceptedAtMs
        if (previous != null) {
            val remaining = cooldownMs - (nowMs - previous)
            if (remaining > 0) return remaining
        }
        lastAcceptedAtMs = nowMs
        return 0L
    }
}
