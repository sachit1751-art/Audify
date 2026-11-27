// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PlaylistImportFormat { SPOTIFY_CSV, SPOTIFY_TSV, M3U, AUDIFY_JSON }

/** Untrusted source metadata. [providerId] and [sourceLocation] are never playback IDs/URIs. */
data class PlaylistImportEntry(
    val sourceRow: Int,
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val durationMs: Long? = null,
    val providerId: String? = null,
    val sourceLocation: String? = null,
)

data class PlaylistImportDocument(
    val format: PlaylistImportFormat,
    val formatVersion: Int?,
    val playlistName: String?,
    val entries: List<PlaylistImportEntry>,
)

/** Strict, bounded, platform-neutral parsing for playlist exports. */
object PlaylistFileImporter {
    const val MAX_INPUT_BYTES = 5 * 1024 * 1024
    const val MAX_TRACKS = 1_000
    const val MAX_FIELD_CHARS = 4_096
    private const val MAX_JSON_DEPTH = 16

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        allowTrailingComma = false
    }

    fun detect(content: String, fileName: String? = null): PlaylistImportFormat? {
        val text = content.removePrefix("\uFEFF").trimStart()
        val extension = fileName.orEmpty().substringAfterLast('.', "").lowercase()
        return when {
            text.startsWith("#EXTM3U") || extension in setOf("m3u", "m3u8") -> PlaylistImportFormat.M3U
            text.startsWith('{') -> PlaylistImportFormat.AUDIFY_JSON
            extension == "json" && text.startsWith('[') -> PlaylistImportFormat.AUDIFY_JSON
            extension == "tsv" || text.lineSequence().firstOrNull()?.contains('\t') == true ->
                PlaylistImportFormat.SPOTIFY_TSV.takeIf { hasTrackHeaders(text, '\t') }
            extension == "csv" || text.lineSequence().firstOrNull()?.contains(',') == true ->
                PlaylistImportFormat.SPOTIFY_CSV.takeIf { hasTrackHeaders(text, ',') }
            else -> null
        }
    }

    fun parse(content: String, fileName: String? = null): PlaylistImportDocument {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_INPUT_BYTES) {
            "Playlist file exceeds the 5 MiB import limit"
        }
        val format = detect(content, fileName)
            ?: error("Unsupported playlist file. Choose a Spotify CSV/TSV, M3U/M3U8, or Audify JSON export.")
        val document = when (format) {
            PlaylistImportFormat.SPOTIFY_CSV -> parseDelimited(content, ',', format)
            PlaylistImportFormat.SPOTIFY_TSV -> parseDelimited(content, '\t', format)
            PlaylistImportFormat.M3U -> parseM3u(content)
            PlaylistImportFormat.AUDIFY_JSON -> parseAudifyJson(content)
        }
        require(document.entries.isNotEmpty()) { "The playlist contains no readable track rows" }
        require(document.entries.size <= MAX_TRACKS) {
            "Playlist contains more than $MAX_TRACKS tracks; split it into smaller files"
        }
        return document
    }

    private fun parseDelimited(
        content: String,
        delimiter: Char,
        format: PlaylistImportFormat,
    ): PlaylistImportDocument {
        val rows = readDelimited(content.removePrefix("\uFEFF"), delimiter)
        require(rows.isNotEmpty()) { "Playlist file is empty" }
        val headers = rows.first().map(::normaliseHeader)
        val titleIndex = headers.indexOfFirst { it in TITLE_HEADERS }
        val artistIndex = headers.indexOfFirst { it in ARTIST_HEADERS }
        require(titleIndex >= 0 && artistIndex >= 0) {
            "Playlist file needs Track Name/Title and Artist columns"
        }
        val albumIndex = headers.indexOfFirst { it in ALBUM_HEADERS }
        val durationIndex = headers.indexOfFirst { it in DURATION_HEADERS }
        val idIndex = headers.indexOfFirst { it in ID_HEADERS }
        val entries = rows.drop(1).mapIndexed { index, row ->
            val title = row.getOrNull(titleIndex).orEmpty().trim()
            PlaylistImportEntry(
                sourceRow = index + 2,
                title = bounded(title, "Track title"),
                artist = bounded(row.getOrNull(artistIndex).orEmpty().trim(), "Artist"),
                album = row.getOrNull(albumIndex)?.trim()?.takeIf(String::isNotBlank)
                    ?.let { bounded(it, "Album") },
                durationMs = row.getOrNull(durationIndex)?.let(::parseDuration),
                providerId = row.getOrNull(idIndex)?.trim()?.takeIf(String::isNotBlank)
                    ?.let(::extractProviderId),
            )
        }
        return PlaylistImportDocument(format, null, null, entries)
    }

    private fun parseM3u(content: String): PlaylistImportDocument {
        val entries = mutableListOf<PlaylistImportEntry>()
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingAlbum: String? = null
        var pendingDuration: Long? = null
        var playlistName: String? = null
        var row = 0

        content.removePrefix("\uFEFF").lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) ->
                    playlistName = line.substringAfter(':').trim().takeIf(String::isNotBlank)
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = line.substringAfter(':')
                    val comma = payload.indexOf(',')
                    val duration = payload.substringBefore(',').trim().toDoubleOrNull()
                    pendingDuration = duration?.takeIf { it >= 0 }?.let { (it * 1_000).toLong() }
                    val display = if (comma >= 0) payload.substring(comma + 1).trim() else ""
                    val separator = display.indexOf(" - ")
                    if (separator > 0) {
                        pendingArtist = display.substring(0, separator).trim()
                        pendingTitle = display.substring(separator + 3).trim()
                    } else {
                        pendingTitle = display
                    }
                }
                line.startsWith("#EXTART:", ignoreCase = true) ->
                    pendingArtist = line.substringAfter(':').trim()
                line.startsWith("#EXTALB:", ignoreCase = true) ->
                    pendingAlbum = line.substringAfter(':').trim()
                line.startsWith('#') -> Unit
                else -> {
                    row++
                    val title = pendingTitle?.takeIf(String::isNotBlank)
                        ?: line.substringAfterLast('/').substringAfterLast('\\')
                            .substringBeforeLast('.', missingDelimiterValue = line)
                    entries += PlaylistImportEntry(
                        sourceRow = row,
                        title = bounded(title, "Track title"),
                        artist = bounded(pendingArtist.orEmpty(), "Artist"),
                        album = pendingAlbum?.takeIf(String::isNotBlank)?.let { bounded(it, "Album") },
                        durationMs = pendingDuration,
                        sourceLocation = bounded(line, "Source path"),
                    )
                    pendingTitle = null
                    pendingArtist = null
                    pendingAlbum = null
                    pendingDuration = null
                }
            }
        }
        return PlaylistImportDocument(PlaylistImportFormat.M3U, null, playlistName, entries)
    }

    private fun parseAudifyJson(content: String): PlaylistImportDocument {
        requireJsonDepth(content)
        val root = json.parseToJsonElement(content.removePrefix("\uFEFF"))
        requireDepth(root, 0)
        val obj = root as? JsonObject ?: error("Audify JSON root must be an object")
        require(obj.string("format") == "audify-playlist") {
            "JSON is not an Audify playlist export (format must be audify-playlist)"
        }
        val version = obj.int("version") ?: error("Audify playlist JSON is missing its version")
        require(version == 1) { "Audify playlist version $version is not supported; supported version: 1" }
        val tracks = obj["tracks"] as? JsonArray ?: error("Audify playlist JSON is missing its tracks array")
        require(tracks.size <= MAX_TRACKS) {
            "Playlist contains more than $MAX_TRACKS tracks; split it into smaller files"
        }
        val entries = tracks.mapIndexed { index, element ->
            val track = element as? JsonObject ?: error("Track ${index + 1} must be an object")
            PlaylistImportEntry(
                sourceRow = index + 1,
                title = bounded(track.string("title").orEmpty(), "Track title"),
                artist = bounded(track.string("artist").orEmpty(), "Artist"),
                album = track.string("album")?.takeIf(String::isNotBlank)?.let { bounded(it, "Album") },
                durationMs = track.long("durationMs")?.takeIf { it >= 0 },
                // IDs are provenance only. They are deliberately not converted to
                // Audify/YouTube playback IDs, even in our own interchange format.
                providerId = track.string("providerId")?.takeIf(String::isNotBlank)
                    ?.let { bounded(it, "Provider ID") },
                sourceLocation = track.string("path")?.takeIf(String::isNotBlank)
                    ?.let { bounded(it, "Source path") },
            )
        }
        return PlaylistImportDocument(
            format = PlaylistImportFormat.AUDIFY_JSON,
            formatVersion = version,
            playlistName = obj.string("name")?.takeIf(String::isNotBlank)?.let { bounded(it, "Playlist name") },
            entries = entries,
        )
    }

    private fun readDelimited(input: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var index = 0
        fun endField() {
            row += bounded(field.toString(), "Field")
            field.setLength(0)
            afterQuote = false
        }
        fun endRow() {
            endField()
            if (row.any(String::isNotEmpty)) rows += row.toList()
            row.clear()
            require(rows.size <= MAX_TRACKS + 1) { "Playlist contains more than $MAX_TRACKS rows" }
        }
        while (index < input.length) {
            val char = input[index]
            when {
                quoted && char == '"' && input.getOrNull(index + 1) == '"' -> {
                    field.append('"'); index++
                }
                quoted && char == '"' -> { quoted = false; afterQuote = true }
                quoted -> field.append(char)
                afterQuote && char == delimiter -> endField()
                afterQuote && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                    endRow()
                }
                afterQuote && char.isWhitespace() -> Unit
                afterQuote -> error("Unexpected character after quoted field")
                char == '"' && field.isEmpty() -> quoted = true
                char == '"' -> error("Unexpected quote in unquoted field")
                char == delimiter -> endField()
                char == '\n' || char == '\r' -> {
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                    endRow()
                }
                else -> field.append(char)
            }
            require(field.length <= MAX_FIELD_CHARS) { "Playlist field exceeds $MAX_FIELD_CHARS characters" }
            index++
        }
        require(!quoted) { "Unclosed quoted field in playlist file" }
        if (field.isNotEmpty() || row.isNotEmpty() || afterQuote) endRow()
        return rows
    }

    private fun hasTrackHeaders(content: String, delimiter: Char): Boolean = runCatching {
        val first = content.removePrefix("\uFEFF").lineSequence().firstOrNull().orEmpty()
        val headers = readDelimited(first, delimiter).firstOrNull().orEmpty().map(::normaliseHeader)
        headers.any { it in TITLE_HEADERS } && headers.any { it in ARTIST_HEADERS }
    }.getOrDefault(false)

    private fun normaliseHeader(value: String): String = value.trim().removePrefix("\uFEFF")
        .lowercase().replace(Regex("[ _-]+"), " ").trim()

    private fun parseDuration(value: String): Long? {
        val raw = value.trim()
        raw.toLongOrNull()?.takeIf { it >= 0 }?.let { return it }
        raw.toDoubleOrNull()?.takeIf { it >= 0 }?.let { return (it * 1_000).toLong() }
        val parts = raw.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3 || parts.any { it < 0 }) return null
        val seconds = parts.fold(0L) { total, part -> total * 60L + part }
        return seconds.takeIf { it > 0 }?.times(1_000L)
    }

    private fun extractProviderId(value: String): String =
        value.substringAfterLast(':').substringBefore('?').trim().take(MAX_FIELD_CHARS)

    private fun bounded(value: String, label: String): String {
        require(value.length <= MAX_FIELD_CHARS) { "$label exceeds $MAX_FIELD_CHARS characters" }
        return value
    }

    private fun requireDepth(element: JsonElement, depth: Int) {
        require(depth <= MAX_JSON_DEPTH) { "Audify playlist JSON nesting exceeds $MAX_JSON_DEPTH levels" }
        when (element) {
            is JsonObject -> element.values.forEach { requireDepth(it, depth + 1) }
            is JsonArray -> element.forEach { requireDepth(it, depth + 1) }
            else -> Unit
        }
    }

    /** Reject pathological nesting before the JSON decoder recurses into it. */
    private fun requireJsonDepth(content: String) {
        var depth = 0
        var inString = false
        var escaped = false
        content.forEach { char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        require(depth <= MAX_JSON_DEPTH) {
                            "Audify playlist JSON nesting exceeds $MAX_JSON_DEPTH levels"
                        }
                    }
                    '}', ']' -> depth--
                }
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
    private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

    private val TITLE_HEADERS = setOf("track name", "track", "title", "song", "name")
    private val ARTIST_HEADERS = setOf("artist name(s)", "artist names", "artist", "artists", "performer")
    private val ALBUM_HEADERS = setOf("album name", "album", "release")
    private val DURATION_HEADERS = setOf("duration ms", "duration (ms)", "duration", "length ms", "length")
    private val ID_HEADERS = setOf("track uri", "track id", "spotify track id", "provider id", "uri")
}
