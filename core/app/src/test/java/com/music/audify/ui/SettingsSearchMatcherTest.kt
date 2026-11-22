package com.music.audify.ui

import com.music.audify.ui.screens.settingsSearchMatches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchMatcherTest {
    @Test
    fun synonymsFindSettingsWithoutExactTitleWords() {
        assertTrue(settingsSearchMatches("dark", "Appearance", "Choose the app theme"))
        assertTrue(settingsSearchMatches("battery", "Performance", "Background playback and cache"))
        assertTrue(settingsSearchMatches("headphones", "Audio output", "Bluetooth device routing"))
        assertTrue(settingsSearchMatches("wifi", "Streaming quality", "Network data usage"))
    }

    @Test
    fun matchingIgnoresAccentsAndPunctuation() {
        assertTrue(settingsSearchMatches("cross fade", "Crossfade", "Playback transition"))
        assertTrue(settingsSearchMatches("appearance", "Appéarance", "Theme"))
    }

    @Test
    fun oneCharacterTypoIsToleratedForLongWords() {
        assertTrue(settingsSearchMatches("performnce", "Performance", "Battery and background work"))
        assertFalse(settingsSearchMatches("prfxxmance", "Performance", "Battery and background work"))
    }

    @Test
    fun everyQueryTermMustMatchAndBlankQueryMatchesAll() {
        assertTrue(settingsSearchMatches("dark theme", "Appearance", "Dark mode"))
        assertFalse(settingsSearchMatches("dark bluetooth", "Appearance", "Dark mode"))
        assertTrue(settingsSearchMatches("   ", "Any setting"))
    }
}
