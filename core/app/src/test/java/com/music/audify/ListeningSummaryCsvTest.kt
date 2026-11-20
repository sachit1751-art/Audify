// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.data.stats.ListeningSummaryCsv
import com.music.audify.data.stats.StoredBucket
import com.music.audify.data.stats.TrackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSummaryCsvTest {
    @Test
    fun escapesCommasQuotesAndLineBreaks() {
        assertEquals("plain", ListeningSummaryCsv.escape("plain"))
        assertEquals("\"A, B\"", ListeningSummaryCsv.escape("A, B"))
        assertEquals("\"She said \"\"yes\"\"\"", ListeningSummaryCsv.escape("She said \"yes\""))
        assertEquals("\"two\nlines\"", ListeningSummaryCsv.escape("two\nlines"))
    }

    @Test
    fun writesMonthlyAggregateRowsAndLabelsThemAsSummary() {
        val csv = ListeningSummaryCsv.write(
            listOf(
                StoredBucket(
                    month = "2026-03",
                    tracks = listOf(
                        TrackEntry(id = "1", title = "Song, One", artist = "Artist \"A\"", album = "Record", ms = 90_000, plays = 2),
                        TrackEntry(id = "2", title = "Unused", artist = "Nobody", ms = 0, plays = 0),
                    ),
                ),
            ),
        )

        assertTrue(csv.startsWith("# Monthly listening summary."))
        assertTrue(csv.contains("song_id,month,song,artist,album,minutes,play_count\r\n"))
        assertTrue(csv.contains("1,2026-03,\"Song, One\",\"Artist \"\"A\"\"\",Record,1.50,2\r\n"))
        assertFalse(csv.contains("Unused"))
        assertFalse(csv.substringAfter("\r\n").substringBefore("\r\n").contains("timestamp"))
    }

    @Test
    fun rowsKeepSameSongSeparateAcrossMonthsAndSortByMonth() {
        val rows = ListeningSummaryCsv.rows(
            listOf(
                StoredBucket(month = "2026-02", tracks = listOf(TrackEntry("id", "Song", "Artist", ms = 60_000, plays = 1))),
                StoredBucket(month = "2026-01", tracks = listOf(TrackEntry("id", "Song", "Artist", ms = 120_000, plays = 3))),
            ),
        )

        assertEquals(listOf("2026-01", "2026-02"), rows.map { it.month })
        assertEquals(listOf("2.00", "1.00"), rows.map { it.minutes })
        assertEquals(listOf("id", "id"), rows.map { it.songId })
        assertEquals(listOf(3, 1), rows.map { it.playCount })
    }
}
