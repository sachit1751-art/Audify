// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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

/** A restrained backdrop blur that feathers scroll content at one screen edge. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun ProgressiveScrollEdgeBlur(
    hazeState: HazeState,
    edge: ScrollBlurEdge,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val progressiveBlur by AppSettings.progressiveBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur || !progressiveBlur) return
    val blurSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val height: Dp = when (edge) {
            ScrollBlurEdge.Top -> (maxHeight * 0.12f).coerceIn(88.dp, 144.dp)
            ScrollBlurEdge.Bottom -> (maxHeight * 0.14f).coerceIn(104.dp, 176.dp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (edge == ScrollBlurEdge.Bottom) Modifier.rotate(180f) else Modifier)
                .hazeEffect(state = hazeState) {
                    backgroundColor = blurSurface
                    tints = emptyList()
                    blurRadius = 24.dp
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
}

enum class ScrollBlurEdge { Top, Bottom }
