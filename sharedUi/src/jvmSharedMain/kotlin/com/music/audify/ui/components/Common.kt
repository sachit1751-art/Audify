package com.music.audify.ui.components

import androidx.compose.ui.layout.ContentScale
import com.music.audify.ui.LocalPullToRefreshEnabled
import com.music.audify.ui.AppUiHost
import com.music.audify.ui.AppUi
import com.music.audify.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.music.audify.ui.player.MESH_CYCLE_MILLIS
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.WelcomeMeshPalettes
import com.music.audify.ui.player.cyclingMeshPalette
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.music.audify.ui.icons.AudifyIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.isMatchPending
import com.music.audify.data.model.isMatchMissing
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.CircularProgressIndicator
import com.music.audify.data.model.artworkAt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border

/**
 * The left and right inset every page's content sits at — the artist page's
 * edge, which every other page now shares, so a track row, a card or a
 * heading starts at the same place whichever page it is on.
 */
val PAGE_GUTTER = 16.dp

/**
 * The inset the floating chrome sits at: the mini player, the tab bar and the
 * top bar's controls. The same as [PAGE_GUTTER], so the bars' edges line up
 * with the content scrolling beneath them rather than sitting just outside it.
 */
val BAR_GUTTER = PAGE_GUTTER

/** Where a divider under a track row starts: clear of the 52dp of artwork. */
val ROW_DIVIDER_INSET = PAGE_GUTTER + 68.dp

/**
 * A numbered row's track-number column, the number centred in it: room for
 * three digits. It sits between two page gutters — the row's own on the left,
 * a matching gap before the title on the right — so the number has the same
 * space either side of it, counted from the screen edge.
 */
val TRACK_NUMBER_WIDTH = 24.dp

/** Where a divider under a numbered row starts: at its title, past the number. */
val NUMBERED_ROW_DIVIDER_INSET = PAGE_GUTTER + TRACK_NUMBER_WIDTH + PAGE_GUTTER

/**
 * How wide the floating bars at the foot of the page — the tab bar and the mini
 * player above it — are ever allowed to get.
 *
 * Both are fixed rows of controls with a fixed amount to say, not content that
 * benefits from room, and a phone is the width they were spaced for. Run right
 * across a tablet the four tabs end up a hand apart with their labels marooned
 * in the middle of nothing, and the mini player puts its artwork and its buttons
 * at opposite ends of the screen with a lake of frosted glass between. Past this
 * they stop growing and centre themselves over the page instead.
 *
 * Set clear of the widest phone (448dp less the two [PAGE_GUTTER]s is 428dp), so
 * on a phone it does nothing and the bars still line up with the page's content.
 */
val FLOATING_BAR_MAX_WIDTH = 440.dp

/**
 * Width of a card in the compact carousels — home shelves, library shelves and
 * the artist page's releases alike.
 *
 * Sized so a phone-width row shows two cards whole with the edge of a third
 * showing: enough to say the row scrolls without a card being half a card.
 */
val SHELF_CARD_WIDTH = 150.dp

/**
 * A track's thumbnail box at [height]: square for a song, 16:9 at the same
 * height for a music video — its picture's own shape, as YouTube Music lists
 * one, rather than a still cropped into a square. Square when the listener
 * picked square video covers, see [VideoThumbnails].
 */
fun Modifier.trackArtSize(height: Dp, isVideo: Boolean): Modifier =
    if (isVideo && !VideoThumbnails.square) size(width = height * (16f / 9f), height = height) else size(height)

/**
 * The "Square video covers" setting as every surface that sizes a music video
 * reads it. Compose state, so a plain read in composition follows it without
 * each caller collecting a flow.
 */
object VideoThumbnails {
    var square by mutableStateOf(true)
}

/** Share of the row a lead-shelf card takes, so the next one peeks in past it. */
private const val HERO_CARD_FRACTION = 0.70f

/**
 * How wide a lead-shelf card is ever allowed to get.
 *
 * The fraction alone is a phone measurement wearing a percent sign: 70% of a
 * tablet is a card the better part of a foot across, and a hero card is a
 * caption over some artwork rather than a canvas — blown up that far it stops
 * being the top of a feed and becomes a poster with a shelf hiding under it.
 *
 * Set just clear of what the widest phone asks for (0.70 of 448dp is 314dp), so
 * every phone keeps the width the fraction gives it and only a screen wider than
 * any phone is held back to it.
 */
private val HERO_CARD_MAX_WIDTH = 320.dp

/** A lead-shelf card's proportions: a touch taller than it is wide. */
const val HERO_CARD_RATIO = 0.92f

/**
 * How wide a lead-shelf card should be in a row [available] wide — the shared
 * answer for the real shelf and for the skeleton that stands in for it, which
 * have to agree to the pixel or the feed jumps when the data lands.
 *
 * Given the row's own width rather than the window's, so it is still right in
 * the narrower column a tablet leaves once the player has taken its pane.
 * Height follows from [HERO_CARD_RATIO], so the card keeps its shape at any
 * width.
 */
fun heroCardWidth(available: Dp): Dp = minOf(available * HERO_CARD_FRACTION, HERO_CARD_MAX_WIDTH)

/** Share of the row a page of tracks takes, so the next page peeks in past it. */
private const val TRACK_COLUMN_FRACTION = 0.88f

/**
 * How wide a sideways-paging column of track rows is ever allowed to get.
 *
 * Same trap [HERO_CARD_MAX_WIDTH] answers, and worse for a list: a track row is
 * artwork, a title and a subtitle, none of which have any use for more room. At
 * 88% of a tablet the row's contents stay their own size and the space all lands
 * between the title and the overflow button, so four songs eat the width of the
 * screen and read as a page half-filled rather than a shelf.
 *
 * 400dp clears what the widest phone asks for (0.88 of 448dp is 394dp), so every
 * phone keeps the width the fraction gives it and only a tablet is held back —
 * to a column near a phone's own width, which is the size these rows were drawn
 * at, with the next page showing beside it.
 */
private val TRACK_COLUMN_MAX_WIDTH = 400.dp

/**
 * How wide a column of track rows should be in a row [available] wide — shared
 * by Home's Recents, an artist's Top songs, and the skeletons that stand in for
 * them, which have to agree to the pixel or the page jumps when the data lands.
 */
fun trackColumnWidth(available: Dp): Dp =
    minOf(available * TRACK_COLUMN_FRACTION, TRACK_COLUMN_MAX_WIDTH)

/** How many cards sit across a library grid row, and how wide each lands. */
data class LibraryGridSpec(val columns: Int, val cardWidth: Dp)

/**
 * The narrowest a library grid card is let get before another column gives way.
 *
 * Deliberately the same 140.dp minimum the Local Music and Downloads grids pass
 * to `GridCells.Adaptive`, so a cover on a "Show all" page is the same size as
 * the same cover on those pages rather than noticeably smaller.
 */
private val LIBRARY_GRID_MIN_CARD_WIDTH = 140.dp

/** Gap between cards in a library grid, in both directions. */
val LIBRARY_GRID_SPACING = 12.dp

private const val LIBRARY_GRID_MIN_COLUMNS = 2

/** Library shelves never grow past this many across, however wide the screen. */
private const val LIBRARY_GRID_MAX_COLUMNS = 5

/**
 * How a Library shelf's full "Show all" page lays out as a grid, in
 * [available] dp of row — see `LibraryGridPage`.
 *
 * Columns follow from [LIBRARY_GRID_MIN_CARD_WIDTH] — as many as fit — rather
 * than from a fixed count, so a phone settles on 2 and a tablet fills out
 * towards the 5-column ceiling. This is the same arithmetic `GridCells.Adaptive`
 * runs, at the same minimum, gutter and spacing the Local Music and Downloads
 * grids use, which is what keeps a card here the size of a card there. Every
 * width is already in dp, which is what
 * makes this "based on device width and dpi" rather than a raw pixel count: a
 * dp reads the same physical size on a 420ppi phone as on a 160ppi tablet, so
 * the column count tracks how much room there actually is rather than how
 * many pixels the panel happens to report.
 *
 * The shelf's own preview row on the Library page itself is unrelated — it
 * keeps the fixed [SHELF_CARD_WIDTH] every other shelf uses and a flat
 * five-card cap rather than a width-derived one, so a card is the same size
 * whether the row it's in scrolls or not. See `LibraryGridShelf`.
 */
fun libraryGrid(available: Dp): LibraryGridSpec {
    val raw = ((available + LIBRARY_GRID_SPACING) / (LIBRARY_GRID_MIN_CARD_WIDTH + LIBRARY_GRID_SPACING))
        .toInt()
    val columns = raw.coerceIn(LIBRARY_GRID_MIN_COLUMNS, LIBRARY_GRID_MAX_COLUMNS)
    val cardWidth = (available - LIBRARY_GRID_SPACING * (columns - 1)) / columns
    return LibraryGridSpec(columns, cardWidth)
}

/**
 * One track row, used by search, library and detail pages.
 *
 * Swiping it either way queues the track or plays it next, per
 * [AppSettings.swipeToPlayNext] — the row springs back rather than
 * dismissing, since nothing is being removed. Long-press opens the actions
 * menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    /**
     * What the trailing ⋮ does, when it should differ from [onLongPress].
     *
     * Defaults to the long-press, since on most pages the two are the same
     * menu. Downloads is the exception: holding a row there starts a
     * multi-selection, and the ⋮ has to stay the actions sheet rather than
     * silently tick a checkbox.
     */
    onMore: (() -> Unit)? = null,
    onSwipeToQueue: (() -> Unit)? = null,
    /**
     * What the row paints over the swipe reveal as it slides back.
     *
     * It has to be the colour of the page the row is *on*, not the theme's
     * background — an album page tinted from its sleeve would otherwise drag a
     * black band across itself on every swipe.
     */
    rowBackground: Color = MaterialTheme.colorScheme.background,
    /**
     * Drawn in place of the artwork, for lists where every row would otherwise
     * repeat the same cover — an album's own track listing.
     */
    trackNumber: Int? = null,
    /**
     * The artist line, and the track number when there is one.
     *
     * A page tinted from its artwork wants this brighter than the flat feeds
     * do: the usual dim grey is pitched against black, and against a mid-toned
     * wash it stops being legible as a second line and starts disappearing.
     */
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * What the badge on an already-downloaded row is tinted, or null to leave
     * those rows unmarked.
     *
     * Null is for the Downloads page itself, where every row qualifies and the
     * badge would say nothing. The colour is a parameter for the same reason
     * [subtitleColor] is: a page tinted from its artwork draws its accent from
     * the sleeve, and the theme's primary against that wash is exactly the
     * kind of thing that reads as pasted on.
     */
    downloadedTint: Color? = MaterialTheme.colorScheme.primary,
    /** Whether this row is the current item in the player's queue. */
    isCurrent: Boolean = false,
    /** Distinguishes active playback from the same current item while paused. */
    isPlaying: Boolean = false,
    /** Accent supplied by artwork-tinted pages. */
    activeTint: Color = MaterialTheme.colorScheme.primary,
    /** Search results use artwork bars instead of the usual current-song icon. */
    searchPlayingStyle: Boolean = false,
    /** True while a Downloads row belongs to the current multi-selection. */
    selected: Boolean = false,
    /**
     * Leads the artist line with the tier the row's source offered — Lossless,
     * Hi-Res Lossless, Dolby Atmos, High quality — for lists where rows from
     * different catalogues sit side by side: the Addons search.
     */
    showSourceQuality: Boolean = false,
    /**
     * A control of the page's own, drawn just ahead of the ⋮ — the Cached
     * songs folder's per-track download button. Null on every other list.
     */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * The album's artist: a row by this same artist leaves its artist line off,
     * as Apple Music's album listings do. Null keeps the line on every row.
     */
    hideArtistOf: String? = null,
    /** False drops the trailing duration, which Apple Music's album listings omit. */
    showDuration: Boolean = true,
) {
    SwipeToQueueBox(
        onSwipeToQueue = onSwipeToQueue,
        modifier = modifier,
    ) {
        SongRowContent(
            song = song,
            onClick = onClick,
            onLongPress = onLongPress,
            onMore = onMore ?: onLongPress,
            modifier = if (onSwipeToQueue != null) Modifier.background(rowBackground) else modifier,
            trackNumber = trackNumber,
            subtitleColor = subtitleColor,
            downloadedTint = downloadedTint,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            activeTint = activeTint,
            searchPlayingStyle = searchPlayingStyle,
            selected = selected,
            showSourceQuality = showSourceQuality,
            trailing = trailing,
            hideArtistOf = hideArtistOf,
            showDuration = showDuration,
        )
    }
}

/**
 * Wraps content with a swipe gesture revealing [QueueSwipeBackground] that
 * triggers [onSwipeToQueue] when swiped past 45% of width and snaps back.
 * If [onSwipeToQueue] is null, renders [content] directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueueBox(
    onSwipeToQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onSwipeToQueue == null) {
        content()
        return
    }

    val haptics = rememberHaptics()
    val swipeStateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
    var boxWidth by remember { mutableFloatStateOf(0f) }
    // Where this row sits (top, height) in the root, read by the rubber pull,
    // and whether this row is the one that was dragged.
    val rowBounds = remember { floatArrayOf(0f, 0f) }
    val dragFlag = remember { booleanArrayOf(false) }
    var armedState by remember { mutableStateOf(false) }

    val currentOnSwipeToQueue by rememberUpdatedState(onSwipeToQueue)

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && currentOnSwipeToQueue != null) {
                val offset = try { swipeStateHolder.value?.requireOffset() ?: 0f } catch (e: Exception) { 0f }
                // Only queue if the physical drag reached half the box width, ignoring short accidental flings.
                if (abs(offset) >= boxWidth * 0.45f) {
                    haptics.play(Haptic.Select)
                    currentOnSwipeToQueue?.invoke()
                }
            }
            false // never actually dismiss; snap back
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )
    swipeStateHolder.value = swipeState

    // The row reveals "Queue" from the first pixel of the drag, but it only
    // *commits* past 45% of the width — so without this the label is a promise
    // the finger can't check. One light tick at the crossing is the whole point:
    // let go now and it queues.
    // Only a row being dragged watches its offset; idle rows run no collector.
    val dragging = swipeState.dismissDirection != SwipeToDismissBoxValue.Settled
    LaunchedEffect(swipeState, boxWidth, dragging) {
        // Let go: the rows that were pulled along spring back past rest. Only
        // the row that was dragged settles them, so an idle row scrolling into
        // view can't cut a pull short.
        if (!dragging && dragFlag[0]) {
            dragFlag[0] = false
            armedState = false
            launch { SwipeRubber.near.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
            launch { SwipeRubber.far.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 200f)) }
            return@LaunchedEffect
        }
        if (!dragging || boxWidth <= 0f) return@LaunchedEffect
        dragFlag[0] = true
        SwipeRubber.sourceTop = rowBounds[0]
        SwipeRubber.sourceHeight = rowBounds[1]
        // A light beat as the row is picked up.
        haptics.play(Haptic.Tick)
        val armAt = boxWidth * 0.45f
        var armed = false
        snapshotFlow { try { swipeState.requireOffset() } catch (e: Exception) { 0f } }
            .collect { offset ->
                // Rubber: the rows around the dragged one are drawn along by a
                // damped share of the pull, the nearest most and the next one
                // less, and never past two rows away. The pull saturates
                // instead of growing with the finger.
                val pull = tanh(offset / (boxWidth * 0.4f))
                launch {
                    SwipeRubber.near.animateTo(
                        pull * boxWidth * 0.085f,
                        spring(dampingRatio = 0.55f, stiffness = 380f),
                    )
                }
                launch {
                    SwipeRubber.far.animateTo(
                        pull * boxWidth * 0.035f,
                        spring(dampingRatio = 0.55f, stiffness = 220f),
                    )
                }
                val travelled = abs(offset)
                when {
                    !armed && travelled >= armAt -> {
                        armed = true
                        armedState = true
                        haptics.play(Haptic.Tick)
                    }
                    // Silent, and with hysteresis: dragging back under the line
                    // re-arms, but so does the spring-back after a successful
                    // queue, and that must not buzz the same gesture twice.
                    armed && travelled < armAt * 0.8f -> {
                        armed = false
                        armedState = false
                    }
                }
            }
    }

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier
            .onSizeChanged { boxWidth = it.width.toFloat() }
            .onGloballyPositioned {
                rowBounds[0] = it.positionInRoot().y
                rowBounds[1] = it.size.height.toFloat()
            }
            // Only drawn, never a swipe: the rows beside a dragged one are moved
            // by this layer alone, so their own swipe state is never touched and
            // being pulled can never queue them.
            .graphicsLayer {
                val near = SwipeRubber.near.value
                val far = SwipeRubber.far.value
                val sourceHeight = SwipeRubber.sourceHeight
                translationX = if (sourceHeight > 0f) {
                    when (abs(((rowBounds[0] - SwipeRubber.sourceTop) / sourceHeight).roundToInt())) {
                        1 -> near
                        2 -> far
                        else -> 0f
                    }
                } else {
                    0f
                }
            },
        backgroundContent = { QueueSwipeBackground(swipeState, armedState) },
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSwipeBackground(swipeState: SwipeToDismissBoxState, armed: Boolean) {
    val playNext by AppUi.host.swipeToPlayNext.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    // The panel brightens as the swipe arms, so it says "let go now" itself.
    val glow by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "queueSwipeGlow",
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val offset = try { swipeState.requireOffset() } catch (e: Exception) { 0f }
                if (offset > 0f) {
                    clipRect(left = 0f, top = 0f, right = offset, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                } else if (offset < 0f) {
                    clipRect(left = size.width + offset, top = 0f, right = size.width, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
            .background(
                Brush.horizontalGradient(
                    listOf(
                        primary.copy(alpha = 0.16f + 0.14f * glow),
                        primary.copy(alpha = 0.28f + 0.18f * glow),
                    ),
                ),
            )
            .padding(horizontal = PAGE_GUTTER),
        verticalAlignment = Alignment.CenterVertically,
        // One label, on the side the row is being pulled from: both at once
        // showed as a doubled icon as soon as the reveal was wide enough.
        horizontalArrangement = if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        QueueSwipeLabel(playNext, swipeState, armed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSwipeLabel(playNext: Boolean, swipeState: SwipeToDismissBoxState, armed: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    // The badge pops when the swipe arms and fills solid, like a button
    // pressed in.
    val pop by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
        label = "queueSwipePop",
    )
    val iconTint by animateColorAsState(
        targetValue = if (armed) MaterialTheme.colorScheme.onPrimary else primary,
        label = "queueSwipeIconTint",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Grows and fades in with the reveal instead of sitting there from the
        // first pixel.
        modifier = Modifier.graphicsLayer {
            val offset = try { swipeState.requireOffset() } catch (e: Exception) { 0f }
            val reach = (abs(offset) / 160.dp.toPx()).coerceIn(0f, 1f)
            alpha = reach
            scaleX = 0.7f + 0.3f * reach
            scaleY = 0.7f + 0.3f * reach
        },
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .padding(5.dp)
                .graphicsLayer {
                    val s = 1f + 0.16f * pop
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.18f + 0.77f * pop)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playNext) AudifyIcons.PlayNext else AudifyIcons.PlayLast,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(if (playNext) Res.string.play_next else Res.string.queue),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = primary,
        )
    }
}

/**
 * The pull shared by every swipeable row on screen: where the dragged row is,
 * and how far the row beside it and the one after that are drawn along. The
 * two are springs, so they trail the finger and overshoot on release.
 */
private object SwipeRubber {
    var sourceTop = 0f
    var sourceHeight = 0f
    val near = Animatable(0f)
    val far = Animatable(0f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRowContent(
    song: Song,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    downloadedTint: Color? = MaterialTheme.colorScheme.primary,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    activeTint: Color = MaterialTheme.colorScheme.primary,
    searchPlayingStyle: Boolean = false,
    selected: Boolean = false,
    showSourceQuality: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    hideArtistOf: String? = null,
    showDuration: Boolean = true,
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent && !searchPlayingStyle) activeTint else MaterialTheme.colorScheme.onBackground,
        label = "song row title",
    )
    val activeBackground = animateColorAsState(
        targetValue = if ((isCurrent && !searchPlayingStyle) || selected) activeTint.copy(alpha = 0.14f) else Color.Transparent,
        label = "song row background",
    )
    // A Spotify track still being matched to a YouTube Music song, or one that
    // has no match: shown, but not something a tap or a menu can act on.
    val matching = song.isMatchPending
    val unavailable = song.isMatchMissing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (unavailable) 0.45f else 1f)
            // Read in draw only, so the fade redraws the row instead of recomposing it.
            .drawWithContent {
                drawRect(activeBackground.value)
                drawContent()
            }
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                liftPadding = PaddingValues(horizontal = PAGE_GUTTER),
            )
            .contextClick(onMore ?: onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            // Centred between the screen edge and the title (see
            // [TRACK_NUMBER_WIDTH]). Same height as the artwork, so rows stay
            // level.
            Box(
                Modifier.width(TRACK_NUMBER_WIDTH).height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (searchPlayingStyle && isCurrent && isPlaying) {
                    SearchPlayingBars()
                } else if (isCurrent) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(Res.string.now_playing),
                        tint = activeTint,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "$trackNumber",
                        style = MaterialTheme.typography.bodyLarge,
                        color = subtitleColor,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Box(Modifier.trackArtSize(52.dp, song.isVideo)) {
                val remoteArtwork = com.music.audify.ui.player.rememberRemoteArtworkUrl(song)
                AsyncImage(
                    model = remember(remoteArtwork) { remoteArtwork?.artworkAt(ROW_ART_PX) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .thumbnailBorder(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (searchPlayingStyle && isCurrent && isPlaying) {
                    SearchPlayingBars(Modifier.align(Alignment.Center))
                }
            }
        }
        // A number is followed by the page gutter again, so the gap from the
        // screen edge to the number and from the number to the title match,
        // whatever its digit count.
        Spacer(Modifier.width(if (trackNumber == null) 14.dp else PAGE_GUTTER))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            val hideArtist = hideArtistOf != null &&
                song.artist.trim().equals(hideArtistOf.trim(), ignoreCase = true)
            val subtitle = remember(song.artist, song.downloadFormat, hideArtist) {
                listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() && !hideArtist },
                    song.downloadFormat,
                ).joinToString(" · ")
            }
            val quality = if (showSourceQuality) song.sourceQualityBadge() else null
            // Apple Music leaves the line off when it would only repeat the album's artist.
            val showSubtitleLine = hideArtistOf == null || subtitle.isNotEmpty() || quality != null
            if (showSubtitleLine) Row(verticalAlignment = Alignment.CenterVertically) {
                if (quality != null) {
                    SourceQualityBadge(
                        quality = quality,
                        color = subtitleColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = if (quality != null && subtitle.isNotEmpty()) " · $subtitle" else subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(Res.string.selected),
                tint = activeTint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (isCurrent && trackNumber == null && !searchPlayingStyle) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(Res.string.now_playing),
                tint = activeTint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (matching) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = subtitleColor,
            )
        } else {
            song.durationText?.takeIf { showDuration }?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = subtitleColor,
                )
            }
        }
        if (trailing != null && !matching && !unavailable) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
        // Same sheet the long-press opens, for anyone who doesn't think to hold.
        if (onMore != null && !matching && !unavailable) {
            RowMoreButton(onClick = onMore, tint = subtitleColor)
        }
    }
}

/**
 * The ⋮ at the end of a row. Its circle is a touch target nobody sees, so it
 * hangs [ROW_MORE_OUTSET] into the page gutter: the dots end at the row's
 * content edge, mirroring the artwork on the left, rather than a whole
 * button's width short of it — and the row's text gets that width back.
 */
@Composable
fun RowMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .leftOutOfHeldPicture()
            .hangIntoGutter(ROW_MORE_OUTSET)
            .size(36.dp)
            .menuAnchor()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = stringResource(Res.string.more),
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** How far a row's [RowMoreButton] reaches past the content edge into the gutter. */
val ROW_MORE_OUTSET = 14.dp

/**
 * Lays a trailing control out [outset] narrower than it draws, so it overhangs
 * its row's end edge by that much: for buttons whose glyph is far smaller than
 * their touch target, which would otherwise sit visibly inset from the edge.
 */
fun Modifier.hangIntoGutter(outset: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = (placeable.width - outset.roundToPx()).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(0, 0) }
}


/**
 * The gap a decorative loop leaves after each of its frames before asking for
 * the next: 25ms lands the next frame on the vsync a thirtieth of a second on,
 * at 60, 90 and 120Hz alike.
 *
 * Every frame of anything inside the page is a frame of the whole page — the
 * glass bars sample it live, so they record it again and re-run their blur —
 * and asked for on every vsync, a 14dp glyph alone kept a 120Hz phone
 * redrawing the screen without pause until it ran hot and throttled. Nothing
 * that only decorates needs more than thirty.
 */
const val MOTION_FRAME_GAP_MS = 25L

/**
 * Whether the playing bars may move. Off while the row they sit in isn't
 * really being looked at — Now Playing pulled up over the page, the app in
 * the background, or the page on its way out — when they would go on
 * redrawing a page nobody can see. They hold where they are and carry on
 * from there when the page is back.
 */
val LocalPlayingBarsMotion = compositionLocalOf { true }

/** Each bar's lowest point; all three peak at full height. */
private val PLAYING_BAR_MINIMUMS = floatArrayOf(0.38f, 0.78f, 0.52f)

/**
 * Bar [index]'s height at [ms]: up from [minimum] to full and back down over
 * its own period, then the same again in reverse, so the three drift in and
 * out of step rather than marching.
 */
private fun playingBarHeight(index: Int, minimum: Float, ms: Long): Float {
    val duration = 520 + index * 130
    val peak = 260 + index * 50
    var t = (ms % (2L * duration)).toInt()
    if (t > duration) t = 2 * duration - t
    return if (t <= peak) {
        minimum + (1f - minimum) * t / peak
    } else {
        1f - (1f - minimum) * (t - peak) / (duration - peak)
    }
}

@Composable
fun SearchPlayingBars(modifier: Modifier = Modifier) {
    // Time in motion rather than an animation per bar: read only in the draw
    // below, so a frame of it redraws this canvas and never recomposes or
    // re-lays out the row it sits in.
    val elapsedNanos = remember { mutableLongStateOf(0L) }
    val running = LocalPlayingBarsMotion.current
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                if (last != Long.MIN_VALUE) elapsedNanos.longValue += now - last
                last = now
            }
            delay(MOTION_FRAME_GAP_MS)
        }
    }
    // The plate is a fixed square so only the bars move, never the box around them.
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 13.dp, height = 14.dp)) {
            val barWidth = 3.dp.toPx()
            val step = barWidth + 2.dp.toPx()
            val corner = CornerRadius(minOf(2.dp.toPx(), barWidth / 2f))
            val ms = elapsedNanos.longValue / 1_000_000L
            PLAYING_BAR_MINIMUMS.forEachIndexed { index, minimum ->
                val height = size.height * playingBarHeight(index, minimum, ms)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(index * step, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/**
 * The mark on a row whose track is already on disk.
 *
 * Sized under the row's "more" glyph on purpose: this is a statement about the
 * track, not something to press, and a status mark that matches an affordance
 * in weight invites a tap that does nothing.
 *
 * Reads the downloads the app keeps in memory ([AppUiHost.downloadedIds]) rather than touching the filesystem — a list cannot
 * afford a file check per row, and the map is kept honest by the disk check
 * every real read of a download goes through. The cost of that trade is a row
 * that can claim a file a file manager has since deleted, until something asks
 * for it and the record is pruned.
 */
@Composable
fun DownloadedBadge(videoId: String, tint: Color, modifier: Modifier = Modifier) {
    if (videoId !in AppUi.host.downloadedIds()) return
    Spacer(Modifier.width(8.dp))
    Icon(
        Icons.Rounded.DownloadDone,
        contentDescription = stringResource(Res.string.downloaded),
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

/** Finger-to-offset distance at which a pull counts as a refresh request. */
private val ELASTIC_PULL_THRESHOLD = 80.dp

/** Where the page rests while a refresh is running. */
private val ELASTIC_PULL_HOLD = 56.dp

/** The rubber-band's asymptote: the page can never be pushed this far. */
private val ELASTIC_PULL_RANGE = 600.dp

/** iOS's UIScrollView rubber-band coefficient. */
private const val ELASTIC_PULL_RUBBER = 0.6f

/** How long a release waits for the refresh to actually start before assuming it already ended. */
private const val ELASTIC_PULL_START_GRACE_MS = 600L

/**
 * The least time the page stays held once a refresh fires. A feed that answers
 * from cache finishes in a few milliseconds; without this the page would drop
 * back before the loading animation had been seen.
 */
private const val ELASTIC_PULL_MIN_HOLD_MS = 900L

/** Unit vectors of the indicator's 12 spokes, clockwise from twelve o'clock. */
private val ELASTIC_SPOKE_COS = FloatArray(12) { cos((-90.0 + it * 30.0) * PI / 180.0).toFloat() }
private val ELASTIC_SPOKE_SIN = FloatArray(12) { sin((-90.0 + it * 30.0) * PI / 180.0).toFloat() }

/**
 * State of the elastic pull-to-refresh: the whole page follows the finger with
 * an iOS-style rubber-band, is held at a rest distance while a refresh runs and
 * springs back when it ends.
 *
 * Hoisted by the caller and bound to the caller's scope, so a pull released on
 * a page that then leaves composition still finishes its spring-back.
 */
@Stable
class ElasticRefreshState internal constructor(private val scope: CoroutineScope) {
    /** How far the page is pushed down, in px. Read in layer/draw blocks only. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** True while the pull is past the refresh threshold. */
    var armed by mutableStateOf(false)
        private set

    /** True from a releasing pull that started a refresh until the refresh is over. */
    var holding by mutableStateOf(false)
        private set

    /** True while a finger is pulling the page (as opposed to it settling on its own). */
    var pulling by mutableStateOf(false)
        private set

    internal var thresholdPx = 0f
        private set
    private var holdPx = 0f
    private var rangePx = 1f
    private var rawDrag = 0f
    private var dragging = false
    private var started = false
    private var finishing = false
    private var holdStartNs = 0L
    private var anim: Job? = null

    internal fun configure(thresholdPx: Float, holdPx: Float, rangePx: Float) {
        this.thresholdPx = thresholdPx
        this.holdPx = holdPx
        this.rangePx = rangePx
    }

    private fun resist(x: Float): Float =
        rangePx * (1f - 1f / (x * ELASTIC_PULL_RUBBER / rangePx + 1f))

    private fun unresist(y: Float): Float {
        val p = (y / rangePx).coerceIn(0f, 0.98f)
        return rangePx * (1f / (1f - p) - 1f) / ELASTIC_PULL_RUBBER
    }

    /** Moves the page by [delta] finger px (down is positive); returns what was used. */
    internal fun dragBy(delta: Float): Float {
        if (!dragging) {
            anim?.cancel()
            anim = null
            rawDrag = unresist(offset)
            dragging = true
            pulling = true
        }
        val next = (rawDrag + delta).coerceAtLeast(0f)
        val used = next - rawDrag
        rawDrag = next
        offset = resist(next)
        if (!armed && offset >= thresholdPx) {
            armed = true
        } else if (armed && offset < thresholdPx * 0.92f) {
            armed = false
        }
        return used
    }

    /** The finger lifted. True if the page was pulled and the release was handled here. */
    internal fun release(refreshingNow: Boolean, onRefresh: () -> Unit): Boolean {
        if (!dragging) return false
        dragging = false
        pulling = false
        rawDrag = 0f
        if (offset <= 0f) {
            armed = false
            return false
        }
        val fire = armed && !holding
        armed = false
        if (fire) {
            holding = true
            finishing = false
            started = refreshingNow
            holdStartNs = System.nanoTime()
            settleTo(holdPx)
            onRefresh()
            scope.launch {
                delay(ELASTIC_PULL_START_GRACE_MS)
                if (holding && !started) finish()
            }
        } else {
            settleTo(0f)
        }
        return true
    }

    internal fun onRefreshingChanged(refreshing: Boolean) {
        if (refreshing) {
            started = true
        } else if (holding && started) {
            finish()
        }
    }

    /** The page is back on screen: drop anything a previous visit left half-done. */
    internal fun onAttach() {
        dragging = false
        pulling = false
        rawDrag = 0f
        armed = false
        if (!holding && offset > 0f) settleTo(0f)
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            val elapsedMs = (System.nanoTime() - holdStartNs) / 1_000_000L
            val remaining = ELASTIC_PULL_MIN_HOLD_MS - elapsedMs
            if (remaining > 0L) delay(remaining)
            holding = false
            started = false
            finishing = false
            if (!dragging) settleTo(0f)
        }
    }

    private fun settleTo(target: Float) {
        anim?.cancel()
        anim = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f),
            ) { value, _ -> offset = value.coerceAtLeast(0f) }
        }
    }
}

@Composable
fun rememberElasticRefreshState(): ElasticRefreshState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ElasticRefreshState(scope) }
}

private class ElasticRefreshConnection(
    private val state: ElasticRefreshState,
    private val isRefreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || state.holding) return Offset.Zero
        // Pushing back up: the page gives its offset back before the list scrolls.
        if (available.y < 0f && state.offset > 0f) return Offset(0f, state.dragBy(available.y))
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || state.holding) return Offset.Zero
        // What the list could not take is the pull past its top edge.
        if (available.y > 0f) return Offset(0f, state.dragBy(available.y))
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (state.release(isRefreshing(), onRefresh)) available else Velocity.Zero
}

/**
 * Pull-to-refresh for the tab feeds: the whole page follows the finger with an
 * iOS-style rubber-band instead of a puck drawn over it.
 *
 * Past the threshold a release holds the page at a rest distance and calls
 * [onRefresh] once; when [refreshing] goes back to false the page springs home.
 * [topInset] is the frosted bar's height, so the small hint in the gap sits
 * below the bar. [state] is hoisted so a feed that leaves the screen mid-pull
 * keeps its place.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    state: ElasticRefreshState,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    if (!LocalPullToRefreshEnabled.current) {
        Box(modifier.fillMaxSize()) { content() }
        return
    }
    val density = LocalDensity.current
    remember(density.density) {
        state.configure(
            thresholdPx = with(density) { ELASTIC_PULL_THRESHOLD.toPx() },
            holdPx = with(density) { ELASTIC_PULL_HOLD.toPx() },
            rangePx = with(density) { ELASTIC_PULL_RANGE.toPx() },
        )
    }
    val haptics = rememberHaptics()
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val connection = remember(state) {
        ElasticRefreshConnection(state, { latestRefreshing }, { latestOnRefresh() })
    }
    LaunchedEffect(state) { state.onAttach() }
    LaunchedEffect(refreshing) { state.onRefreshingChanged(refreshing) }
    LaunchedEffect(state) {
        snapshotFlow { state.armed }.collect { if (it) haptics.play(Haptic.Tick) }
    }
    Box(modifier.fillMaxSize().nestedScroll(connection)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = state.offset },
        ) {
            content()
        }
        RefreshInputBlocker(state = state)
        ElasticRefreshHint(state = state, topInset = topInset)
    }
}

/**
 * Menelan semua sentuhan di atas halaman selama refresh berjalan, supaya kartu
 * tidak bisa diketuk dan daftar tidak bisa digeser sementara halaman ditahan.
 * Dibaca di composable sendiri agar hanya ia yang recompose saat status berganti.
 */
@Composable
private fun RefreshInputBlocker(state: ElasticRefreshState) {
    if (!state.holding) return
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    )
}

/**
 * An iOS-style spoke indicator in the gap the pull opens, with a short label
 * under it. The spokes appear one by one with the drag, then a bright head
 * steps around them, trailing a fade, while the refresh runs. It lives in the
 * page, so it
 * goes where the page goes. Composed only while the page is displaced, so
 * nothing animates at rest.
 */
@Composable
private fun ElasticRefreshHint(state: ElasticRefreshState, topInset: Dp) {
    val visible by remember(state) { derivedStateOf { state.offset > 0f } }
    if (!visible) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val spin = rememberInfiniteTransition(label = "elasticRefreshSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(960, easing = LinearEasing)),
        label = "elasticRefreshAngle",
    )
    val blend by animateFloatAsState(
        targetValue = if (state.holding) 1f else 0f,
        animationSpec = tween(280),
        label = "elasticRefreshBlend",
    )
    // 0 = pull, 1 = release, 2 = refreshing. Kept through the spring-back so the
    // label does not flip back to "pull" while the page is going home.
    var mode by remember { mutableStateOf(0) }
    val target = when {
        state.holding -> 2
        state.pulling && state.armed -> 1
        state.pulling -> 0
        else -> -1
    }
    LaunchedEffect(target) { if (target >= 0) mode = target }
    val label = stringResource(
        when (mode) {
            2 -> Res.string.pull_refreshing
            1 -> Res.string.pull_release
            else -> Res.string.pull_to_refresh
        },
    )
    val density = LocalDensity.current
    val insetPx = with(density) { topInset.toPx() }
    val halfPx = with(density) { 21.dp.toPx() }
    val fadeStartPx = with(density) { 18.dp.toPx() }
    val fadeSpanPx = with(density) { 36.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = insetPx + state.offset / 2f - halfPx
                    alpha = ((state.offset - fadeStartPx) / fadeSpanPx).coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val progress = (state.offset / state.thresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val center = Offset(size.width / 2f, size.height / 2f)
                val inner = 5.5.dp.toPx()
                val outer = 10.dp.toPx()
                val strokeWidth = 2.2.dp.toPx()
                // Like iOS's activity indicator, the head steps from spoke to spoke.
                val head = (angle / 30f).toInt() % 12
                for (i in 0 until 12) {
                    val pullAlpha = (progress * 12f - i).coerceIn(0f, 1f) * 0.5f
                    val behind = (head - i + 12) % 12
                    val spinAlpha = 1f - behind * 0.075f
                    val a = pullAlpha + (spinAlpha - pullAlpha) * blend
                    if (a <= 0.01f) continue
                    val dx = ELASTIC_SPOKE_COS[i]
                    val dy = ELASTIC_SPOKE_SIN[i]
                    drawLine(
                        color = color.copy(alpha = a),
                        start = Offset(center.x + dx * inner, center.y + dy * inner),
                        end = Offset(center.x + dx * outer, center.y + dy * outer),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

/**
 * The prompt atop Home while signed out: a card in the welcome pages' living
 * mesh, so the one thing Home asks of a signed-out listener looks like an
 * invitation from the app rather than a settings row that wandered in.
 *
 * Drawn white-on-colour in either theme, like Replay and the welcome — the
 * mesh is the card, and white type is what sits on colour.
 */
@Composable
fun SignInBanner(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the mesh may move. Off, it finishes the fade it is in and rests:
     * a blurred layer re-drawn every frame is not something to leave running
     * under a scroll, or under the player where nobody sees it.
     */
    motion: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = 900f), label = "signInScale")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onSignIn),
    ) {
        // Reuse the animated onboarding mesh rather than a flat theme surface.
        // Pause it while Home is scrolling to avoid continuously blurring a
        // card that is moving out of view.
        MeshGradientBackground(
            palette = cyclingMeshPalette(WelcomeMeshPalettes, running = motion),
            modifier = Modifier.matchParentSize(),
            continuous = true,
            driftMillis = 6_000,
            blurRadius = 36.dp,
            colorFadeMillis = MESH_CYCLE_MILLIS,
            paused = !motion,
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)) {
            Text(
                text = stringResource(Res.string.sign_in_youtube_music),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.W800,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_google_g),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.sign_in_with_google),
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER + 6.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The narrow, pill-shaped text field this app uses instead of Material's
 * `OutlinedTextField` — the same box the alerts and the search bar are built
 * from.
 *
 * Flat `surfaceVariant`, an 11dp radius, no outline and no floating label. A
 * stock `OutlinedTextField` dropped onto one of these pages reads as a widget
 * borrowed from another app: it is the only thing on the screen wearing a
 * border, and its label reserves a band of empty space above every row it sits
 * in whether or not there is a label to put there.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    /**
     * What the box is filled with.
     *
     * The default is the fill the alerts want, since their frosted card is
     * `surface` and this sits on top of it. A field dropped into a settings
     * card has to be told otherwise: those cards are *already* `surfaceVariant`
     * (see `SettingsGroup`), and a field the colour of the card it is in is a
     * field nobody can see.
     */
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(container, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Keeps accent icons legible against the current surface. */
fun accessibleThemeIconTint(accent: Color, foreground: Color): Color = Color(
    red = accent.red * .55f + foreground.red * .45f,
    green = accent.green * .55f + foreground.green * .45f,
    blue = accent.blue * .55f + foreground.blue * .45f,
    alpha = 1f,
)
