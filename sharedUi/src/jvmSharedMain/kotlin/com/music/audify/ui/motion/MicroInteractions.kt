// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.ui.player.PlayerSettings

/**
 * Restrained visual response for small, frequently tapped controls.
 * The clickable modifier must use the same [interactionSource]. The click itself
 * remains synchronous; this only changes the rendered layer while pressed.
 */
@Composable
fun Modifier.microPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = MotionTokens.PRESS_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) pressedScale else 1f,
        animationSpec = when {
            reduceMotion -> snap()
            pressed -> tween(MotionTokens.PRESS_FEEDBACK_MS, easing = MotionTokens.STANDARD_EASING)
            else -> spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "microPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The same theme-owned ripple across custom controls; omitted when motion is reduced. */
@Composable
fun microRipple(): Indication? {
    val reduceMotion by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    return if (reduceMotion) null else LocalIndication.current
}
