package com.music.audify.data.lyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.security.MessageDigest

/**
 * Lirik yang sudah ketemu disimpan di disk, jadi lagu yang sama tidak perlu
 * menanyai belasan sumber lagi dan lirik tetap tampil saat offline. Hanya hasil
 * yang BERHASIL disimpan: "tidak ketemu" bisa berarti sedang offline, jadi tidak dicatat.
 */
object LyricsDiskCache {
    /** Diisi aplikasi saat start. Null = tanpa cache. */
    @Volatile
    var dir: File? = null

    private const val VERSION = 1
    private const val MAX_BYTES = 20L * 1024 * 1024
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private var writes = 0

    private fun file(key: String): File? {
        val d = dir ?: return null
        val hex = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(d, "$hex.json")
    }

    fun read(key: String): LyricsRepository.Result? {
        val f = file(key) ?: return null
        if (!f.isFile || System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) return null
        return runCatching {
            val root = Json.parseToJsonElement(f.readText()).jsonObject
            if (root["v"]?.jsonPrimitive?.intOrNull != VERSION) return@runCatching null
            val source = LyricsSource.valueOf(root.getValue("src").jsonPrimitive.content)
            val lines = root.getValue("lines").jsonArray.map { it.jsonObject.toLine() }
            if (lines.isEmpty()) null else LyricsRepository.Result(source, lines)
        }.getOrNull()
    }

    fun write(key: String, result: LyricsRepository.Result) {
        val f = file(key) ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val body = buildJsonObject {
                put("v", VERSION)
                put("src", result.source.name)
                putJsonArray("lines") { result.lines.forEach { add(it.toJson()) } }
            }
            val tmp = File(f.path + ".tmp")
            tmp.writeText(body.toString())
            tmp.renameTo(f)
            if (writes++ % 20 == 0) prune(f.parentFile)
        }
    }

    private fun prune(d: File?) {
        val files = d?.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES * 3 / 4) break
            total -= f.length()
            f.delete()
        }
    }

    private fun LyricLine.toJson(): JsonObject = buildJsonObject {
        put("t", timeMs)
        put("x", text)
        if (words.isNotEmpty()) {
            putJsonArray("w") {
                words.forEach { w ->
                    addJsonObject {
                        put("s", w.startMs)
                        put("e", w.endMs)
                        put("x", w.text)
                        if (w.syllables.isNotEmpty()) {
                            putJsonArray("y") {
                                w.syllables.forEach { y ->
                                    addJsonObject {
                                        put("s", y.startMs)
                                        put("e", y.endMs)
                                        put("a", y.startChar)
                                        put("b", y.endChar)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        sungUntilMs?.let { put("u", it) }
        background?.let { put("bg", it.toJson()) }
        if (alignment != LyricAlignment.Start) put("al", alignment.name)
    }

    private fun JsonObject.toLine(): LyricLine = LyricLine(
        timeMs = getValue("t").jsonPrimitive.longOrNull ?: 0L,
        text = getValue("x").jsonPrimitive.content,
        words = this["w"]?.jsonArray?.map { it.jsonObject.toWord() }.orEmpty(),
        sungUntilMs = this["u"]?.jsonPrimitive?.longOrNull,
        background = this["bg"]?.jsonObject?.toLine(),
        alignment = this["al"]?.jsonPrimitive?.contentOrNull
            ?.let { name -> runCatching { LyricAlignment.valueOf(name) }.getOrNull() }
            ?: LyricAlignment.Start,
    )

    private fun JsonObject.toWord(): LyricWord = LyricWord(
        startMs = getValue("s").jsonPrimitive.longOrNull ?: 0L,
        endMs = getValue("e").jsonPrimitive.longOrNull ?: 0L,
        text = getValue("x").jsonPrimitive.content,
        syllables = this["y"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            LyricSyllable(
                startMs = o.getValue("s").jsonPrimitive.longOrNull ?: 0L,
                endMs = o.getValue("e").jsonPrimitive.longOrNull ?: 0L,
                startChar = o.getValue("a").jsonPrimitive.intOrNull ?: 0,
                endChar = o.getValue("b").jsonPrimitive.intOrNull ?: 0,
            )
        }.orEmpty(),
    )
}