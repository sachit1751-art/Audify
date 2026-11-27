package com.music.audify.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import java.util.UUID
import com.music.audify.R
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState
import com.music.audify.data.model.UserPlaylist
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.components.trackArtSize
import com.music.audify.ui.theme.ArtworkPalette

/** A playlist row is an occurrence, not merely a media ID. */
internal data class ReorderPlaylistEntry(val key: String, val song: Song)

internal fun createReorderPlaylistEntries(songs: List<Song>): List<ReorderPlaylistEntry> =
    songs.map { ReorderPlaylistEntry(UUID.randomUUID().toString(), it) }

internal fun restoreReorderPlaylistEntries(
    order: MutableList<ReorderPlaylistEntry>,
    original: List<ReorderPlaylistEntry>,
) {
    order.clear()
    order.addAll(original)
}

/**
 * Rearranges one of the account's playlists: every entry with a handle to drag
 * it by, and nothing sent until Save.
 *
 * Drawn in place of the playlist page's own track list, on that page's
 * artwork colours ([palette]), rather than in a sheet over it — the page is
 * already the playlist, and a sheet's own drag-to-dismiss was one more
 * gesture competing with the rows'. Back closes it, as the X does.
 *
 * Held locally and saved as one edit, rather than sent a move at a time while
 * dragging, because a drag passes through a dozen positions on the way to the
 * one it was meant for, and each of those would otherwise be a request that
 * could land out of order with the next.
 *
 * [entries] is the playlist as YouTube has it now — loading, failed, or the
 * list to start from. [saving] holds the form while the edit is out.
 */
@Composable
fun ReorderPlaylistPage(
    playlist: UserPlaylist,
    entries: UiState<List<Song>>,
    saving: Boolean,
    palette: ArtworkPalette,
    onSave: (List<Song>) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    BackHandler(onBack = onClose)
    val original = (entries as? UiState.Success)?.data.orEmpty()
    val order = remember(original) {
        mutableStateListOf<ReorderPlaylistEntry>().apply {
            addAll(createReorderPlaylistEntries(original))
        }
    }
    val changed = order.map { it.song.setVideoId } != original.map { it.setVideoId }

    Column(modifier.fillMaxSize().padding(top = topBarContentPadding())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.back),
                    tint = palette.onBackground,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reorder_songs),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onBackground,
                )
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The page's filled pill — white with dark text, as its Play is.
            Button(
                onClick = { onSave(order.map { it.song }) },
                enabled = changed && !saving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.16f),
                    disabledContentColor = palette.onBackgroundVariant,
                ),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = palette.onBackground,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(stringResource(R.string.save_order))
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = palette.divider)

        when (entries) {
            UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.5.dp,
                    color = palette.onBackground,
                    modifier = Modifier.size(26.dp),
                )
            }

            is UiState.Error -> MessageState(
                message = entries.message,
                modifier = Modifier.padding(horizontal = 22.dp),
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
            )

            is UiState.Success -> ReorderableSongList(order, enabled = !saving, palette, contentPadding)
        }
    }
}

/**
 * The drag itself, over a lazy list so a playlist of hundreds of tracks
 * doesn't compose all of them at once.
 *
 * The held row keeps two numbers: which entry it is ([held], by set-video-id)
 * and how far it is drawn from its own slot ([offset]). Each time its centre
 * crosses into a neighbour the two trade places in [order], and the offset is
 * reduced by exactly the distance its slot just moved, so the row stays under
 * the finger rather than jumping with the swap.
 */
@Composable
private fun ReorderableSongList(
    order: MutableList<ReorderPlaylistEntry>,
    enabled: Boolean,
    palette: ArtworkPalette,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    var held by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf<List<ReorderPlaylistEntry>?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edgeZone = with(density) { 56.dp.toPx() }
    val edgeSpeed = with(density) { 900.dp.toPx() }

    fun settle() {
        val key = held ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        val top = dragged.offset + offset
        val center = top + dragged.size / 2f
        // Songs only: the footer after the last row is a list item too, and
        // carrying a row onto it would move it to an index past the end.
        val target = items.firstOrNull { item ->
            item.key != key && item.index < order.size &&
                center >= item.offset && center <= item.offset + item.size
        }
        if (target != null) {
            val from = dragged.index
            val to = target.index
            if (from !in order.indices) return
            // LazyColumn holds its place by the key of the first visible row;
            // moving that row would drag the whole list along with it, so the
            // current position is pinned explicitly across the swap.
            val first = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            order.add(to, order.removeAt(from))
            if (from == first || to == first) listState.requestScrollToItem(first, firstOffset)
            offset -= (target.offset - dragged.offset).toFloat()
        }
        // Near either edge the list scrolls under the held row, so a track
        // can be carried further than one screen.
        val viewport = listState.layoutInfo
        autoScroll = when {
            top < viewport.viewportStartOffset + edgeZone && listState.canScrollBackward -> -edgeSpeed
            top + dragged.size > viewport.viewportEndOffset - edgeZone && listState.canScrollForward -> edgeSpeed
            else -> 0f
        }
    }

    fun end() {
        held = null
        offset = 0f
        autoScroll = 0f
        dragOrigin = null
    }

    fun cancel() {
        dragOrigin?.let { restoreReorderPlaylistEntries(order, it) }
        end()
    }

    LaunchedEffect(autoScroll != 0f) {
        if (autoScroll == 0f) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (held != null && autoScroll != 0f) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
            previous = now
            val scrolled = listState.scrollBy(autoScroll * seconds)
            if (scrolled == 0f) break
            // The list moved and the finger didn't: the row's slot went with
            // the list, so the row is drawn that much further from it.
            offset += scrolled
            settle()
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        itemsIndexed(order, key = { _, entry -> entry.key }) { _, entry ->
            val key = entry.key
            val dragging = held == key
            val start: () -> Unit = {
                dragOrigin = order.toList()
                held = key
                offset = 0f
            }
            val drag: (Float) -> Unit = { delta ->
                offset += delta
                settle()
            }
            ReorderRow(
                song = entry.song,
                palette = palette,
                lifted = dragging,
                enabled = enabled,
                onDragStart = start,
                onDrag = drag,
                onDragEnd = ::end,
                onDragCancel = ::cancel,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) offset else 0f }
                    .then(if (dragging) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)),
            )
        }
        item(key = "reorder-footer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReorderRow(
    song: Song,
    palette: ArtworkPalette,
    lifted: Boolean,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Holding anywhere on the row picks it up too, not only the handle — the
    // handle is a small target, and a long press is what lists elsewhere on
    // the phone teach people to try first.
    val gestures = Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart() },
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onDrag = { change, amount ->
                change.consume()
                onDrag(amount.y)
            },
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Opaque when lifted: it is drawn over the rows it passes.
            .background(if (lifted) palette.elevated.compositeOver(palette.background) else Color.Transparent)
            .then(gestures)
            .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = if (song.isVideo) song.thumbnailUrl else song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = if (song.isVideo) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .trackArtSize(44.dp, song.isVideo)
                .clip(RoundedCornerShape(7.dp))
                .thumbnailBorder(RoundedCornerShape(7.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.drag_to_reorder),
            tint = palette.onBackgroundVariant,
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                    )
                },
        )
    }
}
