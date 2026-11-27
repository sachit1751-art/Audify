package com.music.audify.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AppleAlbumInfoTest {
    @Test
    fun `hi res lossless takes precedence over lossless`() {
        val info = AppleAlbumInfo(
            releaseDate = LocalDate.of(2024, 1, 1),
            copyright = null,
            genre = null,
            audioTraits = listOf("lossless", "hi-res-lossless", "atmos"),
        )

        assertEquals("Hi-Res Lossless", info.audioQualityLabel())
    }

    @Test
    fun `lossless trait maps to album badge`() {
        val info = AppleAlbumInfo(null, null, null, listOf("lossless"))

        assertEquals("Lossless", info.audioQualityLabel())
    }

    @Test
    fun `non lossless traits do not create an album quality badge`() {
        val info = AppleAlbumInfo(null, null, null, listOf("atmos", "lossy-stereo"))

        assertNull(info.audioQualityLabel())
    }
}
