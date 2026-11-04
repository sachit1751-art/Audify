// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {
    @Test
    fun durationIsSharedAcrossTargets() {
        assertEquals(225_000L, "3:45".durationMillis())
        assertEquals(3_723_000L, "1:02:03".durationMillis())
        assertEquals(0L, "not-a-duration".durationMillis())
        assertEquals(0L, null.durationMillis())
    }

    @Test
    fun localPlaylistBrowseIdsAreNotPrefixedAsRemoteYouTubeIds() {
        val local = UserPlaylist("local:playlist:device-123", "Offline", "2 songs", null)
        val remote = UserPlaylist("PL123", "Remote", "2 songs", null)

        assertEquals("local:playlist:device-123", local.browseId)
        assertEquals("VLPL123", remote.browseId)
    }

    @Test
    fun artworkSizeHintIsSharedAcrossTargets() {
        assertEquals(
            "https://example.test/w720-h720.jpg",
            "https://example.test/w120-h120.jpg".artworkAt(720),
        )
        assertEquals("https://example.test/cover.jpg", "https://example.test/cover.jpg".artworkAt(720))
        assertEquals(
            "https://yt3.ggpht.com/ytc/AIdro_x123=s512-c-k-c0x00ffffff-no-rj",
            "https://yt3.ggpht.com/ytc/AIdro_x123=s88-c-k-c0x00ffffff-no-rj".artworkAt(512),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/a/ACg8oc=s512-c",
            "https://lh3.googleusercontent.com/a/ACg8oc=s96-c".artworkAt(512),
        )
        assertEquals(
            "https://example.test/cover=s88.jpg",
            "https://example.test/cover=s88.jpg".artworkAt(512),
        )
        assertEquals(
            "https://notggpht.com/avatar=s88.jpg",
            "https://notggpht.com/avatar=s88.jpg".artworkAt(512),
        )
    }

    private fun album(id: String) = ShelfItem(id, "", null, null, id)
    private fun song(id: String) = ShelfItem(id, "", null, id, null)

    @Test
    fun shelfComposeKeysAreStableAcrossReorderingAndUniqueForDuplicates() {
        val first = song("same")
        val second = album("album")
        val duplicate = song("same")
        val original = listOf(first, second, duplicate)
        val originalKeys = original.stableShelfComposeKeys()
        assertEquals(3, originalKeys.toSet().size)
        assertEquals(originalKeys[0], listOf(first, duplicate, second).stableShelfComposeKeys()[0])
        assertEquals(originalKeys[1], listOf(second, first, duplicate).stableShelfComposeKeys()[0])
    }

    @Test
    fun songComposeKeysAreStableAcrossReorderingAndUniqueForDuplicates() {
        val first = Song(videoId = "same", title = "one", artist = "artist", thumbnailUrl = null)
        val second = Song(videoId = "other", title = "two", artist = "artist", thumbnailUrl = null)
        val duplicate = first.copy(title = "duplicate")
        val original = listOf(first, second, duplicate)
        val keys = original.stableSongComposeKeys()
        assertEquals(3, keys.toSet().size)
        assertEquals(keys[1], listOf(second, first, duplicate).stableSongComposeKeys()[0])
        assertEquals(keys[0], listOf(first, duplicate, second).stableSongComposeKeys()[0])
    }

    @Test
    fun aReorderedReleaseShelfUnderAnotherTitleIsARepeat() {
        val albums = HomeShelf("Albums & singles", (1..10).map { album("a$it") })
        val reordered = HomeShelf("New albums & singles", (10 downTo 2).map { album("a$it") } + album("b"))
        val other = HomeShelf("Charts", (1..10).map { album("c$it") } + album("a1"))
        assertEquals(listOf(other), listOf(reordered, other).withoutRepeatsOf(listOf(albums)))
        assertEquals(listOf(albums), listOf(albums, reordered).withoutRepeatsOf(emptyList()))
    }

    @Test
    fun songShelvesAndAFewSharedAlbumsAreKept() {
        val recents = HomeShelf("Recents", (1..10).map { song("s$it") })
        val listenAgain = HomeShelf("Listen again", (1..10).map { song("s$it") } + album("a1") + album("a2"))
        val albums = HomeShelf("Albums & singles", (1..10).map { album("a$it") })
        assertEquals(listOf(listenAgain), listOf(listenAgain).withoutRepeatsOf(listOf(recents, albums)))
    }
}
