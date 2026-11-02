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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

private const val SCROLL_EDGE_BLUR_PEAK = 0.38f

/** A restrained backdrop blur that feathers scroll content at one screen edge. */
@Composable
fun ProgressiveScrollEdgeBlur(
    hazeState: HazeState,
    pageColor: Color,
    edge: ScrollBlurEdge,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val progressiveScrollEdgeBlur by AppSettings.progressiveScrollEdgeBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur || !progressiveScrollEdgeBlur) return

    val height: Dp = when (edge) {
        ScrollBlurEdge.Top -> 116.dp
        ScrollBlurEdge.Bottom -> 132.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (edge == ScrollBlurEdge.Bottom) Modifier.rotate(180f) else Modifier)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeMaterials.ultraThin(pageColor).then {
                    progressive(
                        HazeProgressive.verticalGradient(
                            easing = EaseOutCubic,
                            startIntensity = SCROLL_EDGE_BLUR_PEAK,
                            endIntensity = 0f,
                        ),
                    )
                    noiseFactor(0f)
                },
                performanceMode = HazePerformanceMode.Performance,
            ),
    )
}

enum class ScrollBlurEdge { Top, Bottom }
