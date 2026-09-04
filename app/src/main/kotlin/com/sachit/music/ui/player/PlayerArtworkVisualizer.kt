/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.player

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sachit.music.utils.computeVisualizerLevels
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

private const val BAR_COUNT = 24
private const val BAR_SPACING_DP = 3
private const val BAR_WIDTH_DP = 3
private const val OVERLAY_HEIGHT_DP = 34
private const val TARGET_CAPTURE_SIZE = 512
private const val SMOOTHING_FACTOR = 0.25f
private const val DECAY_FACTOR = 0.85f
private const val IDLE_FLOOR = 0.05f

/**
 * Renders a live FFT bar strip over the player artwork.
 *
 * The strip is only meaningful while a local audio session is playing, so the
 * caller gates [active] with playback state (paused, casting, or Listen
 * Together guest → inactive). When inactive the bars smoothly decay to a flat
 * idle line instead of freezing mid-frame.
 *
 * All Android framework access is wrapped: if the platform refuses a
 * [Visualizer] (unsupported session, offload playback, missing permission) the
 * composable simply shows the idle line — it never throws.
 */
@Composable
fun PlayerArtworkVisualizer(
    active: Boolean,
    audioSessionIdProvider: () -> Int,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
) {
    var levels by remember { mutableStateOf(FloatArray(BAR_COUNT)) }
    var sessionId by remember { mutableIntStateOf(0) }
    val latestFft = remember { AtomicReference<ByteArray?>(null) }

    // Poll the audio session: ExoPlayer assigns it lazily, so re-check until a
    // valid (> 0) session appears or the visualizer is deactivated.
    LaunchedEffect(active) {
        while (active) {
            val candidate = runCatching { audioSessionIdProvider() }.getOrDefault(0)
            if (candidate > 0 && candidate != sessionId) {
                sessionId = candidate
                break
            }
            delay(250)
        }
        if (!active) {
            sessionId = 0
            latestFft.set(null)
        }
    }

    DisposableEffect(active, sessionId) {
        var visualizer: Visualizer? = null
        if (active && sessionId > 0) {
            visualizer =
                runCatching {
                    val captureRange = Visualizer.getCaptureSizeRange()
                    val requested = min(TARGET_CAPTURE_SIZE, captureRange[1])
                    Visualizer(sessionId).apply {
                        captureSize = max(requested, captureRange[0])
                        setDataCaptureListener(
                            object : Visualizer.OnDataCaptureListener {
                                override fun onWaveFormDataCapture(
                                    visualizer: Visualizer?,
                                    waveform: ByteArray?,
                                    samplingRate: Int,
                                ) = Unit

                                override fun onFftDataCapture(
                                    visualizer: Visualizer?,
                                    fft: ByteArray?,
                                    samplingRate: Int,
                                ) {
                                    fft?.let { latestFft.set(it.copyOf()) }
                                }
                            },
                            Visualizer.getMaxCaptureRate() / 2,
                            false,
                            true,
                        )
                        enabled = true
                    }
                }.getOrNull()
        }
        onDispose {
            runCatching {
                visualizer?.enabled = false
                visualizer?.release()
            }
            latestFft.set(null)
        }
    }

    // Animation loop: read the newest FFT, smooth toward it while playing and
    // decay toward the idle floor otherwise. Runs only while the strip exists
    // on screen (the artwork area of the expanded player). Bars are drawn in a
    // Canvas draw phase, so updating `levels` here only invalidates the strip's
    // draw — the rest of the player does not recompose.
    LaunchedEffect(active) {
        var smoothed = FloatArray(BAR_COUNT)
        var lastUpdate = 0L
        val frameInterval = 16_666_667L // ~60fps cap regardless of display refresh
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val captured = latestFft.get()
                val hasData = active && captured != null
                val desiredTarget = if (hasData) computeVisualizerLevels(captured, BAR_COUNT) else FloatArray(BAR_COUNT)

                // Throttle steady-state animation; still settle quickly after a
                // data source disappears (pause / track end).
                val changed = hasData || smoothed.any { it > IDLE_FLOOR + 0.01f }
                if (changed && frameTimeNanos - lastUpdate >= frameInterval) {
                    lastUpdate = frameTimeNanos
                    for (i in smoothed.indices) {
                        val desired = max(desiredTarget[i], IDLE_FLOOR)
                        smoothed[i] += (desired - smoothed[i]) * SMOOTHING_FACTOR
                        if (hasData) {
                            smoothed[i] *= DECAY_FACTOR // natural fall-off between captures
                        }
                    }
                    levels = smoothed.copyOf()
                }
            }
            // Idle: no data flowing and bars at the floor — back off instead of
            // waking at the frame rate so the strip costs nothing until
            // playback resumes or the next FFT arrives.
            val idle =
                (!active || latestFft.get() == null) &&
                    smoothed.all { it <= IDLE_FLOOR + 0.01f }
            if (idle) delay(250)
        }
    }

    Canvas(
        modifier = modifier,
    ) {
        val barWidthPx = BAR_WIDTH_DP.dp.toPx()
        val spacingPx = BAR_SPACING_DP.dp.toPx()
        val totalBarsWidth = BAR_COUNT * barWidthPx + (BAR_COUNT - 1) * spacingPx
        val startX = (size.width - totalBarsWidth) / 2f
        val availableHeight = size.height * 0.9f

        levels.forEachIndexed { index, level ->
            val barHeight = max(availableHeight * level.coerceIn(0f, 1f), 2.dp.toPx())
            val left = startX + index * (barWidthPx + spacingPx)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f),
            )
        }
    }
}

/**
 * Convenience wrapper: scrim pill + [PlayerArtworkVisualizer] bars, aligned to
 * the bottom-center of the artwork area by the caller's layout.
 */
@Composable
fun PlayerArtworkVisualizerOverlay(
    active: Boolean,
    audioSessionIdProvider: () -> Int,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(OVERLAY_HEIGHT_DP.dp / 2f))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .height(OVERLAY_HEIGHT_DP.dp)
                    .fillMaxWidth(0.72f),
        ) {
            PlayerArtworkVisualizer(
                active = active,
                audioSessionIdProvider = audioSessionIdProvider,
                barColor = barColor,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(OVERLAY_HEIGHT_DP.dp),
            )
        }
    }
}
