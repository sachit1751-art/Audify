// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing

/** Shared motion values for the Android/JVM Compose surfaces. Keep replacements behavior-neutral. */
object MotionTokens {
    const val CONTROL_STATE_MS = 160
    const val PRESS_FEEDBACK_MS = 105
    const val ICON_STATE_MS = 150
    const val TOGGLE_STATE_MS = 180
    const val SMALL_POPUP_MS = 170
    const val SELECTION_INDICATOR_MS = 210
    const val ARTWORK_CROSSFADE_MS = 210
    const val PRESS_SCALE = 0.96f

    const val SHORT_FADE_MS = 180
    const val CONTENT_TRANSITION_MS = 220
    const val SHEET_ENTER_MS = 260
    const val NAVIGATION_TRANSITION_MS = 220
    const val NAVIGATION_FADE_MS = 180
    const val NAVIGATION_TINT_MS = CONTROL_STATE_MS

    val STANDARD_EASING: Easing = FastOutSlowInEasing
    val LINEAR_EASING: Easing = LinearEasing
}
