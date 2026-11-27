// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun grayscaleDoesNotAcquireTheDefaultRedHue() {
        assertEquals(0f, adaptedArtworkSaturation(0f, minimum = 0.20f, maximum = 0.62f), 0f)
        assertEquals(0f, adaptedArtworkSaturation(0f, minimum = 0.55f, maximum = 1f), 0f)
    }

    @Test
    fun nearlyNeutralArtworkKeepsItsSubtleSaturation() {
        assertEquals(0.08f, adaptedArtworkSaturation(0.08f, minimum = 0.20f, maximum = 0.62f), 0f)
    }

    @Test
    fun genuinelyChromaticArtworkIsStillStrengthenedAndCapped() {
        assertEquals(0.55f, adaptedArtworkSaturation(0.25f, minimum = 0.55f, maximum = 1f), 0f)
        assertEquals(0.62f, adaptedArtworkSaturation(0.90f, minimum = 0.20f, maximum = 0.62f), 0f)
    }

    @Test
    fun neutralBottomEdgeFallsBackToTheAlbumsDominantColour() {
        val dominant = Color(0xFF315E9A)
        assertEquals(dominant, artworkEdgeTint(Color(0xFF777777), dominant))
        assertEquals(dominant, artworkEdgeTint(Color(0xFF080808), dominant))
    }

    @Test
    fun chromaticBottomEdgeKeepsItsOwnColour() {
        val edge = Color(0xFFB34B28)
        assertEquals(edge, artworkEdgeTint(edge, Color(0xFF315E9A)))
    }

}
