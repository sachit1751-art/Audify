@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.music.audify.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Cover -> release page transition (Apple Music style): the tapped card's
 * cover is the same element as the sleeve on the page it opens, so it moves
 * and resizes there instead of two covers cross-fading.
 *
 * Only the card that was tapped carries the shared element (see
 * [markCoverTapped]), so the same album shown on two cards never collides.
 */
val LocalCoverTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalCoverVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

// One tapped card per release rather than a single global. Opening an album and
// then an artist from it must not make the album's card forget it was the one
// tapped, or going back twice leaves the cover with nowhere to fly to.
private val tappedCovers = mutableStateMapOf<String, Any>()

/** Call from a card's click, before it navigates. [token] is the card's own `remember { Any() }`. */
fun markCoverTapped(browseId: String?, token: Any) {
    if (browseId.isNullOrEmpty()) return
    if (tappedCovers.size > 32 && !tappedCovers.containsKey(browseId)) tappedCovers.clear()
    tappedCovers[browseId] = token
}

/**
 * Marks a cover as the shared element for [browseId]. On a card pass its
 * [token]; on the page's own sleeve leave [token] null. Does nothing outside
 * [CoverTransitionBox] or when the card was not the one tapped.
 */
@Composable
fun Modifier.coverSharedElement(browseId: String?, token: Any? = null): Modifier {
    val scope = LocalCoverTransitionScope.current ?: return this
    val visibility = LocalCoverVisibilityScope.current ?: return this
    if (browseId.isNullOrEmpty()) return this
    val active = if (token == null) {
        true
    } else {
        // A boolean, so a tap recomposes only the cards whose answer flips.
        val tapped by remember(token, browseId) {
            derivedStateOf { tappedCovers[browseId] === token }
        }
        tapped
    }
    if (!active) return this
    return with(scope) {
        this@coverSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = "cover:$browseId"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> tween(durationMillis = 780, easing = CoverEasing) },
        )
    }
}

/**
 * A card's tap that opens a page: marks the cover, waits one frame so the card
 * has picked up its shared element and been laid out with it, then runs
 * [action]. Navigating in the very frame the marker flips can start the
 * transition before the source exists, and the cover then just fades.
 */
@Composable
fun rememberCoverTap(browseId: String?, token: Any, action: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(action)
    return {
        if (browseId.isNullOrEmpty()) {
            latest.value()
        } else {
            markCoverTapped(browseId, token)
            scope.launch {
                withFrameNanos { }
                latest.value()
            }
        }
    }
}

/** Long, soft landing: the cover is easy to follow and settles without a bounce. */
private val CoverEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * Fades and lifts a piece of a page in while its page is entering, after
 * [delayMs], so text and buttons arrive behind the cover rather than with it.
 * A page that is already on screen (or outside [CoverTransitionBox]) is drawn
 * as it is, with no animation. Runs on the draw layer only: no recomposition.
 */
@Composable
fun Modifier.enterReveal(delayMs: Int = 0, rise: Dp = 14.dp): Modifier {
    val visibility = LocalCoverVisibilityScope.current ?: return this
    val progress by visibility.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(durationMillis = 420, delayMillis = delayMs + 260, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 160)
            }
        },
        label = "enterReveal",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * rise.toPx()
    }
}

/** A Box that hosts the shared transition for everything inside it. */
@Composable
fun CoverTransitionBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalCoverTransitionScope provides this) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}