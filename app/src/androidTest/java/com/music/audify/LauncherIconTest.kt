package com.music.audify

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.audify.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test
    fun switchingIconKeepsExactlyOneLauncherAliasEnabledAndCanRestoreCurrentIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val aliases = listOf("LauncherDefault", "LauncherPrevious", "LauncherOcean", "LauncherSunset", "LauncherViolet")
            .map { ComponentName(context, "${context.packageName}.$it") }

        try {
            AppSettings.setLauncherIcon(context, "previous")
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                context.packageManager.getComponentEnabledSetting(aliases[1]),
            )
            aliases.filterIndexed { index, _ -> index != 1 }.forEach { alias ->
                assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(alias))
            }

            AppSettings.setLauncherIcon(context, "default")
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                context.packageManager.getComponentEnabledSetting(aliases[0]),
            )
            aliases.drop(1).forEach { alias ->
                assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(alias))
            }
        } finally {
            AppSettings.setLauncherIcon(context, "default")
        }
    }
}
