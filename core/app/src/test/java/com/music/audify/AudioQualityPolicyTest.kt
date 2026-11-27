package com.music.audify

import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.AudioQuality
import com.music.audify.data.settings.permits
import com.music.audify.data.sources.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityPolicyTest {

    @Test
    fun `lossless ceiling permits every configured source and the YouTube fallback`() {
        SourceKind.entries.forEach { kind -> assertTrue(AudioQuality.LOSSLESS.permits(kind)) }
    }

    @Test
    fun `high ceiling avoids lossless-only source kinds but keeps lossy sources`() {
        assertFalse(AudioQuality.HIGH.permits(SourceKind.ADDON))
        assertFalse(AudioQuality.HIGH.permits(SourceKind.MODULE))
        assertFalse(AudioQuality.HIGH.permits(SourceKind.CUSTOM_MODULE))
        assertTrue(AudioQuality.HIGH.permits(SourceKind.JIOSAAVN))
        assertTrue(AudioQuality.HIGH.permits(SourceKind.YOUTUBE))
    }

    @Test
    fun `medium and low ceilings use only the YouTube fallback`() {
        listOf(AudioQuality.MEDIUM, AudioQuality.LOW).forEach { quality ->
            SourceKind.entries.filter { it != SourceKind.YOUTUBE }.forEach { kind ->
                assertFalse("$quality must not request $kind", quality.permits(kind))
            }
            assertTrue(quality.permits(SourceKind.YOUTUBE))
        }
    }

    @Test
    fun `connection and temporary ceiling select the effective stream policy`() {
        assertEquals(
            AudioQuality.LOSSLESS,
            AppSettings.ceilingFor(AudioQuality.LOSSLESS, AudioQuality.LOW, false, null),
        )
        assertEquals(
            AudioQuality.LOW,
            AppSettings.ceilingFor(AudioQuality.LOSSLESS, AudioQuality.LOW, true, null),
        )
        assertEquals(
            AudioQuality.HIGH,
            AppSettings.ceilingFor(AudioQuality.LOSSLESS, AudioQuality.LOW, false, AudioQuality.HIGH),
        )
        assertEquals(
            AudioQuality.LOW,
            AppSettings.ceilingFor(AudioQuality.LOW, AudioQuality.LOSSLESS, false, AudioQuality.HIGH),
        )
    }
}
