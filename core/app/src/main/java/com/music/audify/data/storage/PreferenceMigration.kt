package com.music.audify.data.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Copies values from a previous SharedPreferences filename without deleting
 * the source. Preference filenames are part of the on-device data format: a
 * branding change must never make an existing install look like a fresh one.
 */
internal object PreferenceMigration {

    private const val MARKER_PREFIX = "_audify_legacy_preferences_migrated_"

    @Synchronized
    fun migrate(context: Context, targetName: String, legacyName: String) {
        if (targetName == legacyName) return

        val target = context.applicationContext.getSharedPreferences(targetName, Context.MODE_PRIVATE)
        val marker = MARKER_PREFIX + legacyName
        if (target.getBoolean(marker, false)) return

        val legacy = context.applicationContext.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        val copied = mergeMissing(target, legacy)
        if (!copied) {
            android.util.Log.w("Audify", "Could not completely migrate preferences $legacyName to $targetName; retaining the source")
            return
        }

        if (!target.edit().putBoolean(marker, true).commit()) {
            android.util.Log.w("Audify", "Could not persist migration marker for $legacyName")
            return
        }
        android.util.Log.i("Audify", "Migrated ${legacy.all.size} legacy preference entries from $legacyName to $targetName; source retained")
    }

    /** Merge source values only where the destination has no value. */
    internal fun mergeMissing(target: SharedPreferences, source: SharedPreferences): Boolean {
        val missing = missingValues(target.all, source.all)
        if (missing.isEmpty()) return true

        val editor = target.edit()
        missing.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (!editor.commit()) return false

        val stored = target.all
        return missing.all { (key, value) -> stored[key] == value }
    }

    internal fun missingValues(
        target: Map<String, *>,
        source: Map<String, *>,
    ): Map<String, Any> = source
        .filterKeys { it !in target }
        .mapNotNull { (key, value) -> value?.let { key to it } }
        .toMap()
}
