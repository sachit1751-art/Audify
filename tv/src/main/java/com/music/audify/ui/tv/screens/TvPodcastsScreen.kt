// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.BrowseType
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.PodcastProviderShow
import com.music.audify.data.model.PodcastDiscoveryCategory
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.Song
import com.music.audify.ui.tv.components.TvButton
import com.music.audify.ui.tv.components.TvErrorState
import com.music.audify.ui.tv.components.TvEmptyState
import com.music.audify.ui.tv.theme.TvDimensions
import com.music.audify.ui.tv.theme.TvGlass
import com.music.audify.ui.tv.theme.TvType
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TvPodcastsScreen(
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var categories by remember { mutableStateOf<List<PodcastDiscoveryCategory>?>(null) }
    var savedShows by remember { mutableStateOf<List<PodcastProviderShow>?>(null) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var savedError by remember { mutableStateOf<String?>(null) }
    var discoveryLoading by remember { mutableStateOf(true) }
    var savedLoading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey) {
        // Keep the last successful catalogue visible while refreshing. A
        // transient failure must not turn a populated page into an empty one.
        discoveryError = null
        savedError = null
        savedLoading = true
        discoveryLoading = true
        coroutineScope {
            launch {
                val saved = YtMusicRepository.libraryPodcastShows()
                savedShows = retainLastSuccessful(savedShows, saved)
                savedError = saved.exceptionOrNull()?.message
                savedLoading = false
            }
            launch {
                val discovery = YtMusicRepository.podcastDiscoveryCategories()
                categories = retainLastSuccessful(categories, discovery)
                discoveryError = discovery.exceptionOrNull()?.message
                discoveryLoading = false
            }
        }
    }
    val shelves = buildList {
        savedShows.orEmpty().takeIf { it.isNotEmpty() }?.let { shows ->
            add(HomeShelf(
                title = "Saved shows",
                subtitle = "From your YouTube Music podcast library",
                items = shows.map { show -> ShelfItem(show.title, show.subtitle, show.thumbnailUrl, null, show.browseId) },
            ))
        }
        categories.orEmpty().filter { it.shows.isNotEmpty() }.forEach { category ->
            add(HomeShelf(
                title = category.title,
                subtitle = "YouTube Music playlist search results · not verified podcast shows",
                items = category.shows.map { show ->
                    ShelfItem(show.title, "Playlist result · ${show.subtitle}", show.thumbnailUrl, null, show.browseId)
                },
            ))
        }
    }
    val errors = listOfNotNull(savedError?.let { "Saved shows: $it" }, discoveryError?.let { "Discovery: $it" })
    val loading = savedLoading || discoveryLoading
    when {
        shelves.isEmpty() && loading -> TvShelvesLoading(modifier)
        shelves.isEmpty() && errors.isNotEmpty() -> TvErrorState(
            message = errors.joinToString("\n"),
            onRetry = { refreshKey++ },
            modifier = modifier,
        )
        shelves.isEmpty() -> TvEmptyState(
            title = "No Podcasts Yet",
            message = "Save a podcast show in YouTube Music, or try discovery again later.",
            modifier = modifier,
            action = { TvButton(text = "Retry", onClick = { refreshKey++ }) },
        )
        else -> TvShelfFeed(
            shelves = shelves,
            onPlaySong = { _: Song -> },
            onSongMenu = {},
            onNavigateToDetail = onNavigateToDetail,
            modifier = modifier,
            loadingMore = loading,
            header = if (errors.isNotEmpty()) ({
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = TvDimensions.SafeMarginHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = errors.joinToString(" · "),
                        color = TvGlass.TextSecondary,
                        style = TvType.Caption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TvButton(text = "Retry", onClick = { refreshKey++ })
                }
            }) else null,
        )
    }
}

/** Keep the last known-good provider snapshot when a refresh fails. */
internal fun <T> retainLastSuccessful(current: T?, result: Result<T>): T? =
    result.getOrNull() ?: current
