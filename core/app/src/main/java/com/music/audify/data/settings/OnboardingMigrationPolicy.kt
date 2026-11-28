package com.music.audify.data.settings

/** Existing preference keys are evidence of an upgrade, not a first install. */
internal fun shouldCompleteOnboardingForExistingInstall(
    currentPreferenceKeys: Set<String>,
    legacyPreferenceKeys: Set<String>,
    onboardingKey: String,
    migrationKey: String,
): Boolean {
    if (onboardingKey in currentPreferenceKeys) return false
    val existingKeys = currentPreferenceKeys - migrationKey
    return existingKeys.isNotEmpty() || legacyPreferenceKeys.isNotEmpty()
}

/** A non-empty preference file is never a first install, even if it only has a migration marker. */
internal fun shouldSeedFirstInstallDefaults(currentPreferenceKeys: Set<String>): Boolean =
    currentPreferenceKeys.isEmpty()

/** Recover a partially completed migration only when the marker is the sole target key. */
internal fun shouldRecoverLegacyPreferencesFromMarkerOnlyStore(
    currentPreferenceKeys: Set<String>,
    legacyPreferenceKeys: Set<String>,
    migrationKey: String,
): Boolean = currentPreferenceKeys == setOf(migrationKey) && legacyPreferenceKeys.isNotEmpty()

/** Restore only legacy values whose current destination still equals a known first-install default. */
internal fun legacyPreferenceKeysOverwrittenByFirstInstallDefaults(
    currentValues: Map<String, Any?>,
    legacyValues: Map<String, Any?>,
    firstInstallDefaults: Map<String, Any>,
): Set<String> = firstInstallDefaults.keys.filterTo(mutableSetOf()) { key ->
    currentValues[key] == firstInstallDefaults[key] &&
        legacyValues.containsKey(key) &&
        legacyValues[key] != firstInstallDefaults[key]
}

/** Never overwrite a destination preference; import only keys not already present. */
internal fun legacyPreferenceKeysToImport(currentPreferenceKeys: Set<String>, legacyPreferenceKeys: Set<String>): Set<String> =
    legacyPreferenceKeys - currentPreferenceKeys
