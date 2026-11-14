package com.music.audify.data.canvas

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Indeks di disk: lagu/rilisan -> [CanvasArtwork] yang pernah ditemukan dan
 * lolos [CanvasArtwork.matches].
 *
 * Klip videonya sendiri sudah disimpan [CanvasCache], tapi tanpa indeks ini
 * URL-nya hanya hidup di RAM ([CanvasRepository]) dan hilang saat proses mati.
 * Offline, pencarian ke provider gagal, sehingga klip yang sebenarnya sudah
 * ada di disk tidak pernah diputar.
 */
object CanvasIndex {

    private const val MAX_ENTRIES = 500

    private val entries = object : LinkedHashMap<String, CanvasArtwork>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CanvasArtwork>) =
            size > MAX_ENTRIES
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()
    private var file: File? = null

    fun init(context: Context) {
        val target = File(context.cacheDir, "canvas_index.json")
        file = target
        runCatching {
            if (!target.exists()) return@runCatching
            val array = JSONArray(target.readText())
            synchronized(entries) {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    entries[o.getString("key")] = CanvasArtwork(
                        url = o.getString("url"),
                        fallbackUrl = o.stringOrNull("fallbackUrl"),
                        title = o.stringOrNull("title"),
                        artist = o.stringOrNull("artist"),
                        album = o.stringOrNull("album"),
                        source = runCatching { CanvasSource.valueOf(o.optString("source")) }
                            .getOrDefault(CanvasSource.OTHER),
                        portraitUrl = o.stringOrNull("portraitUrl"),
                    )
                }
            }
        }
    }

    fun get(key: String): CanvasArtwork? = synchronized(entries) { entries[key] }

    fun put(key: String, artwork: CanvasArtwork) {
        synchronized(entries) {
            if (entries[key] == artwork) return
            entries[key] = artwork
        }
        persist()
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
        scope.launch {
            synchronized(writeLock) { runCatching { file?.delete() } }
        }
    }

    private fun persist() {
        val target = file ?: return
        val snapshot = synchronized(entries) { entries.toList() }
        scope.launch {
            synchronized(writeLock) {
                runCatching {
                    val array = JSONArray()
                    snapshot.forEach { (key, a) ->
                        array.put(
                            JSONObject()
                                .put("key", key)
                                .put("url", a.url)
                                .put("fallbackUrl", a.fallbackUrl)
                                .put("title", a.title)
                                .put("artist", a.artist)
                                .put("album", a.album)
                                .put("source", a.source.name)
                                .put("portraitUrl", a.portraitUrl),
                        )
                    }
                    val tmp = File(target.parentFile, target.name + ".tmp")
                    tmp.writeText(array.toString())
                    if (!tmp.renameTo(target)) {
                        target.writeText(array.toString())
                        tmp.delete()
                    }
                }
            }
        }
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }
}