package com.music.audify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.music.audify.data.model.SearchFilter
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.SearchResult
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState
import com.music.audify.data.model.artworkAt
import com.music.audify.data.settings.AppSettings
import com.music.audify.sharedui.resources.Res as SharedRes
import com.music.audify.sharedui.resources.add_songs_done
import com.music.audify.sharedui.resources.add_songs_suggested
import com.music.audify.sharedui.resources.add_songs_to_queue
import com.music.audify.sharedui.resources.search_hint
import com.music.audify.ui.components.backdrop.Backdrop
import com.music.audify.ui.player.PlayerBackHandler
import com.music.audify.ui.utils.containSheetGestures
import org.jetbrains.compose.resources.stringResource

/**
 * "Add Songs to Queue": popup kaca yang melayang di tengah player. Memakai
 * material yang sama dengan popup ⋯ ([iosMenuPanel]); begitu dibuka sudah berisi
 * lagu yang berhubungan dengan yang sedang diputar, dan berganti jadi hasil
 * pencarian setelah mengetik. Mengetuk lagu menambahkannya ke antrean dan popup
 * tetap terbuka sampai Done.
 *
 * [suggestions] null berarti masih dimuat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSongsDialog(
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    results: UiState<List<SearchResult>>?,
    typeahead: List<SearchResult>,
    suggestions: List<Song>?,
    backdrop: Backdrop?,
    onAdd: (Song) -> Unit,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    onLiveSearch: () -> Unit,
    onDismiss: () -> Unit,
    /** Judul popup; null = "Add Songs to Queue". */
    title: String? = null,
    /** Dipakai di luar player, saat tidak ada [backdrop] untuk blur. */
    hazeState: HazeState? = null,
) {
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    // Back: kalau keyboard sedang tampil, hanya keyboard yang ditutup, popup tetap.
    val imeVisible = rememberUpdatedState(WindowInsets.isImeVisible)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    PlayerBackHandler(
        enabled = open,
        onBack = {
            if (imeVisible.value) {
                keyboard?.hide()
                focusManager.clearFocus()
            } else {
                onDismiss()
            }
        },
    )
    // Cari otomatis saat mengetik (jeda singkat), tanpa menunggu tombol cari.
    LaunchedEffect(open, query) {
        if (!open || query.isBlank()) return@LaunchedEffect
        delay(450)
        onLiveSearch()
    }
    var added by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(open) { if (!open) added = emptySet() }
    val colors = iosMenuColors()
    val scrim = Color.Black.copy(alpha = if (colors.dark) 0.34f else 0.16f)

    val searching = query.isNotBlank()
    val rows: List<Song> = remember(searching, results, typeahead, suggestions) {
        if (!searching) {
            suggestions.orEmpty()
        } else {
            val source = (results as? UiState.Success)?.data ?: typeahead
            source.mapNotNull { row ->
                when (row) {
                    is SearchResult.TopTrack -> row.song
                    is SearchResult.Track -> row.song
                    is SearchResult.Browse -> null
                }
            }.distinctBy { it.videoId }
        }
    }
    val loading = if (searching) results is UiState.Loading else suggestions == null

    Box(
        Modifier
            .fillMaxSize()
            .then(if (open) Modifier.containSheetGestures() else Modifier)
            .imePadding(),
    ) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(if (reduceMotion) 90 else 180)),
            exit = fadeOut(tween(if (reduceMotion) 90 else 150)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(160)) +
                (if (reduceMotion) EnterTransition.None else scaleIn(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                    initialScale = 0.9f,
                )),
            exit = fadeOut(tween(120)) +
                (if (reduceMotion) ExitTransition.None else scaleOut(tween(140), targetScale = 0.94f)),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.66f)
                    // Ketukan di panel tidak boleh jatuh ke scrim di bawahnya.
                    .pointerInput(Unit) {}
                    .iosMenuPanel(translucent = true, backdrop = backdrop, hazeState = hazeState),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title ?: stringResource(SharedRes.string.add_songs_to_queue),
                        color = colors.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(SharedRes.string.add_songs_done),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.pressed)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.secondaryLabel,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(SharedRes.string.search_hint),
                                color = colors.secondaryLabel,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
                            cursorBrush = SolidColor(colors.label),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = colors.secondaryLabel,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onQueryChange("") },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = searching,
                    enter = fadeIn(tween(if (reduceMotion) 90 else 220)) +
                        (if (reduceMotion) EnterTransition.None else expandVertically(tween(260))),
                    exit = fadeOut(tween(if (reduceMotion) 90 else 160)) +
                        (if (reduceMotion) ExitTransition.None else shrinkVertically(tween(200))),
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        GlassSegmentedControl(
                            options = ADD_SONGS_FILTERS.map { it.label },
                            selectedIndex = ADD_SONGS_FILTERS.indexOf(filter).coerceAtLeast(0),
                            onSelect = { onFilterChange(ADD_SONGS_FILTERS[it]) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            liquid = true,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            color = colors.secondaryLabel,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                            if (!searching && rows.isNotEmpty()) {
                                item(key = "suggested-header") {
                                    Text(
                                        text = stringResource(SharedRes.string.add_songs_suggested),
                                        color = colors.secondaryLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            items(rows, key = { it.videoId }) { song ->
                                AddSongRow(
                                    song = song,
                                    added = song.videoId in added,
                                    colors = colors,
                                    onClick = {
                                        added = added + song.videoId
                                        onAdd(song)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ADD_SONGS_FILTERS = listOf(SearchFilter.ALL, SearchFilter.SONGS, SearchFilter.VIDEOS)

@Composable
private fun AddSongRow(song: Song, added: Boolean, colors: IosMenuColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !added, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = colors.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = colors.secondaryLabel,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.pressed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = null,
                tint = if (added) colors.secondaryLabel else colors.label,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}