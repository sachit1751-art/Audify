package com.music.audify.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentTest {
    @Test
    fun parsesMinuteAndHourTimestamps() {
        assertEquals(77_000L, parseSkipTimestamp("1:17"))
        assertEquals(112_000L, parseSkipTimestamp("1:52"))
        assertEquals(3_723_000L, parseSkipTimestamp("1:02:03"))
    }

    @Test
    fun rejectsMalformedOrAmbiguousTimestamps() {
        listOf("", "1", "1:60", "1:2:60", "-1:20", "1:2:3:4", "abc", "9999999999:00")
            .forEach { assertNull("$it should be rejected", parseSkipTimestamp(it)) }
    }

    @Test
    fun formatsPositionsAndKeepsHourPrecision() {
        assertEquals("1:17", formatSkipTimestamp(77_900L))
        assertEquals("1:02:03", formatSkipTimestamp(3_723_000L))
    }

    @Test
    fun normalizesOverlappingAndAdjacentRanges() {
        assertEquals(
            listOf(SkipSegment(10_000L, 50_000L), SkipSegment(80_000L, 90_000L)),
            normalizeSkipSegments(
                listOf(
                    SkipSegment(30_000L, 50_000L),
                    SkipSegment(10_000L, 30_000L),
                    SkipSegment(80_000L, 90_000L),
                    SkipSegment(40_000L, 20_000L),
                ),
            ),
        )
    }

    @Test
    fun onlySkipsWhilePlayheadIsInsideTheConfiguredRange() {
        val ranges = listOf(SkipSegment(77_000L, 112_000L))
        assertNull(ranges.activeAt(76_999L, 180_000L))
        assertEquals(SkipSegment(77_000L, 112_000L), ranges.activeAt(77_000L, 180_000L))
        assertEquals(SkipSegment(77_000L, 112_000L), ranges.activeAt(111_999L, 180_000L))
        assertNull(ranges.activeAt(112_000L, 180_000L))
        assertNull(ranges.activeAt(90_000L, 100_000L))
    }

    @Test
    fun invalidRangesAreDiscarded() {
        assertTrue(normalizeSkipSegments(listOf(SkipSegment(-1L, 10L), SkipSegment(20L, 20L))).isEmpty())
    }
}
