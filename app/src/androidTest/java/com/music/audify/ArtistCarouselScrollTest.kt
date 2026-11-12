package com.music.audify

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.data.model.Song
import com.music.audify.data.model.stableSongColumnComposeKeys
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistCarouselScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun repeatedTrackGroupsKeepUniqueLazyRowKeysWhileScrolling() {
        val group = listOf("a", "b", "c", "d").map { id ->
            Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null)
        }
        val songs = group + group
        val columns = songs.chunked(4)
        val columnKeys = songs.stableSongColumnComposeKeys(columnSize = 4)

        composeRule.setContent {
            LazyRow(Modifier.testTag("artist-top-songs")) {
                itemsIndexed(columns, key = { index, _ -> columnKeys[index] }) { index, _ ->
                    Text(
                        text = "Artist column ${index + 1}",
                        modifier = Modifier.width(240.dp).height(64.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("artist-top-songs").performScrollToIndex(1)
        composeRule.onNodeWithText("Artist column 2").assertIsDisplayed()
    }
}
