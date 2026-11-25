package com.music.audify.playback

import com.music.audify.data.settings.SkipSegment

/** A transition interval that contains only audio the listener will actually hear. */
internal data class AudibleTransitionWindow(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Returns the last audible media position after applying the selected out-point. */
internal fun effectiveAudiblePlaybackEndMs(
    actualDurationMs: Long,
    selectedEndMs: Long?,
    skipSegments: List<SkipSegment>,
): Long {
    val playbackEndMs = minOf(actualDurationMs, selectedEndMs ?: actualDurationMs)
    val trailingSkip = skipSegments.firstOrNull { it.isValid && it.startMs < playbackEndMs && it.endMs >= playbackEndMs }
    return minOf(playbackEndMs, trailingSkip?.startMs ?: playbackEndMs)
}

/**
 * Moves a planned transition past any skipped interval it would overlap.
 *
 * The requested interval is expressed on the source track's media timeline.
 * If the playhead has already jumped over its original start, the remaining
 * fade starts at the current position rather than waiting for a point that can
 * no longer be reached. The result is null when no audible transition window
 * remains inside the selected playback window.
 */
internal fun audibleTransitionWindow(
    requestedStartMs: Long,
    requestedEndMs: Long,
    currentPositionMs: Long,
    playbackStartMs: Long,
    playbackEndMs: Long,
    skipSegments: List<SkipSegment>,
): AudibleTransitionWindow? {
    if (requestedEndMs <= requestedStartMs || playbackEndMs <= playbackStartMs) return null

    val requestedDurationMs = requestedEndMs - requestedStartMs
    var startMs = maxOf(requestedStartMs, playbackStartMs, currentPositionMs)
    var endMs = minOf(requestedEndMs, playbackEndMs)

    // A seek can move the playhead into or beyond a planned transition. Keep
    // the original end where possible and shorten the remaining fade instead
    // of scheduling against a media position that has already been skipped.
    val orderedSegments = skipSegments.asSequence()
        .filter { it.isValid && it.endMs > playbackStartMs && it.startMs < playbackEndMs }
        .sortedBy { it.startMs }
        .toList()

    for (segment in orderedSegments) {
        val overlapsRequested = segment.startMs < requestedEndMs && segment.endMs > requestedStartMs
        val overlapsCurrent = segment.startMs < endMs && segment.endMs > startMs
        val containsPlayhead = currentPositionMs >= segment.startMs && currentPositionMs < segment.endMs
        if (!overlapsRequested && !overlapsCurrent && !containsPlayhead) continue

        startMs = maxOf(segment.endMs, currentPositionMs, playbackStartMs)
        val playheadBeforeSkippedRange = currentPositionMs < segment.startMs
        val playheadStillBeforePlannedEnd = currentPositionMs < requestedEndMs
        endMs = minOf(
            playbackEndMs,
            when {
                playheadBeforeSkippedRange -> maxOf(endMs, startMs + requestedDurationMs)
                playheadStillBeforePlannedEnd -> maxOf(endMs, requestedEndMs)
                else -> startMs + requestedDurationMs
            },
        )
        if (endMs <= startMs) return null
    }

    if (endMs <= startMs) return null

    // Guard against a chain of adjacent/overlapping ranges even if callers
    // supply an unnormalised list.
    if (orderedSegments.any { it.startMs < endMs && it.endMs > startMs }) return null
    return AudibleTransitionWindow(startMs, endMs)
}

/**
 * Chooses an incoming cue that can play through the overlap without hitting a
 * saved skip range. A skip later than the overlap is left for the normal
 * playback ticker; a skip intersecting the overlap moves the cue beyond it.
 */
internal fun audibleIncomingCueMs(
    requestedCueMs: Long,
    overlapMs: Long,
    playbackStartMs: Long,
    playbackEndMs: Long,
    skipSegments: List<SkipSegment>,
): Long? {
    if (playbackEndMs <= playbackStartMs) return null
    var cueMs = maxOf(requestedCueMs, playbackStartMs)
    if (cueMs >= playbackEndMs) return null
    val orderedSegments = skipSegments.asSequence().filter { it.isValid }.sortedBy { it.startMs }.toList()
    while (true) {
        val overlapEndMs = if (overlapMs > Long.MAX_VALUE - cueMs) Long.MAX_VALUE else cueMs + overlapMs
        val intersecting = orderedSegments.firstOrNull { segment ->
            segment.endMs > cueMs && segment.startMs < overlapEndMs
        } ?: break
        cueMs = intersecting.endMs
        if (cueMs >= playbackEndMs) return null
    }
    return cueMs.takeIf { it < playbackEndMs && playbackEndMs - it >= overlapMs }
}
