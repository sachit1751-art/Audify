package com.music.audify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.music.audify.ui.components.backdrop.backdrops.layerBackdrop
import com.music.audify.ui.components.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.components.backdrop.backdrops.rememberBackdrop
import com.music.audify.ui.components.backdrop.drawBackdrop
import com.music.audify.ui.components.backdrop.effects.blur
import com.music.audify.ui.components.backdrop.effects.lens
import com.music.audify.ui.components.backdrop.highlight.Highlight
import com.music.audify.ui.components.backdrop.shadow.InnerShadow
import com.music.audify.ui.components.backdrop.shadow.Shadow
import kotlin.math.abs

/**
 * The iOS 26 slider: a thin capsule track with a pill of a thumb. Touched, the
 * thumb lifts into glass and refracts the track under it (see
 * [LiquidGlassThumb]); let go, it settles back to white.
 *
 * It takes the shape of Material3's `Slider` the settings rows already call.
 * Horizontal drags and taps set the value, so a vertical swipe that starts on
 * it still scrolls the page. With [steps] above zero the value snaps to that
 * many evenly spaced stops between the ends. A null [activeColor] is the
 * theme's accent; a null [inactiveColor] is the platform's grey.
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
) {
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val active = activeColor ?: MaterialTheme.colorScheme.primary
    val inactive = inactiveColor ?: Color(0xFF8E8E93).copy(alpha = if (dark) 0.36f else 0.24f)
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassOk = isGlassSupported() && !reduceDynamicBlur && LocalLiquidGlassEnabled.current

    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val span = rangeEnd - rangeStart
    val thumbWidthPx = with(density) { SliderThumbWidth.toPx() }
    val scope = rememberCoroutineScope()
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    var widthPx by remember { mutableFloatStateOf(0f) }
    var didDrag by remember { mutableStateOf(false) }
    var downX by remember { mutableFloatStateOf(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    val trackBackdrop = rememberLayerBackdrop()
    val capsule = remember { RoundedCornerShape(percent = 50) }

    val anim = remember(scope, rangeStart, rangeEnd, steps, thumbWidthPx) {
        // Pusat thumb bergerak antara setengah thumb dari tiap ujung, jadi
        // pemetaan tidak berubah saat thumb membesar di bawah jari.
        fun usable() = (widthPx - thumbWidthPx).coerceAtLeast(1f)
        fun fractionAt(x: Float) = ((x - thumbWidthPx / 2f) / usable()).coerceIn(0f, 1f)
        fun snapped(v: Float): Float {
            val clamped = v.coerceIn(rangeStart, rangeEnd)
            if (steps <= 0 || span <= 0f) return clamped
            val stops = steps + 1
            val f = (clamped - rangeStart) / span
            return rangeStart + span * ((f * stops).roundToInt() / stops.toFloat())
        }
        DampedDragAnimation(
            animationScope = scope,
            initialValue = value.coerceIn(rangeStart, rangeEnd),
            valueRange = rangeStart..rangeEnd,
            visibilityThreshold = (span / 1000f).coerceAtLeast(1e-6f),
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = { position ->
                didDrag = false
                downX = position.x
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = true
                    val centre = thumbWidthPx / 2f + usable() * progress
                    // Jari di atas thumb: lanjut dari nilainya. Di tempat lain: loncat ke jari.
                    raw = if (abs(downX - centre) <= thumbWidthPx / 2f) {
                        requestedValue
                    } else {
                        rangeStart + span * fractionAt(downX)
                    }
                }
                raw = (raw + span * dragAmount.x / usable()).coerceIn(rangeStart, rangeEnd)
                latestOnValueChange(snapped(raw))
            },
            onDragStopped = {
                if (!didDrag) {
                    // Ketukan di trek.
                    val v = snapped(rangeStart + span * fractionAt(downX))
                    animateToValue(v)
                    latestOnValueChange(v)
                }
                didDrag = false
            },
            onDragCancelled = { didDrag = false },
        )
    }
    LaunchedEffect(value, anim) {
        val target = value.coerceIn(rangeStart, rangeEnd)
        if (anim.requestedValue != target) anim.updateValue(target)
    }

    // Kaca hanya dikomposisikan selama ada yang bergerak; diam, thumb putih biasa.
    val glass = glassOk && anim.isActive

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(if (enabled) anim.modifier else Modifier)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Trek: yang dilihat kaca thumb. Direkam hanya selama kaca aktif.
        Box(Modifier.then(if (glass) Modifier.layerBackdrop(trackBackdrop) else Modifier)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(inactive)
                    .height(SliderTrackHeight)
                    .fillMaxWidth(),
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(active)
                    .height(SliderTrackHeight)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        // Isian sampai pusat thumb.
                        val inset = thumbWidthPx / 2f
                        val end = (inset + (constraints.maxWidth - inset * 2f) * anim.progress)
                            .roundToInt()
                            .coerceIn(0, constraints.maxWidth)
                        layout(end, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }

        if (glass) {
            val thumbBackdrop = rememberBackdrop(trackBackdrop) { drawTrack ->
                val p = anim.pressProgress
                scale(lerp(2f / 3f, 1f, p), lerp(0f, 1f, p)) {
                    drawTrack()
                }
            }
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = (widthPx - thumbWidthPx).coerceAtLeast(0f) * anim.progress
                    }
                    .drawBackdrop(
                        backdrop = thumbBackdrop,
                        shape = { capsule },
                        effects = {
                            val p = anim.pressProgress
                            blur(8f.dp.toPx() * (1f - p))
                            lens(
                                10f.dp.toPx() * p,
                                14f.dp.toPx() * p,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            val p = anim.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = p,
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 4f.dp,
                                color = Color.Black.copy(alpha = 0.05f),
                            )
                        },
                        innerShadow = {
                            val p = anim.pressProgress
                            InnerShadow(
                                radius = 4f.dp * p,
                                alpha = p,
                            )
                        },
                        layerBlock = { dampedThumbScale(anim, 10f) },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = (1f - anim.pressProgress).coerceIn(0f, 1f)))
                        },
                    )
                    .size(SliderThumbWidth, SliderThumbHeight),
            )
        } else {
            RestingThumb(
                Modifier
                    .graphicsLayer {
                        translationX = (widthPx - thumbWidthPx).coerceAtLeast(0f) * anim.progress
                        dampedThumbScale(anim, 10f)
                    }
                    .size(SliderThumbWidth, SliderThumbHeight),
            )
        }
    }
}

private val SliderHeight = 40.dp
private val SliderTrackHeight = 6.dp
private val SliderThumbWidth = 40.dp
private val SliderThumbHeight = 24.dp