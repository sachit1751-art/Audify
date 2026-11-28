package com.music.audify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.SkipSegment
import com.music.audify.data.settings.SkipSegmentStore
import com.music.audify.data.settings.formatSkipTimestamp
import com.music.audify.data.settings.normalizeSkipSegments
import com.music.audify.data.settings.parseSkipTimestamp
import com.music.audify.ui.icons.SfIcons

/** One shared multi-range editor used from the player and every song action menu. */
@Composable
internal fun SkipSegmentsDialog(
    trackId: String,
    trackTitle: String,
    artistName: String = "",
    artworkUrl: String? = null,
    durationMs: Long = 0L,
    onDismiss: () -> Unit,
) {
    var saved by remember(trackId) { mutableStateOf(AppSettings.skipSegmentsForTrack(trackId)) }
    var entries by remember(trackId) {
        mutableStateOf(saved.map { formatSkipTimestamp(it.startMs) to formatSkipTimestamp(it.endMs) })
    }
    LaunchedEffect(trackId, durationMs) {
        val legacyWindow = AppSettings.playbackWindowForTrack(trackId)
        if (durationMs > 0L && legacyWindow != null) {
            // Preserve the old playable window by migrating its excluded edges to skip ranges.
            val migrated = buildList {
                addAll(AppSettings.skipSegmentsForTrack(trackId))
                if (legacyWindow.startMs > 0L) add(SkipSegment(0L, legacyWindow.startMs))
                if (legacyWindow.endMs < durationMs) add(SkipSegment(legacyWindow.endMs, durationMs))
            }.let(::normalizeSkipSegments)
            AppSettings.setSkipSegmentsForTrack(trackId, migrated)
            AppSettings.setPlaybackWindowForTrack(trackId, null)
            saved = migrated
            entries = migrated.map { formatSkipTimestamp(it.startMs) to formatSkipTimestamp(it.endMs) }
        }
    }
    var error by remember(trackId) { mutableStateOf<String?>(null) }
    val durationKnown = durationMs > 0L
    val colors = MaterialTheme.colorScheme

    GlassAlertDialog(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (artworkUrl != null) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Skip sections",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                        )
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (artistName.isNotBlank()) {
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Choose the parts to skip. Everything between them keeps playing.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                        color = colors.onSurfaceVariant,
                    )
                    if (durationKnown) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.onSurface.copy(alpha = 0.07f))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(SfIcons.Clock, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.onSurfaceVariant)
                            Spacer(Modifier.width(5.dp))
                            Text(formatSkipTimestamp(durationMs), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            AlertRule()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (entries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(SfIcons.Sliders, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No sections added", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                        Text("Add a time range to skip part of this song.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                entries.forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.onSurface.copy(alpha = 0.045f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", modifier = Modifier.width(18.dp), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            SkipTimeField(
                                label = "FROM", value = entry.first, placeholder = "0:00", modifier = Modifier.weight(1f),
                                onValueChange = { value -> entries = entries.toMutableList().also { it[index] = value to entry.second }; error = null },
                            )
                            SkipTimeField(
                                label = "TO", value = entry.second, placeholder = "0:20", modifier = Modifier.weight(1f),
                                onValueChange = { value -> entries = entries.toMutableList().also { it[index] = entry.first to value }; error = null },
                            )
                            Box(
                                modifier = Modifier.size(38.dp).clip(CircleShape)
                                    .background(colors.error.copy(alpha = 0.10f))
                                    .clickable { entries = entries.toMutableList().also { it.removeAt(index) }; error = null },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(SfIcons.Trash, contentDescription = "Delete skip range ${index + 1}", tint = colors.error, modifier = Modifier.size(17.dp))
                            }
                        }
                        if (durationKnown) {
                            val start = parseSkipTimestamp(entry.first)?.coerceIn(0L, durationMs) ?: 0L
                            val end = parseSkipTimestamp(entry.second)?.coerceIn(0L, durationMs) ?: durationMs
                            val safeStart = minOf(start, (durationMs - 1L).coerceAtLeast(0L))
                            val safeEnd = maxOf(end, (safeStart + 1L).coerceAtMost(durationMs))
                            RangeSlider(
                                value = safeStart.toFloat()..safeEnd.toFloat(),
                                onValueChange = { range ->
                                    val from = range.start.toLong().coerceIn(0L, durationMs - 1L)
                                    val to = range.endInclusive.toLong().coerceIn(from + 1L, durationMs)
                                    entries = entries.toMutableList().also {
                                        it[index] = formatSkipTimestamp(from) to formatSkipTimestamp(to)
                                    }
                                    error = null
                                },
                                valueRange = 0f..durationMs.toFloat(),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatSkipTimestamp(safeStart), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                Text(formatSkipTimestamp(safeEnd), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (entries.size < SkipSegmentStore.MAX_SEGMENTS_PER_TRACK) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                entries = entries + ("" to "")
                                error = null
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SfIcons.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add another range", color = colors.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                error?.let {
                    Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!durationKnown) {
                    Text(
                        "Enter times as m:ss or h:mm:ss. Track length is unavailable, so end times cannot be checked yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (saved.isNotEmpty()) {
                    Text(
                        text = "Clear all ranges",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                entries = emptyList()
                                error = null
                            }
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                        color = colors.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            AlertRule()
            AlertAction(
                label = "Save ranges",
                emphasised = true,
                enabled = entries.isNotEmpty() || saved.isNotEmpty(),
                onClick = {
                    val parsed = entries.mapNotNull { (startText, endText) ->
                        val start = parseSkipTimestamp(startText)
                        val end = parseSkipTimestamp(endText)
                        if (start == null || end == null || end <= start || (durationKnown && end > durationMs)) null
                        else SkipSegment(start, end)
                    }
                    when {
                        parsed.size != entries.size -> error = if (durationKnown)
                            "Check each range: use valid times, with the end after the start and within the track."
                        else "Check each range: use valid times and an end after the start."
                        else -> {
                            AppSettings.setSkipSegmentsForTrack(trackId, parsed)
                            if (durationKnown) AppSettings.setPlaybackWindowForTrack(trackId, null)
                            onDismiss()
                        }
                    }
                },
            )
            AlertRule()
            AlertAction(label = "Cancel", emphasised = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun SkipTimeField(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.onSurface.copy(alpha = 0.055f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
            color = colors.onSurfaceVariant,
        )
        Box {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant.copy(alpha = 0.65f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
