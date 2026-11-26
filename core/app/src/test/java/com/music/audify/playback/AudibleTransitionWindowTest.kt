package com.music.audify.playback

import com.music.audify.data.settings.SkipSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudibleTransitionWindowTest {
    @Test
    fun trailingSkipRangeBecomesTheEffectivePlaybackEnd() {
        assertEquals(
            115_000L,
            effectiveAudiblePlaybackEndMs(
                actualDurationMs = 180_000L,
                selectedEndMs = 123_000L,
                skipSegments = listOf(SkipSegment(115_000L, 180_000L)),
            ),
        )
        assertEquals(
            123_000L,
            effectiveAudiblePlaybackEndMs(
                actualDurationMs = 180_000L,
                selectedEndMs = 123_000L,
                skipSegments = listOf(SkipSegment(60_000L, 70_000L)),
            ),
        )
    }

    @Test
    fun movesTransitionPastSkippedAudioAndPreservesItsLength() {
        assertEquals(
            AudibleTransitionWindow(70_000L, 82_000L),
            audibleTransitionWindow(
                requestedStartMs = 60_000L,
                requestedEndMs = 72_000L,
                currentPositionMs = 40_000L,
                playbackStartMs = 0L,
                playbackEndMs = 100_000L,
                skipSegments = listOf(SkipSegment(65_000L, 70_000L)),
            ),
        )
    }

    @Test
    fun startsImmediatelyWhenPlayheadAlreadyJumpedPastPlannedStart() {
        assertEquals(
            AudibleTransitionWindow(70_000L, 75_000L),
            audibleTransitionWindow(
                requestedStartMs = 60_000L,
                requestedEndMs = 75_000L,
                currentPositionMs = 70_000L,
                playbackStartMs = 0L,
                playbackEndMs = 100_000L,
                skipSegments = listOf(SkipSegment(65_000L, 70_000L)),
            ),
        )
    }

    @Test
    fun relocatesExpiredPlanWhenThePlayheadIsStillInsideItsSkippedRange() {
        assertEquals(
            AudibleTransitionWindow(80_000L, 95_000L),
            audibleTransitionWindow(
                requestedStartMs = 60_000L,
                requestedEndMs = 75_000L,
                currentPositionMs = 78_000L,
                playbackStartMs = 0L,
                playbackEndMs = 120_000L,
                skipSegments = listOf(SkipSegment(65_000L, 80_000L)),
            ),
        )
    }

    @Test
    fun clipsTransitionToPlaybackWindowAndRejectsAnEmptyAudibleTail() {
        assertEquals(
            AudibleTransitionWindow(10_000L, 85_000L),
            audibleTransitionWindow(
                requestedStartMs = 5_000L,
                requestedEndMs = 90_000L,
                currentPositionMs = 0L,
                playbackStartMs = 10_000L,
                playbackEndMs = 85_000L,
                skipSegments = emptyList(),
            ),
        )
        assertNull(
            audibleTransitionWindow(
                requestedStartMs = 75_000L,
                requestedEndMs = 90_000L,
                currentPositionMs = 70_000L,
                playbackStartMs = 10_000L,
                playbackEndMs = 85_000L,
                skipSegments = listOf(SkipSegment(75_000L, 90_000L)),
            ),
        )
    }

    @Test
    fun movesIncomingCueOnlyWhenSkipIntersectsTheOverlap() {
        assertEquals(
            70_000L,
            audibleIncomingCueMs(
                requestedCueMs = 60_000L,
                overlapMs = 12_000L,
                playbackStartMs = 0L,
                playbackEndMs = 120_000L,
                skipSegments = listOf(SkipSegment(65_000L, 70_000L)),
            ),
        )
        assertEquals(
            60_000L,
            audibleIncomingCueMs(
                requestedCueMs = 60_000L,
                overlapMs = 4_000L,
                playbackStartMs = 0L,
                playbackEndMs = 120_000L,
                skipSegments = listOf(SkipSegment(70_000L, 80_000L)),
            ),
        )
        assertEquals(
            80_000L,
            audibleIncomingCueMs(
                requestedCueMs = 60_000L,
                overlapMs = 12_000L,
                playbackStartMs = 0L,
                playbackEndMs = 120_000L,
                skipSegments = listOf(SkipSegment(65_000L, 70_000L), SkipSegment(75_000L, 80_000L)),
            ),
        )
    }

    @Test
    fun rejectsIncomingCueWhenNoAudibleWindowRemains() {
        assertNull(
            audibleIncomingCueMs(
                requestedCueMs = 60_000L,
                overlapMs = 12_000L,
                playbackStartMs = 0L,
                playbackEndMs = 70_000L,
                skipSegments = listOf(SkipSegment(65_000L, 80_000L)),
            ),
        )
        assertNull(
            audibleIncomingCueMs(
                requestedCueMs = 65_000L,
                overlapMs = 12_000L,
                playbackStartMs = 0L,
                playbackEndMs = 70_000L,
                skipSegments = emptyList(),
            ),
        )
    }
}
