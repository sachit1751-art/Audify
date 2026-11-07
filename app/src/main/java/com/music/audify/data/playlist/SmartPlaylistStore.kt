// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.playlist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Rules live separately from ordinary playlists; no Room schema or playlist mutation is involved. */
object SmartPlaylistStore {
    private const val PREFS = "audify_smart_playlists"
    private const val KEY_DEFINITIONS = "definitions_v1"
    private lateinit var prefs: SharedPreferences
    private val _definitions = MutableStateFlow<List<SmartPlaylistDefinition>>(emptyList())
    val definitions = _definitions.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _definitions.value = decode(prefs.getString(KEY_DEFINITIONS, "[]") ?: "[]")
    }

    @Synchronized
    fun save(definition: SmartPlaylistDefinition): SmartPlaylistDefinition {
        checkInitialized()
        require(SmartPlaylistEngine.validate(definition) == null) {
            SmartPlaylistEngine.validate(definition) ?: "Invalid smart playlist"
        }
        require(definition.id in _definitions.value.map { it.id } ||
            _definitions.value.size < SmartPlaylistEngine.MAX_PLAYLISTS) {
            "You can save up to ${SmartPlaylistEngine.MAX_PLAYLISTS} smart playlists"
        }
        val updated = listOf(definition) + _definitions.value.filterNot { it.id == definition.id }
        persist(updated)
        _definitions.value = updated
        return definition
    }

    @Synchronized
    fun setPaused(id: String, paused: Boolean): Boolean = update(id) { it.copy(paused = paused) }

    @Synchronized
    fun delete(id: String): Boolean {
        checkInitialized()
        val updated = _definitions.value.filterNot { it.id == id }
        if (updated.size == _definitions.value.size) return false
        persist(updated)
        _definitions.value = updated
        return true
    }

    private fun update(id: String, transform: (SmartPlaylistDefinition) -> SmartPlaylistDefinition): Boolean {
        checkInitialized()
        val current = _definitions.value.firstOrNull { it.id == id } ?: return false
        val updated = _definitions.value.map { if (it.id == id) transform(it) else it }
        persist(updated)
        _definitions.value = updated
        return current != updated.firstOrNull { it.id == id }
    }

    private fun persist(definitions: List<SmartPlaylistDefinition>) {
        val json = JSONArray().apply {
            definitions.forEach { definition ->
                put(JSONObject().apply {
                    put("id", definition.id)
                    put("title", definition.title)
                    put("artists", JSONArray(definition.artists))
                    put("likedOnly", definition.likedOnly)
                    put("addedWithinDays", definition.addedWithinDays ?: JSONObject.NULL)
                    put("excludeRecentHistory", definition.excludeRecentHistory)
                    put("playedWithinDays", definition.playedWithinDays ?: JSONObject.NULL)
                    put("notPlayedWithinDays", definition.notPlayedWithinDays ?: JSONObject.NULL)
                    put("minimumPlays", definition.minimumPlays ?: JSONObject.NULL)
                    put("sortBy", definition.sortBy.name)
                    put("maxTracks", definition.maxTracks)
                    put("paused", definition.paused)
                })
            }
        }.toString()
        check(prefs.edit().putString(KEY_DEFINITIONS, json).commit()) {
            "Could not save smart playlist rules"
        }
    }

    private fun decode(raw: String): List<SmartPlaylistDefinition> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = item.optString("title").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val artists = item.optJSONArray("artists")?.let { values ->
                (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty().distinct().take(SmartPlaylistEngine.MAX_ARTISTS)
            val addedDays = if (item.isNull("addedWithinDays")) null else item.optInt("addedWithinDays")
            val playedDays = if (item.isNull("playedWithinDays")) null else item.optInt("playedWithinDays")
            val notPlayedDays = if (item.isNull("notPlayedWithinDays")) null else item.optInt("notPlayedWithinDays")
            val minimumPlays = if (item.isNull("minimumPlays")) null else item.optInt("minimumPlays")
            SmartPlaylistDefinition(
                id = id,
                title = title.take(80),
                artists = artists,
                likedOnly = item.optBoolean("likedOnly"),
                addedWithinDays = addedDays?.takeIf { it in 1..SmartPlaylistEngine.MAX_AGE_DAYS },
                excludeRecentHistory = item.optBoolean("excludeRecentHistory"),
                playedWithinDays = playedDays?.takeIf { it in 1..SmartPlaylistEngine.MAX_AGE_DAYS },
                notPlayedWithinDays = notPlayedDays?.takeIf { it in 1..SmartPlaylistEngine.MAX_AGE_DAYS },
                minimumPlays = minimumPlays?.takeIf { it in 1..10_000 },
                sortBy = runCatching { SmartPlaylistSort.valueOf(item.optString("sortBy")) }
                    .getOrDefault(SmartPlaylistSort.LIBRARY_ORDER),
                maxTracks = item.optInt("maxTracks", 100).coerceIn(1, SmartPlaylistEngine.MAX_TRACKS),
                paused = item.optBoolean("paused"),
            ).takeIf { SmartPlaylistEngine.validate(it) == null }
        }.distinctBy { it.id }.take(SmartPlaylistEngine.MAX_PLAYLISTS)
    }.getOrDefault(emptyList())

    private fun checkInitialized() = check(this::prefs.isInitialized) {
        "SmartPlaylistStore must be initialized before use"
    }
}
