package com.music.audify.ui.screens

import com.music.audify.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPicksLayoutTest {
    @Test
    fun quickPicksKeepThreeTracksPerHorizontalColumnAndPreserveOrder() {
        (0..10).forEach { count ->
            val tracks = (0 until count).map { index -> track("track-$index") }
            val columns = quickPicksColumns(tracks)

            assertEquals(count, columns.sumOf { it.size })
            assertTrue(columns.all { it.size in 1..3 })
            assertEquals(tracks, columns.flatten())
            assertEquals((count + 2) / 3, columns.size)
        }
    }

    @Test
    fun fullColumnsAlwaysContainExactlyThreeTracks() {
        val columns = quickPicksColumns((0 until 8).map { track("track-$it") })

        assertEquals(listOf(3, 3, 2), columns.map { it.size })
    }

    private fun track(id: String) = ShelfItem(
        title = id,
        subtitle = "Artist",
        thumbnailUrl = "https://example.invalid/$id.jpg",
        videoId = id,
        browseId = null,
    )
}
