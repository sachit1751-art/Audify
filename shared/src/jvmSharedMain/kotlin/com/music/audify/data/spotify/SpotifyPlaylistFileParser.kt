// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

/** File formats recognised by the Spotify playlist-file import layer. */
enum class SpotifyPlaylistFileFormat {
    SPOTIFY_CSV,
    SPOTIFY_TSV,
    JSON_UNSUPPORTED,
    M3U_UNSUPPORTED,
    UNKNOWN,
}

/**
 * Reads playlist CSV/TSV files produced by Spotify export tools.
 *
 * This is deliberately a parser layer: the app currently has no playlist-file
 * picker, column-mapping screen, or file-import host to call it from. Parsed
 * tracks use [SpotifyImportTrack], the same input type as the link importer.
 */
object SpotifyPlaylistFileParser {

    private data class Header(
        val title: Int,
        val artist: Int,
        val spotifySignature: Boolean,
    )

    /** Detects supported Spotify tables by header signature, with extensions as hints. */
    fun detect(content: String, fileName: String? = null): SpotifyPlaylistFileFormat {
        val name = fileName.orEmpty().substringAfterLast('.', "").lowercase()
        val trimmed = content.removePrefix("\uFEFF").trimStart()
        if (trimmed.startsWith("#EXTM3U")) {
            return SpotifyPlaylistFileFormat.M3U_UNSUPPORTED
        }
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            return SpotifyPlaylistFileFormat.JSON_UNSUPPORTED
        }

        val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() } ?:
            return SpotifyPlaylistFileFormat.UNKNOWN
        val candidates = listOf(
            '\t' to SpotifyPlaylistFileFormat.SPOTIFY_TSV,
            ',' to SpotifyPlaylistFileFormat.SPOTIFY_CSV,
        )
        val spotifyFormat = candidates.firstNotNullOfOrNull { (delimiter, format) ->
            val headerRow = runCatching { readDelimited(firstLine, delimiter).firstOrNull() }.getOrNull()
            val header = headerRow?.let(::headerFor)
            format.takeIf { header?.spotifySignature == true }
        }
        if (spotifyFormat != null) return spotifyFormat
        return when (name) {
            "json" -> SpotifyPlaylistFileFormat.JSON_UNSUPPORTED
            "m3u", "m3u8" -> SpotifyPlaylistFileFormat.M3U_UNSUPPORTED
            else -> SpotifyPlaylistFileFormat.UNKNOWN
        }
    }

    /** Parses a Spotify CSV/TSV table into the same candidates used by link import. */
    fun parse(content: String, format: SpotifyPlaylistFileFormat): List<SpotifyImportTrack> {
        val delimiter = when (format) {
            SpotifyPlaylistFileFormat.SPOTIFY_CSV -> ','
            SpotifyPlaylistFileFormat.SPOTIFY_TSV -> '\t'
            else -> error("Unsupported Spotify playlist file format: $format")
        }
        val rows = readDelimited(content.removePrefix("\uFEFF"), delimiter)
        require(rows.isNotEmpty()) { "Playlist file is empty" }
        val header = headerFor(rows.first())
            ?: error("Spotify playlist file must contain Track Name and Artist columns")
        require(header.spotifySignature) { "File does not have a recognised Spotify export header" }

        return rows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(header.title).orEmpty().trim()
            if (title.isEmpty()) return@mapNotNull null
            SpotifyImportTrack(
                title = title,
                artist = row.getOrNull(header.artist).orEmpty().trim(),
            )
        }.also { tracks ->
            require(tracks.isNotEmpty()) { "No tracks found in Spotify playlist file" }
        }
    }

    private fun headerFor(columns: List<String>): Header? {
        val names = columns.map { it.trim().removePrefix("\uFEFF").lowercase() }
        val title = names.indexOfFirst { it in TITLE_HEADERS }
        val artist = names.indexOfFirst { it in ARTIST_HEADERS }
        if (title < 0 || artist < 0) return null
        val signature = names.any { it in SPOTIFY_SIGNATURE_HEADERS } ||
            ("track name" in names && "artist name(s)" in names)
        return Header(title, artist, signature)
    }

    /** Small RFC-4180-style reader; quoted fields may contain delimiters/newlines. */
    private fun readDelimited(input: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var index = 0

        fun endField() {
            row += field.toString()
            field.setLength(0)
            afterQuote = false
        }
        fun endRow() {
            endField()
            if (row.any(String::isNotEmpty)) rows += row.toList()
            row.clear()
        }

        while (index < input.length) {
            val char = input[index]
            when {
                quoted && char == '"' && input.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                quoted && char == '"' -> {
                    quoted = false
                    afterQuote = true
                }
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
            index++
        }
        require(!quoted) { "Unclosed quoted field in playlist file" }
        if (field.isNotEmpty() || row.isNotEmpty() || afterQuote) endRow()
        return rows
    }

    private val TITLE_HEADERS = setOf("track name", "track", "title")
    private val ARTIST_HEADERS = setOf("artist name(s)", "artist names", "artist", "artists")
    private val SPOTIFY_SIGNATURE_HEADERS = setOf(
        "track uri", "track id", "album uri", "album artist name(s)", "added at", "added by",
    )
}
