package com.music.audify.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun SkipSegmentsDialog(
    trackId: String,
    trackTitle: String,
    durationMs: Long,
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
    val sliderDuration = durationMs.coerceAtLeast(1_000L)
    val sliderActive = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val sliderInactive = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    LaunchedEffect(trackId) {
        AppSettings.setSkipSegmentsTrack(trackId)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skip_segments)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(trackTitle, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = {
                            fromText = it
                            parseSkipTimestamp(it)?.let { parsed -> startMs = parsed }
                            error = 0
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.skip_segment_from)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = {
                            toText = it
                            parseSkipTimestamp(it)?.let { parsed -> endMs = parsed }
                            error = 0
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.skip_segment_to)) },
                        singleLine = true,
                    )
                }
                Text("${stringResource(R.string.skip_segment_from)} · ${formatSkipTimestamp(startMs)}")
                ThinSlider(
                    value = (startMs.toFloat() / sliderDuration).coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        startMs = ((fraction * sliderDuration).toLong() / 1_000L) * 1_000L
                        fromText = formatSkipTimestamp(startMs)
                        error = 0
                    },
                    activeColor = sliderActive,
                    inactiveColor = sliderInactive,
                    idleHeight = 5.dp,
                    activeHeight = 8.dp,
                )
                Text("${stringResource(R.string.skip_segment_to)} · ${formatSkipTimestamp(endMs)}")
                ThinSlider(
                    value = (endMs.toFloat() / sliderDuration).coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        endMs = ((fraction * sliderDuration).toLong() / 1_000L) * 1_000L
                        toText = formatSkipTimestamp(endMs)
                        error = 0
                    },
                    activeColor = sliderActive,
                    inactiveColor = sliderInactive,
                    idleHeight = 5.dp,
                    activeHeight = 8.dp,
                )
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
