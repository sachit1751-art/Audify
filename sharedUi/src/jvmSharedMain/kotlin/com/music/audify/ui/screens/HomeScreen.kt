package com.music.audify.ui.screens

import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.HOME_SLOT_TOP_PICKS
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.Account
import com.music.audify.data.model.homeShelfKey
import com.music.audify.ui.components.VideoThumbnails
import com.music.audify.ui.components.trackArtSize
import com.music.audify.ui.components.contextClick
import com.music.audify.ui.AppUi
import com.music.audify.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import com.music.audify.ui.rememberIsForeground
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.music.audify.ui.components.longPressMenuClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import com.music.audify.ui.components.ShelfRow
import com.music.audify.ui.components.shelfStagger
import com.music.audify.ui.components.SkeletonShimmerHost
import com.music.audify.ui.components.RevealSeenState
import com.music.audify.ui.components.rememberRevealSeenState
import com.music.audify.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Text
import com.music.audify.ui.components.ElasticRefreshState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.GraphicEq
import com.music.audify.ui.icons.AudifyIcons
import com.music.audify.data.YtMusicRepository
import coil3.compose.AsyncImage
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.HEADER_ART_PX
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.LOCAL_SPEED_DIAL_TITLE
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.ShelfLayout
import com.music.audify.data.model.Song
import com.music.audify.data.model.TopPick
import com.music.audify.data.model.TopPickMood
import com.music.audify.data.model.PodcastProviderShow
import com.music.audify.data.model.UiState
import java.util.Locale
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.components.HERO_CARD_RATIO
import com.music.audify.ui.components.MessageState
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.RowMoreButton
import com.music.audify.ui.components.PullToRefresh
import com.music.audify.ui.components.SHELF_CARD_WIDTH
import com.music.audify.ui.components.SearchPlayingBars
import com.music.audify.ui.components.SignInBanner
import com.music.audify.ui.components.coverFlightSource
import com.music.audify.ui.components.hideDuringCoverFlight
import com.music.audify.ui.components.rememberCoverFlightTap
import com.music.audify.ui.components.markCoverTapped
import com.music.audify.ui.components.rememberCoverTap
import com.music.audify.ui.components.feedMoreSkeleton
import com.music.audify.ui.components.feedSkeleton
import com.music.audify.ui.components.recentlyPlayedSkeleton
import com.music.audify.ui.components.ShelfSkeleton
import com.music.audify.ui.components.heroCardWidth
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.components.trackColumnWidth
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.MeshPalette
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A slightly longer exponential coast for the main Home feed. */
private class HomeFlingBehavior : FlingBehavior {
    private val decay = exponentialDecay<Float>(frictionMultiplier = 0.82f)

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val scrollScope = this
        var previousValue = 0f
        var remainingVelocity = initialVelocity
        var hitBoundary = false
        Animatable(0f).animateDecay(initialVelocity, decay) {
            val delta = value - previousValue
            val consumed = scrollScope.scrollBy(delta)
            previousValue = value
            remainingVelocity = velocity
            if (abs(delta - consumed) > 0.5f) {
                hitBoundary = true
            }
        }
        return if (hitBoundary) 0f else remainingVelocity
    }
}

/** How far the Listen now heading and feed sit above the shared top inset. */
internal val HOME_TITLE_LIFT = 64.dp

private const val RECENTS_TITLE = "Recents"

/** Speed dial leads Home, above Listen again and Recents, wherever the feed put it. */
private fun List<HomeShelf>.withoutSpeedDial(): List<HomeShelf> =
    filterNot { shelf -> SPEED_DIAL_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) } }
private const val RECENT_TRACKS_PER_COLUMN = 4
private const val QUICK_PICKS_ROWS = 3
private val QUICK_PICK_COLUMN_WIDTH = 286.dp
private val QUICK_PICK_COLUMN_SPACING = 18.dp

internal fun quickPicksColumns(items: List<ShelfItem>): List<List<ShelfItem>> =
    items.chunked(QUICK_PICKS_ROWS)

private val LISTEN_AGAIN_TITLES = listOf("Listen again", "Nghe lại", "もう一度聴く", "Dengarkan lagi", "Putar ulang", "다시 듣기")
private val SPEED_DIAL_TITLES = listOf(LOCAL_SPEED_DIAL_TITLE, "Speed dial", "Panggilan cepat", "Quay số nhanh", "スピードダイヤル", "스피드 다이얼")
private val QUICK_PICKS_TITLES = listOf("Quick picks", "Lựa chọn nhanh", "Chọn nhanh đài phát", "クイック ミックス", "Pilihan cepat", "Pilihan singkat", "빠른 선곡", "빠른 선택")

/** Keep Quick picks as the first regular Home shelf, even while Recents is loading. */
private fun List<HomeShelf>.withQuickPicksFirst(): List<HomeShelf> {
    val quickPicks = indexOfFirst { shelf ->
        QUICK_PICKS_TITLES.any { it.equals(shelf.title.trim(), ignoreCase = true) }
    }
    if (quickPicks <= 0) return this
    return toMutableList().also { it.add(0, it.removeAt(quickPicks)) }
}

/**
 * Quick picks menempati slot Listen again (baris kartu persegi di depan),
 * dan Listen again pindah ke slot Quick picks (daftar berhalaman).
 * Dibiarkan apa adanya bila salah satunya tidak ada.
 */
private fun List<HomeShelf>.withQuickPicksInListenAgainSlot(): List<HomeShelf> {
    val quick = indexOfFirst { shelf -> QUICK_PICKS_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) } }
    val again = indexOfFirst { shelf -> LISTEN_AGAIN_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) } }
    if (quick < 0 || again < 0 || quick == again) return this
    return toMutableList().also {
        it[quick] = this[again]
        it[again] = this[quick]
    }
}

/**
 * Recents and YouTube's Listen again trade places: Listen again leads the
 * feed and Recents sits where it was. Left as is when either is missing, as
 * for a guest, who has no Recents.
 */
private fun List<HomeShelf>.withListenAgainFirst(): List<HomeShelf> {
    val recents = indexOfFirst { it.title.trim().equals(RECENTS_TITLE, ignoreCase = true) }
    val again = indexOfFirst { shelf -> LISTEN_AGAIN_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) } }
    if (recents < 0 || again < 0 || recents == again) return this
    return toMutableList().also {
        it[recents] = this[again]
        it[again] = this[recents]
    }
}

/**
 * Listen again adalah satu baris kartu persegi, jadi shelf tepat di bawahnya
 * tidak boleh berbentuk sama: shelf berbentuk lain terdekat (daftar berhalaman
 * seperti Recents/Quick picks dulu, lalu video) naik mengisi slot itu.
 * Dibiarkan apa adanya bila semua shelf di bawahnya berbentuk kartu.
 */
private fun List<HomeShelf>.withOtherModelBelowListenAgain(): List<HomeShelf> {
    val again = indexOfFirst { shelf -> QUICK_PICKS_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) } }
    if (again < 0 || again + 1 >= size) return this
    fun HomeShelf.isPagedList() =
        layout == ShelfLayout.LIST || title.trim().equals(RECENTS_TITLE, ignoreCase = true)
    fun HomeShelf.isCardRow() = layout == ShelfLayout.CARDS && !isPagedList()
    if (!this[again + 1].isCardRow()) return this
    val from = (again + 2 until size).firstOrNull { this[it].isPagedList() }
        ?: (again + 2 until size).firstOrNull { !this[it].isCardRow() }
        ?: return this
    return toMutableList().also { it.add(again + 1, it.removeAt(from)) }
}

/** How many placeholder rows a custom layout shows while the feed loads. */
private const val HOME_SKELETON_SLOTS = 4

private const val SPEED_DIAL_COLUMNS = 3
private const val SPEED_DIAL_ROWS = 3
private val SPEED_DIAL_GAP = 12.dp

/**
 * Speed dial: a grid of square covers, three across and three down per page,
 * as YouTube Music draws it. One tap plays the track; more pages scroll sideways.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaySongs: ((List<Song>) -> Unit)? = null,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        HomeSectionHeader(shelf.title, shelf.subtitle)
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        val shuffleSongs = remember(shelf.items) {
            shelf.items.mapNotNull { item ->
                val id = item.videoId ?: return@mapNotNull null
                if (item.browseId != null) {
                    null
                } else {
                    Song(videoId = id, title = item.title, artist = item.subtitle, thumbnailUrl = item.thumbnailUrl)
                }
            }
        }
        val canShuffle = onPlaySongs != null && shuffleSongs.isNotEmpty()
        val perPage = SPEED_DIAL_COLUMNS * SPEED_DIAL_ROWS
        val pages = remember(shelf.items, canShuffle) {
            if (canShuffle) {
                listOf(shelf.items.take(perPage - 1)) + shelf.items.drop(perPage - 1).chunked(perPage)
            } else {
                shelf.items.chunked(perPage)
            }
        }
        BoxWithConstraints {
            val pageWidth = maxWidth - PAGE_GUTTER * 2
            val tile = (pageWidth - SPEED_DIAL_GAP * (SPEED_DIAL_COLUMNS - 1)) / SPEED_DIAL_COLUMNS
            val listState = rememberShelfListState("speed-dial:${homeShelfKey(shelf.title)}")
            if (pages.size > 1) {
                val currentPage by remember(listState) {
                    derivedStateOf {
                        val index = listState.firstVisibleItemIndex
                        if (listState.firstVisibleItemScrollOffset > 160) index + 1 else index
                    }
                }
                SpeedDialPageDots(
                    count = pages.size,
                    current = currentPage,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                )
            }
            ShelfRow(
                state = listState,
                contentPadding = PaddingValues(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(PAGE_GUTTER),
            ) {
                items(pages, contentType = { "speed-dial-page" }) { page ->
                    Column(
                        modifier = Modifier.width(pageWidth),
                        verticalArrangement = Arrangement.spacedBy(SPEED_DIAL_GAP),
                    ) {
                        repeat(SPEED_DIAL_ROWS) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(SPEED_DIAL_GAP)) {
                                repeat(SPEED_DIAL_COLUMNS) { column ->
                                    val slot = rowIndex * SPEED_DIAL_COLUMNS + column
                                    val item = page.getOrNull(slot)
                                    if (canShuffle && page === pages.first() && slot == SPEED_DIAL_COLUMNS * SPEED_DIAL_ROWS - 1) {
                                        SpeedDialShuffleTile(
                                            onClick = { onPlaySongs?.invoke(shuffleSongs.shuffled()) },
                                            modifier = Modifier.width(tile),
                                        )
                                    } else if (item != null) {
                                        SpeedDialTile(
                                            item = item,
                                            onClick = { onItemClick(item) },
                                            onLongPress = onItemLongPress?.let { { it(item) } },
                                            isCurrent = isCurrentItem(item),
                                            isPlaying = isCurrentItem(item) && isPlaying,
                                            modifier = Modifier.width(tile),
                                        )
                                    } else {
                                        Spacer(Modifier.width(tile))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shuffle card in the last slot of a speed dial page: accent gradient, glass disc, springy press. */
@Composable
private fun SpeedDialShuffleTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val phase = remember { Animatable(0f) }
    var searching by remember { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    // Idle breathing: a slow wave running through the four dots. It resumes from
    // where it stopped, and stops while searching or while the app is not on screen.
    val idle = remember { Animatable(0f) }
    val foreground = rememberIsForeground()
    LaunchedEffect(foreground, searching) {
        if (!foreground || searching) return@LaunchedEffect
        while (true) {
            val start = idle.value % 1f
            idle.snapTo(start)
            idle.animateTo(start + 1f, tween(durationMillis = 3200, easing = LinearEasing))
        }
    }
    // One full swirl, as if looking for a song, then the shuffle starts.
    LaunchedEffect(searching) {
        if (searching) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(durationMillis = 1100, easing = LinearEasing))
            latestClick()
            phase.snapTo(0f)
            searching = false
        }
    }
    val dotColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .thumbnailBorder(shape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface),
                ),
            )
            .longPressMenuClickable(onClick = { if (!searching) searching = true }, onLongClick = null),
    ) {
        Canvas(
            Modifier
                .align(Alignment.Center)
                .size(56.dp),
        ) {
            val p = phase.value
            val eased = p * p * (3f - 2f * p)
            val envelope = sin(PI.toFloat() * p)
            val turn = 2f * PI.toFloat() * eased
            val idleT = idle.value * 2f * PI.toFloat()
            val calm = 1f - envelope
            val unit = size.minDimension
            val orbit = unit * 0.19f * 1.4142f * (1f - 0.45f * envelope)
            val base = unit * 0.085f
            for (i in 0 until 4) {
                val wave = sin(idleT + i * PI.toFloat() / 2f)
                val angle = (PI.toFloat() / 4f + i * PI.toFloat() / 2f) + turn + 0.12f * calm * wave
                val radius = base * (1f + 0.5f * envelope * sin(4f * PI.toFloat() * p + i * PI.toFloat() / 2f) + 0.22f * calm * wave)
                val reach = orbit * (1f + 0.07f * calm * wave)
                drawCircle(
                    color = dotColor,
                    radius = radius,
                    center = Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach),
                )
            }
        }
    }
}

/** Small glass disc with a chevron, marking a speed dial card that opens a page instead of playing. */
@Composable
private fun SpeedDialBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
            .border(0.5.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AudifyIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** Page control dots under the speed dial grid: the current one is solid and full size. */
@Composable
private fun SpeedDialPageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val dotAlpha by animateFloatAsState(
                targetValue = if (selected) 0.9f else 0.28f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            )
            val dotScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.78f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                        alpha = dotAlpha
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBrowse = item.browseId != null && item.videoId == null
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .thumbnailBorder(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))))
                .padding(
                    start = 8.dp,
                    end = if (isBrowse) 32.dp else 8.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizeCardSubtitle(item.subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        if (isBrowse) {
            SpeedDialBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}

/**
 * Loading placeholders in the order the listener arranged Home: Top Picks,
 * Recents and every shelf each get a skeleton in their own slot. Hidden rows
 * are not passed in; Top Picks, Find Your Mood and Recents only get one when
 * they are actually going to be drawn.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.homeLayoutSkeleton(
    slots: List<String>,
    withTopPicks: Boolean,
    withMoods: Boolean,
    withRecents: Boolean,
) {
    val recentsKey = homeShelfKey(RECENTS_TITLE)
    // These shelves render as a four-track paged list, not as square artwork
    // cards. Keep their loading footprint identical to the content that will
    // replace it once the feed arrives.
    val pagedListKeys = (LISTEN_AGAIN_TITLES + RECENTS_TITLE)
        .mapTo(HashSet(), ::homeShelfKey)
    var shown = 0
    for (key in slots) {
        if (shown >= HOME_SKELETON_SLOTS) break
        val position = shown
        when {
            key == HOME_SLOT_TOP_PICKS -> if (withTopPicks) {
                topPicksSkeleton()
                shown++
            }
            key == recentsKey -> if (withRecents) {
                recentlyPlayedSkeleton(key = recentsKey)
                shown++
            }
            key in pagedListKeys -> {
                recentlyPlayedSkeleton(key = key)
                shown++
            }
            key == HOME_SLOT_MOODS -> if (withMoods) {
                item(key = "skeleton:slot:$key") { ShelfSkeleton(index = position) }
                shown++
            }
            else -> {
                item(key = "skeleton:slot:$key") { ShelfSkeleton(index = position) }
                shown++
            }
        }
    }
    if (shown == 0) feedSkeleton(firstIsHero = false)
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

/**
 * Quick Picks is a compact three-row track shelf on Home: each horizontal
 * column contains three tracks, and further columns continue to the right.
 * Artwork comes directly from the track thumbnail; no branded placeholder or
 * gradient treatment is applied to real recommendations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPicksShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        HomeSectionHeader(shelf.title, shelf.subtitle)
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        ShelfRow(
            state = rememberShelfListState("quick-picks:${homeShelfKey(shelf.title)}"),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(QUICK_PICK_COLUMN_SPACING),
        ) {
            itemsIndexed(
                quickPicksColumns(shelf.items),
                key = { index, column -> "quick-picks:${column.firstOrNull()?.videoId ?: index}" },
                contentType = { _, _ -> "quick-picks-column" },
            ) { _, column ->
                Column(Modifier.width(QUICK_PICK_COLUMN_WIDTH)) {
                    column.forEach { item ->
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

@Composable
private fun QuickPickTrackRow(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    isCurrent: Boolean,
    isPlaying: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(52.dp, item.isVideo)) {
            AsyncImage(
                model = if (item.isVideo) item.thumbnailUrl else item.thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = if (item.isVideo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
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

@Composable
fun HomeScreen(
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    /** The tapped item and the recommendation shelf it came from. */
    onItemClick: (ShelfItem, String) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: ElasticRefreshState,
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
    /**
     * The "Top Picks for You" row above Recents. Null while it has not been
     * built yet, empty when there is nothing to pick from. The row is only
     * drawn by a caller that can act on it, i.e. passes [onTopPickClick].
     */
    topPicks: List<TopPick>? = null,
    topPicksLoading: Boolean = false,
    /** The signed-in account's name, for the personal station's "Name's Station". */
    listenerName: String? = null,
    onTopPickClick: ((TopPick, String) -> Unit)? = null,
    onTopPickLongPress: ((TopPick) -> Unit)? = null,
    /** A Find Your Mood tile, and its name. The shelf is only drawn when this is set. */
    onMoodClick: ((TopPickMood, String) -> Unit)? = null,
    /** Find Your Mood's Show all: every mood and activity. */
    onMoodsShowAll: () -> Unit = {},
    /** Whether the Top Picks and mood artwork may animate — see [LocalArtworkMotion]. */
    artworkMotion: Boolean = true,
    /** Memutar daftar lagu dari awal; dipakai tombol play di kartu playlist. */
    onPlaySongs: ((List<Song>) -> Unit)? = null,
    /** The listener's own order and hidden rows. Empty keeps Home's built-in order. */
    homeLayout: HomeLayout = HomeLayout(),
    /** Swaps the logo and wordmark for a plain [title], without the mark. */
    hideBranding: Boolean = false,
    /** The listener's pinned playlists, as one row above the feed. Null or empty draws nothing. */
    pinnedShelf: HomeShelf? = null,
    /** A discovery shelf selected for the listener's current local-time block. */
    timeAwareShelf: HomeShelf? = null,
    /** Header actions: search, arrange Home shelves, and open the account. */
    onSearchClick: (() -> Unit)? = null,
    onCustomizeClick: (() -> Unit)? = null,
    account: Account? = null,
    onAccountClick: (() -> Unit)? = null,
    onListenTogetherClick: (() -> Unit)? = null,
    onPodcastsClick: ((PodcastProviderShow?) -> Unit)? = null,
) {
    val successShelves = (state as? UiState.Success)?.data
    val editorialIndex = remember(successShelves, onTopPickClick != null) {
        successShelves?.let { shelves ->
            if (onTopPickClick != null) editorialShelfIndex(shelves) else null
        }
    }
    // This feed list is consumed by LazyColumn's DSL. Reuse it across unrelated
    // recompositions (playback progress, scroll state, artwork state) instead of
    // allocating/filtering it again while the actual shelf data has not changed.
    val visibleShelves = remember(successShelves, editorialIndex) {
        if (successShelves != null && editorialIndex != null) {
            successShelves.filterIndexed { index, _ -> index != editorialIndex }
        } else {
            successShelves
        }
    }
    // Top Picks' pictures and motion wait for the feed — see LocalArtworkImages.
    val feedReady = state !is UiState.Loading
    // Content first composed during a fling must appear fully formed. Running
    // delayed alpha/translation reveals while the list is moving reads as card
    // flicker and competes with image decoding for the frame budget.
    val revealEnabled = feedReady && !listState.isScrollInProgress
    // Home shelf entrances run once per stable shelf key, including after a
    // return from another tab. The saveable set is bounded by RevealSeenState.
    val revealSeenState = rememberRevealSeenState()
    // A custom layout places Top Picks itself, among the other rows, once the feed
    // is in. Until then it only goes above the feed when the layout puts it first;
    // anywhere else it waits for its slot rather than jump there later.
    // A custom layout draws Top Picks (and its skeleton) in its own slot; only an
    // error page keeps it above the message when the layout puts it first.
    val topPicksAboveFeed = if (homeLayout.isCustom) {
        state is UiState.Error &&
            homeLayout.slots.firstOrNull { !it.hidden }?.key == HOME_SLOT_TOP_PICKS
    } else {
        true
    }
    // Held from the first frame, so the row's slot never opens up after the feed is drawn.
    val topPicksWaiting = topPicks == null && (topPicksLoading || state is UiState.Loading)
    var homePodcastShows by remember { mutableStateOf<List<PodcastProviderShow>>(emptyList()) }
    LaunchedEffect(feedReady) {
        if (!feedReady || onPodcastsClick == null) return@LaunchedEffect
        val saved = YtMusicRepository.libraryPodcastShows().getOrNull().orEmpty()
        homePodcastShows = saved
        YtMusicRepository.podcastDiscoveryCategories().getOrNull().orEmpty()
            .flatMap { it.shows }
            .let { discovered -> (saved + discovered).distinctBy(PodcastProviderShow::browseId).take(12) }
            .also { homePodcastShows = it }
    }
    CompositionLocalProvider(
        LocalArtworkMotion provides (artworkMotion && feedReady),
        LocalArtworkImages provides feedReady,
    ) {
    // The loops also hold still while the feed is being scrolled — see
    // LocalArtworkScrolling for why that is asked rather than provided.
    PausedWhileScrolling(listState) {

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        // Home lifts its heading under the frosted bar; keep the refresh hint below that heading.
        topInset = contentPadding.calculateTopPadding() + HOME_TITLE_LIFT,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        SkeletonShimmerHost(enabled = !feedReady || topPicksWaiting || recentlyPlayedLoading || loadingMore) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // A slightly longer decay lets a fast swipe settle progressively
            // instead of feeling as though the feed hits an invisible brake.
            flingBehavior = remember { HomeFlingBehavior() },
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
                item(key = "home-header") {
                    Box(Modifier.fillMaxWidth().revealOnAppear("home:header", revealSeenState, revealEnabled)) {
                        HomeHeader(
                            title = title,
                            account = account,
                            onSearchClick = onSearchClick,
                            onCustomizeClick = onCustomizeClick,
                            onAccountClick = onAccountClick,
                        )
                    }
                }
            }
            if (!signedIn && onSignIn != null) {
                item {
                    // Read in this item, so a scroll starting or ending
                    // recomposes the banner alone.
                    SignInBanner(
                        onSignIn = onSignIn,
                        modifier = Modifier.padding(bottom = 8.dp).revealOnAppear(
                            "home:sign-in", revealSeenState, revealEnabled,
                        ),
                        motion = LocalArtworkMotion.current && !listState.isScrollInProgress,
                    )
                }
            }
            // Pinned playlists: one tap from Home instead of a trip to Library.
            if (pinnedShelf != null && pinnedShelf.items.isNotEmpty()) {
                item(key = "pinned-playlists") {
                    Box(Modifier.fillMaxWidth().revealOnAppear("home:pinned", revealSeenState, revealEnabled)) {
                        Shelf(
                            shelf = pinnedShelf,
                            onItemClick = { onItemClick(it, pinnedShelf.title) },
                            onItemLongPress = onItemLongPress,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            homeStyle = true,
                        )
                    }
                }
            }
            if (timeAwareShelf != null && timeAwareShelf.items.isNotEmpty()) {
                item(key = "time-aware-discovery") {
                    Box(Modifier.fillMaxWidth().revealOnAppear("home:time-aware", revealSeenState, revealEnabled)) {
                        Shelf(
                            shelf = timeAwareShelf,
                            onItemClick = { onItemClick(it, timeAwareShelf.title) },
                            onItemLongPress = onItemLongPress,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            homeStyle = true,
                        )
                    }
                }
            }
            if (onTopPickClick != null && topPicksAboveFeed) {
                when {
                    !topPicks.isNullOrEmpty() -> item(key = "top-picks") {
                        Box(Modifier.fillMaxWidth().revealOnAppear("home:top-picks", revealSeenState, revealEnabled)) {
                            TopPicksShelf(
                                picks = topPicks,
                                listenerName = listenerName,
                                onClick = onTopPickClick,
                                onLongPress = onTopPickLongPress,
                            )
                        }
                    }
                    topPicksWaiting -> topPicksSkeleton()
                }
            }
            when (state) {
                is UiState.Loading -> {
                    if (homeLayout.isCustom) {
                        // Placeholders in the listener's own order, so what loads
                        // lands where its skeleton already stood.
                        homeLayoutSkeleton(
                            slots = homeLayout.slots.filterNot { it.hidden }.map { it.key },
                            withTopPicks = onTopPickClick != null,
                            withMoods = onMoodClick != null,
                            withRecents = recentlyPlayedLoading,
                        )
                    } else if (recentlyPlayedLoading) {
                        // Keep the exact LazyColumn key when the real Recents
                        // shelf replaces this placeholder. Compose can then
                        // retain its measured slot and scroll anchor.
                        recentlyPlayedSkeleton(key = homeShelfKey(RECENTS_TITLE))
                        // Recents owns the leading layout while its request is
                        // pending, so the feed behind it starts with ordinary
                        // shelf placeholders rather than another hero card.
                        feedSkeleton(firstIsHero = false)
                    } else {
                        feedSkeleton(firstIsHero = leadHero)
                    }
                }
                is UiState.Error -> item {
                    MessageState(
                        state.message,
                        actionLabel = stringResource(Res.string.retry).takeIf { state.retryable },
                        onAction = onRetry.takeIf { state.retryable },
                    )
                }
                is UiState.Success -> {
                    // The Apple-style release row now leads Explore; keep it out of Home.
                    // Recents' skeleton is drawn inside the list, in the slot Recents
                    // will take, rather than stacked above rows that then swap places.
                    itemsIndexedShelves(
                        shelves = visibleShelves ?: state.data,
                        onMoodClick = onMoodClick,
                        onMoodsShowAll = onMoodsShowAll,
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        firstIsHero = leadHero,
                        recentsPending = recentlyPlayedLoading,
                        onPlaySongs = onPlaySongs,
                        homeLayout = homeLayout,
                        onListenTogetherClick = onListenTogetherClick,
                        podcastShows = homePodcastShows,
                        onPodcastsClick = onPodcastsClick,
                        revealSeenState = revealSeenState,
                        revealEnabled = revealEnabled,
                        topPicksPending = onTopPickClick != null && topPicksWaiting,
                        topPicksItem = if (onTopPickClick != null && !topPicks.isNullOrEmpty()) {
                            {
                                TopPicksShelf(
                                    picks = topPicks,
                                    listenerName = listenerName,
                                    onClick = onTopPickClick,
                                    onLongPress = onTopPickLongPress,
                                )
                            }
                        } else {
                            null
                        },
                    )
                    if (loadingMore) feedMoreSkeleton()
                }
            }
        }
        }
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
}

/**
 * The shelves Home draws from a loaded feed, in the order it draws them when the
 * listener has not arranged anything. [dropEditorial] is true wherever Top Picks
 * is on, since the release row then leads Explore instead.
 */
fun homeFeedShelves(shelves: List<HomeShelf>, dropEditorial: Boolean = true): List<HomeShelf> {
    val editorial = if (dropEditorial) editorialShelfIndex(shelves) else null
    val kept = if (editorial != null) shelves.filterIndexed { index, _ -> index != editorial } else shelves
    val quickPicks = kept.indexOfFirst { it.title.equals("Quick picks", ignoreCase = true) }
    val albums = kept.indexOfFirst { it.title.contains("Albums", ignoreCase = true) }
    val ordered = if (quickPicks >= 0 && albums >= 0 && albums < quickPicks) {
        kept.toMutableList().apply { add(quickPicks, removeAt(albums)) }
    } else kept
    return ordered.withoutSpeedDial().withQuickPicksFirst()
}

/**
 * Every slot Home has right now, as layout keys, in the order Home draws them
 * without a saved layout: Top Picks first, Find Your Mood after Recents.
 */
fun homeDefaultSlotKeys(
    shelves: List<HomeShelf>,
    withTopPicks: Boolean,
    withMoods: Boolean,
    dropEditorial: Boolean = withTopPicks,
): List<String> {
    val ordered = homeFeedShelves(shelves, dropEditorial = dropEditorial)
    val moodAt = ordered.indexOfFirst { it.title.equals("Quick picks", ignoreCase = true) }
        .takeIf { it >= 0 }?.plus(1) ?: ordered.size
    return buildList {
        if (withTopPicks) add(HOME_SLOT_TOP_PICKS)
        ordered.forEachIndexed { index, shelf ->
            if (withMoods && index == moodAt) add(HOME_SLOT_MOODS)
            add(homeShelfKey(shelf.title))
        }
        if (withMoods && moodAt >= ordered.size) add(HOME_SLOT_MOODS)
    }.distinct()
}

/** A shelf's title the way Home shows it, for the layout dialog. */
@Composable
fun homeShelfLabel(title: String): String = localizeShelfTitle(title.trim().replace('\n', ' '))

/**
 * Recents mirrors the artist page's top-tracks pager. Any other lead shelf keeps
 * Apple's full-bleed treatment, while the remaining shelves use square cards.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedShelves(
    shelves: List<HomeShelf>,
    onItemClick: (ShelfItem, String) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
    firstIsHero: Boolean = true,
    onMoodClick: ((TopPickMood, String) -> Unit)? = null,
    onMoodsShowAll: () -> Unit = {},
    onPlaySongs: ((List<Song>) -> Unit)? = null,
    homeLayout: HomeLayout = HomeLayout(),
    /** The Top Picks row, when there is one to draw. Only placed by a custom [homeLayout]. */
    topPicksItem: (@Composable () -> Unit)? = null,
    /** The row is still being built: a custom [homeLayout] holds its slot with a skeleton. */
    topPicksPending: Boolean = false,
    /** Recents is still loading: a skeleton holds the slot it will land in. */
    recentsPending: Boolean = false,
    onListenTogetherClick: (() -> Unit)? = null,
    podcastShows: List<PodcastProviderShow> = emptyList(),
    onPodcastsClick: ((PodcastProviderShow?) -> Unit)? = null,
    revealSeenState: RevealSeenState,
    revealEnabled: Boolean,
) {
    // Recents swaps places with Listen again once it arrives, so its slot is
    // reserved from the start and the rows beneath are already where they will stay.
    val withRecentsSlot = if (
        recentsPending && shelves.none { it.title.trim().equals(RECENTS_TITLE, ignoreCase = true) }
    ) {
        listOf(HomeShelf(title = RECENTS_TITLE, items = emptyList())) + shelves
    } else {
        shelves
    }
    val quickPicksIndex = withRecentsSlot.indexOfFirst { it.title.equals("Quick picks", ignoreCase = true) }
    val albumsIndex = withRecentsSlot.indexOfFirst { it.title.contains("Albums", ignoreCase = true) }
    val ordered = if (!homeLayout.isCustom && quickPicksIndex >= 0 && albumsIndex >= 0 && albumsIndex < quickPicksIndex) {
        withRecentsSlot.toMutableList().apply { add(quickPicksIndex, removeAt(albumsIndex)) }.withoutSpeedDial()
    } else {
        withRecentsSlot.withoutSpeedDial()
    }.let { if (homeLayout.isCustom) it else it.withQuickPicksFirst() }
    // 1.9.3 places Find Your Mood immediately after Quick Picks.
    val moodAt = ordered.indexOfFirst { it.title.equals("Quick picks", ignoreCase = true) }
        .takeIf { it >= 0 }?.plus(1) ?: ordered.size
    fun shelfItem(index: Int, shelf: HomeShelf) {
        if (recentsPending && shelf.items.isEmpty() && shelf.title == RECENTS_TITLE) {
            recentlyPlayedSkeleton(key = homeShelfKey(shelf.title))
            return
        }
        // Shelf identity must not depend on its current position. Recents can
        // arrive after the core feed and be prepended; index-based keys caused
        // every existing shelf below it to be disposed and recreated.
        val stableShelfKey = homeShelfKey(shelf.title)
        item(key = stableShelfKey, contentType = shelf.layout) {
            Column(
                modifier = Modifier.fillMaxWidth().revealOnAppear(
                    key = "home:$stableShelfKey",
                    seenState = revealSeenState,
                    enabled = revealEnabled,
                ),
            ) {
            val openItem: (ShelfItem) -> Unit = { item -> onItemClick(item, shelf.title) }
            val isListenAgain = LISTEN_AGAIN_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) }
            val isQuickPicks = QUICK_PICKS_TITLES.any { shelf.title.trim().equals(it, ignoreCase = true) }
            // Quick Picks has its own compact three-row horizontal track shelf
            // on Home. The Library keeps its independent shelf presentation.
            if (isQuickPicks) {
                QuickPicksShelf(
                    shelf = shelf.copy(
                        title = localizeShelfTitle(shelf.title),
                        subtitle = localizeShelfSubtitle(shelf.subtitle),
                    ),
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                )
            } else if (shelf.isMixShelf()) {
                MixCoverShelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                )
            } else if (onPlaySongs != null && shelf.isPlaylistPreviewShelf()) {
                PlaylistPreviewShelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    onPlaySongs = onPlaySongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                )
            } else if (isListenAgain || (
                    !isQuickPicks && (
                        shelf.layout == ShelfLayout.LIST ||
                            shelf.title.equals(RECENTS_TITLE, ignoreCase = true)
                        )
                    )
            ) {
                RecentShelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                )
            } else if (index == 0 && firstIsHero && shelf.layout == ShelfLayout.CARDS) {
                HeroShelf(
                    shelf = shelf,
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                )
            } else {
                Shelf(
                    shelf = if (isQuickPicks || isListenAgain) {
                        shelf.copy(
                            title = localizeShelfTitle(shelf.title),
                            subtitle = localizeShelfSubtitle(shelf.subtitle),
                        )
                    } else {
                        shelf
                    },
                    onItemClick = openItem,
                    onItemLongPress = onItemLongPress,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    homeStyle = true,
                )
            }
            }
        }
        if (shelf.title.equals("Quick picks", ignoreCase = true)) {
            item(key = "home-listen-together") {
                Box(Modifier.fillMaxWidth().revealOnAppear("home:listen-together", revealSeenState, revealEnabled)) {
                    ListenTogetherShelf(shelf.items.take(2).map { it.thumbnailUrl }, onListenTogetherClick)
                }
            }
            if (onPodcastsClick != null) {
                item(key = "home-podcasts") {
                    Box(Modifier.fillMaxWidth().revealOnAppear("home:podcasts", revealSeenState, revealEnabled)) {
                        HomePodcastsShelf(podcastShows, onPodcastsClick)
                    }
                }
            }
        }
    }

    if (homeLayout.isCustom) {
        // The listener's own order: Top Picks, Find Your Mood and every shelf are
        // slots on one list. What the layout has not seen yet goes last.
        val entries = buildList<Pair<String, HomeShelf?>> {
            if (topPicksItem != null || topPicksPending) add(HOME_SLOT_TOP_PICKS to null)
            if (onMoodClick != null) add(HOME_SLOT_MOODS to null)
            ordered.forEach { add(homeShelfKey(it.title) to it) }
        }
        var shelfIndex = 0
        homeLayout.arrange(entries) { it.first }.forEach { (key, shelf) ->
            when {
                shelf != null -> shelfItem(shelfIndex++, shelf)
                key == HOME_SLOT_TOP_PICKS -> if (topPicksItem != null) {
                    item(key = "top-picks") {
                        Box(Modifier.fillMaxWidth().revealOnAppear("home:top-picks", revealSeenState, revealEnabled)) {
                            topPicksItem()
                        }
                    }
                } else {
                    topPicksSkeleton()
                }
                else -> item(key = "find-your-mood") {
                    Box(Modifier.fillMaxWidth().revealOnAppear("home:find-your-mood", revealSeenState, revealEnabled)) {
                        if (onMoodClick != null) FindYourMoodShelf(onMoodClick, onMoodsShowAll)
                    }
                }
            }
        }
        return
    }

    ordered.forEachIndexed { index, shelf ->
        if (index == moodAt && onMoodClick != null) {
            item(key = "find-your-mood") {
                Box(Modifier.fillMaxWidth().revealOnAppear("home:find-your-mood", revealSeenState, revealEnabled)) {
                    FindYourMoodShelf(onMoodClick, onMoodsShowAll)
                }
            }
        }
        shelfItem(index, shelf)
    }
    if (moodAt >= ordered.size && onMoodClick != null) {
        item(key = "find-your-mood") {
            Box(Modifier.fillMaxWidth().revealOnAppear("home:find-your-mood", revealSeenState, revealEnabled)) {
                FindYourMoodShelf(onMoodClick, onMoodsShowAll)
            }
        }
    }
    if (ordered.none { it.title.equals("Quick picks", ignoreCase = true) } && onPodcastsClick != null) {
        item(key = "home-listen-together-fallback") {
            Box(Modifier.fillMaxWidth().revealOnAppear("home:listen-together", revealSeenState, revealEnabled)) {
                ListenTogetherShelf(emptyList(), onListenTogetherClick)
            }
        }
        item(key = "home-podcasts") {
            Box(Modifier.fillMaxWidth().revealOnAppear("home:podcasts", revealSeenState, revealEnabled)) {
                HomePodcastsShelf(podcastShows, onPodcastsClick)
            }
        }
    }
}

@Composable
private fun ListenTogetherShelf(artworks: List<String?>, onClick: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        HomeSectionHeader("Listen together")
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(listOf("Start a session", "Join a session").withIndex().toList(), key = { it.value }) { (index, label) ->
                Box(
                    modifier = Modifier.width(300.dp).height(142.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = onClick != null) { onClick?.invoke() },
                ) {
                    val artwork = artworks.getOrNull(index)
                    if (!artwork.isNullOrBlank()) {
                        AsyncImage(model = artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.scrim.copy(alpha = .86f), MaterialTheme.colorScheme.scrim.copy(alpha = .28f))),
                        ),
                    )
                    Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                        Text("Listen together", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .82f))
                        Text(label, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePodcastsShelf(
    shows: List<PodcastProviderShow>,
    onOpen: (PodcastProviderShow?) -> Unit,
) {
    Column(Modifier.padding(bottom = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Podcasts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text(
                "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpen(null) },
            )
        }
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (shows.isEmpty()) {
                item(key = "home-podcasts-discover") {
                    Column(
                        modifier = Modifier.width(SHELF_CARD_WIDTH).clickable { onOpen(null) },
                    ) {
                        Box(
                            Modifier.size(SHELF_CARD_WIDTH)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.LibraryMusic,
                                contentDescription = "Podcasts",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                        Text("Discover podcasts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        Text("Browse shows and episodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(shows, key = { "home-podcast:${it.browseId}" }) { show ->
                Column(Modifier.width(SHELF_CARD_WIDTH)) {
                    Box(
                        Modifier.size(SHELF_CARD_WIDTH)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onOpen(show) },
                    ) {
                        if (!show.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = show.thumbnailUrl,
                                contentDescription = show.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.Center).size(42.dp),
                            )
                        }
                    }
                    Text(show.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                    if (show.subtitle.isNotBlank()) {
                        Text(show.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The same four-rows-per-page treatment used by an artist's Top songs. */
@Composable
internal fun RecentShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        RecentSectionHeader(title = localizeShelfTitle(shelf.title), subtitle = localizeShelfSubtitle(shelf.subtitle))
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        BoxWithConstraints {
            val columnWidth = trackColumnWidth(maxWidth)
            val recentColumns = remember(shelf.items) { shelf.items.chunked(RECENT_TRACKS_PER_COLUMN) }
            ShelfRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recentColumns, contentType = { "recent-column" }) { column ->
                    Column(Modifier.width(columnWidth)) {
                        column.forEachIndexed { index, item ->
                            RecentTrackRow(
                                item = item,
                                onClick = { onItemClick(item) },
                                onLongPress = onItemLongPress?.let { { it(item) } },
                                isCurrent = isCurrentItem(item),
                                isPlaying = isCurrentItem(item) && isPlaying,
                            )
                            if (index < column.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 60.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
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
        isVideo = item.isVideo,
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
    /** A music video: its thumbnail at 16:9 rather than squared off. */
    isVideo: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(48.dp, isVideo)) {
            AsyncImage(
                model = if (isVideo) thumbnailUrl else thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = if (isVideo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(7.dp))
                    .thumbnailBorder(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
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
private fun HomeTitle(title: String, hideBranding: Boolean, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.displayLarge
    val logoHeight = with(LocalDensity.current) { (style.fontSize * LOGO_TO_FONT).toDp() }
    Column(modifier = modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp)) {
        if (hideBranding) {
            Text(
                text = title,
                style = style,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Wordmark(
                logoHeight = logoHeight,
                fontSize = style.fontSize * AUDIFY_TO_FONT,
                color = MaterialTheme.colorScheme.onBackground,
                gap = 2.dp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        )
        // "Listen now" is hidden for now; the wordmark above stays.
        // Text(
        //     text = title,
        //     style = style,
        //     color = MaterialTheme.colorScheme.onBackground,
        // )
    }
}

/** Compact Home header: brand mark, time-aware greeting, and working page actions. */
@Composable
private fun HomeHeader(
    title: String,
    account: Account?,
    onSearchClick: (() -> Unit)?,
    onCustomizeClick: (() -> Unit)?,
    onAccountClick: (() -> Unit)?,
) {
    val hour = java.time.LocalTime.now().hour
    val profileThumbnail = account?.thumbnailUrl
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep the three actions inside the row on narrow phones instead of
        // letting their fixed touch targets crowd the account label.
        val compactHeader = maxWidth < 360.dp
        val actionSize = if (compactHeader) 38.dp else 42.dp
        val actionGap = if (compactHeader) 2.dp else 4.dp
        // Scale the brand mark with the available content width, but keep it
        // within a legible range on compact phones and wide layouts.
        val logoWidth = (maxWidth * 0.11f).coerceIn(if (compactHeader) 30.dp else 34.dp, 50.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PAGE_GUTTER, end = 8.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(actionGap),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = "Audify",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(logoWidth).height(logoWidth / LOGO_ASPECT),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = account?.name?.takeIf { it.isNotBlank() } ?: title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onCustomizeClick != null) {
                HomeHeaderAction(Icons.Rounded.Tune, "Arrange Home", onCustomizeClick, actionSize)
            }
            HomeHeaderAction(Icons.Rounded.Search, "Search", onSearchClick, actionSize)
            Box(
                modifier = Modifier
                    .size(actionSize)
                    .clip(CircleShape)
                    .clickable(enabled = onAccountClick != null, onClick = { onAccountClick?.invoke() }),
                contentAlignment = Alignment.Center,
            ) {
                if (!profileThumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = profileThumbnail,
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderAction(icon: ImageVector, description: String, onClick: (() -> Unit)?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** The wordmark's height against the heading's font size. */
private const val LOGO_TO_FONT = 0.70f

/** The Audify wordmark's size against the heading's font size. */
private const val AUDIFY_TO_FONT = 0.88f

/** ic_logo's viewport, 730 x 484. */
private const val LOGO_ASPECT = 730f / 484f

/**
 * Shared by the home feed, Explore and Library so headings line up across tabs.
 *
 * [onShowAll] is only ever set on Library, whose rows stop at five cards
 * rather than running the shelf's whole length — see [LibraryGridShelf].
 * Home and Explore never pass it, so their heading is unchanged.
 */
@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String = "",
    onShowAll: (() -> Unit)? = null,
    actionLabel: String? = null,
    emphasized: Boolean = false,
) {
    val displayTitle = localizeShelfTitle(title)
    val displaySubtitle = localizeShelfSubtitle(subtitle)
    Row(
        modifier = Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = if (emphasized) 8.dp else 10.dp)
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
@Composable
internal fun SectionHeader(title: String, subtitle: String = "", onShowAll: (() -> Unit)? = null) {
    val displayTitle = localizeShelfTitle(title)
    val displaySubtitle = localizeShelfSubtitle(subtitle)
    Row(
        modifier = Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (displaySubtitle.isNotBlank()) {
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onShowAll != null) {
            Text(
                text = stringResource(Res.string.show_all),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
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
            trimmed.equals("Terakhir diputar", ignoreCase = true) ||
            trimmed.equals("Diputar baru-baru ini", ignoreCase = true) ||
            trimmed.equals("最近", ignoreCase = true) ->
            stringResource(Res.string.shelf_recents)
        trimmed.equals("Playlists", ignoreCase = true) ||
            trimmed.equals("Danh sách phát", ignoreCase = true) ||
            trimmed.equals("Playlist", ignoreCase = true) ||
            trimmed.equals("Daftar putar", ignoreCase = true) ||
            trimmed.equals("再生リスト", ignoreCase = true) ->
            stringResource(Res.string.playlists)
        trimmed.equals("Albums", ignoreCase = true) ||
            trimmed.equals("Album", ignoreCase = true) ||
            trimmed.equals("アルバム", ignoreCase = true) ->
            stringResource(Res.string.albums)
        trimmed.equals("Artists", ignoreCase = true) ||
            trimmed.equals("Nghệ sĩ", ignoreCase = true) ||
            trimmed.equals("Artis", ignoreCase = true) ||
            trimmed.equals("アーティスト", ignoreCase = true) ->
            stringResource(Res.string.artists)
        trimmed.equals("Subscriptions", ignoreCase = true) ||
            trimmed.equals("Đã đăng ký", ignoreCase = true) ||
            trimmed.equals("Langganan", ignoreCase = true) ||
            trimmed.equals("登録チャンネル", ignoreCase = true) ->
            stringResource(Res.string.subscriptions)
        trimmed.equals("Trending community playlists", ignoreCase = true) ||
            trimmed.equals("Danh sách phát cộng đồng thịnh hành", ignoreCase = true) ||
            trimmed.equals("Playlist komunitas yang sedang tren", ignoreCase = true) ||
            trimmed.equals("Playlist komunitas populer", ignoreCase = true) ||
            trimmed.equals("Danh sách phát thịnh hành trong cộng đồng người dùng", ignoreCase = true) ||
            trimmed.equals("急上昇のコミュニティ再生リスト", ignoreCase = true) ->
            stringResource(Res.string.shelf_trending_community_playlists)
        trimmed.equals("Featured playlists for you", ignoreCase = true) ||
            trimmed.equals("Danh sách phát đề xuất cho bạn", ignoreCase = true) ||
            trimmed.equals("Playlist pilihan untuk Anda", ignoreCase = true) ||
            trimmed.equals("Playlist unggulan untuk Anda", ignoreCase = true) ||
            trimmed.equals("Danh sách phát nổi bật dành cho bạn", ignoreCase = true) ||
            trimmed.equals("おすすめの再生リスト", ignoreCase = true) ->
            stringResource(Res.string.shelf_featured_playlists_for_you)
        SPEED_DIAL_TITLES.any { trimmed.equals(it, ignoreCase = true) } ->
            stringResource(Res.string.shelf_speed_dial)
        trimmed.equals("Quick picks", ignoreCase = true) ||
            trimmed.equals("Lựa chọn nhanh", ignoreCase = true) ||
            trimmed.equals("Pilihan cepat", ignoreCase = true) ||
            trimmed.equals("Chọn nhanh đài phát", ignoreCase = true) ||
            trimmed.equals("クイック ミックス", ignoreCase = true) ->
            stringResource(Res.string.shelf_quick_picks)
        trimmed.equals("Listen again", ignoreCase = true) ||
            trimmed.equals("Nghe lại", ignoreCase = true) ||
            trimmed.equals("Dengarkan lagi", ignoreCase = true) ||
            trimmed.equals("もう一度聴く", ignoreCase = true) ->
            stringResource(Res.string.shelf_listen_again)
        trimmed.equals("Mixed for you", ignoreCase = true) ||
            trimmed.equals("Dành riêng cho bạn", ignoreCase = true) ||
            trimmed.equals("Mix untuk Anda", ignoreCase = true) ||
            trimmed.equals("Campuran untuk Anda", ignoreCase = true) ||
            trimmed.equals("ミックス", ignoreCase = true) ->
            stringResource(Res.string.shelf_mixed_for_you)
        trimmed.startsWith("Similar to", ignoreCase = true) -> {
            val rest = trimmed.substring(10).trim()
            stringResource(Res.string.shelf_similar_to, rest)
        }
        trimmed.startsWith("Mirip dengan", ignoreCase = true) -> {
            val rest = trimmed.substring(12).trim()
            stringResource(Res.string.shelf_similar_to, rest)
        }
        trimmed.startsWith("Serupa dengan", ignoreCase = true) -> {
            val rest = trimmed.substring(13).trim()
            stringResource(Res.string.shelf_similar_to, rest)
        }
        trimmed.startsWith("Tương tự như", ignoreCase = true) -> {
            val rest = trimmed.substring(12).trim()
            stringResource(Res.string.shelf_similar_to, rest)
        }
        trimmed.equals("Forgotten favorites", ignoreCase = true) ||
            trimmed.equals("Giai điệu quen thuộc", ignoreCase = true) ||
            trimmed.equals("Favorit terlupakan", ignoreCase = true) ||
            trimmed.equals("Favorit yang terlupakan", ignoreCase = true) ||
            trimmed.equals("よく聴いたお気に入りの曲", ignoreCase = true) ->
            stringResource(Res.string.shelf_forgotten_favorites)
        trimmed.equals("Recommended music videos", ignoreCase = true) ||
            trimmed.equals("Video âm nhạc đề xuất", ignoreCase = true) ||
            trimmed.equals("Video musik yang direkomendasikan", ignoreCase = true) ||
            trimmed.equals("Video musik rekomendasi", ignoreCase = true) ||
            trimmed.equals("おすすめのミュージック ビデオ", ignoreCase = true) ->
            stringResource(Res.string.shelf_recommended_music_videos)
        trimmed.equals("From your library", ignoreCase = true) ||
            trimmed.equals("Từ thư viện của bạn", ignoreCase = true) ||
            trimmed.equals("Dari perpustakaan Anda", ignoreCase = true) ||
            trimmed.equals("Dari koleksi Anda", ignoreCase = true) ||
            trimmed.equals("Dari pustaka Anda", ignoreCase = true) ||
            trimmed.equals("ライブラリから", ignoreCase = true) ->
            stringResource(Res.string.shelf_from_your_library)
        trimmed.equals("Charts", ignoreCase = true) ||
            trimmed.equals("Bảng xếp hạng", ignoreCase = true) ||
            trimmed.equals("Tangga lagu", ignoreCase = true) ||
            trimmed.equals("Chart", ignoreCase = true) ||
            trimmed.equals("チャート", ignoreCase = true) ->
            stringResource(Res.string.shelf_charts)
        trimmed.equals("New releases", ignoreCase = true) ||
            trimmed.equals("Bản phát hành mới", ignoreCase = true) ||
            trimmed.equals("Rilis baru", ignoreCase = true) ||
            trimmed.equals("Rilisan baru", ignoreCase = true) ||
            trimmed.equals("最新リリース", ignoreCase = true) ->
            stringResource(Res.string.shelf_new_releases)
        trimmed.equals("Top music videos", ignoreCase = true) ||
            trimmed.equals("Video âm nhạc hàng đầu", ignoreCase = true) ||
            trimmed.equals("Video musik teratas", ignoreCase = true) ||
            trimmed.equals("Video musik terpopuler", ignoreCase = true) ||
            trimmed.equals("人気のミュージック ビデオ", ignoreCase = true) ->
            stringResource(Res.string.shelf_top_music_videos)
        trimmed.equals("For you", ignoreCase = true) ||
            trimmed.equals("Dành cho bạn", ignoreCase = true) ||
            trimmed.equals("Untuk Anda", ignoreCase = true) ||
            trimmed.equals("Untukmu", ignoreCase = true) ||
            trimmed.equals("あなたへのおすすめ", ignoreCase = true) ->
            stringResource(Res.string.shelf_for_you)
        trimmed.equals("Hits today", ignoreCase = true) ||
            trimmed.equals("Today's Hits", ignoreCase = true) ||
            trimmed.equals("Bản hit hôm nay", ignoreCase = true) ||
            trimmed.equals("Hits hari ini", ignoreCase = true) ||
            trimmed.equals("Hit hari ini", ignoreCase = true) ||
            trimmed.equals("今日のヒット曲", ignoreCase = true) ->
            stringResource(Res.string.shelf_hits_today)
        trimmed.equals("Artists on the rise", ignoreCase = true) ||
            trimmed.equals("Nghệ sĩ đang lên", ignoreCase = true) ||
            trimmed.equals("Artis yang sedang naik daun", ignoreCase = true) ||
            trimmed.equals("Artis yang sedang naik", ignoreCase = true) ->
            stringResource(Res.string.shelf_artists_on_the_rise)
        trimmed.equals("Concerts", ignoreCase = true) ||
            trimmed.equals("Buổi hòa nhạc", ignoreCase = true) ||
            trimmed.equals("Konser", ignoreCase = true) ||
            trimmed.equals("コンサート", ignoreCase = true) ->
            stringResource(Res.string.shelf_concerts)
        trimmed.startsWith("Shorts", ignoreCase = true) ||
            trimmed.equals("Shorts nổi bật", ignoreCase = true) ||
            trimmed.equals("ショート", ignoreCase = true) ->
            stringResource(Res.string.shelf_shorts)
        trimmed.equals("Trending", ignoreCase = true) ||
            trimmed.equals("Thịnh hành", ignoreCase = true) ||
            trimmed.equals("Sedang tren", ignoreCase = true) ||
            trimmed.equals("Tren", ignoreCase = true) ||
            trimmed.equals("急上昇", ignoreCase = true) ->
            stringResource(Res.string.shelf_trending)
        trimmed.equals("New albums & singles", ignoreCase = true) ||
            trimmed.equals("New albums and singles", ignoreCase = true) ||
            trimmed.equals("Album & single baru", ignoreCase = true) ||
            trimmed.equals("Album & single terbaru", ignoreCase = true) ||
            trimmed.equals("Album dan single terbaru", ignoreCase = true) ->
            stringResource(Res.string.shelf_new_albums_singles)
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
            trimmed.equals("Lagu teratas saat ini", ignoreCase = true) ||
            trimmed.equals("注目の曲", ignoreCase = true) ->
            stringResource(Res.string.shelf_top_tunes_right_now)
        trimmed.equals("From the community", ignoreCase = true) ||
            trimmed.equals("Từ cộng đồng", ignoreCase = true) ||
            trimmed.equals("Dari komunitas", ignoreCase = true) ||
            trimmed.equals("コミュニティより", ignoreCase = true) ->
            stringResource(Res.string.shelf_from_the_community)
        trimmed.equals("YouTube Charts", ignoreCase = true) ||
            trimmed.equals("Bảng xếp hạng YouTube", ignoreCase = true) ||
            trimmed.equals("Tangga Lagu YouTube", ignoreCase = true) ||
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
                trimmed.equals("Bài hát", ignoreCase = true) || trimmed.equals("曲", ignoreCase = true) ||
                trimmed.equals("Lagu", ignoreCase = true) -> songLabel
            trimmed.equals("Single", ignoreCase = true) || trimmed.equals("Đĩa đơn", ignoreCase = true) ||
                trimmed.equals("シングル", ignoreCase = true) -> singleLabel
            trimmed.equals("Chart", ignoreCase = true) || trimmed.equals("Bảng xếp hạng", ignoreCase = true) ||
                trimmed.equals("チャート", ignoreCase = true) || trimmed.equals("Tangga lagu", ignoreCase = true) -> chartLabel
            trimmed.equals("Playlist", ignoreCase = true) || trimmed.equals("Danh sách phát", ignoreCase = true) ||
                trimmed.equals("再生リスト", ignoreCase = true) -> playlistLabel
            lower.endsWith(" views") || lower.endsWith(" view") || lower.endsWith(" lượt xem") ||
                lower.endsWith(" 回視聴") || lower.endsWith("回視聴") || lower.endsWith(" tayangan") ||
                lower.endsWith(" penayangan") -> {
                val count = trimmed.substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_views_format, count) else part
            }
            lower.endsWith(" plays") || lower.endsWith(" play") || lower.endsWith(" lượt phát") ||
                lower.endsWith(" 回再生") || lower.endsWith("回再生") || lower.endsWith(" pemutaran") ||
                lower.endsWith(" diputar") -> {
                val count = trimmed.substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_plays_format, count) else part
            }
            lower.endsWith(" songs") || lower.endsWith(" song") || lower.endsWith(" bài hát") ||
                lower.endsWith(" 曲") || lower.endsWith("曲") || lower.endsWith(" lagu") -> {
                val count = trimmed.substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_songs_format, count) else part
            }
            lower.endsWith(" monthly listeners") || lower.endsWith(" audiens bulanan") ||
                lower.endsWith(" pendengar bulanan") -> {
                val count = trimmed.substringBeforeLast(' ', "").substringBeforeLast(' ', "").trim()
                if (count.any { it.isDigit() }) stringResource(Res.string.card_monthly_listeners_format, count) else part
            }
            else -> part
        }
    }
    return localizedParts.joinToString(delimiter)
}

/**
 * Kunci stabil untuk tiap kartu rak: videoId, lalu browseId, lalu judul.
 * Kembaran dalam satu rak diberi akhiran, karena LazyList menolak kunci ganda.
 */
private fun shelfItemKeys(items: List<ShelfItem>): List<String> {
    val seen = HashMap<String, Int>(items.size)
    return items.map { item ->
        val base = "i:" + (item.videoId ?: item.browseId ?: "${item.title}|${item.subtitle}")
        val n = seen.merge(base, 1, Int::plus)!!
        if (n == 1) base else "$base#$n"
    }
}

@Composable
private fun HeroShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        HomeSectionHeader(shelf.title, shelf.subtitle, emphasized = true)
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        val itemKeys = remember(shelf.items) { shelfItemKeys(shelf.items) }
        // Measured rather than taken as a share of the parent, because the card
        // has a ceiling as well as a fraction — see [heroCardWidth]. A fixed
        // width is also the only one of the two the aspect ratio below can turn
        // into a height, so the card keeps its shape however it was arrived at.
        BoxWithConstraints {
            val cardWidth = heroCardWidth(maxWidth)
            ShelfRow(
                state = rememberShelfListState("hero:${homeShelfKey(shelf.title)}"),
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(
                    shelf.items,
                    key = { index, _ -> itemKeys[index] },
                    contentType = { _, _ -> "hero-card" },
                ) { index, item ->
                    HeroCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongPress = onItemLongPress?.let { { it(item) } },
                        modifier = Modifier.shelfStagger(index).width(cardWidth),
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
    val heroToken = remember { Any() }
    val openHeroWithCover = rememberCoverFlightTap(item.browseId, heroToken, onClick)
    Box(
        modifier = modifier
            .aspectRatio(HERO_CARD_RATIO)
            .clip(RoundedCornerShape(18.dp))
            .thumbnailBorder(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(
                onClick = openHeroWithCover,
                onLongClick = onLongPress,
            )
            .contextClick(onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(HEADER_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .coverFlightSource(heroToken, item.thumbnailUrl.artworkAt(HEADER_ART_PX), 18.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp)),
        )
        if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier
                .hideDuringCoverFlight(heroToken)
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f)),
                    ),
                )
                .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 14.dp),
        ) {
                Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                val displaySubtitle = localizeCardSubtitle(item.subtitle)
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
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
    leadingCard: (@Composable () -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    homeStyle: Boolean = false,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        if (homeStyle) HomeSectionHeader(shelf.title, shelf.subtitle) else SectionHeader(shelf.title, shelf.subtitle)
        val isCurrentItem = shelf.items.playingItemMatcher(currentSong)
        val itemKeys = remember(shelf.items) { shelfItemKeys(shelf.items) }
        ShelfRow(
            state = rememberShelfListState(homeShelfKey(shelf.title)),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leadingCard?.let { card -> item(key = "leading") { card() } }
            itemsIndexed(
                shelf.items,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, _ -> "shelf-card" },
            ) { index, item ->
                Box(Modifier.shelfStagger(index)) {
                    // Mixed shelves (notably Trending) can contain both
                    // catalogue artwork and music-video uploads. Choose the
                    // renderer per item so a video never falls through the
                    // square, centre-cropped album-art card just because most
                    // of its neighbours are ordinary cards.
                    if (shelf.layout == ShelfLayout.VIDEO || item.isVideo) {
                        VideoShelfCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = onItemLongPress?.let { { it(item) } },
                            isCurrent = isCurrentItem(item),
                            isPlaying = isCurrentItem(item) && isPlaying,
                        )
                    } else {
                        ShelfCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = onItemLongPress?.let { { it(item) } },
                            isCurrent = isCurrentItem(item),
                            isPlaying = isCurrentItem(item) && isPlaying,
                            homeStyle = homeStyle,
                        )
                    }
                }
            }
        }
    }
}

/** Music-video cards follow the landscape thumbnail format used by the feed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoShelfCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier.width(VIDEO_CARD_WIDTH),
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .thumbnailBorder(RoundedCornerShape(12.dp))
                .background(placeholderColor),
        ) {
            AsyncImage(
                // Video stills are not resizable googleusercontent art; the
                // largest the row listed is already about card size.
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = localizeCardSubtitle(item.subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Music videos use landscape thumbnails by default; the explicit square preference remains respected. */
val VIDEO_ASPECT: Float get() = if (VideoThumbnails.square) 1f else 16f / 9f

/** Keep video and square cards on the same shelf width; the image keeps its own ratio. */
val VIDEO_CARD_WIDTH: Dp get() = SHELF_CARD_WIDTH

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
        modifier = modifier.clickable(onClick = onClick),
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
private fun ServiceCard(
    colors: List<Color>,
    trackKey: String,
    icon: ImageVector,
    iconTint: Color = Color.White,
    /** How much of the card's own colours is laid back over the mesh on a light theme. */
    lightLift: Float = 0f,
) {
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
            // Home may show several of these tiles at once. Let each mesh
            // settle after its entrance instead of keeping multiple blurred
            // canvases animating at display refresh rate while the feed rests.
            continuous = false,
            blurRadius = 24.dp,
        )
        // Light theme only: the mesh clamps every colour it is given into a
        // dark band, which on a white page reads heavy. Laying the card's own
        // colours back over it restores the tile's intended brightness.
        if (lightLift > 0f && MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(colors.map { it.copy(alpha = lightLift) })),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
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
    homeStyle: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val coverToken = remember { Any() }
    val openWithCover = rememberCoverFlightTap(item.browseId, coverToken, onClick)
    Column(
        modifier = modifier
            .longPressMenuClickable(
                onClick = openWithCover,
                onLongClick = onLongPress,
            )
            .contextClick(onLongPress),
    ) {
        when (item.browseId) {
            "local:downloads" -> {
                val palette = remember(homeStyle, scheme.primary, scheme.tertiary) {
                    MeshPalette(if (homeStyle) listOf(scheme.primary, scheme.tertiary) else listOf(Color(0xFF1E3C72), Color(0xFF2A5298)))
                }
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
                        continuous = false,
                        blurRadius = 24.dp,
                    )
                    Icon(
                        imageVector = AudifyIcons.Download,
                        contentDescription = null,
                        tint = if (homeStyle) scheme.onPrimary else Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            "local:all" -> ServiceCard(
                colors = if (homeStyle) listOf(scheme.secondary, scheme.tertiary) else listOf(Color(0xFF134E5E), Color(0xFF71B280)),
                trackKey = "local:all",
                icon = Icons.Rounded.LibraryMusic,
                iconTint = if (homeStyle) scheme.onSecondary else Color.White,
            )
            YtMusicRepository.LIKED_MUSIC -> ServiceCard(
                colors = if (homeStyle) listOf(scheme.primary, scheme.tertiary) else listOf(Color(0xFFFF7AB8), Color(0xFF9B6CFF)),
                trackKey = YtMusicRepository.LIKED_MUSIC,
                icon = Icons.Rounded.Favorite,
                lightLift = 0.6f,
                iconTint = if (homeStyle) scheme.onPrimary else Color.White,
            )
            "local:cache" -> ServiceCard(
                colors = if (homeStyle) listOf(scheme.primaryContainer, scheme.secondaryContainer) else listOf(Color(0xFF42275A), Color(0xFF734B6D)),
                trackKey = "local:cache",
                icon = Icons.Rounded.Cached,
                iconTint = if (homeStyle) scheme.onPrimaryContainer else Color.White,
            )
            "local:webdav" -> ServiceCard(
                colors = if (homeStyle) listOf(scheme.tertiaryContainer, scheme.primaryContainer) else listOf(Color(0xFF3A1C71), Color(0xFFD76D77)),
                trackKey = "local:webdav",
                icon = Icons.Rounded.Folder,
                iconTint = if (homeStyle) scheme.onTertiaryContainer else Color.White,
            )
            "local:smb" -> ServiceCard(
                colors = if (homeStyle) listOf(scheme.secondaryContainer, scheme.surfaceVariant) else listOf(Color(0xFF0F2027), Color(0xFF2C5364)),
                trackKey = "local:smb",
                icon = Icons.Rounded.Storage,
                iconTint = if (homeStyle) scheme.onSecondaryContainer else Color.White,
            )
            else -> {
                Box(
                    modifier = Modifier
                        .coverFlightSource(coverToken, item.thumbnailUrl.artworkAt(CARD_ART_PX), 12.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (!homeStyle || item.videoId == null) Modifier.thumbnailBorder(RoundedCornerShape(12.dp))
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
        Spacer(Modifier.height(10.dp))
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        val displaySubtitle = localizeCardSubtitle(item.subtitle)
        Text(
            text = displaySubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
