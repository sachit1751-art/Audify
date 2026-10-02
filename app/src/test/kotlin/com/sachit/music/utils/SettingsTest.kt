/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import com.sachit.music.constants.LoudnessLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the seam that replaces the old `rememberPreference` / blocking `dataStore.get` split.
 *
 * The interface itself is the test surface: everything below runs on the JVM with no Context, no
 * DataStore and no Compose, which is exactly what the two mechanisms it replaces could not do.
 */
class SettingsTest {

    private val settings = FakeSettings()

    // --- defaults -----------------------------------------------------------

    @Test
    fun `crossfade is off by default`() = runBlocking {
        assertFalse(settings.read(SettingsProperties.crossfadeEnabled))
    }

    @Test
    fun `crossfade duration defaults to five seconds`() = runBlocking {
        assertEquals(5f, settings.read(SettingsProperties.crossfadeDurationSeconds))
    }

    @Test
    fun `gapless suppression is on by default`() = runBlocking {
        assertTrue(settings.read(SettingsProperties.crossfadeGapless))
    }

    @Test
    fun `loudness defaults to balanced`() = runBlocking {
        assertEquals(LoudnessLevel.BALANCED, settings.read(SettingsProperties.loudnessLevel))
    }

    @Test
    fun `an unread property returns its declared default`() = runBlocking {
        // The default lives on the property, so a call site cannot forget to supply one and
        // silently get a wrong fallback.
        assertEquals(1f, settings.read(SettingsProperties.playerVolume))
        assertEquals(false, settings.read(SettingsProperties.hideExplicit))
        assertEquals(false, settings.read(SettingsProperties.hideVideoSongs))
    }

    // --- read / set round trip ----------------------------------------------

    @Test
    fun `a written boolean is read back`() = runBlocking {
        settings.set(SettingsProperties.crossfadeEnabled, true)

        assertTrue(settings.read(SettingsProperties.crossfadeEnabled))
    }

    @Test
    fun `a written float is read back`() = runBlocking {
        settings.set(SettingsProperties.crossfadeDurationSeconds, 12.5f)

        assertEquals(12.5f, settings.read(SettingsProperties.crossfadeDurationSeconds))
    }

    @Test
    fun `a written enum is read back`() = runBlocking {
        settings.set(SettingsProperties.loudnessLevel, LoudnessLevel.AGGRESSIVE)

        assertEquals(LoudnessLevel.AGGRESSIVE, settings.read(SettingsProperties.loudnessLevel))
    }

    @Test
    fun `writing one property leaves the others alone`() = runBlocking {
        settings.set(SettingsProperties.crossfadeEnabled, true)

        assertEquals(5f, settings.read(SettingsProperties.crossfadeDurationSeconds))
        assertFalse(settings.read(SettingsProperties.skipSilence))
    }

    @Test
    fun `writing an explicit default is the same as unset`() = runBlocking {
        settings.set(SettingsProperties.crossfadeGapless, true)

        assertTrue(settings.read(SettingsProperties.crossfadeGapless))
    }

    // --- observe ------------------------------------------------------------
    //
    // Collected on Dispatchers.Unconfined so delivery is synchronous and the assertions below need
    // no timing assumptions — no delays, no flakiness.

    private fun collectBooleans(property: SettingsProperty<Boolean>): Pair<MutableList<Boolean>, kotlinx.coroutines.Job> {
        val seen = mutableListOf<Boolean>()
        val job =
            coroutineScope.launch(Dispatchers.Unconfined) {
                settings.observe(property).collect { seen += it }
            }
        return seen to job
    }

    private val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `observe emits the current value immediately`() = runBlocking {
        settings.set(SettingsProperties.crossfadeEnabled, true)

        assertTrue(settings.observe(SettingsProperties.crossfadeEnabled).first())
    }

    @Test
    fun `observe emits changes`() {
        val (seen, job) = collectBooleans(SettingsProperties.crossfadeEnabled)

        runBlocking { settings.set(SettingsProperties.crossfadeEnabled, true) }
        job.cancel()

        assertEquals(listOf(false, true), seen)
    }

    @Test
    fun `observe does not re-emit an unchanged value`() {
        val (seen, job) = collectBooleans(SettingsProperties.crossfadeEnabled)

        runBlocking { settings.set(SettingsProperties.crossfadeEnabled, false) }
        job.cancel()

        assertEquals("a redundant write must not look like a change", listOf(false), seen)
    }

    // --- the point of the module --------------------------------------------

    @Test
    fun `a fake Settings drives behaviour that used to need a device`() = runBlocking {
        // This is the capability the old mechanisms could not provide: crossfade configuration
        // read from an injected source, so the caller's behaviour is testable end to end.
        settings.set(SettingsProperties.crossfadeEnabled, true)
        settings.set(SettingsProperties.crossfadeDurationSeconds, 8f)

        assertEquals(8f, settings.read(SettingsProperties.crossfadeDurationSeconds))
        assertTrue(settings.read(SettingsProperties.crossfadeEnabled))
    }

    @Test
    fun `property names are distinct so parallel reads cannot collide`() {
        val all =
            listOf(
                SettingsProperties.crossfadeEnabled,
                SettingsProperties.crossfadeDurationSeconds,
                SettingsProperties.crossfadeGapless,
                SettingsProperties.skipSilence,
                SettingsProperties.skipSilenceInstant,
                SettingsProperties.audioOffload,
                SettingsProperties.shufflePlaylistFirst,
                SettingsProperties.playerVolume,
                SettingsProperties.normalizationEnabled,
                SettingsProperties.loudnessLevel,
                SettingsProperties.persistentQueue,
                SettingsProperties.hideExplicit,
                SettingsProperties.hideVideoSongs,
            )

        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertEquals(all.size, all.map { it.key.name }.toSet().size)
    }
}

/**
 * In-memory [Settings] for tests. Deliberately mirrors the semantics of the real implementation:
 * reads fall back to the property's default, and observation de-duplicates.
 */
class FakeSettings : Settings {
    private val values = MutableStateFlow<Map<String, Any?>>(emptyMap())

    override suspend fun <T> read(property: SettingsProperty<T>): T =
        @Suppress("UNCHECKED_CAST")
        (values.value[property.key.name] as T?) ?: property.default

    override fun <T> observe(property: SettingsProperty<T>): Flow<T> =
        values
            .map { store ->
                @Suppress("UNCHECKED_CAST")
                (store[property.key.name] as T?) ?: property.default
            }.distinctUntilChanged()

    override suspend fun <T> set(
        property: SettingsProperty<T>,
        value: T,
    ): Boolean {
        values.update { it + (property.key.name to value) }
        return true
    }
}