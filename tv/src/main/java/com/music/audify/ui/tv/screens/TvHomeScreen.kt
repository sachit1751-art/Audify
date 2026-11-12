// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.model.BrowseType
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeSlot
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.homeShelfKey
import com.music.audify.data.model.PodcastProviderShow
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.PodcastDiscoveryCategory
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState
import com.music.audify.playback.playSongs
import com.music.audify.ui.MainViewModel
import com.music.audify.ui.tv.components.TvActivityIndicator
import com.music.audify.ui.tv.components.TvCard
import com.music.audify.ui.tv.components.TvButton
import com.music.audify.ui.tv.components.TvChromeScrollEffect
import com.music.audify.ui.tv.components.TvErrorState
import com.music.audify.ui.tv.components.ProvideTvFeedScrolling
import com.music.audify.ui.tv.components.TvLockup
import com.music.audify.ui.tv.components.TvShelf
import com.music.audify.ui.tv.components.TvShelfTitle
import com.music.audify.ui.tv.components.TvShelfPlaceholder
import com.music.audify.ui.tv.components.TvDialog
import com.music.audify.ui.tv.components.TvDialogButton
import com.music.audify.ui.tv.components.TvListRow
import com.music.audify.ui.tv.dialogs.TvSongMenu
import com.music.audify.ui.tv.theme.TvDimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun TvHomeScreen(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onOpenPodcasts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeState by viewModel.home.collectAsState()
    val loadingMore by viewModel.homeLoadingMore.collectAsState()
    val savedLayout by AppSettings.homeLayout.collectAsState()
    val scope = rememberCoroutineScope()
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var showLayoutEditor by remember { mutableStateOf(false) }
    var lastSuccessfulShelves by remember { mutableStateOf<List<HomeShelf>?>(null) }
    var podcastCategories by remember { mutableStateOf<List<PodcastDiscoveryCategory>>(emptyList()) }
    var savedPodcastShows by remember { mutableStateOf<List<PodcastProviderShow>>(emptyList()) }
    LaunchedEffect(Unit) {
        // These are independent provider requests. Do not make discovery wait
        // behind the saved-library request (or vice versa) on the Home shelf.
        coroutineScope {
            launch {
                YtMusicRepository.libraryPodcastShows().onSuccess { savedPodcastShows = it }
            }
            launch {
                YtMusicRepository.podcastDiscoveryCategories().onSuccess { podcastCategories = it }
            }
        }
    }

    LaunchedEffect(homeState) {
        (homeState as? UiState.Success)?.let { lastSuccessfulShelves = it.data }
    }
    val currentShelves = (homeState as? UiState.Success)?.data ?: lastSuccessfulShelves
    if (currentShelves == null) {
        when (val state = homeState) {
            is UiState.Error -> TvErrorState(
                message = state.message,
                onRetry = { viewModel.refresh(MainViewModel.Feed.HOME) },
                modifier = modifier,
            )
            else -> TvShelvesLoading(modifier)
        }
    } else {
            val podcastShelfItems = buildList {
                savedPodcastShows.forEach { show ->
                    add(ShelfItem(show.title, show.subtitle.ifBlank { "Saved show" }, show.thumbnailUrl, null, show.browseId))
                }
                podcastCategories.flatMap { it.shows }.forEach { show ->
                    add(ShelfItem(show.title, "YouTube Music podcast", show.thumbnailUrl, null, show.browseId))
                }
            }.distinctBy { it.browseId }.take(12)
            // Keep the shelf and See all entry point even when discovery and the
            // saved library are empty or temporarily unavailable. Otherwise the
            // dedicated page becomes unreachable precisely in the empty/error case.
            val podcastShelf = HomeShelf(
                title = "Podcasts",
                subtitle = "Saved shows and public YouTube Music podcasts",
                items = podcastShelfItems,
            )
            val arrangedShelves = savedLayout.arrange(currentShelves, keyOf = { homeShelfKey(it.title) })
            TvShelfFeed(
                shelves = listOf(podcastShelf) + arrangedShelves,
                loadingMore = loadingMore,
                onNearEnd = { viewModel.loadMoreHome() },
                onPlaySong = { song ->
                    scope.launch { mediaController?.playSongs(listOf(song), 0) }
                    onNavigateToNowPlaying()
                },
                onSongMenu = { menuSong = it },
                onNavigateToDetail = onNavigateToDetail,
                onOpenPodcasts = onOpenPodcasts,
                header = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) { TvHomeGreeting() }
                        TvButton(
                            text = "Refresh",
                            onClick = { viewModel.refresh(MainViewModel.Feed.HOME) },
                        )
                        Spacer(Modifier.width(10.dp))
                        TvButton(text = "Customize Home", onClick = { showLayoutEditor = true })
                    }
                },
                picksFirst = true,
                picksFirstIndex = 1,
                modifier = modifier,
            )
    }

    if (showLayoutEditor) {
        val availableKeys = currentShelves.orEmpty()
            .map { homeShelfKey(it.title) }
            .distinct()
        TvHomeLayoutDialog(
            layout = savedLayout,
            availableKeys = availableKeys,
            onSave = AppSettings::setHomeLayout,
            onDismiss = { showLayoutEditor = false },
        )
    }

    menuSong?.let { song ->
        TvSongMenu(
            song = song,
            viewModel = viewModel,
            mediaController = mediaController,
            onNavigateToDetail = onNavigateToDetail,
            onDismiss = { menuSong = null },
        )
    }
}

@Composable
private fun TvHomeLayoutDialog(
    layout: HomeLayout,
    availableKeys: List<String>,
    onSave: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var order by remember(layout, availableKeys) {
        mutableStateOf(layout.editorOrder(availableKeys))
    }
    var hidden by remember(layout, availableKeys) {
        mutableStateOf<Set<String>>(order.filterTo(linkedSetOf(), layout::isHidden))
    }
    TvDialog(
        onDismissRequest = onDismiss,
        title = "Customize Home",
        message = "Choose which shelves appear and arrange their order. Select a row to show or hide it.",
        width = 620.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            order.forEachIndexed { index, key ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvListRow(
                        title = key.removePrefix("shelf:").replace('-', ' ').replaceFirstChar { it.uppercase() },
                        subtitle = if (key in hidden) "Hidden · select to restore" else "Visible · select to hide",
                        value = "${index + 1}",
                        onClick = {
                            hidden = hidden.toMutableSet().apply {
                                if (!add(key)) remove(key)
                            }
                            onSave(HomeLayout(order.map { HomeSlot(it, it in hidden) }))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TvButton(
                        text = "Up",
                        enabled = index > 0,
                        onClick = {
                            order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                            onSave(HomeLayout(order.map { HomeSlot(it, it in hidden) }))
                        },
                    )
                    Spacer(Modifier.width(5.dp))
                    TvButton(
                        text = "Down",
                        enabled = index < order.lastIndex,
                        onClick = {
                            order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                            onSave(HomeLayout(order.map { HomeSlot(it, it in hidden) }))
                        },
                    )
                }
            }
            Spacer(Modifier.width(1.dp))
            TvDialogButton(text = "Reset layout", onClick = {
                order = availableKeys
                hidden = emptySet<String>()
                onSave(HomeLayout())
            })
            TvDialogButton(text = "Done", onClick = onDismiss, initialFocus = true)
        }
    }
}

/** Placeholder shelves while a feed loads. */
@Composable
internal fun TvShelvesLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = TvDimensions.ContentTop + 6.dp),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing),
    ) {
        TvShelfPlaceholder(width = 160.dp, count = 5)
        TvShelfPlaceholder()
    }
}

/**
 * A vertical run of shelves under the tab bar — Home, an Explore category, an
 * artist's releases. With [picksFirst] the first shelf gets the large Top Picks
 * cards; every other shelf is lockups.
 */
@Composable
internal fun TvShelfFeed(
    shelves: List<HomeShelf>,
    onPlaySong: (Song) -> Unit,
    onSongMenu: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
    picksFirst: Boolean = false,
    picksFirstIndex: Int = 0,
    loadingMore: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onOpenPodcasts: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    TvChromeScrollEffect(listState)

    if (onNearEnd != null) {
        val nearEnd by remember(listState) {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 3
            }
        }
        LaunchedEffect(nearEnd, shelves.size) { if (nearEnd) onNearEnd() }
    }

    // The first shelf's cards rest just under its title: ContentTop, the title's
    // line, then the row's own top padding. Every focused shelf stops there.
    val headerOffset = if (header != null) 40.dp else 0.dp
    ProvideTvFeedScrolling(anchor = TvDimensions.ContentTop + headerOffset + 19.dp + 11.dp) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = TvDimensions.ContentTop, bottom = 45.dp),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing - 11.dp),
    ) {
        if (header != null) {
            item(key = "header", contentType = "header") { header() }
        }
        itemsIndexed(
            items = shelves,
            // Podcast discovery arrives after the music feed. Its insertion at
            // index 0 must not change the keys of every existing shelf: doing
            // so disposes their LazyRow state and drops D-pad focus restoration.
            key = { index, shelf -> shelf.feedKey(index) },
            contentType = { index, shelf ->
                when {
                    picksFirst && index == picksFirstIndex -> "picks"
                    shelf.isArtistShelf() -> "artists"
                    else -> "lockups"
                }
            },
        ) { index, shelf ->
            val onItem: (ShelfItem) -> Unit = { item ->
                val browseId = item.browseId
                if (item.videoId == null && browseId != null && shelf.isPodcastShelf()) {
                    onNavigateToDetail(browseId, item.title, item.subtitle, item.thumbnailUrl, BrowseType.PODCAST)
                } else {
                    openShelfItem(item, onPlaySong, onNavigateToDetail)
                }
            }
            val onItemMenu: (ShelfItem) -> (() -> Unit)? = { item ->
                item.toSong()?.let { song -> { onSongMenu(song) } }
            }
            when {
                picksFirst && index == picksFirstIndex -> TvShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                    items = shelf.items,
                    key = { it.itemKey() },
                ) { item ->
                    TvCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkUrl = item.thumbnailUrl,
                        cardWidth = 163.dp,
                        onClick = { onItem(item) },
                        onLongClick = onItemMenu(item),
                    )
                }
                shelf.isArtistShelf() -> TvShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                    items = shelf.items,
                    key = { it.itemKey() },
                ) { item ->
                    TvLockup(
                        title = item.title,
                        subtitle = null,
                        artworkUrl = item.thumbnailUrl,
                        circle = true,
                        width = 120.dp,
                        onClick = { onItem(item) },
                    )
                }
                else -> TvShelf(
                    title = shelf.title,
                    subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                    items = shelf.items,
                    key = { it.itemKey() },
                    trailing = if (shelf.title == "Podcasts" && onOpenPodcasts != null) {
                        { TvButton(text = "See all", onClick = onOpenPodcasts) }
                    } else null,
                ) { item ->
                    TvLockup(
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkUrl = item.thumbnailUrl,
                        width = 131.dp,
                        onClick = { onItem(item) },
                        onLongClick = onItemMenu(item),
                    )
                }
            }
        }
        if (loadingMore) {
            item(key = "more", contentType = "more") {
                Box(modifier = Modifier.fillMaxWidth().padding(19.dp), contentAlignment = Alignment.Center) {
                    TvActivityIndicator()
                }
            }
        }
    }
    }
}

@Composable
private fun TvHomeGreeting() {
    val greeting by produceState(initialValue = tvHomeGreeting()) {
        while (true) {
            value = tvHomeGreeting()
            delay(60_000L)
        }
    }
    TvShelfTitle(title = greeting)
}

private fun tvHomeGreeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun ShelfItem.itemKey(): String = videoId ?: browseId ?: title

private fun HomeShelf.isArtistShelf(): Boolean =
    items.isNotEmpty() && items.count { it.browseId?.startsWith("UC") == true } * 2 > items.size

private fun HomeShelf.isPodcastShelf(): Boolean =
    title.equals("Podcasts", ignoreCase = true) ||
        title.equals("Saved shows", ignoreCase = true) ||
        title.endsWith("podcasts", ignoreCase = true)

internal fun HomeShelf.feedKey(index: Int): String {
    if (title.equals("Podcasts", ignoreCase = true)) return "podcasts"
    val first = items.firstOrNull()
    val identity = first?.videoId ?: first?.browseId ?: moreBrowseId
    return buildString {
        append(title)
        append('#')
        append(discoveryCategory.orEmpty())
        append('#')
        append(identity ?: "empty-$index")
    }
}

internal fun ShelfItem.toSong(): Song? = videoId?.let {
    Song(videoId = it, title = title, artist = subtitle, thumbnailUrl = thumbnailUrl)
}

internal fun browseTypeOf(browseId: String): BrowseType = when {
    browseId.startsWith("UC") || browseId.startsWith("FEmusic_artist") -> BrowseType.ARTIST
    browseId.startsWith("MPRE") || browseId.startsWith("FEmusic_album") -> BrowseType.ALBUM
    browseId.startsWith("MPSP") -> BrowseType.PODCAST
    browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD") || browseId == "LM" -> BrowseType.PLAYLIST
    else -> BrowseType.OTHER
}

internal fun openShelfItem(
    item: ShelfItem,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
) {
    val song = item.toSong()
    val browseId = item.browseId
    when {
        song != null -> onPlaySong(song)
        browseId != null -> onNavigateToDetail(browseId, item.title, item.subtitle, item.thumbnailUrl, browseTypeOf(browseId))
    }
}
