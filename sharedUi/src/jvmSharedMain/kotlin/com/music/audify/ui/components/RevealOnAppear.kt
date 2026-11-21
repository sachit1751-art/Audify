package com.music.audify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Bounded, saveable history for one-time section reveals. */
@Stable
class RevealSeenState(initial: List<String> = emptyList()) {
    private val seen = LinkedHashSet(initial.takeLast(MAX_SEEN_KEYS))
    private var burstAtNanos = 0L
    private var burstCount = 0

    fun hasSeen(key: String): Boolean = key in seen

    fun markSeen(key: String) {
        seen.remove(key)
        seen.add(key)
        while (seen.size > MAX_SEEN_KEYS) seen.remove(seen.first())
    }

    internal fun nextBurstDelayMillis(): Long {
        val now = System.nanoTime()
        if (now - burstAtNanos > BURST_WINDOW_NANOS) {
            burstAtNanos = now
            burstCount = 0
        }
        return (burstCount++).coerceAtMost(MAX_BURST_STEPS) * BURST_STEP_MS
    }

    internal fun savedKeys(): List<String> = seen.toList()

    companion object {
        private const val MAX_SEEN_KEYS = 160
        private const val BURST_WINDOW_NANOS = 110_000_000L
        private const val BURST_STEP_MS = 42L
        private const val MAX_BURST_STEPS = 4

        val Saver = listSaver<RevealSeenState, String>(
            save = { state: RevealSeenState -> state.savedKeys() },
            restore = { saved: List<String> -> RevealSeenState(saved) },
        )
    }
}

@Composable
fun rememberRevealSeenState(): RevealSeenState =
    rememberSaveable(saver = RevealSeenState.Saver) { RevealSeenState() }

/**
 * A short first-appearance reveal. A key is marked before animation starts, so
 * a fast fling or a returning screen never replays the same section animation.
 */
@Composable
fun Modifier.revealOnAppear(
    key: String,
    seenState: RevealSeenState,
    enabled: Boolean = true,
    durationMillis: Int = 260,
    rise: Dp = 10.dp,
): Modifier {
    val alreadySeen = remember(key, seenState) { seenState.hasSeen(key) }
    if (alreadySeen) return this

    val progress = remember(key, seenState) { Animatable(0f) }
    LaunchedEffect(key, enabled, seenState) {
        if (seenState.hasSeen(key)) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // Never leave content transparent when motion is disabled (or when
        // the loading gate changes while the item is composed).
        if (!enabled) {
            progress.snapTo(1f)
            seenState.markSeen(key)
            return@LaunchedEffect
        }
        delay(seenState.nextBurstDelayMillis())
        progress.animateTo(
            1f,
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        )
        seenState.markSeen(key)
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = (1f - value) * rise.toPx()
        // ModulateAlpha avoids allocating an offscreen layer for every shelf.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
