package com.music.audify

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconStateTest {
    @Test
    fun runtimeEnabledAliasWinsOverManifestDefault() {
        assertTrue(isLauncherComponentEnabled(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, false))
    }

    @Test
    fun runtimeDisabledDefaultAliasDoesNotAppearEnabled() {
        assertFalse(isLauncherComponentEnabled(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, true))
    }

    @Test
    fun defaultOverrideUsesManifestDeclaration() {
        assertTrue(isLauncherComponentEnabled(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, true))
        assertFalse(isLauncherComponentEnabled(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, false))
    }
}
