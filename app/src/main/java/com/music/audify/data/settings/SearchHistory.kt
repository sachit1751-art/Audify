package com.music.audify.data.settings

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.music.audify.data.model.SearchHistoryEntity
import com.music.audify.data.model.withCorrectedType

/**
 * What's been searched for lately, kept on this device only.
 *
 * YouTube Music has an account-level search history, but reading it back needs
 * a separate signed-in call on every keystroke's worth of empty query — and it
 * would be wrong for a signed-out user. A local list is instant, works either
 * way, and is what Spotify and Apple Music show in the same spot.
 *
 * Stored as JSON rather than a delimited string: a query can contain any
 * character, separators included.
 */
object SearchHistory {

    /** Deep enough to be useful, shallow enough that the list stays scannable. */
    private const val MAX_ENTRIES = 20

    private lateinit var prefs: SharedPreferences
    private val json = Json
    private val serializer = ListSerializer(SearchHistoryEntity.serializer())
    private val addonSerializer = ListSerializer(String.serializer())

    private val _recent = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())

    /** Most recent first. */
    val recent: StateFlow<List<SearchHistoryEntity>> = _recent.asStateFlow()

    private val _addonRecent = MutableStateFlow<List<String>>(emptyList())

    // Hydration runs off the UI thread. Mutations made before it finishes are
    // replayed over the disk snapshot so a fast first search cannot be lost.
    private val lock = Any()
    private val reloadLock = Any()
    @Volatile private var hydrated = false
    private val pendingRecent = mutableListOf<(List<SearchHistoryEntity>) -> List<SearchHistoryEntity>>()
    private val pendingAddon = mutableListOf<(List<String>) -> List<String>>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * What was typed into the Addons search, most recent first. Text only: an
     * addon search has no entity worth remembering, and re-running the words
     * is what a tap on it should do.
     */
    val addonRecent: StateFlow<List<String>> = _addonRecent.asStateFlow()

    fun recordAddonQuery(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        mutateAddon { current ->
            (listOf(text) + current.filterNot { it.equals(text, ignoreCase = true) }).take(MAX_ENTRIES)
        }
    }

    fun removeAddonQuery(query: String) = mutateAddon { current -> current.filterNot { it == query } }

    fun clearAddonQueries() = mutateAddon { emptyList() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        hydrated = false
        ioScope.launch { reload() }
    }

    /**
     * Re-reads the list off disk.
     *
     * This shares its preference file with [AppSettings], so an import replaces
     * what is stored here without ever calling [record] — and the flow above
     * would otherwise go on serving the list the old device had.
     */
    fun reload() {
        if (!this::prefs.isInitialized) return
        synchronized(reloadLock) {
            val diskRecent = runCatching {
                json.decodeFromString(serializer, prefs.getString(KEY_HISTORY, null) ?: "[]")
                    .map { it.withCorrectedType() }
            }.getOrDefault(emptyList())
            val diskAddon = runCatching {
                json.decodeFromString(addonSerializer, prefs.getString(KEY_ADDON_HISTORY, null) ?: "[]")
            }.getOrDefault(emptyList())
            synchronized(lock) {
                val recent = pendingRecent.fold(diskRecent) { current, mutation -> mutation(current) }
                val addon = pendingAddon.fold(diskAddon) { current, mutation -> mutation(current) }
                _recent.value = recent
                _addonRecent.value = addon
                if (pendingRecent.isNotEmpty()) {
                    prefs.edit { putString(KEY_HISTORY, json.encodeToString(serializer, recent)) }
                }
                if (pendingAddon.isNotEmpty()) {
                    prefs.edit { putString(KEY_ADDON_HISTORY, json.encodeToString(addonSerializer, addon)) }
                }
                pendingRecent.clear()
                pendingAddon.clear()
                hydrated = true
            }
        }
    }

    /**
     * Records [entity], or moves it back to the top if it's already there.
     * Matching is by id so re-tapping an existing entity updates its timestamp.
     */
    fun record(entity: SearchHistoryEntity) {
        mutateRecent { current ->
            (listOf(entity) + current.filterNot { it.id.equals(entity.id, ignoreCase = true) }).take(MAX_ENTRIES)
        }
    }

    fun remove(id: String) {
        mutateRecent { current -> current.filterNot { it.id.equals(id, ignoreCase = true) } }
    }

    fun clear() = mutateRecent { emptyList() }

    fun clearAll() {
        clear()
        clearAddonQueries()
    }

    private fun mutateRecent(mutation: (List<SearchHistoryEntity>) -> List<SearchHistoryEntity>) {
        synchronized(lock) {
            val next = mutation(_recent.value)
            _recent.value = next
            if (hydrated) {
                if (this::prefs.isInitialized) {
                    prefs.edit { putString(KEY_HISTORY, json.encodeToString(serializer, next)) }
                }
            } else {
                pendingRecent += mutation
            }
        }
    }

    private fun mutateAddon(mutation: (List<String>) -> List<String>) {
        synchronized(lock) {
            val next = mutation(_addonRecent.value)
            _addonRecent.value = next
            if (hydrated) {
                if (this::prefs.isInitialized) {
                    prefs.edit { putString(KEY_ADDON_HISTORY, json.encodeToString(addonSerializer, next)) }
                }
            } else {
                pendingAddon += mutation
            }
        }
    }

    private const val KEY_HISTORY = "search_history"
    private const val KEY_ADDON_HISTORY = "addon_search_history"
}
