package com.music.audify.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentStoreTest {
    @Test
    fun normalizesOverlappingAndAdjacentRangesWithoutChangingTrackBoundaries() {
        assertEquals(
            listOf(SkipSegment(10_000, 30_000), SkipSegment(40_000, 50_000)),
            SkipSegmentStore.normalize(listOf(
                SkipSegment(20_000, 30_000),
                SkipSegment(10_000, 20_000),
                SkipSegment(40_000, 50_000),
                SkipSegment(-1, 5_000),
                SkipSegment(60_000, 60_000),
            )),
        )
    }

    @Test
    fun persistedRangesRoundTripAndRemainIsolatedPerTrack() {
        val saved = mapOf(
            "track-a" to listOf(SkipSegment(77_000, 112_000)),
            "track-b" to listOf(SkipSegment(5_000, 8_000), SkipSegment(20_000, 25_000)),
        )
        assertEquals(saved, SkipSegmentStore.decode(SkipSegmentStore.encode(saved)))
    }

    @Test
    fun timestampParserAcceptsMinuteAndHourFormsAndRejectsInvalidRanges() {
        assertEquals(77_000L, SkipSegmentStore.parseTimestamp("1:17"))
        assertEquals(3_723_500L, SkipSegmentStore.parseTimestamp("1:02:03.5"))
        assertNull(SkipSegmentStore.parseTimestamp("1:75"))
        assertNull(SkipSegmentStore.parseTimestamp("-1:17"))
        assertNull(SkipSegmentStore.parseTimestamp("1:2:3:4"))
    }

    @Test
    fun segmentLookupUsesInclusiveStartExclusiveEnd() {
        val ranges = mapOf("track-a" to listOf(SkipSegment(77_000, 112_000)))
        assertEquals(SkipSegment(77_000, 112_000), SkipSegmentStore.matchingSegment("track-a", 77_000, ranges))
        assertNull(SkipSegmentStore.matchingSegment("track-a", 112_000, ranges))
        assertNull(SkipSegmentStore.matchingSegment("track-b", 80_000, ranges))
    }

    @Test
    fun corruptedOrOversizedSettingsFailClosed() {
        assertTrue(SkipSegmentStore.decode("not-json").isEmpty())
        assertTrue(SkipSegmentStore.decode(" ".repeat(512 * 1024 + 1)).isEmpty())
    }
}
