// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

private val EDGE_BLUR_RADIUS = 24.dp
private val BOTTOM_EDGE_HEIGHT = 132.dp
private const val EDGE_FADE_STOPS = 9

private val EdgeBlurFade = Brush.verticalGradient(
    colorStops = Array(EDGE_FADE_STOPS + 1) { i ->
        val t = i / EDGE_FADE_STOPS.toFloat()
        t to Color.Black.copy(alpha = 1f - EaseInOut.transform(t))
    },
)

/** A restrained backdrop blur that feathers scroll content at one screen edge. */
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
    val height = if (edge == ScrollBlurEdge.Top) topBarHeight() + 16.dp else BOTTOM_EDGE_HEIGHT
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (edge == ScrollBlurEdge.Bottom) Modifier.rotate(180f) else Modifier)
            // Mask the sampled layer itself. This keeps the blur feathered at
            // the inner edge instead of drawing a tinted material slab over
            // the last 132dp of the feed. Rotating the whole layer mirrors the
            // exact same ramp at the bottom without changing its colour math.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(EdgeBlurFade, blendMode = BlendMode.DstIn)
            }
            .hazeEffect(state = hazeState) {
                backgroundColor = pageColor
                tints = emptyList()
                blurRadius = EDGE_BLUR_RADIUS
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(0.33f)
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseInOut,
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
    )
}

enum class ScrollBlurEdge { Top, Bottom }
