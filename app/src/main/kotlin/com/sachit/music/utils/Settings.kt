/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sachit.music.constants.LoudnessLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A named setting: the storage key, the default when unset, and a human-readable name.
 *
 * Carries its own encode/decode so an enum stored as a string (which is how every enum preference
 * in this app is persisted) is a first-class setting rather than a special case. That is what lets
 * [Settings] stay at three functions no matter how many settings exist or how they are stored —
 * adding a setting does not widen the interface.
 */
class SettingsProperty<T>(
    val key: Preferences.Key<*>,
    val default: T,
    val name: String,
    val decode: (Any?) -> T,
    val encode: (T) -> Any,
) {
    override fun toString(): String = name
}

/** A boolean setting. Storages as a native boolean. */
fun booleanSetting(
    key: Preferences.Key<Boolean>,
    default: Boolean,
    name: String,
): SettingsProperty<Boolean> = SettingsProperty(key, default, name, { it as? Boolean ?: default }, { it })

/** A float setting. */
fun floatSetting(
    key: Preferences.Key<Float>,
    default: Float,
    name: String,
): SettingsProperty<Float> = SettingsProperty(key, default, name, { it as? Float ?: default }, { it })

/** An int setting. */
fun intSetting(
    key: Preferences.Key<Int>,
    default: Int,
    name: String,
): SettingsProperty<Int> = SettingsProperty(key, default, name, { it as? Int ?: default }, { it })

/** A string setting. */
fun stringSetting(
    key: Preferences.Key<String>,
    default: String,
    name: String,
): SettingsProperty<String> = SettingsProperty(key, default, name, { it as? String ?: default }, { it })

/** A long setting. */
fun longSetting(
    key: Preferences.Key<Long>,
    default: Long,
    name: String,
): SettingsProperty<Long> = SettingsProperty(key, default, name, { it as? Long ?: default }, { it })

/**
 * An enum setting, persisted by [Enum.name].
 *
 * An unrecognised stored value — a setting written by a newer build, or a renamed entry — falls
 * back to [default] rather than throwing, so a downgrade or a rename cannot crash startup.
 */
inline fun <reified T : Enum<T>> enumSetting(
    key: Preferences.Key<String>,
    default: T,
    name: String,
): SettingsProperty<T> =
    SettingsProperty(
        key = key,
        default = default,
        name = name,
        decode = { raw -> (raw as? String)?.let { stored -> enumValues<T>().find { it.name == stored } } ?: default },
        encode = { it.name },
    )

/**
 * Read and write application settings.
 *
 * Replaces two ad-hoc mechanisms that used to coexist: `rememberPreference(...)` (391 call sites,
 * Compose-bound and therefore untestable) and `context.dataStore.get(...)` (131 call sites, which
 * resolved via `runBlocking(Dispatchers.IO)` and blocked the calling thread).
 *
 * Deliberately small. Three functions cover every setting:
 *
 * - [read] for one-shot reads at a real async boundary,
 * - [observe] for reactive reads,
 * - [set] for writes.
 *
 * Nothing here is Android-specific and nothing blocks, so behaviour that depends on a setting can
 * be tested by handing the code under test a fake implementation.
 */
interface Settings {
    /** The current value of [property], or its default when unset. */
    suspend fun <T> read(property: SettingsProperty<T>): T

    /** Emits the current value, then every change. Never completes. */
    fun <T> observe(property: SettingsProperty<T>): Flow<T>

    /** Persists [value]. Returns true on success, false if the write failed. */
    suspend fun <T> set(
        property: SettingsProperty<T>,
        value: T,
    ): Boolean
}

/**
 * The settings the playback path reads today.
 *
 * Scoped to what crossfade, stream resolution and audio processing need — the rest migrate as
 * their call sites move. Adding a setting here is one line, which is the point: today adding one
 * means editing a 740-line key file, a settings screen, and hoping nothing else needed to agree.
 */
object SettingsProperties {
    val crossfadeEnabled =
        booleanSetting(
            key = com.sachit.music.constants.CrossfadeEnabledKey,
            default = false,
            name = "crossfadeEnabled",
        )

    val crossfadeDurationSeconds =
        floatSetting(
            key = com.sachit.music.constants.CrossfadeDurationKey,
            default = 5f,
            name = "crossfadeDuration",
        )

    val crossfadeGapless =
        booleanSetting(
            key = com.sachit.music.constants.CrossfadeGaplessKey,
            default = true,
            name = "crossfadeGapless",
        )

    val skipSilence =
        booleanSetting(
            key = com.sachit.music.constants.SkipSilenceKey,
            default = false,
            name = "skipSilence",
        )

    val skipSilenceInstant =
        booleanSetting(
            key = com.sachit.music.constants.SkipSilenceInstantKey,
            default = false,
            name = "skipSilenceInstant",
        )

    val audioOffload =
        booleanSetting(
            key = com.sachit.music.constants.AudioOffload,
            default = false,
            name = "audioOffload",
        )

    val shufflePlaylistFirst =
        booleanSetting(
            key = com.sachit.music.constants.ShufflePlaylistFirstKey,
            default = false,
            name = "shufflePlaylistFirst",
        )

    val playerVolume =
        floatSetting(
            key = com.sachit.music.constants.PlayerVolumeKey,
            default = 1f,
            name = "playerVolume",
        )

    val normalizationEnabled =
        booleanSetting(
            key = com.sachit.music.constants.AudioNormalizationKey,
            default = true,
            name = "audioNormalization",
        )

    val loudnessLevel =
        enumSetting(
            key = com.sachit.music.constants.LoudnessLevelKey,
            default = LoudnessLevel.BALANCED,
            name = "loudnessLevel",
        )

    val persistentQueue =
        booleanSetting(
            key = com.sachit.music.constants.PersistentQueueKey,
            default = true,
            name = "persistentQueue",
        )

    val hideExplicit =
        booleanSetting(
            key = com.sachit.music.constants.HideExplicitKey,
            default = false,
            name = "hideExplicit",
        )

    val hideVideoSongs =
        booleanSetting(
            key = com.sachit.music.constants.HideVideoSongsKey,
            default = false,
            name = "hideVideoSongs",
        )

    // Automatic sleep timer. The window strings are stored as "HH:mm" and the day-time map as
    // "0=09:00-23:00;1=22:00-06:00", which is the format SleepTimerSchedule parses.
    val sleepTimerEnabled =
        booleanSetting(
            key = com.sachit.music.constants.SleepTimerEnabledKey,
            default = false,
            name = "sleepTimerEnabled",
        )

    val sleepTimerRepeat =
        stringSetting(
            key = com.sachit.music.constants.SleepTimerRepeatKey,
            default = "daily",
            name = "sleepTimerRepeat",
        )

    val sleepTimerStartTime =
        stringSetting(
            key = com.sachit.music.constants.SleepTimerStartTimeKey,
            default = "09:00",
            name = "sleepTimerStartTime",
        )

    val sleepTimerEndTime =
        stringSetting(
            key = com.sachit.music.constants.SleepTimerEndTimeKey,
            default = "23:00",
            name = "sleepTimerEndTime",
        )

    val sleepTimerDefaultMinutes =
        floatSetting(
            key = com.sachit.music.constants.SleepTimerDefaultKey,
            default = 30f,
            name = "sleepTimerDefaultMinutes",
        )

    val sleepTimerCustomDays =
        stringSetting(
            key = com.sachit.music.constants.SleepTimerCustomDaysKey,
            default = "0,1,2,3,4",
            name = "sleepTimerCustomDays",
        )

    val sleepTimerDayTimes =
        stringSetting(
            key = com.sachit.music.constants.SleepTimerDayTimesKey,
            default = "",
            name = "sleepTimerDayTimes",
        )

    val lastFullSync =
        longSetting(
            key = com.sachit.music.constants.LastFullSyncKey,
            default = 0L,
            name = "lastFullSync",
        )
}

/**
 * A [Settings] backed by the app's DataStore.
 *
 * Writes deliberately go through [safeDataStoreEdit] rather than editing the store directly: that
 * helper recreates the datastore directory before every write, which is what keeps the write from
 * dying with an IOException on OEM ROMs where the directory disappears after deep sleep.
 */
class DataStoreSettings(
    private val context: Context,
) : Settings {
    private val dataStore: DataStore<Preferences>
        get() = context.applicationContext.dataStore

    override suspend fun <T> read(property: SettingsProperty<T>): T =
        property.decode(dataStore.data.first().asMap()[property.key])

    override fun <T> observe(property: SettingsProperty<T>): Flow<T> =
        dataStore.data
            .map { property.decode(it.asMap()[property.key]) }
            .distinctUntilChanged()

    override suspend fun <T> set(
        property: SettingsProperty<T>,
        value: T,
    ): Boolean {
        val encoded = property.encode(value)
        return context.applicationContext.safeDataStoreEdit { preferences ->
            @Suppress("UNCHECKED_CAST")
            preferences[property.key as Preferences.Key<Any>] = encoded
        }
    }
}

/**
 * Compose bridge: reads [property] as observable state and writes changes back.
 *
 * Equivalent to the old `rememberPreference(key, default)`, but keyed by the property rather than
 * by a raw key plus a default the call site had to remember to keep in sync.
 */
@Composable
fun <T> rememberSetting(property: SettingsProperty<T>): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings = remember(context) { context.settings() }
    val state = settings.observe(property).collectAsState(initial = property.default)

    return remember(property) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch { settings.set(property, value) }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

/** The application's [Settings], created once per process. */
fun Context.settings(): Settings = SettingsHolder.get(this)

private object SettingsHolder {
    @Volatile
    private var instance: Settings? = null

    fun get(context: Context): Settings =
        instance ?: synchronized(this) {
            instance ?: DataStoreSettings(context.applicationContext).also { instance = it }
        }
}