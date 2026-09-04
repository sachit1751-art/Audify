/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import kotlin.math.sqrt

/**
 * Pure FFT → visualizer-bar math, isolated from Android framework classes so it
 * can be unit-tested on the JVM.
 *
 * Input format matches [android.media.audiofx.Visualizer.getFft]: a byte array
 * of [capture size] entries where index 0 holds the real DC component, index 1
 * the real Nyquist component, and every following pair `(2i, 2i + 1)` is the
 * real/imaginary part of frequency bin `i`. Both DC and Nyquist are skipped, as
 * they carry no useful spectral energy for a bar display.
 */

/** Largest possible magnitude of a single FFT pair (two full-scale bytes). */
private const val MAX_FFT_MAGNITUDE = 181f

/**
 * Computes `barCount` normalized bar heights (0f..1f) from a raw FFT byte array.
 *
 * Bins are aggregated into roughly log-spaced buckets (via a square-root index
 * map) so low frequencies get proportional visual weight, then a square root is
 * applied to the bucket magnitude for a pleasant dynamic range. Deterministic:
 * same input always yields the same output.
 */
fun computeVisualizerLevels(
    fft: ByteArray,
    barCount: Int,
): FloatArray {
    if (barCount <= 0) return FloatArray(0)
    if (fft.size < 4) return FloatArray(barCount)

    val usableBins = (fft.size - 2) / 2
    if (usableBins <= 0) return FloatArray(barCount)

    fun magnitudeOf(pairIndex: Int): Float {
        val real = fft[2 + pairIndex * 2].toFloat()
        val imaginary = fft[3 + pairIndex * 2].toFloat()
        val normalized = (sqrt(real * real + imaginary * imaginary) / MAX_FFT_MAGNITUDE).coerceIn(0f, 1f)
        // Square root lifts quiet content into view; full-scale still reads as 1.
        return sqrt(normalized)
    }

    val levels = FloatArray(barCount)
    val lastBinIndex = usableBins - 1
    var previousEnd = 0

    for (bar in 0 until barCount) {
        val fraction = (bar + 1).toDouble() / barCount
        // sqrt index map: later bars cover quadratically wider (higher) ranges.
        val end = (lastBinIndex * sqrt(fraction)).toInt().coerceAtLeast(previousEnd + 1)

        var peak = 0f
        for (bin in previousEnd until end.coerceAtMost(usableBins)) {
            val magnitude = magnitudeOf(bin)
            if (magnitude > peak) peak = magnitude
        }
        levels[bar] = peak
        previousEnd = end
    }

    return levels
}
