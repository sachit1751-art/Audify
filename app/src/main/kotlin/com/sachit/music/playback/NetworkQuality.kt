/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import android.net.ConnectivityManager
import com.sachit.music.constants.AudioQuality

/** Coarse class of the network the device is currently on. */
enum class NetworkClass { WIFI, METERED, OFFLINE }

/**
 * Resolve the effective maximum audio quality for the current network.
 *
 * Priority: an explicit per-network key wins, then the global [AudioQualityKey] value,
 * then [AudioQuality.AUTO]. Unset network keys (null) mean existing users keep today's
 * behavior exactly.
 *
 * [OFFLINE] returns `global ?: AUTO` to keep the function total; nothing resolves a
 * stream while offline in practice.
 */
fun resolveAudioQuality(
    networkClass: NetworkClass,
    global: AudioQuality?,
    wifi: AudioQuality?,
    metered: AudioQuality?,
): AudioQuality =
    when (networkClass) {
        NetworkClass.WIFI -> wifi ?: global ?: AudioQuality.AUTO
        NetworkClass.METERED -> metered ?: global ?: AudioQuality.AUTO
        NetworkClass.OFFLINE -> global ?: AudioQuality.AUTO
    }

/**
 * Parse a stored quality key value into [AudioQuality], or null when unset, blank, or
 * unknown (callers then fall back to the global key). "VERY_HIGH" is a legacy alias of HIGH.
 */
fun String?.toAudioQualityOrNull(): AudioQuality? =
    when (this) {
        null, "" -> null
        "VERY_HIGH" -> AudioQuality.HIGH
        else -> AudioQuality.entries.find { it.name == this }
    }

/**
 * Map the current active network to its [NetworkClass]. Kept separate from the pure
 * resolver above so the resolution rules stay unit-testable without Android types.
 */
fun ConnectivityManager.networkClass(): NetworkClass =
    try {
        if (activeNetwork == null) {
            NetworkClass.OFFLINE
        } else {
            if (isActiveNetworkMetered) NetworkClass.METERED else NetworkClass.WIFI
        }
    } catch (e: Exception) {
        NetworkClass.OFFLINE
    }
