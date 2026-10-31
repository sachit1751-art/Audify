package com.music.audify.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BiniLyricsMatchingTest {

    @Test
    fun `selects matching candidate instead of trusting first ranked result`() {
        val wrong = hit(title = "Hello", artist = "Adele Tribute", duration = 295)
        val right = hit(title = "Hello", artist = "Adele", duration = 295)

        assertEquals(
            right,
            BiniLyrics.bestHit(listOf(wrong, right), "Hello", "Adele - Topic", 295_000L),
        )
    }

    @Test
    fun `rejects different title`() {
        assertNull(
            BiniLyrics.bestHit(
                listOf(hit(title = "Hello (Live)", artist = "Adele", duration = 295)),
                "Hello",
                "Adele",
                295_000L,
            ),
        )
    }

    @Test
    fun `rejects different artist`() {
        assertNull(
            BiniLyrics.bestHit(
                listOf(hit(title = "Hello", artist = "Hello Adele Tribute", duration = 295)),
                "Hello",
                "Adele",
                295_000L,
            ),
        )
    }

    @Test
    fun `rejects duration outside the recording tolerance`() {
        assertNull(
            BiniLyrics.bestHit(
                listOf(hit(title = "Hello", artist = "Adele", duration = 310)),
                "Hello",
                "Adele",
                295_000L,
            ),
        )
    }

    @Test
    fun `allows absent optional duration metadata`() {
        val candidate = hit(title = "Hello", artist = "Adele", duration = null)

        assertEquals(
            candidate,
            BiniLyrics.bestHit(listOf(candidate), "Hello", "Adele", 295_000L),
        )
    }

    private fun hit(title: String, artist: String, duration: Int?) =
        BiniLyrics.Hit(
            trackName = title,
            artistName = artist,
            duration = duration,
            isrc = "USRC17607839",
            lyricsUrl = "https://example.invalid/lyrics.ttml",
        )
}
