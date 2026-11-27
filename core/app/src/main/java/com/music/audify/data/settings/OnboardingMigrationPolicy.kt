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

/** Seed defaults only when the migration marker is absent and no old settings exist. */
internal fun shouldSeedFirstInstallDefaults(currentPreferenceKeys: Set<String>, migrationKey: String): Boolean =
    currentPreferenceKeys == setOf(migrationKey)

/** Never overwrite a destination preference; import only keys not already present. */
internal fun legacyPreferenceKeysToImport(currentPreferenceKeys: Set<String>, legacyPreferenceKeys: Set<String>): Set<String> =
    legacyPreferenceKeys - currentPreferenceKeys
