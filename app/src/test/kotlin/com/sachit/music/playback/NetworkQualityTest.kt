/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import com.sachit.music.constants.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkQualityTest {

    @Test
    fun `explicit network key wins over the global key`() {
        assertEquals(
            AudioQuality.LOW,
            resolveAudioQuality(NetworkClass.METERED, AudioQuality.LOSSLESS, null, AudioQuality.LOW),
        )
        assertEquals(
            AudioQuality.LOSSLESS,
            resolveAudioQuality(NetworkClass.WIFI, AudioQuality.AUTO, AudioQuality.LOSSLESS, AudioQuality.LOW),
        )
    }

    @Test
    fun `unset network key falls back to the global key`() {
        assertEquals(
            AudioQuality.LOSSLESS,
            resolveAudioQuality(NetworkClass.METERED, AudioQuality.LOSSLESS, AudioQuality.LOSSLESS, null),
        )
        assertEquals(
            AudioQuality.LOSSLESS,
            resolveAudioQuality(NetworkClass.METERED, AudioQuality.LOSSLESS, null, null),
        )
        assertEquals(
            AudioQuality.LOSSLESS,
            resolveAudioQuality(NetworkClass.WIFI, AudioQuality.LOSSLESS, null, AudioQuality.LOW),
        )
    }

    @Test
    fun `all unset resolves to AUTO`() {
        assertEquals(
            AudioQuality.AUTO,
            resolveAudioQuality(NetworkClass.WIFI, null, null, null),
        )
        assertEquals(
            AudioQuality.AUTO,
            resolveAudioQuality(NetworkClass.METERED, null, null, null),
        )
    }

    @Test
    fun `AUTO stays AUTO through the resolver on metered networks`() {
        // The AUTO -> LOW degradation on metered networks is the streaming client's own
        // mapping (InnerTubeXPlayer.toInnerTubeX), not the resolver's job.
        assertEquals(
            AudioQuality.AUTO,
            resolveAudioQuality(NetworkClass.METERED, AudioQuality.AUTO, null, null),
        )
        assertEquals(
            AudioQuality.AUTO,
            resolveAudioQuality(NetworkClass.METERED, AudioQuality.AUTO, AudioQuality.HIGH, null),
        )
    }

    @Test
    fun `OFFLINE falls back to the global key and keeps the function total`() {
        assertEquals(
            AudioQuality.LOSSLESS,
            resolveAudioQuality(NetworkClass.OFFLINE, AudioQuality.LOSSLESS, null, AudioQuality.LOW),
        )
        assertEquals(
            AudioQuality.AUTO,
            resolveAudioQuality(NetworkClass.OFFLINE, null, null, null),
        )
    }

    @Test
    fun `stored key parser handles unset blank and legacy values`() {
        assertNull("".toAudioQualityOrNull())
        assertNull(null.toAudioQualityOrNull())
        assertNull("BOGUS".toAudioQualityOrNull())
        assertEquals(AudioQuality.AUTO, "AUTO".toAudioQualityOrNull())
        assertEquals(AudioQuality.LOW, "LOW".toAudioQualityOrNull())
        assertEquals(AudioQuality.HIGH, "HIGH".toAudioQualityOrNull())
        assertEquals(AudioQuality.LOSSLESS, "LOSSLESS".toAudioQualityOrNull())
        assertEquals(AudioQuality.HIGH, "VERY_HIGH".toAudioQualityOrNull())
    }
}
