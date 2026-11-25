// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.ui.motion.MotionTokens

private val PLAY_LEFT = arrayOf(
    Offset(22f, 10f), Offset(51f, 30f), Offset(51f, 70f), Offset(22f, 90f),
)
private val PAUSE_LEFT = arrayOf(
    Offset(22f, 10f), Offset(42f, 10f), Offset(42f, 90f), Offset(22f, 90f),
)
private val PLAY_RIGHT = arrayOf(
    Offset(51f, 30f), Offset(80f, 50f), Offset(51f, 70f), Offset(51f, 30f),
)
private val PAUSE_RIGHT = arrayOf(
    Offset(58f, 10f), Offset(78f, 10f), Offset(78f, 90f), Offset(58f, 90f),
)

private fun morphPath(
    from: Array<Offset>,
    to: Array<Offset>,
    progress: Float,
    width: Float,
    height: Float,
): Path = Path().apply {
    from.indices.forEach { index ->
        val start = from[index]
        val end = to[index]
        val x = (start.x + (end.x - start.x) * progress) * width / 100f
        val y = (start.y + (end.y - start.y) * progress) * height / 100f
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * Play/pause state change shared by the mini and full players.
 *
 * The play triangle separates into two moving halves that settle into the pause
 * bars (and reverse on resume). The caller's touch target and layout footprint
 * never change size.
 */
@Composable
fun PlayPauseMorphIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceAnimation) 0 else MotionTokens.ICON_STATE_MS,
            easing = MotionTokens.STANDARD_EASING,
        ),
        label = "playPausePathMorph",
    )

    Canvas(
        modifier = modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics {
                this.contentDescription = contentDescription
            } else Modifier),
    ) {
        drawPath(morphPath(PLAY_LEFT, PAUSE_LEFT, progress, this.size.width, this.size.height), tint)
        drawPath(morphPath(PLAY_RIGHT, PAUSE_RIGHT, progress, this.size.width, this.size.height), tint)
    }
}
