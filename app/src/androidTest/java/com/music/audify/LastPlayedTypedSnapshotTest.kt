// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.data.model.Song
import com.music.audify.playback.LastPlayed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastPlayedTypedSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy { context.getSharedPreferences("bitchord_last_played", Context.MODE_PRIVATE) }

    @Before
    fun setUp() {
        LastPlayed.init(context)
        LastPlayed.clearImmediately()
    }

    @After
    fun tearDown() {
        LastPlayed.clearImmediately()
    }

    @Test
    fun queueAndPlaybackPositionRoundTripWithoutSerializingEveryProgressUpdate() {
        val songs = listOf(song("first"), song("current"), song("next"))

        LastPlayed.saveQueueImmediately(songs, index = 1, positionMs = 4_567L)
        val restored = LastPlayed.load()

        assertNotNull(restored)
        assertEquals(listOf("first", "current", "next"), restored!!.songs.map { it.videoId })
        assertEquals(1, restored.index)
        assertEquals(4_567L, restored.positionMs)
    }

    @Test
    fun legacyQueueSnapshotWithEmbeddedIndexAndPositionStillDecodes() {
        preferences.edit().putString(
            "queue",
            """{"tracks":[{"id":"legacy-id","title":"Legacy","artist":"Artist"}],"index":0,"positionMs":1234}""",
        ).commit()

        val restored = LastPlayed.load()

        assertNotNull(restored)
        assertEquals("legacy-id", restored!!.songs.single().videoId)
        assertEquals(0, restored.index)
        assertEquals(1_234L, restored.positionMs)
    }

    @Test
    fun queueWindowKeepsTheCurrentTrackAndBoundsTheRestoredHistory() {
        val beforeEnd = LastPlayed.window(size = 10_000, index = 5_000)
        val nearEnd = LastPlayed.window(size = 10_000, index = 9_999)

        assertTrue(beforeEnd.count() <= 76)
        assertTrue(5_000 in beforeEnd)
        assertEquals(9_999, nearEnd.last)
        assertTrue(nearEnd.count() <= 76)
        assertEquals(IntRange.EMPTY, LastPlayed.window(size = 0, index = 0))
    }

    @Test
    fun malformedSnapshotIsTreatedAsAbsent() {
        preferences.edit().putString("queue", "not-json").commit()
        assertNull(LastPlayed.load())
    }

    private fun song(id: String) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )
}
