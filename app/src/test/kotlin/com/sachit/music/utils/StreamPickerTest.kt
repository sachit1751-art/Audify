/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPickerTest {

    private fun candidate(
        mimeType: String?,
        codecs: String?,
        bitrate: Int?,
    ) = StreamPicker.Candidate(mimeType, codecs, bitrate)

    @Test
    fun `flac mimeType is detected as lossless`() {
        assertTrue(StreamPicker.isLossless(candidate("audio/flac", "flac", 900_000)))
    }

    @Test
    fun `alac codec is detected as lossless`() {
        assertTrue(StreamPicker.isLossless(candidate("audio/mp4", "alac", 800_000)))
    }

    @Test
    fun `opus and aac are not lossless`() {
        assertFalse(StreamPicker.isLossless(candidate("audio/webm", "opus", 256_000)))
        assertFalse(StreamPicker.isLossless(candidate("audio/mp4", "mp4a.40.2", 128_000)))
    }

    @Test
    fun `null mime and codecs are not lossless`() {
        assertFalse(StreamPicker.isLossless(candidate(null, null, 128_000)))
    }

    @Test
    fun `detection is case insensitive`() {
        assertTrue(StreamPicker.isLossless(candidate("audio/FLAC", "FLAC", 900_000)))
    }

    @Test
    fun `empty list picks nothing`() {
        assertNull(StreamPicker.pickBestStream(emptyList(), wantLossless = true))
    }

    @Test
    fun `prefers lossless over higher-bitrate lossy when wanted`() {
        val flac = candidate("audio/flac", "flac", 700_000)
        val opus = candidate("audio/webm", "opus", 256_000)
        val best = StreamPicker.pickBestStream(listOf(opus, flac), wantLossless = true)
        assertEquals(flac, best)
    }

    @Test
    fun `falls back to highest bitrate lossy when no lossless exists`() {
        val opus256 = candidate("audio/webm", "opus", 256_000)
        val m4a128 = candidate("audio/mp4", "mp4a.40.2", 128_000)
        val best = StreamPicker.pickBestStream(listOf(m4a128, opus256), wantLossless = true)
        assertEquals(opus256, best)
    }

    @Test
    fun `lossless is ignored when not wanted`() {
        val flac = candidate("audio/flac", "flac", 700_000)
        val opus = candidate("audio/webm", "opus", 256_000)
        val best = StreamPicker.pickBestStream(listOf(flac, opus), wantLossless = false)
        assertEquals(opus, best)
    }

    @Test
    fun `picks highest bitrate among multiple lossless candidates`() {
        val flac44 = candidate("audio/flac", "flac", 700_000)
        val flac48 = candidate("audio/flac", "flac", 900_000)
        val best = StreamPicker.pickBestStream(listOf(flac44, flac48), wantLossless = true)
        assertEquals(flac48, best)
    }

    @Test
    fun `null bitrate treated as zero`() {
        val unknown = candidate("audio/webm", "opus", null)
        val opus = candidate("audio/webm", "opus", 128_000)
        val best = StreamPicker.pickBestStream(listOf(unknown, opus), wantLossless = true)
        assertEquals(opus, best)
    }

    @Test
    fun `only lossless candidates still picks one when not wanted instead of failing`() {
        val flac = candidate("audio/flac", "flac", 700_000)
        val best = StreamPicker.pickBestStream(listOf(flac), wantLossless = false)
        assertEquals(flac, best)
    }
}
