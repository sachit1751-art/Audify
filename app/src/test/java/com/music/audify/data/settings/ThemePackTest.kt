package com.music.audify.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackTest {
    @Test
    fun allEighteenBuiltInThemesHaveUniqueStableIdsAndValidPaletteColors() {
        assertEquals(18, ThemePacks.builtIns.size)
        assertEquals(18, ThemePacks.builtIns.map { it.id }.distinct().size)
        ThemePacks.builtIns.forEach { pack ->
            listOf(pack.primaryHex, pack.secondaryHex, pack.tertiaryHex).forEach { color ->
                assertTrue(color.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            }
        }
    }

    @Test
    fun exportedThemeCanBeImportedWithASeparateIdentity() {
        val original = ThemePacks.builtIns.first { it.id == "ocean" }
        val imported = ThemePacks.decode(ThemePacks.encode(original))

        assertEquals(original.name, imported.name)
        assertEquals(original.primaryHex, imported.primaryHex)
        assertEquals(original.secondaryHex, imported.secondaryHex)
        assertEquals(original.tertiaryHex, imported.tertiaryHex)
        assertEquals(original.surfaceStyle, imported.surfaceStyle)
        assertNotEquals(original.id, imported.id)
    }

    @Test
    fun customThemeLibraryRetainsIdsAcrossPersistence() {
        val pack = ThemePack("custom-id", "My theme", "#112233", "#445566", "#778899")
        val restored = ThemePacks.decodeLibrary(ThemePacks.encodeLibrary(listOf(pack)))

        assertEquals(listOf(pack), restored)
        assertEquals(pack, ThemePacks.resolve("custom-id", restored))
    }

    @Test
    fun malformedThemeFilesAreRejected() {
        val result = runCatching {
            ThemePacks.decode("""{"format":"audify-theme","version":1,"name":"Broken","primary":"red"}""")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun legacyThemeFileDefaultsToClassicSurfaceStyle() {
        val legacy = """{"format":"audify-theme","version":1,"name":"Legacy","primary":"#112233","secondary":"#445566","tertiary":"#778899"}"""
        assertEquals(ThemeSurfaceStyle.DEFAULT, ThemePacks.decode(legacy).surfaceStyle)
    }

    @Test
    fun importedThemeCannotExceedSizeLimitOrInjectSurfaceTokens() {
        val oversized = " ".repeat(16_385)
        assertTrue(runCatching { ThemePacks.decode(oversized) }.isFailure)
        val unknownStyle = """{"format":"audify-theme","version":1,"name":"Safe","primary":"#112233","secondary":"#445566","tertiary":"#778899","surfaceStyle":"EXECUTE_CODE"}"""
        assertEquals(ThemeSurfaceStyle.DEFAULT, ThemePacks.decode(unknownStyle).surfaceStyle)
    }

    @Test
    fun builtInsIncludeAccessibleSurfaceVariants() {
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.AMOLED })
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.MIDNIGHT })
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.GRAPHITE })
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.DEEP_PURPLE })
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.WARM })
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.SLATE })
    }

    @Test
    fun everyBuiltInAccentHasAtLeastWcagAaContrastWithItsChosenLabelColour() {
        ThemePacks.builtIns.forEach { pack ->
            listOf(pack.primaryHex, pack.secondaryHex, pack.tertiaryHex).forEach { accent ->
                val content = ThemePacks.contentColorHex(accent)
                assertTrue("$content on $accent must meet 4.5:1", contrastRatio(accent, content) >= 4.5)
            }
        }
    }

    private fun contrastRatio(firstHex: String, secondHex: String): Double {
        fun luminance(hex: String): Double {
            val rgb = hex.drop(1).chunked(2).map { it.toInt(16) / 255.0 }
            val linear = rgb.map { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
        }
        val first = luminance(firstHex)
        val second = luminance(secondHex)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
