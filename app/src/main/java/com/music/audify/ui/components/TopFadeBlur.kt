// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * The run the fade needs below the bar to get from full blur to none without
 * the eye finding where it got there.
 *
 * Shortened from 120, then from 88: the blur is meant to belong to the top of
 * the screen, and at 88 the strip came to 167dp on a 1080p phone — over a fifth
 * of the display held under a blur that was only ever there to carry a 52dp
 * bar. It reads as a hazy band the page scrolls through rather than as glass
 * behind the bar.
 *
 * Shortening it is safe in the direction that used to be the problem. The
 * gradient is eased across the whole strip rather than across this run alone,
 * so a shorter strip decays to nothing *sooner in absolute distance*: the first
 * heading, which sits a fixed distance down the screen, ends up with less blur
 * behind it than before rather than more. What it costs is intensity over the
 * bar's own glyphs, which is why this cannot keep going — the tail still has to
 * reach nothing before the layer ends, or the layer's end is the edge the whole
 * fade exists to hide.
 */
private val FADE_RUN = 32.dp

/**
 * How much blur the fade reaches at its outer edge — short of all of it.
 *
 * The last quarter buys almost nothing visually and costs the most: a blur has
 * nothing to sample past the edge of its own layer, so the harder it is pushed
 * there the more of the layer is flat material colour rather than blurred
 * content, and the more that edge reads as a band of colour laid over the page.
 * Stopping at three quarters keeps the ramp and loses the band.
 */
private const val PEAK = 0.75f

/**
 * How dark the readability scrim starts, at the very top of the strip.
 *
 * Modest on purpose: it is there to give white glyphs a floor on a pale sleeve,
 * not to grey out the artwork. Anything heavier and the bar stops being a fade
 * over a picture and starts being a header with a picture behind it.
 */
private const val SCRIM_PEAK = 0.42f

/** Enough stops that the ramp does not band across a near-flat colour. */
private const val SCRIM_STOPS = 12

/**
 * The glass behind artwork-led top bars: full blur along the top edge, ramping
 * to nothing on the way down.
 *
 * A bar carrying a uniform pane is a rectangle sitting on the page, and its
 * bottom edge is a line drawn across whatever scrolls under it. That reads
 * worst on a detail page, whose artwork runs up under the status bar, but it
 * is the same hard edge on a feed. Ordinary pages now deliberately accept that
 * bounded pane and use [TopBarBlur]. Artwork-led pages, including Replay, now
 * use their own full-bleed background with no status-bar effect layered over
 * it, so this progressive variant is no longer mounted by the app chrome.
 *
 * Fading out instead leaves the title and back arrow something to be legible
 * against and the page nothing to be interrupted by.
 */
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    /**
     * The colour of the page behind this: the theme's background on a feed, a
     * detail page's artwork wash on one of those. See the effect below for why
     * it cannot just be the theme's in both cases.
     */
    pageColor: Color,
    modifier: Modifier = Modifier,
    /** Cross-fades against the bottom edge treatment as the page scrolls. */
    alpha: Float = 1f,
    /**
     * A wash laid over the blur to keep the bar's glyphs readable.
     *
     * Blur alone does not settle contrast — it makes a pale sleeve into a pale
     * blur, and a row of bright artwork scrolling under a feed's title into a
     * bright smear. The scrim gives the glyphs a floor to sit on whatever
     * happens to pass beneath them.
     *
     * Laid *over* the blur rather than under it, which is the only order that
     * works: haze samples the content tagged as its source, not whatever
     * sibling happens to sit between that content and itself, so a scrim
     * underneath would be painted over by the blurred content and do nothing.
     */
    scrimColor: Color,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val progressiveScrollEdgeBlur by AppSettings.progressiveScrollEdgeBlur.collectAsStateWithLifecycle()
    // Respect both the global reduced-blur preference and the user's specific
    // progressive-edge preference. The top and bottom edges are one setting.
    if (reduceDynamicBlur || !progressiveScrollEdgeBlur) return

    // Start at the top of the window and fade through the status bar, top bar,
    // and its content gap. Starting this layer below the bar creates a visible
    // horizontal seam across artwork because the blur has a hard layer edge.
    // The gradient reaches zero before the first content row begins.
    val height = topBarHeight() + TopBarContentGap + FADE_RUN
    val fadeModifier = modifier
        .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) }
    Box(
        modifier = fadeModifier
            .fillMaxWidth()
            .height(height)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                // Keyed to the colour of the page underneath, not the theme's.
                // The progressive ramp is kept in the immutable Blur style.
                style = HazeMaterials.ultraThin(pageColor).then {
                    progressive(
                        HazeProgressive.verticalGradient(
                            easing = EaseOutCubic,
                            startIntensity = PEAK,
                            endIntensity = 0f,
                        ),
                    )
                    // Uniform noise would show over the untouched foot of the ramp.
                    noiseFactor(0f)
                },
                performanceMode = HazePerformanceMode.Performance,
            ),
    )

    val scrim = remember(scrimColor) {
        Brush.verticalGradient(
            // The same eased-out shape as the blur above it, so the two arrive
            // at nothing together. A scrim that outlasted the blur would leave
            // a tinted band hanging below a fade that had already finished —
            // the one artefact this bar exists to avoid.
            colorStops = Array(SCRIM_STOPS) { i ->
                val t = i / (SCRIM_STOPS - 1f)
                t to scrimColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
            },
        )
    }
    Box(
        modifier = fadeModifier
            .fillMaxWidth()
            .height(height)
            .background(scrim),
    )
}

/**
 * The uniform glass used by ordinary top bars.
 *
 * Unlike [TopFadeBlur], this occupies only the status-bar inset and the 52dp
 * bar itself. Its material is deliberately identical to [FloatingBottomBar]'s
 * non-liquid-glass surface: the same regular Haze material, the same surface
 * colour and the same reduced-resolution sampling supplied by
 * [optimizedHazeEffect]. [FrostedTopBar] draws the hairline at its lower edge.
 *
 * Replay and artwork detail pages draw neither component and let their own
 * full-bleed background show through unchanged.
 */
@Composable
fun TopBarBlur(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // FrostedTopBar supplies the solid surface when dynamic blur is reduced.
    if (reduceDynamicBlur) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight())
            .optimizedHazeEffect(
                state = hazeState,
                style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
            ),
    )
}
