package com.music.audify.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreferenceMigrationTest {

    @Test
    fun `legacy values fill missing keys without replacing current values`() {
        val current = mapOf(
            "theme" to "dark",
            "volume" to 0.8f,
        )
        val legacy = mapOf(
            "theme" to "light",
            "volume" to 0.5f,
            "onboarding_complete" to true,
            "last_position" to 42L,
            "selected_sources" to setOf("youtube", "addon"),
        )

        val migrated = PreferenceMigration.missingValues(current, legacy)

        assertEquals(
            mapOf(
                "onboarding_complete" to true,
                "last_position" to 42L,
                "selected_sources" to setOf("youtube", "addon"),
            ),
            migrated,
        )
        assertFalse(migrated.containsKey("theme"))
        assertFalse(migrated.containsKey("volume"))
    }

    @Test
    fun `empty destination receives the complete legacy snapshot`() {
        val legacy = mapOf("cookie" to "preserved", "count" to 3)

        assertEquals(legacy, PreferenceMigration.missingValues(emptyMap<String, Any>(), legacy))
    }

    @Test
    fun `migration is a no-op when the destination already contains every key`() {
        val current = mapOf("cookie" to "new", "count" to 4)
        val legacy = mapOf("cookie" to "old", "count" to 3)

        assertEquals(emptyMap<String, Any>(), PreferenceMigration.missingValues(current, legacy))
    }
}
