package com.music.audify.ui.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing

/** Shared motion values for the Android/JVM Compose surfaces. Keep replacements behavior-neutral. */
object MotionTokens {
    const val CONTROL_STATE_MS = 160
    const val SHORT_FADE_MS = 180
    const val CONTENT_TRANSITION_MS = 220
    const val SHEET_ENTER_MS = 260

    val STANDARD_EASING: Easing = FastOutSlowInEasing
    val LINEAR_EASING: Easing = LinearEasing
}
