// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.motion

import kotlin.test.Test
import kotlin.test.assertTrue

class MotionTokensTest {
    @Test
    fun microInteractionDurationsStayWithinTheUsabilityBudget() {
        assertTrue(MotionTokens.PRESS_FEEDBACK_MS in 90..120)
        assertTrue(MotionTokens.ICON_STATE_MS in 140..180)
        assertTrue(MotionTokens.TOGGLE_STATE_MS in 160..200)
        assertTrue(MotionTokens.SMALL_POPUP_MS in 150..190)
        assertTrue(MotionTokens.SELECTION_INDICATOR_MS in 180..240)
        assertTrue(MotionTokens.ARTWORK_CROSSFADE_MS in 180..240)
    }

    @Test
    fun pressScaleRemainsSubtleAndDoesNotShrinkTheTouchTargetSemantics() {
        assertTrue(MotionTokens.PRESS_SCALE in 0.95f..1f)
    }
}
