package com.music.audify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.R
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.HOME_SLOT_TOP_PICKS
import com.music.audify.data.model.LIBRARY_SLOT_LINKS
import com.music.audify.data.model.LIBRARY_SLOT_ON_DEVICE
import com.music.audify.data.model.LIBRARY_SLOT_PINNED
import com.music.audify.data.model.LIBRARY_SLOT_REPLAY
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.HomeSlot
import com.music.audify.data.model.homeShelfKey
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.player.edgeScrollSpeed
import com.music.audify.ui.screens.homeDefaultSlotKeys
import com.music.audify.ui.screens.homeFeedShelves
import com.music.audify.ui.screens.homeShelfLabel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The listener's own arrangement of Home: drag a row by its handle to move it,
 * tick or untick it to show or hide it. Same frosted alert as
 * [LyricsSourcesDialog]. Every change is written straight to
 * [AppSettings.homeLayout]; rows the feed does not have right now keep the
 * place they were saved at.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun HomeLayoutDialog(
    hazeState: HazeState,
    shelves: List<HomeShelf>,
    /** The account's library shelves, for the Library tab of the dialog. */
    libraryShelves: List<HomeShelf> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val layout by AppSettings.homeLayout.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)
    val motion = rememberMacOSPopupState(onClosed = onDismiss)

    // 0 = Home, 1 = Library.
    var tab by remember { mutableIntStateOf(0) }

    // Library tab: the fixed rows plus whatever shelves the account's library has.
    val libraryLayout by AppSettings.libraryLayout.collectAsStateWithLifecycle()
    val libraryTitles = remember(libraryShelves) {
        val titles = libraryShelves.map { it.title } + YtMusicRepository.PLAYLISTS_SHELF
        titles.distinctBy { homeShelfKey(it) }.associate { homeShelfKey(it) to it }
    }
    val libraryDefaults = remember(libraryTitles) {
        listOf(LIBRARY_SLOT_PINNED, LIBRARY_SLOT_LINKS, LIBRARY_SLOT_REPLAY, LIBRARY_SLOT_ON_DEVICE) +
            libraryTitles.keys
    }
    val libraryOrder = remember(libraryLayout, libraryDefaults) {
        val remembered = libraryLayout.slots.map { it.key }.filter { it !in libraryDefaults }
        libraryLayout.arrange(libraryDefaults + remembered, dropHidden = false) { it }
    }
    val libraryHidden = remember(libraryLayout, libraryOrder) {
        libraryOrder.filter { libraryLayout.isHidden(it) }.toSet()
    }
    fun saveLibrary(keys: List<String>, hiddenKeys: Set<String>) {
        if (keys.isEmpty()) return
        val unseen = libraryLayout.slots.filter { it.key !in keys }
        AppSettings.setLibraryLayout(
            HomeLayout(keys.map { HomeSlot(it, it in hiddenKeys) } + unseen),
        )
    }

    // Top Picks and Find Your Mood hide with the layout. AppSettings keeps these
    // two flags in step with it, so a flag set before any layout existed still
    // reads as hidden here.
    val topPicksHidden by AppSettings.hideHomeTopPicks.collectAsStateWithLifecycle()
    val moodsHidden by AppSettings.hideHomeMoods.collectAsStateWithLifecycle()
    val recentsHidden by AppSettings.hideHomeRecents.collectAsStateWithLifecycle()
    val recentsKey = remember { homeShelfKey("Recents") }
    // Home keeps the release row out of the feed only while Top Picks shows.
    val titles = remember(shelves, topPicksHidden) {
        homeFeedShelves(shelves, dropEditorial = !topPicksHidden)
            .distinctBy { homeShelfKey(it.title) }
            .associate { homeShelfKey(it.title) to it.title }
    }
    val defaults = remember(shelves, topPicksHidden) {
        homeDefaultSlotKeys(
            shelves,
            withTopPicks = true,
            withMoods = true,
            dropEditorial = !topPicksHidden,
        )
    }
    // The feed is personalised and changes on every load, so a saved row can be
    // missing from it right now. Keep those rows in the list at their saved place
    // instead of letting them vanish until the feed happens to return them.
    val order = remember(layout, defaults) {
        val remembered = layout.slots.map { it.key }.filter { it !in defaults }
        layout.arrange(defaults + remembered, dropHidden = false) { it }
    }
    val hidden = remember(layout, order, topPicksHidden, moodsHidden, recentsHidden) {
        order.filter {
            layout.isHidden(it) ||
                (it == HOME_SLOT_TOP_PICKS && topPicksHidden) ||
                (it == HOME_SLOT_MOODS && moodsHidden) ||
                (it == recentsKey && recentsHidden)
        }.toSet()
    }

    fun save(keys: List<String>, hiddenKeys: Set<String>) {
        if (keys.isEmpty()) return
        val unseen = layout.slots.filter { it.key !in keys }
        AppSettings.setHomeLayout(
            HomeLayout(keys.map { HomeSlot(it, it in hiddenKeys) } + unseen),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .macOSPopupScrim(motion, SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = motion::requestClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .macOSPopupMotion(motion)
                .clip(shape)
                .alertSurface(hazeState, frosted = true, shape = shape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .macOSPopupContent(motion),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(if (tab == 1) R.string.customize_library else R.string.customize_home),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        if (tab == 1) R.string.library_layout_description else R.string.home_layout_description,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            GlassSegmentedControl(
                options = listOf(stringResource(R.string.home), stringResource(R.string.library)),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                liquid = true,
            )

            if (tab == 1) {
                ReorderableHomeSlotList(
                    order = libraryOrder,
                    hidden = libraryHidden,
                    titles = libraryTitles,
                    onReorder = { saveLibrary(it, libraryHidden) },
                    onToggle = { slot ->
                        saveLibrary(
                            libraryOrder,
                            if (slot in libraryHidden) libraryHidden - slot else libraryHidden + slot,
                        )
                    },
                )
            } else if (shelves.isEmpty() && !layout.isCustom) {
                AlertRule()
                Text(
                    text = stringResource(R.string.home_layout_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            } else {
                ReorderableHomeSlotList(
                    order = order,
                    hidden = hidden,
                    titles = titles,
                    onReorder = { save(it, hidden) },
                    onToggle = { slot ->
                        save(order, if (slot in hidden) hidden - slot else hidden + slot)
                    },
                )
            }

            AlertRule()
            AlertAction(
                label = stringResource(R.string.reset_to_default),
                emphasised = false,
                onClick = {
                    if (tab == 1) {
                        AppSettings.setLibraryLayout(HomeLayout())
                    } else {
                        AppSettings.setHomeLayout(HomeLayout())
                    }
                },
            )
            AlertRule()
            AlertAction(label = stringResource(R.string.done), emphasised = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun homeSlotTitle(slot: String, titles: Map<String, String>): String = when (slot) {
    HOME_SLOT_TOP_PICKS -> stringResource(R.string.home_row_top_picks)
    HOME_SLOT_MOODS -> stringResource(R.string.find_your_mood)
    LIBRARY_SLOT_PINNED -> stringResource(R.string.library_row_pinned)
    LIBRARY_SLOT_LINKS -> stringResource(R.string.library_row_links)
    LIBRARY_SLOT_REPLAY -> stringResource(R.string.replay)
    LIBRARY_SLOT_ON_DEVICE -> stringResource(R.string.on_device)
    "shelf:listen-again" -> homeShelfLabel(titles[slot] ?: "Listen again")
    "shelf:quick-picks" -> homeShelfLabel(titles[slot] ?: "Quick picks")
    else -> homeShelfLabel(
        titles[slot] ?: slot.removePrefix("shelf:").replaceFirstChar { it.uppercase() },
    )
}

/**
 * The checkable, drag-reorderable list of Home rows. Same drag model as the
 * lyric sources list: the order is local until a drag ends, and only the
 * finished order is written back through [onReorder].
 */
@Composable
private fun ReorderableHomeSlotList(
    order: List<String>,
    hidden: Set<String>,
    titles: Map<String, String>,
    onReorder: (List<String>) -> Unit,
    onToggle: (String) -> Unit,
) {
    var liveOrder by remember(order) { mutableStateOf(order) }
    var draggedSlot by remember { mutableStateOf<String?>(null) }
    var totalDrag by remember { mutableStateOf(0f) }
    var startIndex by remember { mutableStateOf(0) }
    var pitchPx by remember { mutableStateOf(0f) }
    var lockedPitchPx by remember { mutableStateOf(0f) }

    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableStateOf(0f) }
    val autoScroll = remember { HomeDragAutoScroll() }
    val density = LocalDensity.current
    val edgeZonePx = with(density) { HOME_EDGE_SCROLL_ZONE.toPx() }
    val edgeSpeedPx = with(density) { HOME_EDGE_SCROLL_SPEED.toPx() }

    fun advanceDrag(delta: Float) {
        val slot = draggedSlot ?: return
        val pitch = lockedPitchPx
        if (pitch <= 0f) return
        var index = liveOrder.indexOf(slot)
        if (index < 0) return

        totalDrag = (totalDrag + delta).coerceIn(
            -startIndex * pitch,
            (liveOrder.lastIndex - startIndex) * pitch,
        )

        while (true) {
            val travelled = totalDrag / pitch
            val moved = (index - startIndex).toFloat()
            if (travelled > moved + HOME_SWAP_THRESHOLD && index < liveOrder.lastIndex) {
                liveOrder = liveOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
                index++
            } else if (travelled < moved - HOME_SWAP_THRESHOLD && index > 0) {
                liveOrder = liveOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
                index--
            } else {
                break
            }
        }

        val top = startIndex * pitch + totalDrag - scrollState.value
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + pitch,
            viewportStart = 0,
            viewportEnd = viewportHeightPx.toInt(),
            zone = edgeZonePx,
            speed = edgeSpeedPx,
        )
        val blocked = when {
            speed < 0f -> index <= 0 || !scrollState.canScrollBackward
            speed > 0f -> index >= liveOrder.lastIndex || !scrollState.canScrollForward
            else -> true
        }
        autoScroll.setSpeed(if (blocked) 0f else speed)
    }

    LaunchedEffect(autoScroll.dir) {
        if (autoScroll.dir == 0) return@LaunchedEffect
        scrollState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                val scrolled = scrollBy(autoScroll.speed * seconds)
                if (scrolled == 0f) break
                advanceDrag(scrolled)
            }
        }
    }

    Column(
        modifier = Modifier
            .heightIn(max = HOME_ROWS_MAX_HEIGHT)
            .onSizeChanged { viewportHeightPx = it.height.toFloat() }
            .verticalScroll(scrollState),
    ) {
        liveOrder.forEach { slot ->
            key(slot) {
                val shown = slot !in hidden
                val dragging = slot == draggedSlot
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .onSizeChanged { pitchPx = it.height.toFloat() }
                        .graphicsLayer {
                            translationY = if (dragging) {
                                totalDrag - (liveOrder.indexOf(slot) - startIndex) * lockedPitchPx
                            } else {
                                0f
                            }
                        },
                ) {
                    AlertRule()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ACTION_HEIGHT)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onToggle(slot) },
                            )
                            .padding(start = 4.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.drag_to_reorder),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(18.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedSlot = slot
                                            totalDrag = 0f
                                            startIndex = liveOrder.indexOf(slot)
                                            lockedPitchPx = pitchPx
                                            autoScroll.setSpeed(0f)
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            advanceDrag(delta.y)
                                        },
                                        onDragEnd = {
                                            draggedSlot = null
                                            totalDrag = 0f
                                            autoScroll.setSpeed(0f)
                                            onReorder(liveOrder)
                                        },
                                        onDragCancel = {
                                            draggedSlot = null
                                            totalDrag = 0f
                                            autoScroll.setSpeed(0f)
                                            liveOrder = order
                                        },
                                    )
                                },
                        )
                        Text(
                            text = homeSlotTitle(slot, titles),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface
                                .copy(alpha = if (shown) 1f else 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        if (shown) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.enabled),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How far past a neighbour a row is carried before the two trade places, in rows. */
private const val HOME_SWAP_THRESHOLD = 0.6f

/** How tall the row list may get before it scrolls inside the card. */
private val HOME_ROWS_MAX_HEIGHT = 340.dp

private val HOME_EDGE_SCROLL_ZONE = 28.dp
private val HOME_EDGE_SCROLL_SPEED = 220.dp

/** Which way the list scrolls itself while a row is held near an edge, and how fast. */
private class HomeDragAutoScroll {
    var dir by mutableIntStateOf(0)
        private set
    var speed: Float = 0f
        private set

    fun setSpeed(value: Float) {
        speed = value
        val next = when {
            value > 0f -> 1
            value < 0f -> -1
            else -> 0
        }
        if (dir != next) dir = next
    }
}