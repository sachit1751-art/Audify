package com.music.audify

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

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
    Arctic("LauncherArctic", R.mipmap.ic_launcher_arctic, R.string.app_icon_arctic),
    Mint("LauncherMint", R.mipmap.ic_launcher_mint, R.string.app_icon_mint),
    Lavender("LauncherLavender", R.mipmap.ic_launcher_lavender, R.string.app_icon_lavender),
    Ruby("LauncherRuby", R.mipmap.ic_launcher_ruby, R.string.app_icon_ruby),
    Previous("LauncherPrevious", R.mipmap.ic_launcher_previous, R.string.app_icon_previous);

    private fun component(context: Context) =
        ComponentName(context.packageName, "${context.packageName}.$alias")

    internal fun isEnabled(context: Context): Boolean {
        val pm = context.packageManager
        val component = component(context)
        return isLauncherComponentEnabled(
            overrideState = pm.getComponentEnabledSetting(component),
            manifestEnabled = runCatching { pm.getActivityInfo(component, 0).enabled }.getOrDefault(false),
        )
    }

    companion object {
        private const val PREFERENCES = "launcher_icon"
        private const val SELECTED_ALIAS = "selected_alias"

        /**
         * Icons that were removed leave no alias to enable. If one of them was
         * the pick and Default had been switched off, no launcher entry would be
         * left on the home screen: bring Default back.
         */
        fun repairLauncher(context: Context) {
            val pm = context.packageManager
            // These aliases existed in earlier releases but are no longer
            // selectable. Explicitly disable them so an old selection cannot
            // leave duplicate launcher icons after upgrading.
            listOf("LauncherCoral", "LauncherAmber", "LauncherRose").forEach { alias ->
                // Older aliases may no longer exist in this APK. A missing
                // component must not abort recovery of the currently selected icon.
                runCatching {
                    pm.setComponentEnabledSetting(
                        ComponentName(context.packageName, "${context.packageName}.$alias"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }

            // Some OEM launchers/package managers reset alias overrides while
            // updating or recreating the app. The user's choice is the source
            // of truth; re-apply it before the first Activity is launched.
            val savedAlias = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(SELECTED_ALIAS, null)
            val savedSelection = entries.firstOrNull { it.alias == savedAlias }
            if (savedSelection != null) {
                apply(context, savedSelection)
                return
            }

            val enabled = entries.filter { it.isEnabled(context) }
            if (enabled.size != 1) apply(context, enabled.firstOrNull() ?: Default)
        }

        fun current(context: Context): AppIcon {
            return entries.firstOrNull { it.isEnabled(context) } ?: Default
        }

        /** Enables [icon] before disabling the rest so there is never a moment with no launcher entry. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            // Commit synchronously before changing PackageManager state. If
            // the OEM launcher kills/recreates the process during the switch,
            // startup recovery still knows which icon the user picked.
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit(commit = true) {
                putString(SELECTED_ALIAS, icon.alias)
            }
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
            listOf("LauncherCoral", "LauncherAmber", "LauncherRose").forEach { alias ->
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, "${context.packageName}.$alias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/** PackageManager's ActivityInfo.enabled reflects the manifest, not runtime alias overrides. */
internal fun isLauncherComponentEnabled(overrideState: Int, manifestEnabled: Boolean): Boolean =
    when (overrideState) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestEnabled
        else -> manifestEnabled
    }
