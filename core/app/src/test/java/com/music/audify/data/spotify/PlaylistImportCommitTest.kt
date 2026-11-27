// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

import com.music.audify.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistImportCommitTest {
    private fun song(id: String) = Song(id, "Track $id", "Artist", null)

    @Test
    fun `merge keeps existing order and appends only new video ids once`() {
        val existing = LocalPlaylist("local-1", "Mix", listOf(song("a"), song("b")))

        val merged = mergeImportedSongs(existing, listOf(song("b"), song("c"), song("c"), song("d")))

        assertEquals(listOf("a", "b", "c", "d"), merged.songs.map { it.videoId })
        assertEquals(listOf("a", "b"), existing.songs.map { it.videoId })
    }
}
