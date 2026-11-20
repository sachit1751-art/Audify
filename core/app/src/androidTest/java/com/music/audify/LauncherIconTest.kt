package com.music.audify

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.audify.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test
    fun everyIconVariantHasARealLauncherAliasAndSelectionSurvivesAReadback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager

        try {
            AppIcon.entries.forEach { option ->
                val component = ComponentName(context, "${context.packageName}.${option.alias}")
                val activity = manager.getActivityInfo(component, 0)
                assertEquals("${option.name} must have a launcher icon", option.iconRes, activity.icon)
                AppIcon.apply(context, option)
                assertEquals("${option.name} selection must be readable", option, AppIcon.current(context))
                assertEquals(
                    "${option.name} must be the only enabled launcher alias",
                    1,
                    AppIcon.entries.count {
                        manager.getComponentEnabledSetting(
                            ComponentName(context, "${context.packageName}.${it.alias}"),
                        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    },
                )
            }
        } finally {
            AppIcon.apply(context, AppIcon.Default)
        }
    }

    @Test
    fun launcherRepairRestoresOneEntryWhenAliasesAreDisabledOrDuplicated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val components = AppIcon.entries.map {
            ComponentName(context, "${context.packageName}.${it.alias}")
        }

        try {
            components.forEach {
                manager.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            AppIcon.repairLauncher(context)
            assertEquals(AppIcon.Default, AppIcon.current(context))

            AppIcon.apply(context, AppIcon.Ocean)
            manager.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.${AppIcon.Sunset.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            AppIcon.repairLauncher(context)

            assertEquals(1, components.count { manager.getActivityInfo(it, 0).enabled })
            assertEquals(AppIcon.Ocean, AppIcon.current(context))
        } finally {
            AppIcon.apply(context, AppIcon.Default)
        }
    }

}
