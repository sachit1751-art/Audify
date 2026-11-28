package com.music.audify.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMigrationPolicyTest {

    private val onboardingKey = "onboarding_complete"
    private val migrationKey = "audify_prefs_migrated"

    @Test
    fun `fresh install with empty preferences seeds defaults`() {
        val keys = emptySet<String>()

        assertFalse(shouldCompleteOnboardingForExistingInstall(keys, emptySet(), onboardingKey, migrationKey))
        assertTrue(shouldSeedFirstInstallDefaults(keys))
    }

    @Test
    fun `migration marker alone never triggers first install defaults`() {
        val keys = setOf(migrationKey)

        assertFalse(shouldSeedFirstInstallDefaults(keys))
        assertTrue(shouldRecoverLegacyPreferencesFromMarkerOnlyStore(keys, setOf("theme_palette"), migrationKey))
        assertFalse(shouldRecoverLegacyPreferencesFromMarkerOnlyStore(keys, emptySet(), migrationKey))
    }

    @Test
    fun `existing preference file skips first-run onboarding and does not reseed defaults`() {
        val keys = setOf(migrationKey, "theme_palette", "audio_quality_wifi")

        assertTrue(shouldCompleteOnboardingForExistingInstall(keys, emptySet(), onboardingKey, migrationKey))
        assertFalse(shouldSeedFirstInstallDefaults(keys))
    }

    @Test
    fun `legacy preference file identifies an existing install`() {
        assertTrue(
            shouldCompleteOnboardingForExistingInstall(
                currentPreferenceKeys = setOf(migrationKey),
                legacyPreferenceKeys = setOf("theme_palette"),
                onboardingKey = onboardingKey,
                migrationKey = migrationKey,
            ),
        )
    }

    @Test
    fun `explicit incomplete onboarding state is preserved`() {
        assertFalse(
            shouldCompleteOnboardingForExistingInstall(
                currentPreferenceKeys = setOf(migrationKey, onboardingKey),
                legacyPreferenceKeys = setOf("theme_palette"),
                onboardingKey = onboardingKey,
                migrationKey = migrationKey,
            ),
        )
    }

    @Test
    fun `current settings always win over stale legacy settings`() {
        val current = setOf("theme_palette", "audio_quality_wifi")
        val legacy = setOf("theme_palette", "audio_quality_wifi", "app_font", "old_unmapped_setting")

        assertTrue(
            legacyPreferenceKeysToImport(current, legacy) == setOf("app_font", "old_unmapped_setting"),
        )
    }

    @Test
    fun `legacy settings are imported when destination has no value`() {
        assertTrue(
            legacyPreferenceKeysToImport(emptySet(), setOf("theme_palette", "app_font")) ==
                setOf("theme_palette", "app_font"),
        )
    }

    @Test
    fun `only values still equal to seeded defaults are restored from legacy preferences`() {
        val current = mapOf("theme_mode" to "SYSTEM", "theme_palette" to "custom", "accent" to "GREEN")
        val legacy = mapOf("theme_mode" to "DARK", "theme_palette" to "ocean", "accent" to "BLUE")
        val defaults = mapOf("theme_mode" to "SYSTEM", "theme_palette" to "ocean", "accent" to "GREEN")

        assertTrue(
            legacyPreferenceKeysOverwrittenByFirstInstallDefaults(current, legacy, defaults) ==
                setOf("theme_mode", "accent"),
        )
    }
}
