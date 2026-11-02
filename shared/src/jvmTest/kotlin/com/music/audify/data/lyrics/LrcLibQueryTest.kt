// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Test

class LrcLibQueryTest {

    @Test
    fun `keeps labels that identify a different recording`() {
        for (title in listOf(
            "Song (Remix)",
            "Song (JENNIE Remix)",
            "Song (Live)",
            "Song [Live]",
            "Song (Acoustic)",
            "Song (Remastered 2011)",
            "Song (Sped Up)",
        )) {
            assertEquals(title, LrcLib.cleanTitleForSearch(title))
        }
    }

    @Test
    fun `removes upload packaging and soundtrack credits`() {
        assertEquals(
            "Song",
            LrcLib.cleanTitleForSearch("Song (From 'Raees') | Official Video"),
        )
        assertEquals(
            "Song",
            LrcLib.cleanTitleForSearch("Song (feat. Guest) [Official Video]"),
        )
    }

    @Test
    fun `fuzzy search rejects a closer duration from another recording`() {
        val hits = Json.parseToJsonElement(
            """[
                {"trackName":"Different Song","artistName":"Radiohead","duration":298,"syncedLyrics":"wrong"},
                {"trackName":"Let Down","artistName":"Another Artist","duration":299,"syncedLyrics":"wrong artist"},
                {"trackName":"Let Down","artistName":"Radiohead, Thom Yorke","duration":301,"syncedLyrics":"right"}
            ]""",
        ).jsonArray

        assertEquals("right", LrcLib.bestSearchHit(hits, "Let Down", "Radiohead", 298))
    }

    @Test
    fun `fuzzy search has no result when title or artist identity disagrees`() {
        val hits = Json.parseToJsonElement(
            """[{"trackName":"Let Down (Live)","artistName":"Radiohead","duration":298,"syncedLyrics":"live"}]""",
        ).jsonArray

        assertNull(LrcLib.bestSearchHit(hits, "Let Down", "Radiohead", 298))
    }
}
