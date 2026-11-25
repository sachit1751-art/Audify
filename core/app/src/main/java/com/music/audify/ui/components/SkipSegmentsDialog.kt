package com.music.audify.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.R
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.SkipSegment
import com.music.audify.data.settings.TrackPlaybackWindow
import com.music.audify.data.settings.formatSkipTimestamp
import com.music.audify.data.settings.normalizeSkipSegments
import com.music.audify.data.settings.parseSkipTimestamp
import com.music.audify.ui.player.ThinSlider
import com.music.audify.playback.smart.MixWaveform
import com.music.audify.playback.smart.TrackAnalyzer
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun SkipSegmentsDialog(
    trackId: String,
    trackTitle: String,
    artistName: String,
    artworkUrl: String?,
    durationMs: Long,
    audioUri: Uri?,
    onDismiss: () -> Unit,
) {
    val segments by AppSettings.skipSegments.collectAsStateWithLifecycle()
    var fromText by remember(trackId) { mutableStateOf("") }
    var toText by remember(trackId) { mutableStateOf("") }
    var editingIndex by remember(trackId) { mutableIntStateOf(-1) }
    var error by remember(trackId) { mutableIntStateOf(0) }
    var startMs by remember(trackId) { mutableLongStateOf(0L) }
    var endMs by remember(trackId) { mutableLongStateOf(0L) }
    var playbackWindowEnabled by remember(trackId) { mutableStateOf(false) }
    var playbackStartMs by remember(trackId) { mutableLongStateOf(0L) }
    var playbackEndMs by remember(trackId) { mutableLongStateOf(durationMs) }
    var waveform by remember(trackId, audioUri) { mutableStateOf<MixWaveform.Bands?>(null) }
    var waveformLoading by remember(trackId, audioUri) { mutableStateOf(audioUri != null && durationMs > 0L) }
    val sliderDuration = durationMs.coerceAtLeast(1_000L)
    val sliderActive = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val sliderInactive = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val latestStartMs by rememberUpdatedState(startMs)
    val latestEndMs by rememberUpdatedState(endMs)
    LaunchedEffect(trackId) {
        AppSettings.setSkipSegmentsTrack(trackId)
        startMs = 0L
        endMs = sliderDuration
        fromText = formatSkipTimestamp(0L)
        toText = formatSkipTimestamp(sliderDuration)
        AppSettings.trackPlaybackWindow.value?.let { window ->
            playbackWindowEnabled = true
            playbackStartMs = window.startMs
            playbackEndMs = window.endMs
        } ?: run {
            playbackWindowEnabled = false
            playbackStartMs = 0L
            playbackEndMs = durationMs
        }
    }
    LaunchedEffect(trackId, audioUri, durationMs) {
        waveform = null
        waveformLoading = audioUri != null && durationMs > 0L
        val uri = audioUri
        if (uri != null && durationMs > 0L) {
            waveform = withContext(Dispatchers.IO) {
                runCatching { TrackAnalyzer.editorWaveform(trackId, uri, durationMs) }.getOrNull()
            }
            waveformLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingIndex >= 0) "Edit Skip Segment" else stringResource(R.string.skip_segments)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trackTitle, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(artistName, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(formatSkipTimestamp(durationMs), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.skip_segments_description))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = playbackWindowEnabled, onCheckedChange = { playbackWindowEnabled = it })
                    Text(stringResource(R.string.track_playback_window))
                }
                if (playbackWindowEnabled) {
                    Text(stringResource(R.string.track_playback_window_description))
                    Text("${stringResource(R.string.track_play_from)} · ${formatSkipTimestamp(playbackStartMs)}")
                    ThinSlider(
                        value = (playbackStartMs.toFloat() / sliderDuration).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            playbackStartMs = (((fraction * sliderDuration).toLong() / 1_000L) * 1_000L)
                                .coerceAtMost((sliderDuration - 1_000L).coerceAtLeast(0L))
                            if (playbackStartMs >= playbackEndMs) playbackEndMs = playbackStartMs + 1_000L
                        },
                        activeColor = sliderActive, inactiveColor = sliderInactive,
                        idleHeight = 5.dp, activeHeight = 8.dp,
                    )
                    Text("${stringResource(R.string.track_play_until)} · ${formatSkipTimestamp(playbackEndMs)}")
                    ThinSlider(
                        value = (playbackEndMs.toFloat() / sliderDuration).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            playbackEndMs = (((fraction * sliderDuration).toLong() / 1_000L) * 1_000L)
                                .coerceAtLeast(1_000L)
                            if (playbackEndMs <= playbackStartMs) playbackStartMs = (playbackEndMs - 1_000L).coerceAtLeast(0L)
                        },
                        activeColor = sliderActive, inactiveColor = sliderInactive,
                        idleHeight = 5.dp, activeHeight = 8.dp,
                    )
                    TextButton(onClick = {
                        if (playbackEndMs > playbackStartMs && (durationMs <= 0L || playbackEndMs <= durationMs)) {
                            AppSettings.setPlaybackWindowForTrack(trackId, TrackPlaybackWindow(playbackStartMs, playbackEndMs))
                        } else error = R.string.skip_segment_out_of_track
                    }) { Text(stringResource(R.string.track_playback_window_save)) }
                } else {
                    TextButton(onClick = { AppSettings.setPlaybackWindowForTrack(trackId, null) }) {
                        Text(stringResource(R.string.track_playback_window_clear))
                    }
                }
                if (segments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.skip_segments_empty),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(segments) { index, segment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${formatSkipTimestamp(segment.startMs)} – ${formatSkipTimestamp(segment.endMs)}")
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(onClick = {
                                        editingIndex = index
                                        fromText = formatSkipTimestamp(segment.startMs)
                                        toText = formatSkipTimestamp(segment.endMs)
                                        startMs = segment.startMs
                                        endMs = segment.endMs
                                        error = 0
                                    }) { Text(stringResource(R.string.skip_segment_edit)) }
                                    TextButton(onClick = {
                                        AppSettings.setSkipSegmentsForTrack(
                                            trackId,
                                            segments.filterIndexed { itemIndex, _ -> itemIndex != index },
                                        )
                                        if (editingIndex == index) {
                                            editingIndex = -1
                                            fromText = ""
                                            toText = ""
                                            startMs = 0L
                                            endMs = 0L
                                        }
                                    }) { Text(stringResource(R.string.skip_segment_remove)) }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "TRACK WAVEFORM  ·  ${formatSkipTimestamp(durationMs)}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp, max = 68.dp)
                        .pointerInput(trackId, sliderDuration) {
                            var draggingStart = true
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val startX = latestStartMs.toFloat() / sliderDuration * size.width
                                    val endX = latestEndMs.toFloat() / sliderDuration * size.width
                                    draggingStart = abs(offset.x - startX) <= abs(offset.x - endX)
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val value = ((fraction * sliderDuration).toLong() / 100L) * 100L
                                    if (draggingStart) {
                                        startMs = value.coerceIn(0L, (endMs - 100L).coerceAtLeast(0L))
                                        fromText = formatSkipTimestamp(startMs)
                                    } else {
                                        endMs = value.coerceIn((startMs + 100L).coerceAtMost(sliderDuration), sliderDuration)
                                        toText = formatSkipTimestamp(endMs)
                                    }
                                    error = 0
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val value = ((fraction * sliderDuration).toLong() / 100L) * 100L
                                    if (draggingStart) {
                                        startMs = value.coerceIn(0L, (endMs - 100L).coerceAtLeast(0L))
                                        fromText = formatSkipTimestamp(startMs)
                                    } else {
                                        endMs = value.coerceIn((startMs + 100L).coerceAtMost(sliderDuration), sliderDuration)
                                        toText = formatSkipTimestamp(endMs)
                                    }
                                    error = 0
                                },
                            )
                        },
                ) {
                    val width = size.width
                    val centerY = size.height / 2f
                    val startX = (startMs.toFloat() / sliderDuration * width).coerceIn(0f, width)
                    val endX = (endMs.toFloat() / sliderDuration * width).coerceIn(0f, width)
                    drawRoundRect(
                        color = sliderInactive.copy(alpha = 0.18f),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    )
                    val decoded = waveform
                    val barCount = decoded?.low?.size ?: 72
                    val step = width / barCount
                    repeat(barCount) { index ->
                        val amplitude = if (decoded != null) {
                            val peak = maxOf(decoded.low[index], decoded.mid[index], decoded.high[index])
                            (4.dp.toPx() + peak * 26.dp.toPx()).coerceAtMost(size.height * 0.92f)
                        } else 2.dp.toPx()
                        val x = index * step + step / 2f
                        val selected = x in startX..endX
                        drawLine(
                            color = if (decoded == null) sliderInactive.copy(alpha = 0.45f) else if (selected) sliderActive else sliderInactive,
                            start = Offset(x, centerY - amplitude / 2f),
                            end = Offset(x, centerY + amplitude / 2f),
                            strokeWidth = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                    drawRoundRect(
                        color = sliderActive.copy(alpha = 0.12f),
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size((endX - startX).coerceAtLeast(0f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    )
                    listOf(startX, endX).forEach { x ->
                        drawLine(sliderActive, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        drawCircle(sliderActive, radius = 5.dp.toPx(), center = Offset(x, centerY))
                    }
                }
                if (waveformLoading) {
                    Text("Decoding track waveform…", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (waveform == null) {
                    Text("Real waveform is unavailable until this track's audio is cached and readable. Selection controls remain available.", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.skip_segment_from), modifier = Modifier.weight(0.7f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { startMs = (startMs - 500L).coerceAtLeast(0L); fromText = formatSkipTimestamp(startMs) }) { Text("−0.5") }
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it; parseSkipTimestamp(it)?.let { value -> startMs = value.coerceIn(0L, (endMs - 100L).coerceAtLeast(0L)) }; error = 0 },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true,
                    )
                    TextButton(onClick = { startMs = (startMs + 500L).coerceAtMost((endMs - 100L).coerceAtLeast(0L)); fromText = formatSkipTimestamp(startMs) }) { Text("+0.5") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.skip_segment_to), modifier = Modifier.weight(0.7f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { endMs = (endMs - 500L).coerceAtLeast(startMs + 100L); toText = formatSkipTimestamp(endMs) }) { Text("−0.5") }
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it; parseSkipTimestamp(it)?.let { value -> endMs = value.coerceIn(startMs + 100L, sliderDuration) }; error = 0 },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true,
                    )
                    TextButton(onClick = { endMs = (endMs + 500L).coerceAtMost(sliderDuration); toText = formatSkipTimestamp(endMs) }) { Text("+0.5") }
                }
                if (error != 0) {
                    Text(stringResource(error), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = parseSkipTimestamp(fromText)
                val end = parseSkipTimestamp(toText)
                if (start == null || end == null || end <= start) {
                    error = R.string.skip_segment_invalid
                } else if (durationMs > 0L && end > durationMs) {
                    error = R.string.skip_segment_out_of_track
                } else {
                    val edited = segments.toMutableList()
                    val range = SkipSegment(start, end)
                    if (editingIndex in edited.indices) edited[editingIndex] = range else edited += range
                    AppSettings.setSkipSegmentsForTrack(trackId, normalizeSkipSegments(edited))
                    editingIndex = -1
                    fromText = ""
                    toText = ""
                    startMs = 0L
                    endMs = 0L
                    error = 0
                }
            }) {
                Text(stringResource(if (editingIndex >= 0) R.string.skip_segment_update else R.string.skip_segment_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
