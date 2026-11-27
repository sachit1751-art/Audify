// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

import com.music.audify.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistImportMatcherTest {
    private fun song(id: String, title: String, artist: String, album: String? = null) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = "3:00",
        albumName = album,
    )

    @Test
    fun `unique title and artist result is a strong match`() = kotlinx.coroutines.runBlocking {
        val rows = PlaylistImportMatcher.match(
            listOf(PlaylistImportEntry(2, "Track A", "Artist A", durationMs = 180_000)),
            search = { listOf(song("id-a", "Track A", "Artist A")) },
        )

        assertEquals(PlaylistImportRowStatus.MATCHED, rows.single().status)
        assertEquals(PlaylistImportConfidence.STRONG, rows.single().candidates.single().confidence)
        assertEquals("id-a", rows.single().candidates.single().song.videoId)
    }

    @Test
    fun `near equal candidates are ambiguous and not silently selected`() = kotlinx.coroutines.runBlocking {
        val rows = PlaylistImportMatcher.match(
            listOf(PlaylistImportEntry(2, "Track A", "Artist A")),
            search = {
                listOf(
                    song("id-a", "Track A", "Artist A", "Album A"),
                    song("id-b", "Track A", "Artist A", "Album B"),
                )
            },
        )

        assertEquals(PlaylistImportRowStatus.AMBIGUOUS, rows.single().status)
        assertEquals(2, rows.single().candidates.size)
    }

    @Test
    fun `same title with conflicting artist and no exact duration is unmatched`() = kotlinx.coroutines.runBlocking {
        val rows = PlaylistImportMatcher.match(
            listOf(PlaylistImportEntry(2, "Track A", "Requested Artist")),
            search = { listOf(song("cover", "Track A", "Different Artist")) },
        )
        assertEquals(PlaylistImportRowStatus.UNMATCHED, rows.single().status)
    }

    @Test
    fun `duplicate source occurrences reuse one search and preserve order`() = kotlinx.coroutines.runBlocking {
        var calls = 0
        val rows = PlaylistImportMatcher.match(
            listOf(
                PlaylistImportEntry(2, "Track A", "Artist A"),
                PlaylistImportEntry(3, "Track A", "Artist A"),
            ),
            search = { calls++; listOf(song("id-a", "Track A", "Artist A")) },
        )

        assertEquals(1, calls)
        assertEquals(PlaylistImportRowStatus.MATCHED, rows[0].status)
        assertEquals(PlaylistImportRowStatus.DUPLICATE_OCCURRENCE, rows[1].status)
        assertEquals(2, rows[1].duplicateOfSourceRow)
        assertEquals(listOf(2, 3), rows.map { it.source.sourceRow })
    }

    @Test
    fun `same title artist query is reused when album metadata differs`() = kotlinx.coroutines.runBlocking {
        var calls = 0
        val rows = PlaylistImportMatcher.match(
            listOf(
                PlaylistImportEntry(2, "Track A", "Artist A", album = "Album A"),
                PlaylistImportEntry(3, "Track A", "Artist A", album = "Album B"),
            ),
            search = {
                calls++
                listOf(
                    song("id-a", "Track A", "Artist A", "Album A"),
                    song("id-b", "Track A", "Artist A", "Album B"),
                )
            },
        )

        assertEquals(1, calls)
        assertEquals("id-a", rows[0].candidates.single().song.videoId)
        assertEquals("id-b", rows[1].candidates.single().song.videoId)
    }

    @Test
    fun `blank rows and search failures are visible outcomes`() = kotlinx.coroutines.runBlocking {
        val rows = PlaylistImportMatcher.match(
            listOf(PlaylistImportEntry(2, "", "Artist"), PlaylistImportEntry(3, "Track", "Artist")),
            search = { error("offline") },
        )
        assertEquals(PlaylistImportRowStatus.INVALID, rows[0].status)
        assertEquals(PlaylistImportRowStatus.UNMATCHED, rows[1].status)
        assertTrue(rows[1].reason.orEmpty().contains("offline"))
        assertNull(rows[0].candidates.firstOrNull())
    }
}
