package com.music.audify.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.audify.ui.icons.AudifyIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where and when the last double-tap landed; [burst] counts them so each one replays the effect. */
@Stable
internal class DoubleTapLikeState {
    var burst by mutableIntStateOf(0)
        private set
    var origin by mutableStateOf(Offset.Unspecified)
        private set

    fun play(at: Offset) {
        origin = at
        burst++
    }
}

@Composable
internal fun rememberDoubleTapLikeState(): DoubleTapLikeState = remember { DoubleTapLikeState() }

/** Double-tap on the artwork. Single taps and drags are left to everything else. */
@Composable
internal fun Modifier.doubleTapToLike(
    state: DoubleTapLikeState,
    enabled: Boolean,
    onDoubleTap: () -> Unit,
): Modifier {
    val current by rememberUpdatedState(onDoubleTap)
    return pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onDoubleTap = { position ->
                state.play(position)
                current()
            },
        )
    }
}

/**
 * Bintang yang membesar dengan pantulan di titik ketukan, lalu naik dan memudar,
 * dengan kilau dan percikan kecil di sekitarnya. "Reduce animation" menyisakan
 * bintang yang muncul dan memudar singkat saja.
 */
@Composable
internal fun DoubleTapLikeBurst(
    state: DoubleTapLikeState,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(0f) }
    val spark = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val burst = state.burst
    val density = LocalDensity.current
    val starSize = 92.dp
    val starPx = with(density) { starSize.toPx() }

    LaunchedEffect(burst) {
        if (burst == 0) return@LaunchedEffect
        pop.snapTo(0f)
        spark.snapTo(0f)
        fade.snapTo(0f)
        coroutineScope {
            launch {
                pop.animateTo(
                    1f,
                    if (reduceMotion) tween(120) else spring(dampingRatio = 0.42f, stiffness = 520f),
                )
            }
            launch { spark.animateTo(1f, tween(if (reduceMotion) 120 else 560, easing = FastOutSlowInEasing)) }
            launch {
                delay(if (reduceMotion) 300L else 420L)
                fade.animateTo(1f, tween(if (reduceMotion) 160 else 340, easing = FastOutSlowInEasing))
            }
        }
    }

    if (burst == 0 || !state.origin.isSpecified) return

    Box(
        modifier.drawBehind {
            val o = state.origin
            if (!o.isSpecified || reduceMotion) return@drawBehind
            val s = spark.value
            val visible = 1f - fade.value
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.30f * (1f - s) * visible), Color.Transparent),
                    center = o,
                    radius = starPx * 0.95f,
                ),
                radius = starPx * 0.95f,
                center = o,
            )
            val turn = (burst % 3) * 0.26f
            for (i in 0 until 8) {
                val angle = (i * (2.0 * PI / 8)).toFloat() + turn
                val dist = starPx * (0.55f + 0.95f * s)
                drawCircle(
                    color = Color.White.copy(alpha = (1f - s) * visible * 0.9f),
                    radius = 4.dp.toPx() * (1f - 0.6f * s),
                    center = Offset(o.x + cos(angle) * dist, o.y + sin(angle) * dist),
                )
            }
        },
    ) {
        Icon(
            imageVector = AudifyIcons.StarFilled,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .offset {
                    val o = state.origin
                    IntOffset((o.x - starPx / 2f).roundToInt(), (o.y - starPx / 2f).roundToInt())
                }
                .size(starSize)
                .graphicsLayer {
                    val p = pop.value
                    val out = fade.value
                    val grow = p * (1f + 0.25f * out)
                    scaleX = grow
                    scaleY = grow
                    alpha = 1f - out
                    translationY = -48.dp.toPx() * out
                    rotationZ = if (reduceMotion) 0f else -14f * (1f - p)
                },
        )
    }
}