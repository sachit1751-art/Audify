package com.music.audify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import com.music.audify.data.settings.AppSettings
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Kemunculan popup dengan spring jelly: kartu membesar dari ~0.8x, melewati
 * ukuran akhir sedikit, lalu bergoyang (X dan Y berlawanan fase) sebelum diam.
 * Dipasang di kartu popup (alert, popup audio) tepat setelah width, sebelum clip.
 * "Reduce animation" mematikannya.
 */
@Composable
internal fun Modifier.jellyPopIn(animateIn: Boolean = true, startAt: Float = 0f): Modifier {
    val reduce by AppSettings.reduceAnimation.collectAsState()
    // animateIn = false keeps only the rubber-band pull; the entrance is then
    // owned by MacOSPopupState (see MacOSInspiredPopup.kt).
    val progress = remember { Animatable(if (reduce || !animateIn) 1f else startAt) }
    LaunchedEffect(reduce, animateIn) {
        if (reduce || !animateIn) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f))
        }
    }
    // Rubber-band: the card follows a drag a little, with growing resistance,
    // and springs back on release. Events are only observed, never consumed, and
    // movement a child already took (a scrolling list) is ignored, so taps and
    // scrolling inside the card behave as before.
    val scope = rememberCoroutineScope()
    val pullX = remember { Animatable(0f) }
    val pullY = remember { Animatable(0f) }
    val maxPull = with(LocalDensity.current) { 14.dp.toPx() }
    val soft = maxPull * 6f
    return this
        .pointerInput(reduce) {
            if (reduce) return@pointerInput
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var total = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    if (!change.isConsumed) total += change.positionChange()
                    val dist = total.getDistance()
                    val past = (dist - slop).coerceAtLeast(0f)
                    val target = if (dist == 0f || past == 0f) {
                        Offset.Zero
                    } else {
                        total * (past / dist) * (maxPull / (past + soft))
                    }
                    scope.launch {
                        pullX.snapTo(target.x)
                        pullY.snapTo(target.y)
                    }
                }
                scope.launch { pullX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
                scope.launch { pullY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
            }
        }
        .graphicsLayer {
        translationX = pullX.value
        translationY = pullY.value
        val p = progress.value
        alpha = (p * 3f).coerceIn(0f, 1f)
        val scale = 0.92f + 0.08f * p
        val wobble = (p - 1f) * p * p * 0.12f
        scaleX = scale - wobble
        scaleY = scale + wobble
    }
}