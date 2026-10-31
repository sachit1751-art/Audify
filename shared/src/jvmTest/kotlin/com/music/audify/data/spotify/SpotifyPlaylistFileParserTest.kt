package com.music.audify.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotifyPlaylistFileParserTest {

    @Test
    fun detectsAndParsesSpotifyCsv() {
        val csv = """Track URI,Track Name,Artist Name(s),Album Name,Duration (ms)
            spotify:track:one,"Song, Part One","Artist A; Artist B",Album,183000
            spotify:track:two,Second Song,Artist C,Other Album,201000
        """.trimIndent()

        assertEquals(SpotifyPlaylistFileFormat.SPOTIFY_CSV, SpotifyPlaylistFileParser.detect(csv, "playlist.csv"))
        assertEquals(
            listOf(
                SpotifyImportTrack("Song, Part One", "Artist A; Artist B"),
                SpotifyImportTrack("Second Song", "Artist C"),
            ),
            SpotifyPlaylistFileParser.parse(csv, SpotifyPlaylistFileFormat.SPOTIFY_CSV),
        )
    }

    @Test
    fun detectsAndParsesBomPrefixedTsvWithQuotedNewline() {
        val tsv = "\uFEFFTrack Name\tArtist Name(s)\tTrack URI\r\n" +
            "\"Song\nwith newline\"\tArtist\tspotify:track:one\r\n" +
            "Another\tOther Artist\tspotify:track:two\r\n"

        assertEquals(SpotifyPlaylistFileFormat.SPOTIFY_TSV, SpotifyPlaylistFileParser.detect(tsv))
        assertEquals(
            listOf(
                SpotifyImportTrack("Song\nwith newline", "Artist"),
                SpotifyImportTrack("Another", "Other Artist"),
            ),
            SpotifyPlaylistFileParser.parse(tsv, SpotifyPlaylistFileFormat.SPOTIFY_TSV),
        )
    }

    @Test
    fun detectsFormatsWithoutMisclassifyingGenericTables() {
        assertEquals(
            SpotifyPlaylistFileFormat.UNKNOWN,
            SpotifyPlaylistFileParser.detect("Title,Artist\nSong,Someone", "songs.csv"),
        )
        assertEquals(
            SpotifyPlaylistFileFormat.SPOTIFY_CSV,
            SpotifyPlaylistFileParser.detect(
                "Track Name,Artist Name(s),Track URI\nSong,Artist,spotify:track:x",
                "misleading.json",
            ),
        )
        assertEquals(
            SpotifyPlaylistFileFormat.JSON_UNSUPPORTED,
            SpotifyPlaylistFileParser.detect("{\"tracks\":[]}", "playlist.json"),
        )
        assertEquals(
            SpotifyPlaylistFileFormat.M3U_UNSUPPORTED,
            SpotifyPlaylistFileParser.detect("#EXTM3U\ntrack.mp3", "playlist.m3u"),
        )
        assertEquals(SpotifyPlaylistFileFormat.UNKNOWN, SpotifyPlaylistFileParser.detect("not a playlist"))
    }

    @Test
    fun rejectsMalformedAndEmptySpotifyTables() {
        assertFailsWith<IllegalArgumentException> {
            SpotifyPlaylistFileParser.parse(
                "Track Name,Artist Name(s),Track URI\n\"Unclosed,Artist,spotify:track:x",
                SpotifyPlaylistFileFormat.SPOTIFY_CSV,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SpotifyPlaylistFileParser.parse(
                "Track Name,Artist Name(s),Track URI\n,,spotify:track:x",
                SpotifyPlaylistFileFormat.SPOTIFY_CSV,
            )
        }
    }
}
