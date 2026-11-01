// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Keeps Haze's visual style while using its low-cost rendering profile for
 * surfaces whose large blur hides the extra detail of a quality render.
 *
 * Haze 2 expresses this as a performance policy rather than a source-pixel
 * fraction. Call sites with unusual progressive effects can supply a custom
 * [style] and performance mode.
 */
fun Modifier.optimizedHazeEffect(
    state: HazeState,
    style: HazeBlurStyle = HazeBlurStyle {},
    performanceMode: HazePerformanceMode = HazePerformanceMode.Performance,
): Modifier = hazeBlur(
    input = HazeInput.Sources(state),
    style = style,
    performanceMode = performanceMode,
)
