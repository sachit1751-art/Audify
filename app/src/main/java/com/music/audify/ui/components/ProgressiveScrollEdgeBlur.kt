// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

private const val SCROLL_EDGE_BLUR_PEAK = 0.38f
private val EDGE_BLUR_RADIUS = 24.dp

/** A restrained progressive backdrop blur that feathers scroll content at one screen edge. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun ProgressiveScrollEdgeBlur(
    hazeState: HazeState,
    pageColor: Color,
    edge: ScrollBlurEdge,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val progressiveBlur by AppSettings.progressiveBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur || !progressiveBlur) return

    val height: Dp = when (edge) {
        ScrollBlurEdge.Top -> 116.dp
        ScrollBlurEdge.Bottom -> 132.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (edge == ScrollBlurEdge.Bottom) Modifier.rotate(180f) else Modifier)
            .hazeEffect(state = hazeState) {
                // Haze 1.3's effect API replaces the 1.9.3 haze-blur artifact.
                // Keep the original reduced-resolution sampling and progressive
                // intensity ramp while tinting with the actual page colour.
                backgroundColor = pageColor.copy(alpha = 0.16f)
                tints = emptyList()
                blurRadius = EDGE_BLUR_RADIUS
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(0.33f)
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseOutCubic,
                    startIntensity = SCROLL_EDGE_BLUR_PEAK,
                    endIntensity = 0f,
                )
            },
    )
}

enum class ScrollBlurEdge { Top, Bottom }
