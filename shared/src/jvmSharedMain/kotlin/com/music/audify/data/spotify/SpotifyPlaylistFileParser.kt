// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

/** Legacy Spotify-only facade over the canonical format-neutral parser. */
enum class SpotifyPlaylistFileFormat {
    SPOTIFY_CSV,
    SPOTIFY_TSV,
    JSON_UNSUPPORTED,
    M3U_UNSUPPORTED,
    UNKNOWN,
}

object SpotifyPlaylistFileParser {

    fun detect(content: String, fileName: String? = null): SpotifyPlaylistFileFormat =
        when (PlaylistFileImporter.detect(content, fileName)) {
            PlaylistImportFormat.SPOTIFY_CSV -> if (hasSpotifySignature(content, ',')) {
                SpotifyPlaylistFileFormat.SPOTIFY_CSV
            } else SpotifyPlaylistFileFormat.UNKNOWN
            PlaylistImportFormat.SPOTIFY_TSV -> if (hasSpotifySignature(content, '\t')) {
                SpotifyPlaylistFileFormat.SPOTIFY_TSV
            } else SpotifyPlaylistFileFormat.UNKNOWN
            PlaylistImportFormat.M3U -> SpotifyPlaylistFileFormat.M3U_UNSUPPORTED
            PlaylistImportFormat.AUDIFY_JSON -> SpotifyPlaylistFileFormat.JSON_UNSUPPORTED
            null -> SpotifyPlaylistFileFormat.UNKNOWN
        }

    fun parse(content: String, format: SpotifyPlaylistFileFormat): List<SpotifyImportTrack> {
        val fileName = when (format) {
            SpotifyPlaylistFileFormat.SPOTIFY_CSV -> "playlist.csv"
            SpotifyPlaylistFileFormat.SPOTIFY_TSV -> "playlist.tsv"
            else -> error("Unsupported Spotify playlist file format: $format")
        }
        return PlaylistFileImporter.parse(content, fileName).entries.map {
            SpotifyImportTrack(it.title, it.artist)
        }.filter { it.title.isNotBlank() }.also { tracks ->
            require(tracks.isNotEmpty()) { "No tracks found in Spotify playlist file" }
        }
    }

    private fun hasSpotifySignature(content: String, delimiter: Char): Boolean {
        val header = content.removePrefix("\uFEFF").lineSequence().firstOrNull().orEmpty()
            .split(delimiter).map { it.trim().removePrefix("\uFEFF").lowercase() }
        return header.any { it in SPOTIFY_SIGNATURE_HEADERS } ||
            ("track name" in header && "artist name(s)" in header)
    }

    private val SPOTIFY_SIGNATURE_HEADERS = setOf(
        "track uri", "track id", "album uri", "album artist name(s)", "added at", "added by",
    )
}
