// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.tv.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.BrowseType
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.PodcastDiscoveryCategory
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.Song
import com.music.audify.ui.tv.components.TvErrorState

@Composable
fun TvPodcastsScreen(
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var categories by remember { mutableStateOf<List<PodcastDiscoveryCategory>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey) {
        YtMusicRepository.podcastDiscoveryCategories()
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "Couldn't load podcasts" }
    }
    when {
        categories == null && error == null -> TvShelvesLoading(modifier)
        error != null -> TvErrorState(message = error!!, onRetry = {
            error = null
            categories = null
            refreshKey++
        }, modifier = modifier)
        else -> TvShelfFeed(
            shelves = categories.orEmpty().map { category ->
                HomeShelf(
                    title = category.title,
                    subtitle = "YouTube Music podcast search results",
                    items = category.shows.map { show ->
                        ShelfItem(show.title, show.subtitle, show.thumbnailUrl, null, show.browseId)
                    },
                )
            },
            onPlaySong = { _: Song -> },
            onSongMenu = {},
            onNavigateToDetail = onNavigateToDetail,
            modifier = modifier,
        )
    }
}
