package com.music.audify.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackTest {
    @Test
    fun remainingBuiltInThemesHaveUniqueStableIdsAndValidPaletteColors() {
        assertEquals(7, ThemePacks.builtIns.size)
        assertEquals(7, ThemePacks.builtIns.map { it.id }.distinct().size)
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
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.GRAPHITE })
        assertTrue(ThemePacks.builtIns.any { it.surfaceStyle == ThemeSurfaceStyle.SLATE })
        assertTrue(ThemePacks.builtIns.none { it.id in setOf("violet", "sunset", "orchid", "minimal-slate", "slate", "amoled", "midnight", "rose") })
    }

    @Test
    fun themeBackgroundsKeepTheOriginalNeutralSurfaceTreatment() {
        ThemePacks.builtIns.forEach { pack ->
            val light = ThemePacks.lightBackgroundHex(pack)
            val dark = ThemePacks.darkBackgroundHex(pack)
            assertTrue("${pack.name} light background must be valid", ThemePacks.isValidHex(light))
            assertTrue("${pack.name} dark background must be valid", ThemePacks.isValidHex(dark))
            assertNotEquals("${pack.name} light and dark backgrounds must differ", light, dark)
        }
        assertEquals("#000000", ThemePacks.darkBackgroundHex(ThemePacks.builtIns.first { it.id == "classic" }))
        assertEquals("#15171B", ThemePacks.darkBackgroundHex(ThemePacks.builtIns.first { it.id == "graphite" }))
        assertEquals("#101820", ThemePacks.darkBackgroundHex(ThemePacks.builtIns.first { it.id == "jade" }))
    }

    @Test
    fun nonClassicDefaultSurfaceThemesAlsoTintTheDarkCanvas() {
        ThemePacks.builtIns
            .filter { it.surfaceStyle == ThemeSurfaceStyle.DEFAULT && it.id != ThemePacks.CLASSIC_ID }
            .forEach { pack ->
                val background = ThemePacks.darkBackgroundHex(pack)
                assertTrue("${pack.name} should have a visible dark palette tint", background != "#000000")
                assertTrue("${pack.name} dark background must remain a valid colour", ThemePacks.isValidHex(background))
            }
        assertEquals("#000000", ThemePacks.darkBackgroundHex(ThemePacks.builtIns.first { it.id == ThemePacks.CLASSIC_ID }))
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

    @Test
    fun minimalPresetsAreVisiblyDistinctFromExistingThemesAndEachOther() {
        val minimalIds = setOf("soft-sage", "warm-sand")
        val minimal = ThemePacks.builtIns.filter { it.id in minimalIds }
        val existing = ThemePacks.builtIns.filterNot { it.id in minimalIds }

        assertEquals(minimalIds, minimal.map { it.id }.toSet())
        minimal.forEach { candidate ->
            val minimumDistance = existing.minOf { paletteDistance(candidate, it) }
            assertTrue("${candidate.name} must not duplicate an existing palette ($minimumDistance)", minimumDistance > 40.0)
        }
        minimal.indices.forEach { index ->
            for (other in index + 1 until minimal.size) {
                assertTrue(
                    "${minimal[index].name} and ${minimal[other].name} must remain distinct",
                    paletteDistance(minimal[index], minimal[other]) > 20.0,
                )
            }
        }
    }

    @Test
    fun renderedLightModeAccentsRemainVisibleOnWhiteSurfaces() {
        ThemePacks.builtIns.forEach { pack ->
            listOf(pack.primaryHex, pack.secondaryHex, pack.tertiaryHex).forEach { accent ->
                val rendered = ThemePacks.accessibleLightAccentHex(accent)
                assertTrue("$rendered must be valid", ThemePacks.isValidHex(rendered))
                assertTrue("$rendered must have at least 3:1 contrast on white", contrastRatio(rendered, "#FFFFFF") >= 3.0)
            }
        }
        val brightCustomAccent = ThemePacks.accessibleLightAccentHex("#FDE047")
        assertTrue(contrastRatio(brightCustomAccent, "#FFFFFF") >= 3.0)
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

    /** RMS RGB distance across all three semantic swatches (0..255 scale). */
    private fun paletteDistance(first: ThemePack, second: ThemePack): Double {
        val firstColors = listOf(first.primaryHex, first.secondaryHex, first.tertiaryHex)
        val secondColors = listOf(second.primaryHex, second.secondaryHex, second.tertiaryHex)
        val squared = firstColors.zip(secondColors).flatMap { (a, b) ->
            a.drop(1).chunked(2).zip(b.drop(1).chunked(2)).map { (left, right) ->
                val difference = left.toInt(16) - right.toInt(16)
                difference * difference
            }
        }
        return kotlin.math.sqrt(squared.average())
    }
}
