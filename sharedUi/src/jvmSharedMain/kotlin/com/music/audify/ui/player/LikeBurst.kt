package com.music.audify.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A short, artwork-tinted burst emitted only when a track becomes liked. */
@Composable
internal fun LikeBurst(
    trigger: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier = modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val fade = (1f - p).coerceIn(0f, 1f)
        repeat(8) { index ->
            val angle = (index * 45.0 - 90.0) * PI / 180.0
            val distance = size.minDimension * (0.12f + 0.36f * p)
            val point = Offset(
                center.x + cos(angle).toFloat() * distance,
                center.y + sin(angle).toFloat() * distance,
            )
            val color = if (index % 3 == 0) Color(0xFFFFD66B) else tint
            if (index % 2 == 0) {
                val r = 3.4.dp.toPx() * (1f - p * 0.35f)
                val heart = Path().apply {
                    moveTo(point.x, point.y + r * 0.9f)
                    cubicTo(point.x - r * 2f, point.y - r * 0.2f, point.x - r * 1.1f, point.y - r * 1.8f, point.x, point.y - r * 0.8f)
                    cubicTo(point.x + r * 1.1f, point.y - r * 1.8f, point.x + r * 2f, point.y - r * 0.2f, point.x, point.y + r * 0.9f)
                    close()
                }
                drawPath(heart, color.copy(alpha = fade))
            } else {
                val radius = 2.dp.toPx() * (1f - p * 0.25f)
                drawLine(color.copy(alpha = fade), Offset(point.x - radius * 1.8f, point.y), Offset(point.x + radius * 1.8f, point.y), strokeWidth = 1.4.dp.toPx())
                drawLine(color.copy(alpha = fade), Offset(point.x, point.y - radius * 1.8f), Offset(point.x, point.y + radius * 1.8f), strokeWidth = 1.4.dp.toPx())
            }
        }
    }
}
