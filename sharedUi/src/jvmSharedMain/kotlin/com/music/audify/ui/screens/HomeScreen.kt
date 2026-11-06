// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import com.music.audify.ui.components.contextClick
import com.music.audify.ui.AppUi
import com.music.audify.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.music.audify.ui.components.longPressMenuClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import com.music.audify.ui.components.ShelfRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bluetooth
import com.music.audify.ui.icons.AudifyIcons
import coil3.compose.AsyncImage
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.HEADER_ART_PX
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.stableShelfComposeKeys
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState
import java.util.Locale
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import java.time.LocalTime
import kotlinx.coroutines.delay
import com.music.audify.data.model.artworkAt
import com.music.audify.data.settings.LibraryViewType
import com.music.audify.ui.components.MessageState
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.RowMoreButton
import com.music.audify.ui.components.PlayingAccent
import com.music.audify.ui.components.PullToRefresh
import com.music.audify.ui.components.SHELF_CARD_WIDTH
import com.music.audify.ui.components.SearchPlayingBars
import com.music.audify.ui.components.SignInBanner
import com.music.audify.ui.components.feedMoreSkeleton
import com.music.audify.ui.player.PlayerSettings
import com.music.audify.ui.components.feedSkeleton
import com.music.audify.ui.components.recentlyPlayedSkeleton
import com.music.audify.ui.components.heroCardWidth
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.components.trackColumnWidth
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.MeshPalette
import com.music.audify.ui.player.AudioOutputSheet
import com.music.audify.ui.player.rememberOutputPicker
import dev.chrisbanes.haze.HazeState

/** How far the Listen now heading and feed sit above the shared top inset. */
private val HOME_TITLE_LIFT = 64.dp

private const val RECENTS_TITLE = "Recents"
private const val RECENT_TRACKS_PER_COLUMN = 4

/** Minimum useful width for one Quick Picks column before another is added. */
private val QUICK_PICK_MIN_COLUMN_WIDTH = 208.dp
private val QUICK_PICK_COLUMN_SPACING = 18.dp
private val KEEP_LISTENING_MIN_CARD_WIDTH = 200.dp
private val KEEP_LISTENING_COLUMN_SPACING = 14.dp

/** Derive track columns from the actual content window, including its gutters. */
internal fun quickPickColumns(available: Dp): Int {
    val usable = (available - PAGE_GUTTER * 2).coerceAtLeast(0.dp)
    return ((usable + QUICK_PICK_COLUMN_SPACING) /
        (QUICK_PICK_MIN_COLUMN_WIDTH + QUICK_PICK_COLUMN_SPACING)).toInt().coerceIn(1, 5)
}

/** Keep album cards wide enough for their title and subtitle to remain readable. */
internal fun keepListeningColumns(available: Dp): Int {
    val usable = (available - PAGE_GUTTER * 2).coerceAtLeast(0.dp)
    return ((usable + KEEP_LISTENING_COLUMN_SPACING) /
        (KEEP_LISTENING_MIN_CARD_WIDTH + KEEP_LISTENING_COLUMN_SPACING)).toInt().coerceIn(1, 4)
}

/** Width each weighted Keep Listening card receives after page gutters and inter-card spacing. */
internal fun keepListeningCardWidth(available: Dp): Dp {
    val columns = keepListeningColumns(available)
    val usable = (available - PAGE_GUTTER * 2).coerceAtLeast(0.dp)
    return (usable - KEEP_LISTENING_COLUMN_SPACING * (columns - 1)) / columns
}

private fun List<ShelfItem>.playingItemMatcher(song: Song?): (ShelfItem) -> Boolean {
    song ?: return { false }
    // The card that was played, when the shelf holds it, and only that one —
    // see [com.music.audify.data.model.playingRowMatcher], which rows use.
    if (any { it.videoId == song.videoId }) return { it.videoId == song.videoId }
    val byName = firstOrNull { it.videoId != null && it.title == song.title && it.subtitle == song.artist }
        ?: return { false }
    return { it == byName }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    /** The tapped item and the recommendation shelf it came from. */
    onItemClick: (ShelfItem, String) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /** The page's heading, or null for none — the desktop's pages carry no heading. */
    title: String?,
    signedIn: Boolean = true,
    onSignIn: (() -> Unit)? = null,
    /**
     * Holding a card rather than tapping it. Every card on the feed answers,
     * whichever kind it is: the caller reads the item the same way it does for
     * a tap, so a track card opens the track menu and a card that points at a
     * collection opens the album / playlist one.
     */
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    // Explore doesn't page — only Home has a continuation worth following.
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    recentlyPlayedLoading: Boolean = false,
    /**
     * Whether the feed's first shelf gets the big full-bleed cards. The phone
     * leads with them; on a window as wide as a desktop's they are posters, so
     * there every shelf is the ordinary row.
     */
    leadHero: Boolean = true,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    timeAwareShelf: HomeShelf? = null,
    onSearchClick: (() -> Unit)? = null,
    onCastClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    profileThumbnailUrl: String? = null,
    onMoodClick: ((String) -> Unit)? = null,
    onListenTogetherClick: (() -> Unit)? = null,
    onPlayAllQuickPicks: ((HomeShelf) -> Unit)? = null,
    userName: String? = null,
    greetingMorning: String = "Good morning",
    greetingAfternoon: String = "Good afternoon",
    greetingEvening: String = "Good evening",
) {
    val recentsViewType by AppUi.host.homeRecentsViewType.collectAsStateWithLifecycle()
    var showAudioOutput by remember { mutableStateOf(false) }
    val outputHaze = remember { HazeState() }
    val openAudioOutput = rememberOutputPicker { showAudioOutput = true }
    val featuredItemIds = remember(timeAwareShelf) {
        timeAwareShelf?.items.orEmpty().mapNotNull { it.videoId ?: it.browseId }.toSet()
    }
    val isChronologicalHistoryShelf: (HomeShelf) -> Boolean = { shelf ->
        shelf.title.trim().lowercase(Locale.ROOT) in setOf("recents", "recently played")
    }

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The heading sits higher than the other tabs' first row: pull the
            // whole feed up by [HOME_TITLE_LIFT] with it.
            contentPadding = if (title != null) {
                PaddingValues(
                    top = (contentPadding.calculateTopPadding() - HOME_TITLE_LIFT).coerceAtLeast(0.dp),
                    bottom = contentPadding.calculateBottomPadding(),
                )
            } else {
                contentPadding
            },
        ) {
            if (title != null) {
                item(key = "home-compact-header") {
                    HomeTitle(
                        onSearchClick = onSearchClick,
                        onBluetoothClick = openAudioOutput,
                        onProfileClick = onProfileClick,
                        profileThumbnailUrl = profileThumbnailUrl,
                        userName = userName,
                        greetingMorning = greetingMorning,
                        greetingAfternoon = greetingAfternoon,
                        greetingEvening = greetingEvening,
                    )
                }
            }
            if (!signedIn && onSignIn != null) {
                item(key = "home:sign-in") {
                    SignInBanner(onSignIn = onSignIn, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            if (timeAwareShelf != null) {
                item(key = "time-aware-discovery-shelf") {
                    Shelf(
                        shelf = timeAwareShelf,
                        onItemClick = { item -> onItemClick(item, timeAwareShelf.title) },
                        onItemLongPress = onItemLongPress,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                    )
                }
            }
            when (state) {
                is UiState.Loading -> {
                    if (recentlyPlayedLoading) {
                        recentlyPlayedSkeleton(listLayout = recentsViewType == LibraryViewType.LIST)
                        // Recents owns the leading layout while its request is
                        // pending, so the feed behind it starts with ordinary
                        // shelf placeholders rather than another hero card.
                        feedSkeleton(firstIsHero = false)
                    } else {
                        feedSkeleton(firstIsHero = leadHero)
                    }
                }
                is UiState.Error -> item(key = "home:error") {
                    MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                }
                is UiState.Success -> {
                    if (recentlyPlayedLoading) {
                        recentlyPlayedSkeleton(listLayout = recentsViewType == LibraryViewType.LIST)
                    }
                    // The loading skeleton already owns the hero slot. Until
                    // Recently Played lands, every real shelf must retain its
                    // compact-card layout instead of briefly becoming a hero.
                    itemsIndexedShelves(
                        // The time-aware lead is selected from this same feed;
                        // keep its cards from appearing a second time below.
                        shelves = if (featuredItemIds.isEmpty()) state.data else state.data.mapNotNull { shelf ->
                            if (isChronologicalHistoryShelf(shelf)) return@mapNotNull shelf
                            val remaining = shelf.items.filterNot { (it.videoId ?: it.browseId) in featuredItemIds }
                            remaining.takeIf { it.isNotEmpty() }?.let { shelf.copy(items = it) }
                        },
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                        onPlayAllQuickPicks = onPlayAllQuickPicks,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onMoodClick = onMoodClick,
                        onListenTogetherClick = onListenTogetherClick,
                        firstIsHero = leadHero && !recentlyPlayedLoading,
                        recentsViewType = recentsViewType,
                        onRecentsViewTypeToggle = {
                            AppUi.host.setHomeRecentsViewType(
                                if (recentsViewType == LibraryViewType.LIST) {
                                    LibraryViewType.GRID
                                } else {
                                    LibraryViewType.LIST
                                },
                            )
                        },
                    )
                    if (loadingMore) feedMoreSkeleton()
                }
            }
        }
    }

    if (onLoadMore != null && state is UiState.Success) {
        val loadMore by rememberUpdatedState(onLoadMore)
        // Re-checked on every layout change, rather than on the rising edge of
        // "the tail is in view". A page that appends only a shelf or two leaves
        // the list still near its end, so an edge-triggered effect would never
        // fire a second time: the feed dead-ended at the bottom with no
        // skeleton and no request in flight to explain it. Restarting on
        // [loadingMore] re-checks the moment a page settles, so the next one is
        // asked for while the tail is still on screen to show it loading.
        LaunchedEffect(listState, loadingMore) {
            snapshotFlow {
                val layout = listState.layoutInfo
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
            }.collect { (lastVisible, total) ->
                if (!loadingMore && total > 0 && lastVisible >= total - 3) loadMore()
            }
        }
    }

    if (showAudioOutput) {
        AudioOutputSheet(
            hazeState = outputHaze,
            accountName = userName,
            onDismiss = { showAudioOutput = false },
            onOpenPipeline = null,
            onOpenCast = { onCastClick?.invoke() },
        )
    }
}

/**
 * Recents mirrors the artist page's top-tracks pager. Any other lead shelf keeps
 * Apple's full-bleed treatment, while the remaining shelves use square cards.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedShelves(
    shelves: List<HomeShelf>,
    onItemClick: (ShelfItem, String) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    onPlayAllQuickPicks: ((HomeShelf) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
    onMoodClick: ((String) -> Unit)?,
    onListenTogetherClick: (() -> Unit)?,
    firstIsHero: Boolean = true,
    recentsViewType: LibraryViewType,
    onRecentsViewTypeToggle: () -> Unit,
) {
    shelves.forEachIndexed { index, shelf ->
        item(key = shelf.title + index) {
            val openItem: (ShelfItem) -> Unit = { item -> onItemClick(item, shelf.title) }
            if (index == 0 && shelf.title.equals(RECENTS_TITLE, ignoreCase = true)) {
                RecentShelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    viewType = recentsViewType,
                    onViewTypeToggle = onRecentsViewTypeToggle,
                )
            } else if (index == 0 && firstIsHero) {
                HeroShelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                )
            } else {
                Shelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    onPlayAllQuickPicks = onPlayAllQuickPicks,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                )
            }
        }
        if (shelf.title.equals("Quick picks", ignoreCase = true)) {
            item(key = "home-find-your-mood") { FindYourMood(onMoodClick) }
            item(key = "home-listen-together") {
                ListenTogetherShelf(shelf.items.take(2).map { it.thumbnailUrl }, onListenTogetherClick)
            }
        }
    }
    if (shelves.none { it.title.equals("Quick picks", ignoreCase = true) }) {
        item(key = "home-find-your-mood") { FindYourMood(onMoodClick) }
        item(key = "home-listen-together") { ListenTogetherShelf(emptyList(), onListenTogetherClick) }
    }
}

/** The same four-rows-per-page treatment used by an artist's Top songs. */
@Composable
private fun RecentShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
    viewType: LibraryViewType,
    onViewTypeToggle: () -> Unit,
) {
    Column(Modifier.padding(bottom = 28.dp)) {
        RecentSectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            viewType = viewType,
            onViewTypeToggle = onViewTypeToggle,
        )
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        val itemKeys = remember(shelf.items) { shelf.items.stableShelfComposeKeys() }
        if (viewType == LibraryViewType.LIST) {
            BoxWithConstraints {
                // The docked player leaves a tablet's Home pane narrower than
                // the full window. Keep the rows readable, but use that pane
                // efficiently: the phone-sized 400dp cap otherwise leaves only
                // one full column and a sliver of the next on most tablets.
                val columnWidth = if (maxWidth >= 480.dp) {
                    minOf(maxWidth * 0.42f, 300.dp)
                } else {
                    trackColumnWidth(maxWidth)
                }
                ShelfRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        shelf.items.chunked(RECENT_TRACKS_PER_COLUMN),
                        key = { column -> column.joinToString("|") { it.videoId ?: it.browseId ?: it.title } },
                    ) { column ->
                        Column(Modifier.width(columnWidth)) {
                            column.forEach { item ->
                                RecentTrackRow(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onLongPress = onItemLongPress?.let { { it(item) } },
                                    isCurrent = isCurrentItem(item),
                                    isPlaying = isCurrentItem(item) && isPlaying,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            BoxWithConstraints {
                val cardWidth = heroCardWidth(maxWidth)
                ShelfRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(shelf.items, key = { index, _ -> itemKeys[index] }) { _, item ->
                        HeroCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = onItemLongPress?.let { { it(item) } },
                            modifier = Modifier.width(cardWidth),
                            isCurrent = isCurrentItem(item),
                            isPlaying = isCurrentItem(item) && isPlaying,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSectionHeader(
    title: String,
    subtitle: String,
    viewType: LibraryViewType,
    onViewTypeToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onViewTypeToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (viewType == LibraryViewType.LIST) {
                    AudifyIcons.GridView
                } else {
                    AudifyIcons.ListView
                },
                contentDescription = stringResource(
                    if (viewType == LibraryViewType.LIST) {
                        Res.string.switch_to_grid_view
                    } else {
                        Res.string.switch_to_list_view
                    },
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun RecentTrackRow(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    isCurrent: Boolean,
    isPlaying: Boolean,
) {
    CompactTrackRow(
        title = item.title,
        subtitle = item.subtitle,
        thumbnailUrl = item.thumbnailUrl,
        onClick = onClick,
        onLongPress = onLongPress,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
    )
}

/**
 * One row of Recents' four-to-a-column pager. Public so the desktop artist page
 * can list its top songs the same way.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactTrackRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp)) {
            AsyncImage(
                model = thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onLongPress != null) {
            RowMoreButton(onClick = onLongPress)
        }
    }
}

/**
 * Listen now's large heading, with the wordmark stacked above it.
 *
 * Only the home tab carries it — the top bar no longer shows the logo, so this
 * is where the app's mark lives. Its height is taken from the heading's own
 * font size rather than a fixed dp, so the two keep their proportion under the
 * system font scale.
 */
@Composable
private fun HomeTitle(
    onSearchClick: (() -> Unit)?,
    onBluetoothClick: (() -> Unit)?,
    onProfileClick: (() -> Unit)?,
    profileThumbnailUrl: String?,
    userName: String?,
    greetingMorning: String,
    greetingAfternoon: String,
    greetingEvening: String,
    modifier: Modifier = Modifier,
) {
    val greeting by produceState(initialValue = homeGreeting(greetingMorning, greetingAfternoon, greetingEvening)) {
        while (true) {
            value = homeGreeting(greetingMorning, greetingAfternoon, greetingEvening)
            delay(60_000L)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_logo),
            contentDescription = "Audify",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp).height(27.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (!userName.isNullOrBlank()) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HomeHeaderAction(Icons.Rounded.Search, "Search", onSearchClick)
        HomeHeaderAction(Icons.Rounded.Bluetooth, "Bluetooth audio output", onBluetoothClick)
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .clickable(enabled = onProfileClick != null, role = Role.Button) { onProfileClick?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            if (!profileThumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profileThumbnailUrl,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
            } else {
                Icon(Icons.Rounded.AccountCircle, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
            }
        }
    }
}

private fun homeGreeting(morning: String, afternoon: String, evening: String): String =
    when (LocalTime.now().hour) {
        in 5..11 -> morning
        in 12..16 -> afternoon
        else -> evening
    }

@Composable
private fun HomeHeaderAction(icon: ImageVector, description: String, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

private data class HomeMoodCard(val title: String, val colors: List<Color>)

private val HOME_MOOD_CARDS = listOf(
    HomeMoodCard("Energy", listOf(Color(0xFFFF7043), Color(0xFFE53970))),
    HomeMoodCard("Relax", listOf(Color(0xFF45B7A0), Color(0xFF2674A8))),
    HomeMoodCard("Feeling Blue", listOf(Color(0xFF4385D1), Color(0xFF5146A8))),
    HomeMoodCard("Feel Good", listOf(Color(0xFFFFB52E), Color(0xFFFF6F61))),
    HomeMoodCard("Focus", listOf(Color(0xFF6976D8), Color(0xFF39468E))),
    HomeMoodCard("Heartbreak", listOf(Color(0xFFB85A83), Color(0xFF563D79))),
    HomeMoodCard("Love", listOf(Color(0xFFFF668C), Color(0xFFB83272))),
)

@Composable
private fun FindYourMood(onMoodClick: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SectionHeader("Find Your Mood", emphasized = true)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = if (maxWidth >= 720.dp) 164.dp else 208.dp
            ShelfRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(HOME_MOOD_CARDS, key = HomeMoodCard::title) { mood ->
                    MoodArtCard(
                        mood = mood,
                        onClick = { onMoodClick?.invoke(mood.title) },
                        modifier = Modifier.width(cardWidth),
                        enabled = onMoodClick != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodArtCard(
    mood: HomeMoodCard,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val motion by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "${mood.title}-line-art-press",
    )
    // One quiet breathing cycle per composed card. LazyRow disposes distant
    // cards, so off-screen artwork does not keep an animation clock running.
    val ambient = if (reduceAnimation) {
        0f
    } else {
        rememberInfiniteTransition(label = "${mood.title}-ambient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "${mood.title}-breath",
        ).value
    }
    Column(
        modifier = modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = interaction,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(mood.colors)),
        ) {
            // A restrained highlight makes the card feel tactile without
            // introducing a costly blur or a permanent animated shadow.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f + ambient * 0.05f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f),
                            ),
                        ),
                    ),
            )
            MoodLineArt(
                mood = mood.title,
                progress = motion,
                phase = ambient,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val scale = 1f + motion * 0.045f + ambient * 0.012f
                    scaleX = scale
                    scaleY = scale
                    rotationZ = motion * (if (mood.title == "Feeling Blue") -2f else 2f)
                },
            )
        }
        Text(
            text = mood.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Crisp, mood-specific geometry; the linework responds to the card's press state. */
@Composable
private fun MoodLineArt(mood: String, progress: Float, phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)
        val cx = w * 0.62f
        val cy = h * 0.40f
        val base = Color.White.copy(alpha = 0.76f + progress * 0.18f + phase * 0.06f)
        val faint = Color.White.copy(alpha = 0.30f + progress * 0.18f + phase * 0.10f)
        val stroke = (unit * 0.009f).coerceAtLeast(1.5f)
        val lineStyle = Stroke(width = stroke, cap = StrokeCap.Round)
        withTransform({
            rotate(degrees = progress * 5f + phase * 0.8f, pivot = Offset(cx, cy))
            scale(scaleX = 1f + progress * 0.04f + phase * 0.012f, scaleY = 1f + progress * 0.04f + phase * 0.012f, pivot = Offset(cx, cy))
        }) {
            when (mood) {
                "Energy" -> {
                    val bolt = Path().apply {
                        moveTo(w * .60f, h * .12f)
                        lineTo(w * .35f, h * .53f)
                        lineTo(w * .52f, h * .53f)
                        lineTo(w * .42f, h * .86f)
                        lineTo(w * .72f, h * .42f)
                        lineTo(w * .55f, h * .42f)
                        close()
                    }
                    drawPath(bolt, faint.copy(alpha = .22f + phase * .12f))
                    drawPath(bolt, base, style = Stroke(stroke * 1.45f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawLine(faint, Offset(w * .18f, h * .32f), Offset(w * .30f, h * .32f), stroke, StrokeCap.Round)
                    drawLine(faint, Offset(w * .70f, h * .72f), Offset(w * .84f, h * .72f), stroke, StrokeCap.Round)
                }
                "Energize" -> {
                    val pulse = Path().apply {
                        moveTo(w * .28f, h * .42f)
                        lineTo(w * .43f, h * .42f)
                        lineTo(w * .51f, h * .22f)
                        lineTo(w * .60f, h * .62f)
                        lineTo(w * .70f, h * .34f)
                        lineTo(w * .77f, h * .42f)
                        lineTo(w * .92f, h * .42f)
                    }
                    drawPath(pulse, base, style = Stroke(stroke * 1.5f, cap = StrokeCap.Round))
                    repeat(3) { i -> drawCircle(faint, radius = unit * (.13f + i * .085f), center = Offset(cx, cy), style = lineStyle) }
                }
                "Workout" -> {
                    val bolt = Path().apply {
                        moveTo(w * .63f, h * .12f); lineTo(w * .43f, h * .49f)
                        lineTo(w * .59f, h * .49f); lineTo(w * .47f, h * .78f)
                        lineTo(w * .78f, h * .37f); lineTo(w * .62f, h * .37f); close()
                    }
                    drawPath(bolt, base, style = lineStyle)
                    drawLine(faint, Offset(w * .22f, h * .25f), Offset(w * .34f, h * .25f), stroke, StrokeCap.Round)
                    drawLine(faint, Offset(w * .18f, h * .34f), Offset(w * .30f, h * .34f), stroke, StrokeCap.Round)
                    drawLine(faint, Offset(w * .72f, h * .76f), Offset(w * .86f, h * .76f), stroke, StrokeCap.Round)
                }
                "Relax" -> {
                    repeat(3) { i ->
                        val width = unit * (.48f + i * .10f)
                        val height = unit * (.14f + i * .018f)
                        val left = cx - width / 2f
                        val top = cy - height / 2f + (i - 1) * unit * .12f
                        drawOval(
                            color = if (i == 1) base else faint,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(width, height),
                            style = Stroke(stroke * 1.15f, cap = StrokeCap.Round),
                        )
                    }
                }
                "Feeling Blue" -> {
                    val spiral = Path().apply {
                        val radius = unit * .30f
                        moveTo(cx + radius, cy)
                        cubicTo(cx + radius, cy - radius, cx - radius, cy - radius, cx - radius, cy)
                        cubicTo(cx - radius, cy + radius, cx + radius, cy + radius, cx + radius * .56f, cy)
                        cubicTo(cx + radius * .56f, cy - radius * .56f, cx - radius * .42f, cy - radius * .42f, cx - radius * .42f, cy)
                        cubicTo(cx - radius * .42f, cy + radius * .42f, cx + radius * .22f, cy + radius * .42f, cx + radius * .22f, cy)
                        cubicTo(cx + radius * .22f, cy - radius * .22f, cx - radius * .08f, cy - radius * .22f, cx - radius * .08f, cy)
                    }
                    drawPath(spiral, base, style = Stroke(stroke * 1.25f, cap = StrokeCap.Round))
                }
                "Feel Good" -> {
                    val sparkle = Path().apply {
                        moveTo(cx, cy - unit * .34f)
                        cubicTo(cx + unit * .08f, cy - unit * .08f, cx + unit * .08f, cy - unit * .08f, cx + unit * .34f, cy)
                        cubicTo(cx + unit * .08f, cy + unit * .08f, cx + unit * .08f, cy + unit * .08f, cx, cy + unit * .34f)
                        cubicTo(cx - unit * .08f, cy + unit * .08f, cx - unit * .08f, cy + unit * .08f, cx - unit * .34f, cy)
                        cubicTo(cx - unit * .08f, cy - unit * .08f, cx - unit * .08f, cy - unit * .08f, cx, cy - unit * .34f)
                        close()
                    }
                    drawPath(sparkle, faint.copy(alpha = .20f + phase * .10f))
                    drawPath(sparkle, base, style = Stroke(stroke * 1.3f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                "Heartbreak" -> {
                    val heart = Path().apply {
                        moveTo(w * .62f, h * .78f)
                        cubicTo(w * .05f, h * .43f, w * .30f, h * .12f, w * .50f, h * .34f)
                        cubicTo(w * .70f, h * .12f, w * .95f, h * .43f, w * .62f, h * .78f)
                    }
                    drawPath(heart, base, style = lineStyle)
                    drawLine(faint, Offset(w * .53f, h * .31f), Offset(w * .43f, h * .52f), stroke, StrokeCap.Round)
                    drawLine(faint, Offset(w * .43f, h * .52f), Offset(w * .56f, h * .58f), stroke, StrokeCap.Round)
                }
                "Love" -> {
                    val heart = Path().apply {
                        moveTo(cx, h * .78f)
                        cubicTo(w * .08f, h * .38f, w * .34f, h * .10f, cx, h * .34f)
                        cubicTo(w * .86f, h * .10f, w * .92f, h * .38f, cx, h * .78f)
                    }
                    drawPath(heart, base, style = lineStyle)
                    drawCircle(faint, unit * (.34f + phase * .012f), Offset(cx, cy), style = lineStyle)
                }
                "Focus" -> {
                    repeat(4) { i ->
                        drawCircle(if (i == 0) base else faint, radius = unit * (.07f + i * .09f), center = Offset(cx, cy), style = lineStyle)
                    }
                    drawLine(base, Offset(cx - unit * .34f, cy), Offset(cx - unit * .20f, cy), stroke, StrokeCap.Round)
                    drawLine(base, Offset(cx + unit * .20f, cy), Offset(cx + unit * .34f, cy), stroke, StrokeCap.Round)
                    drawLine(base, Offset(cx, cy - unit * .34f), Offset(cx, cy - unit * .20f), stroke, StrokeCap.Round)
                    drawLine(base, Offset(cx, cy + unit * .20f), Offset(cx, cy + unit * .34f), stroke, StrokeCap.Round)
                }
                "Commute" -> {
                    val road = Path().apply {
                        moveTo(w * .12f, h * .86f); lineTo(w * .48f, h * .24f)
                        moveTo(w * .88f, h * .86f); lineTo(w * .48f, h * .24f)
                    }
                    drawPath(road, base, style = lineStyle)
                    repeat(3) { i ->
                        val y = h * (.55f + i * .12f)
                        val half = unit * (.025f + i * .025f)
                        drawLine(faint, Offset(w * .48f - half, y), Offset(w * .48f + half, y), stroke, StrokeCap.Round)
                    }
                    drawCircle(faint, unit * .045f, Offset(w * .78f, h * .22f), style = lineStyle)
                }
                else -> {
                    repeat(12) { i ->
                        val angle = (i * 30f + progress * 12f) * (kotlin.math.PI.toFloat() / 180f)
                        val inner = unit * .12f
                        val outer = unit * (.23f + if (i % 2 == 0) .10f else .035f)
                        drawLine(
                            if (i % 2 == 0) base else faint,
                            Offset(cx + kotlin.math.cos(angle) * inner, cy + kotlin.math.sin(angle) * inner),
                            Offset(cx + kotlin.math.cos(angle) * outer, cy + kotlin.math.sin(angle) * outer),
                            stroke,
                            StrokeCap.Round,
                        )
                    }
                    drawCircle(base, unit * (.09f + progress * .015f), Offset(cx, cy), style = lineStyle)
                    drawCircle(faint, unit * .39f, Offset(cx, cy), style = lineStyle)
                }
            }
        }
    }
}

@Composable
private fun ListenTogetherShelf(artworks: List<String?>, onClick: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SectionHeader("Listen together", emphasized = true)
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(listOf("Start a session", "Join a session").withIndex().toList(), key = { it.value }) { (index, label) ->
                Box(
                    modifier = Modifier.width(300.dp).height(142.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() },
                ) {
                    val cardArtwork = artworks.getOrNull(index)
                    if (!cardArtwork.isNullOrBlank()) {
                        AsyncImage(model = cardArtwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .28f))),
                        ),
                    )
                    Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                        Text("Listen together", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .78f))
                        Text(label, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Shared by the home feed, Explore and Library so headings line up across tabs.
 *
 * [onShowAll] is only ever set on Library, whose rows stop at five cards
 * rather than running the shelf's whole length — see [LibraryGridShelf].
 * Home and Explore never pass it, so their heading is unchanged.
 */
@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String = "",
    onShowAll: (() -> Unit)? = null,
    actionLabel: String? = null,
    emphasized: Boolean = false,
    horizontalInset: androidx.compose.ui.unit.Dp = PAGE_GUTTER,
) {
    val displayTitle = localizeShelfTitle(title)
    val displaySubtitle = localizeShelfSubtitle(subtitle)
    Row(
        modifier = Modifier
            .padding(horizontal = horizontalInset, vertical = if (emphasized) 8.dp else 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (displaySubtitle.isNotBlank()) {
                Text(
                    text = displaySubtitle,
                    style = if (emphasized) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onShowAll != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onShowAll)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel ?: stringResource(Res.string.show_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
@Composable
internal fun localizeShelfTitle(title: String): String {
    val trimmed = title.trim()
    return when {
        trimmed.equals("Recents", ignoreCase = true) ||
            trimmed.equals("Recently played", ignoreCase = true) ||
            trimmed.equals("Gần đây", ignoreCase = true) ||
            trimmed.equals("最近", ignoreCase = true) ->
            stringResource(Res.string.shelf_recents)
        trimmed.equals("Playlists", ignoreCase = true) ||
            trimmed.equals("Danh sách phát", ignoreCase = true) ||
            trimmed.equals("再生リスト", ignoreCase = true) ->
            stringResource(Res.string.playlists)
        trimmed.equals("Albums", ignoreCase = true) ||
            trimmed.equals("Album", ignoreCase = true) ||
            trimmed.equals("アルバム", ignoreCase = true) ->
            stringResource(Res.string.albums)
        trimmed.equals("Artists", ignoreCase = true) ||
            trimmed.equals("Nghệ sĩ", ignoreCase = true) ||
            trimmed.equals("アーティスト", ignoreCase = true) ->
            stringResource(Res.string.artists)
        trimmed.equals("Subscriptions", ignoreCase = true) ||
            trimmed.equals("Đã đăng ký", ignoreCase = true) ||
            trimmed.equals("登録チャンネル", ignoreCase = true) ->
            stringResource(Res.string.subscriptions)
        trimmed.equals("Trending community playlists", ignoreCase = true) ||
            trimmed.equals("Danh sách phát cộng đồng thịnh hành", ignoreCase = true) ||
            trimmed.equals("Danh sách phát thịnh hành trong cộng đồng người dùng", ignoreCase = true) ||
            trimmed.equals("急上昇のコミュニティ再生リスト", ignoreCase = true) ->
            stringResource(Res.string.shelf_trending_community_playlists)
        trimmed.equals("Featured playlists for you", ignoreCase = true) ||
            trimmed.equals("Danh sách phát đề xuất cho bạn", ignoreCase = true) ||
            trimmed.equals("Danh sách phát nổi bật dành cho bạn", ignoreCase = true) ||
            trimmed.equals("おすすめの再生リスト", ignoreCase = true) ->
            stringResource(Res.string.shelf_featured_playlists_for_you)
        trimmed.equals("Quick picks", ignoreCase = true) ||
            trimmed.equals("Lựa chọn nhanh", ignoreCase = true) ||
            trimmed.equals("Chọn nhanh đài phát", ignoreCase = true) ||
            trimmed.equals("クイック ミックス", ignoreCase = true) ->
            stringResource(Res.string.shelf_quick_picks)
        trimmed.equals("Listen again", ignoreCase = true) ||
            trimmed.equals("Nghe lại", ignoreCase = true) ||
            trimmed.equals("もう一度聴く", ignoreCase = true) ->
            stringResource(Res.string.shelf_listen_again)
        trimmed.equals("Mixed for you", ignoreCase = true) ||
            trimmed.equals("Dành riêng cho bạn", ignoreCase = true) ||
            trimmed.equals("ミックス", ignoreCase = true) ->
            stringResource(Res.string.shelf_mixed_for_you)
        trimmed.startsWith("Similar to", ignoreCase = true) -> {
            val rest = trimmed.substring(10).trim()
            stringResource(Res.string.shelf_similar_to, rest)
        }
        trimmed.startsWith("Tương tự như", ignoreCase = true) -> {
            val rest = trimmed.substring(12).trim()
            stringResource(Res.string.shelf_similar_to, rest)
        }
        trimmed.equals("Forgotten favorites", ignoreCase = true) ||
            trimmed.equals("Giai điệu quen thuộc", ignoreCase = true) ||
            trimmed.equals("よく聴いたお気に入りの曲", ignoreCase = true) ->
            stringResource(Res.string.shelf_forgotten_favorites)
        trimmed.equals("Recommended music videos", ignoreCase = true) ||
            trimmed.equals("Video âm nhạc đề xuất", ignoreCase = true) ||
            trimmed.equals("おすすめのミュージック ビデオ", ignoreCase = true) ->
            stringResource(Res.string.shelf_recommended_music_videos)
        trimmed.equals("From your library", ignoreCase = true) ||
            trimmed.equals("Từ thư viện của bạn", ignoreCase = true) ||
            trimmed.equals("ライブラリから", ignoreCase = true) ->
            stringResource(Res.string.shelf_from_your_library)
        trimmed.equals("Charts", ignoreCase = true) ||
            trimmed.equals("Bảng xếp hạng", ignoreCase = true) ||
            trimmed.equals("チャート", ignoreCase = true) ->
            stringResource(Res.string.shelf_charts)
        trimmed.equals("New releases", ignoreCase = true) ||
            trimmed.equals("Bản phát hành mới", ignoreCase = true) ||
            trimmed.equals("最新リリース", ignoreCase = true) ->
            stringResource(Res.string.shelf_new_releases)
        trimmed.equals("Top music videos", ignoreCase = true) ||
            trimmed.equals("Video âm nhạc hàng đầu", ignoreCase = true) ||
            trimmed.equals("人気のミュージック ビデオ", ignoreCase = true) ->
            stringResource(Res.string.shelf_top_music_videos)
        trimmed.equals("For you", ignoreCase = true) ||
            trimmed.equals("Dành cho bạn", ignoreCase = true) ||
            trimmed.equals("あなたへのおすすめ", ignoreCase = true) ->
            stringResource(Res.string.shelf_for_you)
        trimmed.equals("Hits today", ignoreCase = true) ||
            trimmed.equals("Today's Hits", ignoreCase = true) ||
            trimmed.equals("Bản hit hôm nay", ignoreCase = true) ||
            trimmed.equals("今日のヒット曲", ignoreCase = true) ->
            stringResource(Res.string.shelf_hits_today)
        trimmed.equals("Artists on the rise", ignoreCase = true) ||
            trimmed.equals("Nghệ sĩ đang lên", ignoreCase = true) ->
            stringResource(Res.string.shelf_artists_on_the_rise)
        trimmed.equals("Concerts", ignoreCase = true) ||
            trimmed.equals("Buổi hòa nhạc", ignoreCase = true) ||
            trimmed.equals("コンサート", ignoreCase = true) ->
            stringResource(Res.string.shelf_concerts)
        trimmed.startsWith("Shorts", ignoreCase = true) ||
            trimmed.equals("Shorts nổi bật", ignoreCase = true) ||
            trimmed.equals("ショート", ignoreCase = true) ->
            stringResource(Res.string.shelf_shorts)
        trimmed.equals("Trending", ignoreCase = true) ||
            trimmed.equals("Thịnh hành", ignoreCase = true) ||
            trimmed.equals("急上昇", ignoreCase = true) ->
            stringResource(Res.string.shelf_trending)
        else -> title
    }
}
@Composable
internal fun localizeShelfSubtitle(subtitle: String): String {
    val trimmed = subtitle.trim()
    return when {
        trimmed.equals("TOP TUNES RIGHT NOW", ignoreCase = true) ||
            trimmed.equals("Top tunes right now", ignoreCase = true) ||
            trimmed.equals("Giai điệu hàng đầu hiện nay", ignoreCase = true) ||
            trimmed.equals("注目の曲", ignoreCase = true) ->
            stringResource(Res.string.shelf_top_tunes_right_now)
        trimmed.equals("From the community", ignoreCase = true) ||
            trimmed.equals("Từ cộng đồng", ignoreCase = true) ||
            trimmed.equals("コミュニティより", ignoreCase = true) ->
            stringResource(Res.string.shelf_from_the_community)
        trimmed.equals("YouTube Charts", ignoreCase = true) ||
            trimmed.equals("Bảng xếp hạng YouTube", ignoreCase = true) ||
            trimmed.equals("YouTube チャート", ignoreCase = true) ->
            stringResource(Res.string.shelf_youtube_charts)
        else -> subtitle
    }
}

@Composable
internal fun localizeCardSubtitle(subtitle: String): String {
    if (subtitle.isBlank()) return subtitle
    val delimiter = " • "
    val parts = subtitle.split(delimiter)
    val songLabel = stringResource(Res.string.shelf_song_item)
    val singleLabel = stringResource(Res.string.shelf_single)
    val chartLabel = stringResource(Res.string.shelf_chart)
    val playlistLabel = stringResource(Res.string.playlist)
    val localizedParts = parts.map { part ->
        val trimmed = part.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        when {
            trimmed.equals("Song", ignoreCase = true) || trimmed.equals("Titre", ignoreCase = true) ||
                trimmed.equals("Bài hát", ignoreCase = true) || trimmed.equals("曲", ignoreCase = true) -> songLabel
            trimmed.equals("Single", ignoreCase = true) || trimmed.equals("Đĩa đơn", ignoreCase = true) ||
                trimmed.equals("シングル", ignoreCase = true) -> singleLabel
            trimmed.equals("Chart", ignoreCase = true) || trimmed.equals("Bảng xếp hạng", ignoreCase = true) ||
                trimmed.equals("チャート", ignoreCase = true) -> chartLabel
            trimmed.equals("Playlist", ignoreCase = true) || trimmed.equals("Danh sách phát", ignoreCase = true) ||
                trimmed.equals("再生リスト", ignoreCase = true) -> playlistLabel
            lower.endsWith(" views") || lower.endsWith(" view") || lower.endsWith(" lượt xem") ||
                lower.endsWith(" 回視聴") || lower.endsWith("回視聴") -> {
                val count = trimmed.substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_views_format, count) else part
            }
            lower.endsWith(" plays") || lower.endsWith(" play") || lower.endsWith(" lượt phát") ||
                lower.endsWith(" 回再生") || lower.endsWith("回再生") -> {
                val count = trimmed.substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_plays_format, count) else part
            }
            lower.endsWith(" songs") || lower.endsWith(" song") || lower.endsWith(" bài hát") ||
                lower.endsWith(" 曲") || lower.endsWith("曲") -> {
                val count = trimmed.substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_songs_format, count) else part
            }
            else -> part
        }
    }
    return localizedParts.joinToString(delimiter)
}

@Composable
private fun HeroShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
    ) {
        SectionHeader(shelf.title, shelf.subtitle, emphasized = true)
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        val itemKeys = remember(shelf.items) { shelf.items.stableShelfComposeKeys() }
        // Measured rather than taken as a share of the parent, because the card
        // has a ceiling as well as a fraction — see [heroCardWidth]. A fixed
        // width is also the only one of the two the aspect ratio below can turn
        // into a height, so the card keeps its shape however it was arrived at.
        BoxWithConstraints {
            val cardWidth = heroCardWidth(maxWidth)
            ShelfRow(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(shelf.items, key = { index, _ -> itemKeys[index] }) { _, item ->
                    HeroCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongPress = onItemLongPress?.let { { it(item) } },
                        modifier = Modifier.width(cardWidth),
                        isCurrent = isCurrentItem(item),
                        isPlaying = isCurrentItem(item) && isPlaying,
                    )
                }
            }
        }
    }
}

/** Big card: artwork with the caption laid over a scrim, as on Listen Now. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Box(
        modifier = modifier
            .aspectRatio(1.12f)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (item.videoId == null) Modifier.thumbnailBorder(RoundedCornerShape(18.dp))
                else Modifier,
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(HEADER_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) PlayingAccent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                val displaySubtitle = localizeCardSubtitle(item.subtitle)
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * [leadingCard] rides at the head of the row, ahead of the content — the
 * Library tab's "New playlist" tile, which belongs among the playlists rather
 * than in a bar somewhere above them. [onItemLongPress] opens the album /
 * playlist menu, and is null only where a card points at something with no
 * track list behind it to act on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Shelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    onPlayAllQuickPicks: ((HomeShelf) -> Unit)? = null,
    leadingCard: (@Composable () -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        when {
            shelf.title.equals("Quick picks", ignoreCase = true) -> {
                SectionHeader(
                    title = shelf.title,
                    subtitle = shelf.subtitle,
                    onShowAll = onPlayAllQuickPicks?.let { { it(shelf) } },
                    actionLabel = stringResource(Res.string.play_all),
                    emphasized = true,
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val contentWidth = (maxWidth - PAGE_GUTTER * 2).coerceAtLeast(1.dp)
                    // Keep the reference's two-column density where each row
                    // can still show its title. Narrow windows use one column
                    // rather than squeezing artwork, metadata and overflow
                    // into an unusable strip.
                    val columnCount = if (contentWidth >= 420.dp) 2 else 1
                    val pages = shelf.items.chunked(columnCount * 4)
                    ShelfRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(pages, key = { index, page ->
                            "quick-picks-page-$index-${page.firstOrNull()?.videoId.orEmpty()}"
                        }) { _, page ->
                            if (columnCount == 1) {
                                Column(Modifier.width(contentWidth)) {
                                    page.forEach { item ->
                                        QuickPickTrackRow(
                                            item = item,
                                            onClick = { onItemClick(item) },
                                            onLongPress = onItemLongPress?.let { { it(item) } },
                                            isCurrent = isCurrentItem(item),
                                            isPlaying = isCurrentItem(item) && isPlaying,
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.width(contentWidth),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    listOf(page.take(4), page.drop(4)).forEach { columnItems ->
                                        Column(Modifier.weight(1f)) {
                                            columnItems.forEach { item ->
                                                QuickPickTrackRow(
                                                    item = item,
                                                    onClick = { onItemClick(item) },
                                                    onLongPress = onItemLongPress?.let { { it(item) } },
                                                    isCurrent = isCurrentItem(item),
                                                    isPlaying = isCurrentItem(item) && isPlaying,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            shelf.title.equals("Listen again", ignoreCase = true) ||
                shelf.title.equals("Keep listening", ignoreCase = true) -> {
                SectionHeader("Keep listening", shelf.subtitle, emphasized = true)
                val itemKeys = remember(shelf.items) { shelf.items.stableShelfComposeKeys() }
                ShelfRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(shelf.items, key = { index, _ -> "keep-listening-${itemKeys[index]}" }) { _, item ->
                        ShelfCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = onItemLongPress?.let { { it(item) } },
                            modifier = Modifier.width(KEEP_LISTENING_MIN_CARD_WIDTH),
                            isCurrent = isCurrentItem(item),
                            isPlaying = isCurrentItem(item) && isPlaying,
                            titleMaxLines = 2,
                            subtitleMaxLines = 2,
                            readableMetadata = true,
                        )
                    }
                }
            }
            else -> {
                val homeTitle = when {
                    shelf.title.equals("New releases", ignoreCase = true) -> "Fresh finds"
                    shelf.title.equals("Forgotten favorites", ignoreCase = true) -> "Old favorites"
                    else -> shelf.title
                }
                SectionHeader(homeTitle, shelf.subtitle)
                val itemKeys = remember(shelf.items) { shelf.items.stableShelfComposeKeys() }
                if (shelf.title.contains("video", ignoreCase = true)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val contentWidth = (maxWidth - PAGE_GUTTER * 2).coerceAtLeast(1.dp)
                        val videoCardWidth = (contentWidth * 0.82f)
                            .coerceAtLeast(220.dp)
                            .coerceAtMost(380.dp)
                            .coerceAtMost(contentWidth)
                        ShelfRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(shelf.items, key = { index, _ -> itemKeys[index] }) { _, item ->
                                MusicVideoCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onLongPress = onItemLongPress?.let { { it(item) } },
                                    modifier = Modifier.width(videoCardWidth),
                                    isCurrent = isCurrentItem(item),
                                    isPlaying = isCurrentItem(item) && isPlaying,
                                )
                            }
                        }
                    }
                } else {
                    ShelfRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        leadingCard?.let { card -> item(key = "leading") { card() } }
                        itemsIndexed(shelf.items, key = { index, _ -> itemKeys[index] }) { _, item ->
                            ShelfCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                onLongPress = onItemLongPress?.let { { it(item) } },
                                isCurrent = isCurrentItem(item),
                                isPlaying = isCurrentItem(item) && isPlaying,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickTrackRow(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            AsyncImage(
                model = item.thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizeCardSubtitle(item.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onLongPress != null) RowMoreButton(onClick = onLongPress)
    }
}

/** Landscape artwork stays inside its 16:9 viewport; unlike square playlist covers it is never cropped. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicVideoCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF11141C)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = "Music video: ${item.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = localizeCardSubtitle(item.subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A card that isn't a thing yet — the dashed "New playlist" tile at the head
 * of the Library's playlist row, sized to sit in line with the covers beside
 * it rather than as a button bolted above them.
 */
@Composable
internal fun NewShelfCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
    /** A full-colour mark drawn in place of [icon], for a card that stands for a brand. */
    logo: DrawableResource? = null,
) {
    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                // The mark is a disc with the bars cut out, so tinting it
                // leaves the bars clear: white on dark, black on light.
                Icon(
                    painter = painterResource(logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A device-folder card: mesh gradient in the folder's colours with its glyph
 * on top. One composable for local music, WebDAV and SMB rather than three
 * copies of the same box.
 */
@Composable
private fun ServiceCard(colors: List<Color>, trackKey: String, icon: ImageVector) {
    val palette = remember { MeshPalette(colors) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = trackKey,
            continuous = true,
            blurRadius = 24.dp,
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShelfCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
    /** Set on a Library playlist card that's in [AppSettings.pinnedPlaylists][com.music.audify.data.settings.AppSettings.pinnedPlaylists]. */
    isPinned: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    titleMaxLines: Int = 1,
    subtitleMaxLines: Int = 1,
    /** Higher-contrast, explicit line metrics for dense Keep Listening album grids. */
    readableMetadata: Boolean = false,
) {
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        when (item.browseId) {
            "local:downloads" -> {
                val palette = remember { MeshPalette(listOf(Color(0xFF1E3C72), Color(0xFF2A5298))) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    MeshGradientBackground(
                        palette = palette,
                        trackKey = "local:downloads",
                        continuous = true,
                        blurRadius = 24.dp,
                    )
                    Icon(
                        imageVector = AudifyIcons.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            "local:all" -> ServiceCard(
                colors = listOf(Color(0xFF134E5E), Color(0xFF71B280)),
                trackKey = "local:all",
                icon = Icons.Rounded.LibraryMusic,
            )
            "local:cache" -> ServiceCard(
                colors = listOf(Color(0xFF42275A), Color(0xFF734B6D)),
                trackKey = "local:cache",
                icon = Icons.Rounded.Cached,
            )
            "local:webdav" -> ServiceCard(
                colors = listOf(Color(0xFF3A1C71), Color(0xFFD76D77)),
                trackKey = "local:webdav",
                icon = Icons.Rounded.Folder,
            )
            "local:smb" -> ServiceCard(
                colors = listOf(Color(0xFF0F2027), Color(0xFF2C5364)),
                trackKey = "local:smb",
                icon = Icons.Rounded.Storage,
            )
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (item.videoId == null) Modifier.thumbnailBorder(RoundedCornerShape(12.dp))
                            else Modifier,
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.height(if (readableMetadata) 12.dp else 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPinned) {
                Icon(
                    imageVector = AudifyIcons.Pin,
                    contentDescription = stringResource(Res.string.pinned),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = item.title,
                style = if (readableMetadata) {
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.sp,
                    )
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                softWrap = true,
            )
        }
        val displaySubtitle = localizeCardSubtitle(item.subtitle)
        Text(
            text = displaySubtitle,
            style = if (readableMetadata) {
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.sp,
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (readableMetadata) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = subtitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
        )
    }
}
