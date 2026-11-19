package com.music.audify

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icon choices. Each is an `<activity-alias>` in the manifest
 * carrying the launcher intent filter; exactly one is enabled at a time.
 *
 * Settings previews each adaptive launcher resource at a larger tile size, so
 * the user sees the actual installed icon variant. The first two keep yesterday's
 * alias names so a choice made before the update stays enabled.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int, val previewRes: Int? = null) {
    Default("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_default),
    LiquidGlass("LauncherGlassLight", R.mipmap.ic_liquid_glass, R.string.app_icon_liquid_glass),
    Ocean("LauncherOcean", R.mipmap.ic_launcher_ocean, R.string.app_icon_ocean),
    Sunset("LauncherSunset", R.mipmap.ic_launcher_sunset, R.string.app_icon_sunset),
    Violet("LauncherViolet", R.mipmap.ic_launcher_violet, R.string.app_icon_violet),
    Emerald("LauncherEmerald", R.mipmap.ic_launcher_emerald, R.string.app_icon_emerald),
    Coral("LauncherCoral", R.mipmap.ic_launcher_coral, R.string.app_icon_coral),
    Amber("LauncherAmber", R.mipmap.ic_launcher_amber, R.string.app_icon_amber),
    Arctic("LauncherArctic", R.mipmap.ic_launcher_arctic, R.string.app_icon_arctic),
    Previous("LauncherPrevious", R.mipmap.ic_launcher_previous, R.string.app_icon_previous);

    private fun component(context: Context) =
        ComponentName(context.packageName, "${context.packageName}.$alias")

    companion object {
        /**
         * Icons that were removed leave no alias to enable. If one of them was
         * the pick and Default had been switched off, no launcher entry would be
         * left on the home screen: bring Default back.
         */
        fun repairLauncher(context: Context) {
            val pm = context.packageManager
            val enabled = entries.filter { icon ->
                runCatching { pm.getActivityInfo(icon.component(context), 0).enabled }.getOrDefault(false)
            }
            if (enabled.size != 1) apply(context, enabled.firstOrNull() ?: Default)
        }

        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                runCatching { pm.getActivityInfo(icon.component(context), 0).enabled }.getOrDefault(false)
            } ?: Default
        }

        /** Enables [icon] before disabling the rest so there is never a moment with no launcher entry. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
