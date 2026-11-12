// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
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
    edge: ScrollBlurEdge,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val progressiveScrollEdgeBlur by AppSettings.progressiveScrollEdgeBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur || !progressiveScrollEdgeBlur) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Scale with the actual app window (including split-screen and
        // landscape), with bounds that keep the fade useful on tiny windows
        // without covering an excessive part of tall displays.
        val height: Dp = when (edge) {
            ScrollBlurEdge.Top -> (maxHeight * 0.12f).coerceIn(88.dp, 144.dp)
            ScrollBlurEdge.Bottom -> (maxHeight * 0.14f).coerceIn(104.dp, 176.dp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (edge == ScrollBlurEdge.Bottom) Modifier.rotate(180f) else Modifier)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    // A small neutral surface lift keeps the blur perceptible
                    // on bright and dark artwork without tinting it with the
                    // selected wallpaper or creating an opaque colour band.
                    style = HazeMaterials.ultraThin(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                    ).then {
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
}

enum class ScrollBlurEdge { Top, Bottom }
