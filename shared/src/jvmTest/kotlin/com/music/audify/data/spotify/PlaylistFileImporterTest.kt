// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaylistFileImporterTest {

    @Test
    fun `parses spotify csv with bom quotes unicode album duration and provider id`() {
        val csv = "\uFEFFTrack URI,Track Name,Artist Name(s),Album Name,Duration (ms)\r\n" +
            "spotify:track:abc123,\"Song, Pt. 1\",\"Beyoncé; Jay-Z\",Album,183000\r\n"

        val document = PlaylistFileImporter.parse(csv, "mix.csv")

        assertEquals(PlaylistImportFormat.SPOTIFY_CSV, document.format)
        assertEquals(1, document.entries.size)
        assertEquals("Song, Pt. 1", document.entries.single().title)
        assertEquals("Beyoncé; Jay-Z", document.entries.single().artist)
        assertEquals("Album", document.entries.single().album)
        assertEquals(183_000L, document.entries.single().durationMs)
        assertEquals("abc123", document.entries.single().providerId)
    }

    @Test
    fun `parses tsv with quoted newline and preserves source row`() {
        val tsv = "Track Name\tArtist Name(s)\tTrack URI\r\n" +
            "\"Song\nwith newline\"\tArtist\tspotify:track:xyz\r\n"

        val document = PlaylistFileImporter.parse(tsv, "mix.tsv")

        assertEquals(PlaylistImportFormat.SPOTIFY_TSV, document.format)
        assertEquals(2, document.entries.single().sourceRow)
        assertEquals("Song\nwith newline", document.entries.single().title)
    }

    @Test
    fun `parses extended m3u metadata and keeps paths as untrusted metadata`() {
        val m3u = """#EXTM3U
#PLAYLIST:Road Mix
#EXTINF:183,Artist A - Track One
#EXTALB:Album One
../Music/track-one.mp3
#EXTINF:-1,Track Two
https://example.invalid/audio/two.mp3
""".trimIndent()

        val document = PlaylistFileImporter.parse(m3u, "road.m3u8")

        assertEquals(PlaylistImportFormat.M3U, document.format)
        assertEquals("Road Mix", document.playlistName)
        assertEquals("Track One", document.entries[0].title)
        assertEquals("Artist A", document.entries[0].artist)
        assertEquals("Album One", document.entries[0].album)
        assertEquals(183_000L, document.entries[0].durationMs)
        assertEquals("../Music/track-one.mp3", document.entries[0].sourceLocation)
        assertEquals("Track Two", document.entries[1].title)
        assertNull(document.entries[1].durationMs)
    }

    @Test
    fun `m3u without extinf derives title from file name`() {
        val document = PlaylistFileImporter.parse("#EXTM3U\nfolder/My Track.flac", "list.m3u")
        assertEquals("My Track", document.entries.single().title)
    }

    @Test
    fun `parses only explicitly versioned audify json`() {
        val json = """{
          "format":"audify-playlist",
          "version":1,
          "name":"Saved Mix",
          "tracks":[{"title":"Track","artist":"Artist","album":"Album","durationMs":210000,"providerId":"external-1","path":"../track.flac"}]
        }"""

        val document = PlaylistFileImporter.parse(json, "mix.json")

        assertEquals(PlaylistImportFormat.AUDIFY_JSON, document.format)
        assertEquals(1, document.formatVersion)
        assertEquals("Saved Mix", document.playlistName)
        assertEquals("external-1", document.entries.single().providerId)
        assertEquals("../track.flac", document.entries.single().sourceLocation)
    }

    @Test
    fun `rejects unsupported audify major version`() {
        val json = """{"format":"audify-playlist","version":2,"tracks":[]}"""
        assertFailsWith<IllegalArgumentException> { PlaylistFileImporter.parse(json, "mix.json") }
    }

    @Test
    fun `rejects excessive json nesting before decoding`() {
        val nested = "{".repeat(17) + "0" + "}".repeat(17)
        assertFailsWith<IllegalArgumentException> {
            PlaylistFileImporter.parse(nested, "deep.json")
        }
    }

    @Test
    fun `rejects track count and field length beyond documented limits`() {
        val tooManyRows = buildString {
            append("Title,Artist\n")
            repeat(PlaylistFileImporter.MAX_TRACKS + 1) { append("Track $it,Artist\n") }
        }
        assertFailsWith<IllegalArgumentException> {
            PlaylistFileImporter.parse(tooManyRows, "large.csv")
        }
        val longTitle = "x".repeat(PlaylistFileImporter.MAX_FIELD_CHARS + 1)
        assertFailsWith<IllegalArgumentException> {
            PlaylistFileImporter.parse("Title,Artist\n$longTitle,Artist\n", "long.csv")
        }
    }

    @Test
    fun `rejects generic json and malformed quoted rows`() {
        assertFailsWith<IllegalArgumentException> {
            PlaylistFileImporter.parse("""{"tracks":[]}""", "mix.json")
        }
        assertFailsWith<IllegalArgumentException> {
            PlaylistFileImporter.parse(
                "Track Name,Artist Name(s)\n\"unclosed,Artist",
                "mix.csv",
            )
        }
    }

    @Test
    fun `rejects unsupported empty and oversized documents`() {
        assertFailsWith<IllegalStateException> { PlaylistFileImporter.parse("not a playlist") }
        assertFailsWith<IllegalArgumentException> { PlaylistFileImporter.parse("#EXTM3U\n", "empty.m3u") }
        assertFailsWith<IllegalArgumentException> {
            PlaylistFileImporter.parse("x".repeat(PlaylistFileImporter.MAX_INPUT_BYTES + 1), "large.csv")
        }
    }

    @Test
    fun `preserves repeated source rows rather than silently deduplicating`() {
        val csv = "Title,Artist\nSame,Artist\nSame,Artist\n"
        val document = PlaylistFileImporter.parse(csv, "list.csv")
        assertEquals(listOf(2, 3), document.entries.map { it.sourceRow })
        assertEquals(2, document.entries.size)
    }
}
