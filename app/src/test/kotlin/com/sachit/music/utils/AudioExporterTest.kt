package com.sachit.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExporterTest {
    @Test
    fun `mp4 container is chosen for mp4 mime types`() {
        assertEquals(AudioContainer.MP4, AudioContainer.fromMimeType("audio/mp4"))
        assertEquals(AudioContainer.MP4, AudioContainer.fromMimeType("audio/mp4; codecs=\"mp4a.40.2\""))
        assertEquals(AudioContainer.MP4, AudioContainer.fromMimeType("audio/mpeg"))
        assertEquals(AudioContainer.MP4, AudioContainer.fromMimeType(null))
        assertEquals(AudioContainer.MP4, AudioContainer.fromMimeType(""))
    }

    @Test
    fun `webm container is chosen for webm mime types`() {
        assertEquals(AudioContainer.WEBM, AudioContainer.fromMimeType("audio/webm"))
        assertEquals(AudioContainer.WEBM, AudioContainer.fromMimeType("audio/webm; codecs=\"opus\""))
        assertEquals(AudioContainer.WEBM, AudioContainer.fromMimeType("video/webm"))
    }

    @Test
    fun `file name joins artist and title`() {
        assertEquals("Artist - Title", AudioExporter.safeFileName("Title", "Artist"))
        assertEquals("Title", AudioExporter.safeFileName("Title", null))
        assertEquals("Title", AudioExporter.safeFileName("Title", ""))
    }

    @Test
    fun `file name strips reserved characters`() {
        assertEquals("AC_DC - Back in Black", AudioExporter.safeFileName("Back in Black", "AC_DC"))
        assertEquals("What_ Now", AudioExporter.safeFileName("What? Now", null))
        assertFalse(AudioExporter.safeFileName("a/b\\c:d*e?f\"g<h>i|j", null).contains(Regex("[\\\\/:*?\"<>|]")))
    }

    @Test
    fun `file name drops trailing dots and collapses whitespace`() {
        assertEquals("Title", AudioExporter.safeFileName("Title. ", null))
        assertEquals("a b", AudioExporter.safeFileName("a   \n b", null))
    }

    @Test
    fun `file name falls back for blank input`() {
        assertEquals("Unknown song", AudioExporter.safeFileName("", null))
        assertEquals("Unknown song", AudioExporter.safeFileName("   ", "  "))
    }

    @Test
    fun `file name is capped so extensions survive`() {
        val long = AudioExporter.safeFileName("T".repeat(500), null)
        assertTrue(long.length <= 120)
    }

    @Test
    fun `mime type follows the file extension`() {
        assertEquals(
            "audio/mp4",
            AudioExporter.mimeTypeForFile(java.io.File("/tmp/x/Artist - Title.mp4")),
        )
        assertEquals(
            "audio/webm",
            AudioExporter.mimeTypeForFile(java.io.File("/tmp/x/Artist - Title.webm")),
        )
    }
}
