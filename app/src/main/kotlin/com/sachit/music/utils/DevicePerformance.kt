/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import android.app.ActivityManager
import android.content.Context
import kotlin.math.roundToInt

/**
 * Low-end device detection and the tuning constants derived from it.
 *
 * The tuning functions take primitives instead of a Context so they can be
 * unit-tested without Android framework mocks. `memoryClassMb` is the app
 * heap class in megabytes, i.e. `ActivityManager.memoryClass`.
 */

/** Devices with a per-process heap below this size are treated as low-end. */
private const val LOW_END_MEMORY_CLASS_MB = 128

/** Fraction of the app heap used for Coil's in-memory image cache. */
fun lowEndMemoryCacheFraction(memoryClassMb: Int): Double =
    if (memoryClassMb < LOW_END_MEMORY_CLASS_MB) 0.10 else 0.15

/**
 * Image crossfades cost an extra compositing pass per image. Low-RAM devices
 * are better served by hard cuts during fast scrolling.
 */
fun lowEndUseCrossfade(memoryClassMb: Int): Boolean =
    memoryClassMb >= LOW_END_MEMORY_CLASS_MB

/**
 * Pixel cap for large artwork decodes. A ~320dp card never needs more than
 * `density * 320` pixels; low-end devices are capped at 480 to avoid decoding
 * images at several times their on-screen size. Capable devices keep up to
 * 1080 for sharp full-width artwork.
 */
fun artworkDecodeCapPx(density: Float, memoryClassMb: Int): Int =
    if (memoryClassMb < LOW_END_MEMORY_CLASS_MB) {
        480
    } else {
        (density * 480f).roundToInt().coerceIn(480, 1080)
    }

/** The app's per-process heap class in megabytes. */
fun memoryClassMb(context: Context): Int =
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass