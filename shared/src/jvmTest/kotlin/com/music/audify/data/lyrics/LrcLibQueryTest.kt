// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.lyrics

import org.junit.Assert.assertEquals
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
}
