package com.music.audify.data.lyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsAccentMatchingTest {
    @Test
    fun `Bini candidate matching ignores Latin accents in title and artist`() {
        val hit = BiniLyrics.Hit(
            trackName = "Beyoncé",
            artistName = "Björk",
            duration = 200,
            isrc = "ISRC-1",
            lyricsUrl = "https://example.invalid/lyrics.ttml",
        )

        assertEquals(hit, BiniLyrics.bestHit(listOf(hit), "Beyonce", "Bjork", 200_000L))
    }

    @Test
    fun `LRCLIB candidate matching ignores Latin accents in title and artist`() {
        val hits = Json.parseToJsonElement(
            """[{"trackName":"Beyoncé","artistName":"Björk","duration":200,"syncedLyrics":"[00:01.00]line"}]""",
        ).jsonArray

        assertEquals("[00:01.00]line", LrcLib.bestSearchHit(hits, "Beyonce", "Bjork", 200))
    }

    @Test
    fun `accent folding keeps combining marks outside Latin script`() {
        assertEquals("क़", "क़".accentFoldedIdentity())
    }
}
