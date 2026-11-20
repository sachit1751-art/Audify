package com.music.audify.ui.tv.screens

import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.BrowseType
import com.music.audify.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TvHomeShelfIdentityTest {
    @Test
    fun insertingPodcastShelfDoesNotChangeExistingShelfIdentity() {
        val shelf = HomeShelf(
            title = "Made for you",
            items = listOf(ShelfItem("Track", "Artist", null, "video-1", null)),
        )

        assertEquals(shelf.feedKey(0), shelf.feedKey(1))
    }

    @Test
    fun podcastShelfIdentityDoesNotDependOnItsChangingContents() {
        val before = HomeShelf("Podcasts", listOf(ShelfItem("Show A", "Saved", null, null, "show-a")))
        val after = HomeShelf("Podcasts", listOf(ShelfItem("Show B", "Saved", null, null, "show-b")))

        assertEquals(before.feedKey(0), after.feedKey(0))
    }

    @Test
    fun providerPodcastBrowseIdsKeepTheirPodcastIdentity() {
        assertEquals(BrowseType.PODCAST, browseTypeOf("MPSPverifiedShowId"))
    }
}
