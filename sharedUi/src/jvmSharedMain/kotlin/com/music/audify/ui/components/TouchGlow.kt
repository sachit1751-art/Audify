package com.music.audify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A soft light that follows a finger across a surface. One modifier for every
 * panel, popup and drawer, so the glow reads the same everywhere. Watched in
 * the Initial pass without consuming anything. Put it after the surface's clip
 * so the light stays inside the shape.
 */
@Composable
fun Modifier.touchGlow(
    radius: Dp = 120.dp,
    strength: Float = 1f,
    color: Color = Color.White,
): Modifier {
    val scope = rememberCoroutineScope()
    val center = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val glow = remember { Animatable(0f) }
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var down = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val finger = event.changes.firstOrNull { it.pressed }
                    if (finger != null) {
                        val at = finger.position
                        if (!down) {
                            down = true
                            scope.launch {
                                center.snapTo(at)
                                glow.animateTo(1f, tween(140))
                            }
                        } else {
                            scope.launch { center.animateTo(at, spring(dampingRatio = 1f, stiffness = 900f)) }
                        }
                    } else if (down) {
                        down = false
                        scope.launch { glow.animateTo(0f, tween(380)) }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val a = glow.value
            if (a > 0.01f) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = 0.09f * strength * a),
                        0.55f to color.copy(alpha = 0.03f * strength * a),
                        1f to Color.Transparent,
                        center = center.value,
                        radius = radius.toPx(),
                    ),
                )
            }
        }
}