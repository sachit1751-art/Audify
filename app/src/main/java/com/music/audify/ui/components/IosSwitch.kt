package com.music.audify.ui.components

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.layout.size
import com.music.audify.ui.components.backdrop.backdrops.layerBackdrop
import com.music.audify.ui.components.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
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

/**
 * The iOS 26 switch: a wide green capsule and a white pill of a thumb, which
 * stretches a little toward where it is going while pressed.
 *
 * Pressed, or flipped by its row, the thumb lifts into iOS 26's glass: it grows
 * past the track and goes clear, refracting the track under it — see
 * [LiquidGlassThumb]. At rest it is plain drawing — a track, a thumb, a shadow —
 * and the glass is composed only while it is lifted, because every settings
 * page has a column of these and anything heavier per switch is paid that many
 * times over on every scroll frame.
 *
 * It takes Material3's `Switch` call shape so a settings row adopts it by name
 * alone. [thumbContent] and [colors] are accepted and ignored: on follows the
 * selected Audify theme, just like sliders and other selected states.
 *
 * A null [onCheckedChange] makes it a read-out, the way M3's does: the row
 * around it owns the tap.
 *
 * Interactive, it takes up M3's 48dp touch target around the drawn capsule, as
 * M3's does. The settings rows were spaced around that height — a sub-row has
 * no top padding of its own — and without it they close up on each other.
 *
 * It buzzes when [checked] flips rather than when it is tapped: most rows also
 * toggle from their own `onClick`, which never passes through here, and the
 * value changing is the one thing every path has in common.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassOk = isGlassSupported() && !reduceDynamicBlur && LocalLiquidGlassEnabled.current

    val accent = MaterialTheme.colorScheme.primary
    val trackOff = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)
    val dragWidthPx = with(density) { (TrackWidth - ThumbWidth - ThumbPadding * 2).toPx() }

    var didDrag by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val anim = remember(scope, dragWidthPx, rtl) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = if (checked) 1f else 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            consumeDown = true,
            onDragStarted = {
                didDrag = false
                dragFraction = requestedValue
            },
            onDrag = { _, dragAmount ->
                didDrag = true
                val step = (if (rtl) -dragAmount.x else dragAmount.x) / dragWidthPx
                dragFraction = (dragFraction + step).coerceIn(0f, 1f)
                updateValue(dragFraction)
            },
            onDragStopped = {
                if (didDrag) {
                    val target = dragFraction >= 0.5f
                    updateValue(if (target) 1f else 0f)
                    if (target != currentChecked) currentOnChange?.invoke(target)
                } else {
                    animateToValue(if (currentChecked) 0f else 1f)
                    currentOnChange?.invoke(!currentChecked)
                }
                didDrag = false
            },
            onDragCancelled = {
                didDrag = false
                updateValue(if (currentChecked) 1f else 0f)
            },
        )
    }

    val haptics = rememberHaptics()
    // Skips the first run, so a switch scrolling into view stays quiet.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked, anim) {
        val target = if (checked) 1f else 0f
        if (settled) haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
        // Dibalik oleh barisnya: thumb terangkat selama menempuh jarak.
        if (anim.requestedValue != target) anim.animateToValue(target)
        settled = true
    }

    val trackBackdrop = rememberLayerBackdrop()
    val capsule = remember { RoundedCornerShape(percent = 50) }
    // Kaca hanya dikomposisikan selama ada yang bergerak; diam, thumb putih biasa.
    val glass = glassOk && anim.isActive

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Switch
                            toggleableState = ToggleableState(checked)
                            if (enabled) onClick {
                                currentOnChange?.invoke(!currentChecked)
                                true
                            }
                        }
                        .then(if (enabled) anim.modifier else Modifier)
                } else {
                    Modifier
                },
            )
            .size(TrackWidth, TrackHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .then(if (glass) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(trackOff, accent, anim.value.coerceIn(0f, 1f))) }
                .size(TrackWidth, TrackHeight),
        )

        if (glass) {
            val thumbBackdrop = rememberBackdrop(trackBackdrop) { drawTrack ->
                val p = anim.pressProgress
                scale(lerp(2f / 3f, 0.75f, p), lerp(0f, 0.75f, p)) {
                    drawTrack()
                }
            }
            Box(
                Modifier
                    .graphicsLayer { switchThumbX(anim.value, dragWidthPx, rtl) }
                    .drawBackdrop(
                        backdrop = thumbBackdrop,
                        shape = { capsule },
                        effects = {
                            val p = anim.pressProgress
                            blur(8f.dp.toPx() * (1f - p))
                            lens(
                                5f.dp.toPx() * p,
                                10f.dp.toPx() * p,
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
                        layerBlock = { dampedThumbScale(anim, 50f) },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = (1f - anim.pressProgress).coerceIn(0f, 1f)))
                        },
                    )
                    .size(ThumbWidth, ThumbHeight),
            )
        } else {
            RestingThumb(
                Modifier
                    .graphicsLayer {
                        switchThumbX(anim.value, dragWidthPx, rtl)
                        dampedThumbScale(anim, 50f)
                    }
                    .size(ThumbWidth, ThumbHeight),
            )
        }
    }
}

/** Posisi thumb: dari padding di ujung mati sampai ujung hidup, mengikuti arah tata letak. */
private fun GraphicsLayerScope.switchThumbX(fraction: Float, dragPx: Float, rtl: Boolean) {
    val padding = ThumbPadding.toPx()
    val f = fraction.coerceIn(0f, 1f)
    translationX =
        if (rtl) lerp(-padding, -(padding + dragPx), f)
        else lerp(padding, padding + dragPx, f)
}

private val TrackWidth = 64.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp
private val ThumbPadding = 2.dp
