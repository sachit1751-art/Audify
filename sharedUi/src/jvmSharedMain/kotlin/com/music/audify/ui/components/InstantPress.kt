package com.music.audify.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * A clickable card whose touch effect shows the moment a finger lands.
 *
 * [clickable] inside a scrolling list holds back its press by the tap timeout
 * (about 100 ms) so a scroll that is only starting doesn't flash it, which
 * reads as an effect that needs a hold before it shows. This takes the touch
 * itself, in the initial pass and without consuming it: the card sinks, dims
 * and ripples at once, and lets go the instant the gesture turns into a
 * scroll, leaves the card, or ends.
 *
 * The scale is drawn outside the clip, so the whole card shrinks, rim and
 * all; the dim and the ripple are drawn inside it, so they keep its shape. The
 * click itself is still [clickable]'s, with its semantics and its own timing.
 */
@Composable
fun Modifier.instantPressClickable(
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = INSTANT_PRESS_SCALE,
): Modifier {
    val pressSource = remember { MutableInteractionSource() }
    val clickSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by pressSource.collectIsPressedAsState()
    // Read in draw only: a frame of the spring redraws the card, never recomposes it.
    val scaleAnim = animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "instantPressScale",
    )
    val dimAnim = animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 180),
        label = "instantPressDim",
    )
    return this
        .drawWithContent {
            val factor = scaleAnim.value
            if (factor == 1f) {
                drawContent()
            } else {
                scale(factor, factor) { this@drawWithContent.drawContent() }
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val press = PressInteraction.Press(down.position)
                pressSource.tryEmit(press)
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        // A scroll took the gesture: only a move can be one.
                        if (change.positionChange() != Offset.Zero && change.isConsumed) break
                        val p = change.position
                        if (p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height) break
                    }
                } finally {
                    pressSource.tryEmit(
                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                }
            }
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            val dim = dimAnim.value
            if (dim > 0f) drawRect(Color.Black.copy(alpha = INSTANT_PRESS_DIM * dim))
        }
        .indication(pressSource, indication)
        .clickable(
            interactionSource = clickSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** How far a touched card sinks. */
private const val INSTANT_PRESS_SCALE = 0.95f

/** How dark a touched card goes at full press. */
private const val INSTANT_PRESS_DIM = 0.18f