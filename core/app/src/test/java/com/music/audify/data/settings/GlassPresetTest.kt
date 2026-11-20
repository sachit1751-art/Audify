// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPresetTest {
    @Test
    fun performancePresetDisablesExpensiveLensEffects() {
        val preset = GlassPreset.PERFORMANCE

        assertTrue(preset.frosted)
        assertFalse(preset.custom.refraction > 0f)
        assertFalse(preset.indicator.restLens)
        assertFalse(preset.indicator.depth)
        assertFalse(preset.darkIndicator.restLens)
    }

    @Test
    fun bestExperiencePresetUsesStrongerGlassEffects() {
        val performance = GlassPreset.PERFORMANCE
        val best = GlassPreset.BEST_EXPERIENCE

        assertTrue(best.custom.refraction > performance.custom.refraction)
        assertTrue(best.custom.quality > performance.custom.quality)
        assertTrue(best.indicator.depth)
        assertTrue(best.indicator.restLens)
    }

    @Test
    fun everyPresetMatchesItsLightAndDarkValues() {
        GlassPreset.values().forEach { preset ->
            assertTrue(preset.matches(preset.level, preset.custom, preset.indicator, preset.frosted))
            assertTrue(preset.matches(preset.level, preset.darkCustom, preset.darkIndicator, preset.frosted))
        }
    }
}
