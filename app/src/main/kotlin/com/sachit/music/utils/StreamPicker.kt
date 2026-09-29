/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

/**
 * Pure helpers for lossless-audio detection and best-stream selection (plan 016).
 *
 * The innertubex extractor currently returns a single resolved stream, so the
 * candidate-based picker is exercised by unit tests and ready for the moment the
 * fork surfaces multiple audio variants; [isLossless] drives the honest
 * "Lossless" badge in ShowMediaInfo — shown only when the active format really
 * is FLAC/ALAC, never as a promise.
 */
object StreamPicker {

    /** Minimal format description needed to rank and classify an audio stream. */
    data class Candidate(
        val mimeType: String?,
        val codecs: String?,
        val bitrate: Int?,
    )

    private val losslessMarkers = listOf("flac", "alac")

    /** True only for genuinely lossless codecs (FLAC/ALAC) in mimeType or codec string. */
    fun isLossless(candidate: Candidate): Boolean {
        val haystack = "${candidate.mimeType.orEmpty()} ${candidate.codecs.orEmpty()}".lowercase()
        return losslessMarkers.any { haystack.contains(it) }
    }

    /**
     * Picks the best stream: lossless (FLAC/ALAC) first, then highest bitrate.
     * When [wantLossless] is false — or no lossless candidate exists — the
     * highest-bitrate lossy stream wins. Returns null for an empty input.
     */
    fun pickBestStream(streams: List<Candidate>, wantLossless: Boolean): Candidate? {
        if (streams.isEmpty()) return null
        val (lossless, lossy) = streams.partition { isLossless(it) }
        return when {
            wantLossless && lossless.isNotEmpty() -> lossless.maxByOrNull { it.bitrate ?: 0 }
            lossy.isNotEmpty() -> lossy.maxByOrNull { it.bitrate ?: 0 }
            else -> lossless.maxByOrNull { it.bitrate ?: 0 }
        }
    }
}
