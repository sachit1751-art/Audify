// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.motion.MotionTokens

/** A unique queue result, so repeating the same action restarts its lifetime. */
data class QueueActionNotice(
    val id: Int,
    val message: String,
)

/** Brief queue confirmation shown immediately above the mini player. */
@Composable
fun QueueActionNoticeHost(
    notice: QueueActionNotice?,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            if (reduceAnimation) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(MotionTokens.SMALL_POPUP_MS)) +
                    slideInVertically(tween(MotionTokens.SMALL_POPUP_MS)) { height -> height / 10 }) togetherWith
                    (fadeOut(tween(MotionTokens.SHORT_FADE_MS)) +
                        slideOutVertically(tween(MotionTokens.SHORT_FADE_MS)) { height -> height / 10 })
            }
        },
        contentKey = { it?.id },
        label = "queueActionNotice",
        modifier = modifier.fillMaxWidth(),
    ) { current ->
        if (current != null) {
            Surface(
                color = Color(0xFF282828),
                contentColor = Color.White,
                shape = RoundedCornerShape(5.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}
