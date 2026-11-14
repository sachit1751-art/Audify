/*
 * Adapted from Convx's IosOverscroll.kt (GPL-3.0):
 * https://github.com/cosmictaserdev-creator/Convx
 */
package com.music.audify.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private const val rubberBandConstant = 0.55f
private const val fallbackContainerPx = 2_000f
private const val maxBounceVelocity = 10_000f
private const val highVelocityThreshold = 5_000f
private const val normalBounceStiffness = 247f
private const val fastBounceStiffness = 130f

/**
 * Converts raw pull distance to UIKit's self-limiting rubber-band curve.
 */
private fun rubberBand(rawDistance: Float, containerPx: Float): Float {
    val dimension = containerPx.takeIf { it > 0f } ?: fallbackContainerPx
    val distance = abs(rawDistance)
    val banded = (1f - 1f / (distance * rubberBandConstant / dimension + 1f)) *
        dimension / rubberBandConstant
    return banded * sign(rawDistance)
}

/**
 * An iOS-style overscroll effect for Compose scroll containers.
 *
 * It replaces Android's stretch/glow with a UIScrollView-like rubber band during a
 * drag, then critically damps the content back to its resting position on release.
 */
/** An [OverscrollEffect] that also says how far the content is pulled down right now. */
interface PullDownOverscrollEffect : OverscrollEffect {
    /** Pixels the content sits below its resting place; 0 when idle or pushed up. Snapshot-observable. */
    val pullDownPx: Float
}

private class IosOverscrollEffect(
    // Batas tarikan ke bawah (px). 0 = tanpa batas, ikut tinggi kontainer.
    private val maxPullDownPx: Float = 0f,
    // Batas tarikan ke segala arah (px), dipakai panel menu. 0 = tanpa batas.
    private val maxPullPx: Float = 0f,
) : PullDownOverscrollEffect {
    private val rawPullX = mutableFloatStateOf(0f)
    private val rawPullY = mutableFloatStateOf(0f)
    private var containerWidthPx = 0f
    private var containerHeightPx = 0f

    // The release bounce runs outside the scrollable's scroll mutex, so a new drag
    // doesn't cancel it on its own. Tracked here so a touch can catch the content.
    private var settleJobs: List<Job> = emptyList()

    override val isInProgress: Boolean
        get() = rawPullX.floatValue != 0f || rawPullY.floatValue != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // A finger landing mid-bounce takes over from wherever the content is, like
        // UIScrollView; otherwise the spring keeps overwriting the drag each frame.
        if (source == NestedScrollSource.UserInput) stopSettling()

        // When pulling back from an existing stretch, settle the stretch before
        // allowing the list itself to scroll. This keeps the motion continuous.
        val paidX = payDown(rawPullX, delta.x)
        val paidY = payDown(rawPullY, delta.y)
        val remaining = Offset(delta.x - paidX, delta.y - paidY)
        val consumed = performScroll(remaining)
        val leftover = remaining - consumed

        // Absorb only touch deltas. Fling leftovers must be returned so Compose
        // cancels its decay and gives their velocity to applyToFling below.
        var stretchedX = 0f
        var stretchedY = 0f
        if (source == NestedScrollSource.UserInput) {
            if (leftover.x != 0f) {
                rawPullX.floatValue += leftover.x
                stretchedX = leftover.x
            }
            if (leftover.y != 0f) {
                rawPullY.floatValue += leftover.y
                stretchedY = leftover.y
            }
        }
        return Offset(consumed.x + paidX + stretchedX, consumed.y + paidY + stretchedY)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val consumed = performFling(velocity)
        val leftover = velocity - consumed
        stopSettling()
        if (!isInProgress && leftover == Velocity.Zero) return

        coroutineScope {
            val jobs = listOf(
                launch { settle(rawPullX, leftover.x) },
                launch { settle(rawPullY, leftover.y) },
            )
            settleJobs = jobs
            jobs.forEach { it.join() }
            if (settleJobs === jobs) settleJobs = emptyList()
        }
    }

    private fun stopSettling() {
        if (settleJobs.isEmpty()) return
        settleJobs.forEach { it.cancel() }
        settleJobs = emptyList()
    }

    private fun payDown(pull: MutableFloatState, delta: Float): Float {
        val current = pull.floatValue
        if (current == 0f || delta == 0f || sign(current) == sign(delta)) return 0f
        val target = current + delta
        val next = if (sign(target) != sign(current)) 0f else target
        pull.floatValue = next
        return next - current
    }

    private suspend fun settle(pull: MutableFloatState, leftoverVelocity: Float) {
        if (pull.floatValue == 0f && leftoverVelocity == 0f) return
        val velocity = leftoverVelocity.coerceIn(-maxBounceVelocity, maxBounceVelocity)
        val job = currentCoroutineContext()[Job]
        animate(
            initialValue = pull.floatValue,
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = if (abs(velocity) > highVelocityThreshold) {
                    fastBounceStiffness
                } else {
                    normalBounceStiffness
                },
            ),
        ) { value, _ ->
            // A frame already in flight when a drag cancels us must not stomp the drag.
            if (job?.isActive != false) pull.floatValue = value
        }
    }

    private fun currentOffsetY(): Float {
        val raw = rawPullY.floatValue
        // Tarikan ke bawah dibatasi supaya tidak pernah melewati batas yang
        // diminta pemakainya. Tarikan ke atas tetap memakai kurva biasa.
        return if (maxPullPx > 0f) {
            rubberBand(raw, maxPullPx * rubberBandConstant)
        } else if (maxPullDownPx > 0f && raw > 0f) {
            rubberBand(raw, maxPullDownPx * rubberBandConstant)
        } else {
            rubberBand(raw, containerHeightPx)
        }
    }

    override val pullDownPx: Float
        get() = currentOffsetY().coerceAtLeast(0f)

    override val node: DelegatableNode = IosOverscrollNode(
        offsetX = {
            if (maxPullPx > 0f) {
                rubberBand(rawPullX.floatValue, maxPullPx * rubberBandConstant)
            } else {
                rubberBand(rawPullX.floatValue, containerWidthPx)
            }
        },
        offsetY = { currentOffsetY() },
        onMeasured = { width, height ->
            containerWidthPx = width
            containerHeightPx = height
        },
    )
}

private class IosOverscrollNode(
    private val offsetX: () -> Float,
    private val offsetY: () -> Float,
    private val onMeasured: (Float, Float) -> Unit,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        onMeasured(
            (if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width).toFloat(),
            (if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height).toFloat(),
        )
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = offsetX()
                translationY = offsetY()
            }
        }
    }
}

private class IosOverscrollFactory(
    private val density: Density,
    private val scope: CoroutineScope,
    private val maxPullPx: Float = 0f,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = IosOverscrollEffect(maxPullPx = maxPullPx)

    override fun equals(other: Any?): Boolean =
        other is IosOverscrollFactory && other.density == density && other.scope === scope &&
            other.maxPullPx == maxPullPx

    override fun hashCode(): Int =
        31 * (31 * density.hashCode() + scope.hashCode()) + maxPullPx.hashCode()
}

/** Provides iOS-style rubber-band overscroll to every descendant scroll container. */
@Composable
fun rememberIosOverscrollFactory(): OverscrollFactory {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(density, scope) { IosOverscrollFactory(density, scope) }
}

/** Overscroll iOS untuk panel menu: tarikannya pendek (maks [maxPull]), tidak ikut tinggi panel. */
@Composable
fun rememberIosMenuOverscrollFactory(maxPull: Dp = 36.dp): OverscrollFactory {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    return remember(density, scope, maxPull) {
        IosOverscrollFactory(density, scope, with(density) { maxPull.toPx() })
    }
}

/** Efek iOS yang tarikan ke bawahnya tidak pernah melewati [maxPullDown]. */
@Composable
fun rememberIosOverscrollEffect(maxPullDown: Dp): PullDownOverscrollEffect {
    val density = LocalDensity.current
    return remember(density, maxPullDown) {
        IosOverscrollEffect(with(density) { maxPullDown.toPx() })
    }
}
