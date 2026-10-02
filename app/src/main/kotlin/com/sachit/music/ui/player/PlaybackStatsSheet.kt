/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.player

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import com.sachit.music.LocalPlayerConnection
import com.sachit.music.R
import com.sachit.music.constants.LoudnessLevel
import com.sachit.music.constants.LoudnessLevelKey
import com.sachit.music.playback.NetworkClass
import com.sachit.music.playback.StreamHealth
import com.sachit.music.playback.networkClass
import com.sachit.music.ui.component.Material3SettingsGroup
import com.sachit.music.ui.component.Material3SettingsItem
import com.sachit.music.ui.utils.getLoudnessLevelLabel
import com.sachit.music.utils.StreamPicker
import com.sachit.music.utils.joinByBullet
import com.sachit.music.utils.makeTimeString
import com.sachit.music.utils.rememberEnumPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Live playback diagnostics sheet ("Signal"). Local-only and cheap: every value comes from
 * in-memory state (DB format flow, service stream-client flow, the attached Media3 player).
 * The 1 Hz ticker below lives only while this composable is in the composition — the sheet
 * host unmounts its content on dismiss, which cancels the ticker automatically.
 *
 * Unlike [com.sachit.music.ui.utils.ShowMediaInfo] this never performs network calls; that
 * dialog stays the place for static, fetched metadata.
 */
@Composable
fun PlaybackStatsSheet(mediaId: String) {
    if (mediaId.isBlank()) return
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val player = playerConnection.player

    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle()
    val currentStreamClient by playerConnection.currentStreamClient.collectAsStateWithLifecycle()
    val streamHealthStatus by playerConnection.streamHealth.collectAsStateWithLifecycle()
    val loudnessLevel by rememberEnumPreference(LoudnessLevelKey, defaultValue = LoudnessLevel.BALANCED)

    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    // 1 Hz tick that drives the Live group; cancelled on dismiss (see doc above).
    var liveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            liveTick++
            delay(1000)
        }
    }

    val measuredLufs: Double? =
        currentFormat?.perceptualLoudnessDb
            ?: currentFormat?.loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }

    // ---- Stream group: per-format facts, refreshed by the DB / service flows. ----
    val codecText = currentFormat?.let { format ->
        val base = format.codecs.ifBlank { format.mimeType }
        val isLosslessFormat = StreamPicker.isLossless(
            StreamPicker.Candidate(
                mimeType = format.mimeType,
                codecs = format.codecs,
                bitrate = null,
            ),
        )
        if (isLosslessFormat) "$base · ${stringResource(R.string.lossless_badge)}" else base
    }

    val streamItems = listOf(
        Material3SettingsItem(
            title = { Text(stringResource(R.string.playback_stats_codec)) },
            description = { Text(codecText ?: stringResource(R.string.unknown)) },
        ),
        Material3SettingsItem(
            title = { Text(stringResource(R.string.playback_stats_sample_rate)) },
            description = {
                Text(currentFormat?.sampleRate?.let { "$it Hz" } ?: stringResource(R.string.unknown))
            },
        ),
        Material3SettingsItem(
            title = { Text(stringResource(R.string.playback_stats_bitrate)) },
            description = {
                Text(currentFormat?.bitrate?.let { "${it / 1000} Kbps" } ?: stringResource(R.string.unknown))
            },
        ),
        Material3SettingsItem(
            title = { Text(stringResource(R.string.playback_stats_stream_client)) },
            description = { Text(currentStreamClient ?: stringResource(R.string.unknown)) },
        ),
        Material3SettingsItem(
            title = { Text(stringResource(R.string.playback_stats_loudness)) },
            description = {
                val deltaText = measuredLufs?.let { String.format(locale, "%+.2f dB", it - loudnessLevel.targetLufs) }
                Text(deltaText?.let { joinByBullet(it, getLoudnessLevelLabel(loudnessLevel)) }
                    ?: stringResource(R.string.unknown))
            },
        ),
        // Plan 021: honest source health — shown always so "Healthy" is a real signal.
        Material3SettingsItem(
            title = { Text(stringResource(R.string.playback_stats_source_health)) },
            description = {
                val text =
                    when (streamHealthStatus.health) {
                        StreamHealth.HEALTHY -> stringResource(R.string.playback_stats_source_health_ok)
                        StreamHealth.RETRYING ->
                            stringResource(R.string.playback_stats_source_health_retrying, streamHealthStatus.failureCount)
                        StreamHealth.FALLBACK -> stringResource(R.string.playback_stats_source_health_fallback)
                    }
                Text(text)
            },
        ),
    )

    // ---- Live group: recomputed once per tick, nothing else collects player state. ----
    val live = remember(liveTick) {
        LiveStats(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration,
            bufferedPercent = player.bufferedPercentage,
            renderSampleRate = player.audioFormat?.sampleRate,
            networkClass = connectivityManager?.networkClass() ?: NetworkClass.OFFLINE,
        )
    }

    val networkText = when (live.networkClass) {
        NetworkClass.WIFI -> stringResource(R.string.playback_stats_network_wifi)
        NetworkClass.METERED -> stringResource(R.string.playback_stats_network_metered)
        NetworkClass.OFFLINE -> stringResource(R.string.playback_stats_network_offline)
    }

    val durationText = live.durationMs.takeIf { it != C.TIME_UNSET }?.let { makeTimeString(it) }
        ?: stringResource(R.string.unknown)

    val liveItems = buildList {
        add(
            Material3SettingsItem(
                title = {
                    Text(
                        stringResource(
                            R.string.playback_stats_buffered,
                            makeTimeString(live.positionMs),
                            durationText,
                        )
                    )
                },
                description = {
                    Text("${live.bufferedPercent}%")
                },
            )
        )
        add(
            Material3SettingsItem(
                title = { Text(stringResource(R.string.playback_stats_network)) },
                description = { Text(networkText) },
            )
        )
        // A missing render sample rate is information (renderer not initialized yet),
        // so the row is hidden rather than shown as "unknown".
        live.renderSampleRate?.let { sampleRate ->
            add(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.playback_stats_render_rate)) },
                    description = { Text("$sampleRate Hz") },
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Material3SettingsGroup(
            title = stringResource(R.string.playback_stats_group_stream),
            items = streamItems,
        )
        Spacer(Modifier.height(8.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.playback_stats_group_live),
            items = liveItems,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Snapshot of the player-side values read once per tick. */
private data class LiveStats(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedPercent: Int,
    val renderSampleRate: Int?,
    val networkClass: NetworkClass,
)
