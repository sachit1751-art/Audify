package com.music.audify.ui.screens

import com.music.audify.data.model.ShelfItem
import kotlin.test.Test
import kotlin.test.assertEquals

class MixCoverShelfOrderTest {
    @Test
    fun numberedMyMixesAreSortedNumericallyAndMissingNumbersAreSkipped() {
        val items = listOf(
            mix("My Supermix"),
            mix("My Mix 3"),
            mix("Discover Mix"),
            mix("My Mix 1"),
            mix("My Mix 10"),
            mix("My Mix 2"),
        )

        assertEquals(
            listOf("My Supermix", "My Mix 1", "Discover Mix", "My Mix 2", "My Mix 3", "My Mix 10"),
            orderNumberedMyMixes(items).map(ShelfItem::title),
        )
    }

    @Test
    fun zeroOrOneNumberedMixDoesNotChangeTheList() {
        val items = listOf(mix("My Supermix"), mix("My Mix 4"), mix("Discover Mix"))
        assertEquals(items, orderNumberedMyMixes(items))
    }

    private fun mix(title: String) = ShelfItem(
        title = title,
        subtitle = "",
        thumbnailUrl = null,
        videoId = null,
        browseId = "RDTMAK123",
    )
}
