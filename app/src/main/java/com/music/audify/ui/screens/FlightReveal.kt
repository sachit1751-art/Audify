package com.music.audify.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.audify.ui.components.coverFlightPending
import com.music.audify.ui.components.enterReveal

/**
 * [enterReveal], plus (bila [afterFlight]) menunggu cover terbang selesai dan
 * overlay-nya lepas. Cover terbang digambar di atas seluruh halaman, jadi teks
 * yang muncul selama penerbangan tampil di belakangnya lalu meloncat ke depan
 * saat cover mendarat. Dengan menunggu, teks langsung muncul di depan cover.
 * Halaman yang dibuka tanpa penerbangan tidak menunggu apa pun.
 */
@Composable
internal fun Modifier.flightReveal(
    delayMs: Int = 0,
    rise: Dp = 14.dp,
    afterFlight: Boolean = false,
): Modifier {
    val base = this.enterReveal(delayMs, rise)
    if (!afterFlight) return base
    val flying = coverFlightPending()
    val landed by animateFloatAsState(
        targetValue = if (flying) 0f else 1f,
        animationSpec = if (flying) {
            snap()
        } else {
            tween(durationMillis = 360, delayMillis = delayMs / 3, easing = FastOutSlowInEasing)
        },
        label = "flightReveal",
    )
    return base.graphicsLayer {
        alpha = landed
        translationY = (1f - landed) * rise.toPx()
    }
}