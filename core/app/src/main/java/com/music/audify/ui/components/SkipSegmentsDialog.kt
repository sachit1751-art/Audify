package com.music.audify.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.TrackPlaybackWindow
import com.music.audify.data.settings.formatSkipTimestamp
import com.music.audify.ui.player.ThinSlider

/** Selects the one audible in/out range for a track; everything outside it is excluded. */
@Composable
internal fun SkipSegmentsDialog(
    trackId: String,
    trackTitle: String,
    artistName: String,
    artworkUrl: String?,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    val savedWindow by AppSettings.trackPlaybackWindow.collectAsStateWithLifecycle()
    val totalMs = durationMs.coerceAtLeast(1_000L)
    var startMs by remember(trackId) { mutableLongStateOf(0L) }
    var endMs by remember(trackId) { mutableLongStateOf(totalMs) }
    var error by remember(trackId) { mutableIntStateOf(0) }

    LaunchedEffect(trackId, durationMs) {
        AppSettings.setSkipSegmentsTrack(trackId)
        val existing = AppSettings.playbackWindowForTrack(trackId)
        startMs = existing?.startMs?.coerceIn(0L, totalMs - 1_000L) ?: 0L
        endMs = existing?.endMs?.coerceIn(startMs + 1_000L, totalMs) ?: totalMs
    }

    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val rangeLabel = if (durationMs > 0L) {
        "${formatSkipTimestamp(startMs)} – ${formatSkipTimestamp(endMs)}"
    } else {
        "Track duration unavailable"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip segment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trackTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Text(
                    rangeLabel,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    if (durationMs > 0L) "Only this part of the song will play. The intro and ending outside this range are excluded."
                    else "The track duration has not loaded, so its playback range cannot be edited yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (durationMs > 0L) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Starting point · ${formatSkipTimestamp(startMs)}", style = MaterialTheme.typography.labelLarge)
                    ThinSlider(
                        value = (startMs.toFloat() / totalMs).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            startMs = ((fraction * totalMs).toLong() / 1_000L * 1_000L)
                                .coerceIn(0L, (endMs - 1_000L).coerceAtLeast(0L))
                            error = 0
                        },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        activeColor = primary.copy(alpha = 0.96f),
                        inactiveColor = inactive,
                    )
                }
                if (durationMs > 0L) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ending point · ${formatSkipTimestamp(endMs)}", style = MaterialTheme.typography.labelLarge)
                    ThinSlider(
                        value = (endMs.toFloat() / totalMs).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            endMs = ((fraction * totalMs).toLong() / 1_000L * 1_000L)
                                .coerceIn((startMs + 1_000L).coerceAtMost(totalMs), totalMs)
                            error = 0
                        },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        activeColor = primary.copy(alpha = 0.96f),
                        inactiveColor = inactive,
                    )
                }
                if (error != 0) {
                    Text("Choose an ending point after the starting point.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = durationMs > 0L, onClick = {
                if (endMs <= startMs || endMs > durationMs) {
                    error = 1
                } else {
                    AppSettings.setPlaybackWindowForTrack(trackId, TrackPlaybackWindow(startMs, endMs))
                    // The new single playable window replaces legacy multi-range skip rules.
                    AppSettings.setSkipSegmentsForTrack(trackId, emptyList())
                    onDismiss()
                }
            }) { Text(if (savedWindow != null) "Save changes" else "Save") }
        },
        dismissButton = {
            Row {
                if (savedWindow != null) {
                    TextButton(onClick = {
                        AppSettings.setPlaybackWindowForTrack(trackId, null)
                        AppSettings.setSkipSegmentsForTrack(trackId, emptyList())
                        onDismiss()
                    }) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
