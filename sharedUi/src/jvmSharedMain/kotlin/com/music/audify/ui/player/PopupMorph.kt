package com.music.audify.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Where the last finger went down, in window space. A popup opened by that
 * touch grows out of this point and folds back into it. Only a fresh touch
 * counts, so a popup opened by a timer does not appear to come from an old tap.
 */
internal object PopupOrigin {
    private const val FRESH_MS = 1500L
    private var point = Offset.Unspecified
    private var atNanos = 0L

    fun record(windowPoint: Offset) {
        point = windowPoint
        atNanos = System.nanoTime()
    }

    fun take(): Offset? =
        if (point.isSpecified && (System.nanoTime() - atNanos) / 1_000_000L <= FRESH_MS) point else null
}

/** Records finger-down points into [PopupOrigin]; never consumes anything. */
@Composable
internal fun Modifier.recordPopupOrigin(): Modifier {
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { holder[0] = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        val at = event.changes.first().position
                        holder[0]?.takeIf { it.isAttached }?.let { PopupOrigin.record(it.localToWindow(at)) }
                    }
                }
            }
        }
}

/** Elliptical corners, so after the layer's uneven scale they still read as circles. */
private class PopupMorphShape(private val rx: Float, private val ry: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val x = rx.coerceIn(0f, size.width / 2f)
        val y = ry.coerceIn(0f, size.height / 2f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(x, y)))
    }
}

private const val MORPH_MIN_SCALE = 0.08f
private const val MORPH_UNIFORMITY = 0.45f
private const val MORPH_HEIGHT_LAG = 1.35f
private const val MORPH_FOLD_TILT = 7f
private const val MORPH_ALPHA_RAMP = 3f
private const val MORPH_FADE_START = 0.22f
private const val MORPH_FADE_END = 0.72f

/**
 * The window morph: 0 is the trigger ([origin]), 1 the open [card], both in one
 * coordinate space. Scale, travel, clip radius and a slight fold all follow [p],
 * with the height lagging the width so the card folds out of its trigger.
 */
internal fun GraphicsLayerScope.applyPopupMorph(p: Float, origin: Rect, card: Rect, cornerRadius: Float) {
    if (card.width <= 0f || card.height <= 0f) return
    val pw = p.coerceAtLeast(0f)
    val ph = pw.pow(MORPH_HEIGHT_LAG)

    val rawX = (origin.width / card.width).coerceIn(MORPH_MIN_SCALE, 1f)
    val rawY = (origin.height / card.height).coerceIn(MORPH_MIN_SCALE, 1f)
    val uniform = sqrt(rawX * rawY)
    val sx = lerp(lerp(rawX, uniform, MORPH_UNIFORMITY), 1f, pw)
    val sy = lerp(lerp(rawY, uniform, MORPH_UNIFORMITY), 1f, ph)
    scaleX = sx
    scaleY = sy

    val rest = (1f - p).coerceAtLeast(0f)
    val dx = origin.center.x - card.center.x
    val dy = origin.center.y - card.center.y
    translationX = dx * rest
    translationY = dy * rest

    alpha = (p * MORPH_ALPHA_RAMP).coerceIn(0f, 1f)

    val apparent = lerp(min(origin.width, origin.height) / 2f, cornerRadius, p.coerceIn(0f, 1f))
    clip = p < 1f
    shape = PopupMorphShape(apparent / sx, apparent / sy)

    cameraDistance = 16f * density
    rotationX = MORPH_FOLD_TILT * rest * (dy / (abs(dy) + card.height * 0.5f))
}

/** Fades the card's children in once it has grown, and out before it folds. */
internal fun Modifier.popupMorphContent(progress: () -> Float): Modifier = graphicsLayer {
    val span = (MORPH_FADE_END - MORPH_FADE_START).coerceAtLeast(0.01f)
    alpha = ((progress() - MORPH_FADE_START) / span).coerceIn(0f, 1f)
}