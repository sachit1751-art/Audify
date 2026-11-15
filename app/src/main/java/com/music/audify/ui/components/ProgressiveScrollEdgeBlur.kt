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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

private const val SCROLL_EDGE_BLUR_PEAK = 0.38f

/** A restrained backdrop blur that feathers scroll content at one screen edge. */
@OptIn(ExperimentalHazeMaterialsApi::class)
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
    val height = when (edge) {
        ScrollBlurEdge.Top -> 116.dp
        ScrollBlurEdge.Bottom -> 132.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (edge == ScrollBlurEdge.Bottom) Modifier.rotate(180f) else Modifier)
            .optimizedHazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseOutCubic,
                    startIntensity = SCROLL_EDGE_BLUR_PEAK,
                    endIntensity = 0f,
                )
                noiseFactor = 0f
            },
    )
}

enum class ScrollBlurEdge { Top, Bottom }
