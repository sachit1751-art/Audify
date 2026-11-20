/*
 * The selection pill's motion — the travel spring, the held lift, the
 * acceleration squash and the glass-to-flat hand-off, with their constants —
 * is ported from liquid_glass_easy's LiquidGlassAnimatedNavBar and
 * LiquidGlassLensMotion (pub.dev/packages/liquid_glass_easy, 4.3.4).
 * Copyright (c) 2025 Ahmed Gamil, MIT License.
 *
 * The rendering is Audify's own, on the vendored backdrop library: the lens
 * refracts the tab bar's exported glass and its recorded tab row rather than a
 * screen capture.
 */
package com.music.audify.ui.components.floatingtabbar

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.components.LocalAppBackdrop
import com.music.audify.ui.components.glassContentColor
import com.music.audify.ui.components.isGlassSupported
import com.music.audify.ui.components.backdrop.effects.blur
import com.music.audify.ui.components.backdrop.effects.colorControls
import com.music.audify.ui.components.backdrop.Backdrop
import com.music.audify.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.audify.ui.components.backdrop.backdrops.emptyBackdrop
import com.music.audify.ui.components.backdrop.drawBackdrop
import com.music.audify.ui.components.backdrop.effects.lens
import com.music.audify.ui.components.backdrop.highlight.Highlight
import com.music.audify.ui.components.backdrop.shadow.InnerShadow
import com.music.audify.ui.components.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

/** How much taller than the bar the pill stands while it is lifted. */
internal val PILL_GROW_HEIGHT = 22.dp

/**
 * The selection pill's physics, owned by the tab bar and handed to whichever
 * pill is drawing it.
 *
 * A tap lifts the pill at once — it inflates past the bar on two
 * under-damped springs, one per axis, so it overshoots its raised size and
 * rebounds — and it stays up for the whole journey. The travel is its own
 * spring. Only once the pill has landed ([HANDOVER_START] of the way there) is
 * the lift released, so it deflates where it stands, dipping a little under
 * its resting size and coming back. A drag is the same lift held open by a
 * finger.
 *
 * Along the way the pill's drawn position is sampled every frame and
 * differentiated twice: it stretches wide and flat as it launches, squashes
 * narrow and tall as it brakes, and is undeformed at constant speed. A travel
 * keeps one sense of that deformation for the whole trip, keyed to where it is
 * going; a finger keeps the raw force.
 *
 * The glass is a third, much faster spring, so the pill reads as glass almost
 * as soon as it is touched, and sheds it ([glassPresence]) from the landing
 * while the size is still settling. Once nothing is left to see, [isFlat]
 * flips and the plain pill takes over — two identical pictures, so there is
 * nothing to cross-fade.
 *
 * Positions are in tab steps: `0` is the first tab, `1` the second.
 */
@Stable
internal class GlassPillMotion(
    private val scope: CoroutineScope,
    initialIndex: Int,
    /** Time stretch for the lift and the glass hand-off: 1 is the tab pill's pace, above 1 slower and softer. */
    private val slow: Double = 1.0,
) {
    /** Where the pill is drawn, in tab steps. */
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    /** The width lift, `0` at rest and `1` at the raised size; overshoots both. */
    var liftX by mutableFloatStateOf(0f)
        private set

    /** The height lift; see [liftX]. */
    var liftY by mutableFloatStateOf(0f)
        private set

    /** How much of the material is on, `0`..`1`. */
    var material by mutableFloatStateOf(0f)
        private set

    /** Acceleration squash: width scales by `1 + this`, height by `1 - this`. */
    var deviation by mutableFloatStateOf(0f)
        private set

    /** How much of the pill still reads as glass, `1` lifted to `0` flat. */
    var glassPresence by mutableFloatStateOf(0f)
        private set

    /**
     * How much of the frosted blur is back, `0` while a finger holds the pill
     * (or a tap carries it) to `1` at rest. It comes back on its own fast
     * spring from the moment of release, not from the landing.
     */
    var blurMix by mutableFloatStateOf(1f)
        private set

    /**
     * True when there is nothing left in the pill but a flat fill at rest size,
     * where the plain pill draws exactly the same picture.
     */
    var isFlat by mutableStateOf(true)
        private set

    /** The tab the pill is headed for. */
    var index: Int = initialIndex
        private set

    /** One tab step in dp, which the squash model samples the pill's travel in. */
    var stepDp: Float = 0f

    /** "Reduce animation": every move lands at once, with no lift. */
    var reduceMotion: Boolean = false

    /** Pengali squash akselerasi dari pengaturan indikator; 1 = bawaan. */
    var squashScale: Float = 1f

    private var travelPos = initialIndex.toDouble()
    private var travelVel = 0.0
    private var travelTarget = travelPos
    private var travelFrom = travelPos
    private var travelActive = false

    private var lifted = false
    private var liftXPos = 0.0
    private var liftXVel = 0.0
    private var liftYPos = 0.0
    private var liftYVel = 0.0
    private var liftPos = 0.0
    private var liftVel = 0.0

    private var dragging = false
    private var dragTarget = 0.0
    private var dragFollow = 0.0

    private var travelSign = 0.0
    private var travelSignEased = 0.0
    private var handover = 1.0
    private var blur = 1.0
    private var releasedFromDrag = false
    private var squash = 0.0
    private val squashModel = PillSquashModel()

    private var running = false

    /** A tab was chosen: travel there, lifted. Re-choosing the current tab is a no-op. */
    fun animateTo(next: Int) {
        if (next == index) return
        if (reduceMotion) {
            snapTo(next)
            return
        }
        index = next
        releasedFromDrag = false
        travelActive = true
        lifted = true
        // Retarget from wherever the pill is; the spring keeps its velocity.
        travelFrom = travelPos
        travelTarget = next.toDouble()
        travelSign = signOfTravel(travelTarget - travelFrom)
        isFlat = false
        start()
    }

    /** Place the pill on [target] at rest, with no travel and no lift. */
    fun snapTo(target: Int) {
        index = target
        travelPos = target.toDouble()
        travelTarget = travelPos
        travelFrom = travelPos
        travelVel = 0.0
        travelActive = false
        dragging = false
        lifted = false
        liftXPos = 0.0; liftXVel = 0.0
        liftYPos = 0.0; liftYVel = 0.0
        liftPos = 0.0; liftVel = 0.0
        handover = 1.0
        blur = 1.0
        releasedFromDrag = false
        squash = 0.0
        squashModel.stop()
        travelSign = 0.0
        travelSignEased = 0.0
        publish()
        isFlat = true
    }

    /**
     * Back from a tab that has no place in this row (Search): the pill starts
     * at [from], a step past the row where that tab sits, and travels in lifted
     * like any other move, instead of appearing on the tab with no motion.
     */
    fun enterFrom(from: Double, next: Int) {
        if (reduceMotion) {
            snapTo(next)
            return
        }
        index = next
        dragging = false
        releasedFromDrag = false
        travelPos = from
        travelVel = 0.0
        travelFrom = from
        travelTarget = next.toDouble()
        travelSign = signOfTravel(travelTarget - travelFrom)
        travelActive = true
        lifted = true
        isFlat = false
        publish()
        start()
    }

    /** A finger has picked the pill up where it stands. */
    fun startDrag() {
        dragging = true
        releasedFromDrag = false
        travelActive = false
        lifted = !reduceMotion
        // The hand takes the deformation back off the travel.
        travelSign = 0.0
        dragFollow = travelPos
        dragTarget = travelPos
        travelVel = 0.0
        isFlat = false
        start()
    }

    /** The finger is over [position] tab steps. */
    fun dragTo(position: Float) {
        if (dragging) dragTarget = position.toDouble()
    }

    /**
     * The finger let go and the pill is to settle on [next]. It stays lifted
     * through the snap and comes down on landing, exactly as a tap's does.
     */
    fun release(next: Int) {
        if (!dragging) return
        val from = dragFollow
        dragging = false
        releasedFromDrag = true
        index = next
        if (reduceMotion) {
            snapTo(next)
            return
        }
        travelActive = true
        travelPos = from
        travelVel = 0.0
        travelFrom = from
        travelTarget = next.toDouble()
        travelSign = signOfTravel(travelTarget - travelFrom)
        start()
    }

    private fun start() {
        if (running) return
        running = true
        scope.launch {
            var last = -1L
            try {
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = if (last < 0) 0.0 else (now - last) / 1e9
                    last = now
                    if (!tick(dt, now / 1e9)) break
                }
            } finally {
                running = false
            }
        }
    }

    /** One frame of everything. False once all of it has settled. */
    private fun tick(dt: Double, now: Double): Boolean {
        // 1) Travel.
        var travelSettled = true
        if (travelActive) {
            val r = springStep(travelPos, travelVel, travelTarget, dt, TRAVEL_STIFFNESS, TRAVEL_DAMPING)
            travelPos = r.first
            travelVel = r.second
            travelSettled = abs(travelPos - travelTarget) < 0.003 && abs(travelVel) < 0.05
            if (travelSettled) {
                travelPos = travelTarget
                travelVel = 0.0
            }
        }

        // 2) Under a finger, chase it smoothly.
        if (dragging) {
            dragFollow += if (reduceMotion) {
                dragTarget - dragFollow
            } else {
                (dragTarget - dragFollow) * (1 - exp(-dt / FOLLOW_TAU))
            }
        }

        // 3) The lift, held for the journey and released on landing.
        if (!dragging && (travelSettled || travelProgress() >= HANDOVER_START)) {
            lifted = false
        }
        val liftTarget = if (lifted) 1.0 else 0.0
        stepLift(liftXPos, liftXVel, liftTarget, dt, LIFT_STIFFNESS / (slow * slow), LIFT_DAMPING_X / slow).let {
            liftXPos = it.first; liftXVel = it.second
        }
        stepLift(liftYPos, liftYVel, liftTarget, dt, LIFT_STIFFNESS / (slow * slow), LIFT_DAMPING_Y / slow).let {
            liftYPos = it.first; liftYVel = it.second
        }
        stepLift(liftPos, liftVel, liftTarget, dt, MATERIAL_STIFFNESS / (slow * slow), MATERIAL_DAMPING / slow).let {
            liftPos = it.first; liftVel = it.second
        }
        val liftSettled = !lifted && liftXPos == 0.0 && liftYPos == 0.0 && liftPos == 0.0

        // 4) The deflation outlives the spring that carried the pill there.
        if (travelActive && travelSettled && liftSettled && !dragging) {
            travelActive = false
        }

        // 5) Sample the pill where it is drawn this frame.
        val frac = if (dragging) dragFollow else travelPos
        if (stepDp > 0f) {
            if (!squashModel.isTracking) squashModel.start()
            squash = squashModel.track(frac * stepDp, now, dt)
            if (travelSignEased == 0.0) {
                travelSignEased = travelSign
            } else if (travelSignEased != travelSign) {
                travelSignEased += (travelSign - travelSignEased) * (1 - exp(-dt / SIGN_TAU))
                if (abs(travelSign - travelSignEased) < 0.01) travelSignEased = travelSign
            }
            // Keep the force's magnitude and take its sign from the direction
            // of travel, so a trip deforms one way the whole way.
            val key = travelSignEased
            if (key != 0.0) squash = squash * (1 - abs(key)) - key * abs(squash)
        }

        // 6) The hand-off rides the same landing as the deflation.
        val handoverTarget = if (lifted) 0.0 else 1.0
        val tau = (if (handoverTarget > handover) HANDOVER_TAU else GLASS_RETURN_TAU) * slow
        handover += (handoverTarget - handover) * (1 - exp(-dt / tau))
        if (abs(handoverTarget - handover) < 0.002) handover = handoverTarget

        // 7) The frosted blur: off while held, back the instant a finger lets
        // go (a tap's blur returns when its lift is released on landing).
        val blurTarget = if (dragging || (lifted && !releasedFromDrag)) 0.0 else 1.0
        val blurTau = (if (blurTarget > blur) BLUR_RETURN_TAU else BLUR_OFF_TAU) * slow
        blur += (blurTarget - blur) * (1 - exp(-dt / blurTau))
        if (abs(blurTarget - blur) < 0.002) blur = blurTarget

        val settled = !travelActive && !dragging && liftSettled &&
            abs(squash) < 0.0005 && handover >= 1.0 && blur >= 1.0
        if (settled) {
            squashModel.stop()
            squash = 0.0
            travelSign = 0.0
            travelSignEased = 0.0
        }
        publish()
        isFlat = settled
        return !settled
    }

    private fun publish() {
        position = (if (dragging) dragFollow else travelPos).toFloat()
        liftX = liftXPos.toFloat()
        liftY = liftYPos.toFloat()
        material = liftPos.coerceIn(0.0, 1.0).toFloat()
        deviation = squash.toFloat()
        glassPresence = (1 - handover).coerceIn(0.0, 1.0).toFloat()
        blurMix = blur.coerceIn(0.0, 1.0).toFloat()
    }

    private fun travelProgress(): Double {
        val span = abs(travelTarget - travelFrom)
        if (span < 1e-6) return 1.0
        return (1 - abs(travelTarget - travelPos) / span).coerceIn(0.0, 1.0)
    }

    private fun signOfTravel(span: Double): Double = if (abs(span) < 1e-6) 0.0 else sign(span)

    /**
     * [liveSize] for the flat pill, which is drawn inside the bar's clip: it
     * never exceeds the resting width or height, so a lifted pill cannot be
     * cut off at the top, bottom, or at the first and last tab while the blur
     * fades back in.
     */
    fun flatSize(rest: Size, lifted: Size): Size {
        val s = liveSize(rest, lifted)
        return Size(
            s.width.coerceAtMost(rest.width),
            s.height.coerceAtMost(rest.height),
        )
    }

    /** The size the pill is drawn at this frame, between [rest] and [lifted]. */
    fun liveSize(rest: Size, lifted: Size): Size {
        val w = rest.width + (lifted.width - rest.width) * liftX
        val h = rest.height + (lifted.height - rest.height) * liftY
        val d = deviation * squashScale
        return Size(w * (1 + d), h * (1 - d))
    }

    private companion object {
        const val TRAVEL_STIFFNESS = 280.0
        const val TRAVEL_DAMPING = 31.4

        /** Damping ratio 0.6 across, 0.7 down, so width overshoots a shade further. */
        const val LIFT_STIFFNESS = 250.0
        const val LIFT_DAMPING_X = 19.0
        const val LIFT_DAMPING_Y = 22.1

        /** Critically damped and four times as stiff: the glass is on long before the size settles. */
        const val MATERIAL_STIFFNESS = 1000.0
        const val MATERIAL_DAMPING = 63.3

        const val HANDOVER_START = 0.92
        const val HANDOVER_TAU = 0.09
        const val GLASS_RETURN_TAU = 0.05
        const val FOLLOW_TAU = 0.05
        const val SIGN_TAU = 0.25

        /** Fast and soft: the blur is back in about a tenth of a second. */
        const val BLUR_RETURN_TAU = 0.06
        const val BLUR_OFF_TAU = 0.04

        /** Semi-implicit Euler in 1/240 s substeps, so a long frame cannot blow it up. */
        fun springStep(
            x: Double,
            vel: Double,
            target: Double,
            dt: Double,
            stiffness: Double,
            damping: Double,
        ): Pair<Double, Double> {
            var t = dt
            var px = x
            var pv = vel
            while (t > 0) {
                val step = if (t > 1 / 240.0) 1 / 240.0 else t
                val accel = -stiffness * (px - target) - damping * pv
                pv += accel * step
                px += pv * step
                t -= step
            }
            return px to pv
        }

        /** A spring step snapped to its target once it has nothing left to say. */
        fun stepLift(
            x: Double,
            vel: Double,
            target: Double,
            dt: Double,
            stiffness: Double,
            damping: Double,
        ): Pair<Double, Double> {
            val r = springStep(x, vel, target, dt, stiffness, damping)
            return if (abs(r.first - target) < 0.0008 && abs(r.second) < 0.01) target to 0.0 else r
        }
    }
}

/**
 * Acceleration to deformation: the pill's position over a short window,
 * differentiated twice and averaged, scaled and clamped, then eased so a
 * single noisy frame cannot flick it. Tuned in dp/s².
 */
private class PillSquashModel {
    private val history = ArrayList<Pair<Double, Double>>()
    var isTracking = false
        private set
    private var value = 0.0

    fun start() {
        history.clear()
        value = 0.0
        isTracking = true
    }

    fun stop() {
        isTracking = false
        history.clear()
        value = 0.0
    }

    fun track(x: Double, now: Double, dt: Double): Double {
        if (!isTracking) return value
        history.add(x to now)
        val cutoff = now - SAMPLE_WINDOW
        history.removeAll { it.second < cutoff }
        val raw = (averageAcceleration() * SENSITIVITY).coerceIn(-MAX_DEFORMATION, MAX_DEFORMATION)
        val ease = (dt / RESPONSE_TIME).coerceIn(0.0, 1.0)
        value += (raw - value) * ease
        return value
    }

    private fun averageAcceleration(): Double {
        if (history.size < 3) return 0.0
        val velocities = ArrayList<Pair<Double, Double>>(history.size)
        for (i in 1 until history.size) {
            val dt = history[i].second - history[i - 1].second
            if (dt <= 0) continue
            velocities.add(
                (history[i].first - history[i - 1].first) / dt to
                    (history[i].second + history[i - 1].second) / 2,
            )
        }
        if (velocities.size < 2) return 0.0
        var total = 0.0
        var count = 0
        for (i in 1 until velocities.size) {
            val dt = velocities[i].second - velocities[i - 1].second
            if (dt <= 0) continue
            total += (velocities[i].first - velocities[i - 1].first) / dt
            count++
        }
        return if (count == 0) 0.0 else total / count
    }

    private companion object {
        const val SAMPLE_WINDOW = 0.3
        const val SENSITIVITY = 0.00007

        /** ±12 %: the pill travels inside the bar, and more would climb out of it. */
        const val MAX_DEFORMATION = 0.12
        const val RESPONSE_TIME = 0.18
    }
}

/**
 * Places a pill-sized child centred on [center] at [size], inside a parent it
 * matches the size of — so the pill can move and grow past that parent's
 * edges without ever changing its measured size. Both read on layout, not
 * composition, so a frame of motion is a relayout and nothing more.
 */
internal fun Modifier.pillPlacement(center: () -> Offset, size: () -> Size): Modifier =
    layout { measurable, constraints ->
        val s = size()
        val w = s.width.roundToInt().coerceAtLeast(1)
        val h = s.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.minWidth, constraints.minHeight) {
            val c = center()
            placeable.place((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt())
        }
    }

/** The flat pill: a fill and nothing else. */
@Composable
internal fun FlatSelectionPill(
    color: Color,
    modifier: Modifier,
    barGlass: LayerBackdrop? = null,
) {
    val shape = remember { RoundedCornerShape(percent = 50) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val ind by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    if (reduceDynamicBlur || !isGlassSupported()) {
        Box(modifier.background(color, shape))
        return
    }
    val page = LocalAppBackdrop.current
    val blurPx = with(LocalDensity.current) { INDICATOR_BLUR.toPx() * ind.blur }
    val backdrop = remember(page, barGlass) { PageAndBarBackdrop(page, barGlass) }
    // Frosted: halaman dan kaca bar di bawah indikator di-blur, lalu diberi
    // warna fill indikator di atasnya. Tab digambar di atas pill ini, jadi
    // ikon dan labelnya tetap tajam.
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = INDICATOR_BRIGHTNESS * ind.brightness,
                    saturation = INDICATOR_SATURATION * ind.saturation,
                )
                blur(blurPx)
                // Lengkung lensa tetap ada saat indikator diam, tidak hanya saat digeser.
                if (ind.restLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    lens(
                        refractionHeight = PILL_LENS_HEIGHT.toPx() * ind.restRefractionWidth,
                        refractionAmount = PILL_LENS_AMOUNT.toPx() * ind.restRefraction,
                        depthEffect = ind.depth,
                        chromaticAberration = false,
                    )
                }
            },
            highlight = {
                Highlight.Ambient.copy(
                    width = 1.dp,
                    alpha = (REST_RIM_ALPHA * ind.rim).coerceIn(0f, 1f),
                )
            },
            shadow = null,
            innerShadow = {
                InnerShadow(
                    radius = REST_INNER_RADIUS * ind.innerRadius,
                    offset = DpOffset(0.dp, 0.dp),
                    alpha = (REST_INNER_ALPHA * ind.innerShadow).coerceIn(0f, 1f),
                )
            },
            onDrawSurface = {
                drawRect(
                    color = color.copy(alpha = (color.alpha * ind.fill).coerceIn(0f, 1f)),
                    size = size,
                )
            },
        ),
    )
}

/** Blur radius of the resting selection indicator. */
private val INDICATOR_BLUR = 10.dp

/** Saturation of the page and bar glass seen through the resting indicator. */
private const val INDICATOR_SATURATION = 2.4f

/** A small lift so the blurred backdrop under the indicator reads brighter than the bar. */
private const val INDICATOR_BRIGHTNESS = 0.05f

/**
 * The resting indicator's edge: an even rim and a soft inner shadow, so its top
 * and bottom read as curving inward even when no lens is running. The lifted
 * pill fades down to exactly these values, so the hand-off has no jump.
 */
private const val REST_RIM_ALPHA = 0.45f
private val REST_INNER_RADIUS = 5.dp
private const val REST_INNER_ALPHA = 0.55f

/** Inner shadow radius of the lifted pill at full material. */
private val LIFT_INNER_RADIUS = 10.dp

/** What the resting indicator blurs: the page, then the bar's own glass over it. */
private class PageAndBarBackdrop(
    private val page: Backdrop,
    private val bar: Backdrop?,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(page) { drawBackdrop(density, coordinates, layerBlock) }
        bar?.let { with(it) { drawBackdrop(density, coordinates, layerBlock) } }
    }
}

/**
 * The lifted pill: a lens over the tab bar that refracts the bar's own glass
 * and its tab row, drawn above both and free to stand past the bar's edges.
 *
 * Clear while it is lifted — the selection fill fades out as the material
 * comes on and back in as it goes — with a rim and a contact shadow that are
 * the first things shed on landing, and refraction that eases out behind them.
 * At the end of that it is drawing the bar, the fill and the tabs with no lens,
 * which is exactly what [FlatSelectionPill] under the tabs draws.
 */
@Composable
internal fun GlassSelectionPill(
    motion: GlassPillMotion,
    fillColor: Color,
    barGlass: LayerBackdrop,
    tabRow: LayerBackdrop,
    modifier: Modifier,
    /**
     * Colour the tab row is masked to inside the pill: the accent copy of the
     * row that Kyant's LiquidBottomTabs records for its lens. Unspecified keeps
     * the row's own colours.
     */
    iconTint: Color = Color.Unspecified,
) {
    val page = LocalAppBackdrop.current
    val ind by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    val shape = remember { RoundedCornerShape(percent = 50) }
    // White rim on a dark bar, dark rim on a light one: a white edge over a
    // light page is invisible, so the lens would have nothing to bend.
    val darkGlass = glassContentColor() == Color.White
    val edge = if (darkGlass) {
        Color.White.copy(alpha = BAR_RIM_ALPHA)
    } else {
        Color.Black.copy(alpha = BAR_RIM_ALPHA * 0.7f)
    }
    // The tab row is drawn on top of the lens, not through it: refracting it
    // stretched the glyphs and flipped the labels near the pill's edge.
    val iconLiftMul = ind.iconLift
    val fillMul = ind.fill
    val tabsBackdrop = remember(tabRow, iconTint, motion, iconLiftMul) {
        PillTabsBackdrop(tabRow, iconTint) { 1f + PILL_ICON_LIFT_SCALE * iconLiftMul * motion.material }
    }
    val backdrop = remember(page, barGlass, tabsBackdrop, fillColor, edge, fillMul) {
        SelectionPillBackdrop(page, barGlass, tabsBackdrop, Color.Unspecified, edge) {
            fillColor.copy(alpha = (fillColor.alpha * fillMul * (1f - motion.material)).coerceIn(0f, 1f))
        }
    }
    Box(
        // Cross-fades into the blurred flat pill underneath as the blur returns.
        modifier.graphicsLayer { alpha = 1f - motion.blurMix }.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Dari lengkung saat diam ke lengkung saat terangkat, tanpa lompatan
                    // ke pil datar yang mengambil alih sesudah mendarat.
                    val restAmount = if (ind.restLens) PILL_LENS_AMOUNT.toPx() * ind.restRefraction else 0f
                    val restHeight = if (ind.restLens) PILL_LENS_HEIGHT.toPx() * ind.restRefractionWidth else 0f
                    val liftAmount = PILL_LENS_AMOUNT.toPx() * ind.liftRefraction
                    val liftHeight = PILL_LENS_HEIGHT.toPx() * ind.liftRefractionWidth
                    lens(
                        refractionHeight = restHeight + (liftHeight - restHeight) * motion.glassPresence,
                        refractionAmount = restAmount + (liftAmount - restAmount) * motion.material,
                        depthEffect = ind.depth,
                        chromaticAberration = ind.rainbow > 0f,
                        chromaticStrength = PILL_CHROMATIC_STRENGTH * ind.rainbow * motion.material,
                    )
                }
            },
            highlight = {
                val live = motion.material * rimOf(motion.glassPresence)
                Highlight.Ambient.copy(
                    width = 1.dp,
                    alpha = restBlend((REST_RIM_ALPHA * ind.rim).coerceIn(0f, 1f), live),
                )
            },
            shadow = {
                val alpha = motion.material * rimOf(motion.glassPresence)
                if (alpha > 0f) PILL_SHADOW.copy(alpha = (alpha * ind.shadow).coerceIn(0f, 1f)) else null
            },
            innerShadow = {
                val live = motion.material * rimOf(motion.glassPresence)
                InnerShadow(
                    radius = (REST_INNER_RADIUS + (LIFT_INNER_RADIUS - REST_INNER_RADIUS) * live) * ind.innerRadius,
                    offset = DpOffset(0.dp, 0.dp),
                    alpha = restBlend((REST_INNER_ALPHA * ind.innerShadow).coerceIn(0f, 1f), live),
                )
            },
        ),
    )
}

/**
 * The pill's lens, with the numbers of Kyant's Glass Bottom Bar tutorial:
 * `lens(16dp, 32dp)`. The amount is twice the height, so the band along the
 * edge is narrow but bends hard, which is what makes the bar's top and bottom
 * edges visibly warp where the pill stands past them.
 */
private val PILL_LENS_HEIGHT = 16.dp
private val PILL_LENS_AMOUNT = 32.dp

/** Ikon dan label di dalam pill terangkat membesar sebesar ini pada material penuh (Kyant: 1 -> 1.2). */
private const val PILL_ICON_LIFT_SCALE = 0.2f

/**
 * Rainbow fringe of the lifted pill, a fraction of the shader's full strength.
 * At 1 the dispersion paints a thick rainbow around the corners; this keeps
 * only a faint hint of it.
 */
private const val PILL_CHROMATIC_STRENGTH = 0.15f

/** The rim and its shadow are gone by the time the hand-off is 55% through. */
private fun rimOf(presence: Float): Float = ((presence - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** [rest] at `live` 0, up to 1 at `live` 1. */
private fun restBlend(rest: Float, live: Float): Float = (rest + (1f - rest) * live).coerceIn(0f, 1f)

private val LENS_HEIGHT = 20.dp
private val LENS_AMOUNT = 28.dp

/** The bar's own edge, drawn into what the lens refracts so the lens bends it too. */
private val BAR_RIM_WIDTH = 1.dp
private const val BAR_RIM_ALPHA = 0.35f

/** How far the bar's shadow halo reaches past its edge, and how dark it starts. */
private val BAR_HALO_REACH = 8.dp
private const val BAR_HALO_ALPHA = 0.10f
private const val BAR_HALO_STEPS = 7

private val PILL_SHADOW = Shadow(
    radius = 12.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.3f),
)

/**
 * What the pill refracts, bottom to top: the page, the bar's glass, the
 * selection fill, and the tab row. The fill sits under the tabs here for the
 * same reason the flat pill does — over them it would grey the glyphs.
 */
private class SelectionPillBackdrop(
    private val page: Backdrop,
    private val bar: LayerBackdrop,
    private val tabs: Backdrop,
    private val tint: Color,
    private val edge: Color,
    private val fill: () -> Color,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(page) { drawBackdrop(density, coordinates, layerBlock) }
        with(bar) { drawBackdrop(density, coordinates, layerBlock) }
        drawBarRim(bar, coordinates, edge)
        val color = fill()
        if (color.alpha > 0f) {
            // Past every edge: the recorded area includes the lens's padding.
            drawRect(
                color = color,
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 3, size.height * 3),
            )
        }
        if (tint != Color.Unspecified) {
            // The tab row, masked to one colour: what the pill shows of the
            // tabs under it, refracted by the lens.
            drawTinted(tint) { with(tabs) { drawBackdrop(density, coordinates, layerBlock) } }
        } else {
            with(tabs) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}

/**
 * The exported bar glass has no rim (it is recorded without one), so the lens
 * would refract a bar with no visible edge. This strokes the bar's pill edge at
 * the bar's own position, inside the pill's coordinate space, before the lens
 * bends everything.
 */
private fun DrawScope.drawBarRim(bar: LayerBackdrop, coordinates: LayoutCoordinates?, edge: Color) {
    val barCoordinates = bar.layerCoordinates ?: return
    val here = coordinates ?: return
    if (!barCoordinates.isAttached || !here.isAttached) return
    val offset = try {
        barCoordinates.localPositionOf(here)
    } catch (_: Exception) {
        here.positionInWindow() - barCoordinates.positionInWindow()
    }
    val barSize = barCoordinates.size.toSize()
    if (barSize.width <= 0f || barSize.height <= 0f) return
    val strokeWidth = BAR_RIM_WIDTH.toPx()
    val radius = minOf(barSize.width, barSize.height) / 2f
    val haloReach = BAR_HALO_REACH.toPx()
    val ring = haloReach / BAR_HALO_STEPS
    // Bright along the top and bottom edges, dim between.
    val rim = Brush.verticalGradient(
        colors = listOf(edge, edge.copy(alpha = edge.alpha * 0.3f), edge),
        startY = 0f,
        endY = barSize.height,
    )
    translate(-offset.x, -offset.y) {
        // The bar's soft shadow, which the exported glass leaves out: a dark
        // halo fading outward from the edge, so the pill's lens has something
        // to bend on the far side of the edge as well as on the near side.
        for (i in 1..BAR_HALO_STEPS) {
            val t = i / BAR_HALO_STEPS.toFloat()
            val grow = haloReach * t
            drawRoundRect(
                color = Color.Black.copy(alpha = BAR_HALO_ALPHA * (1f - t)),
                topLeft = Offset(-grow, -grow),
                size = Size(barSize.width + grow * 2f, barSize.height + grow * 2f),
                cornerRadius = CornerRadius(radius + grow, radius + grow),
                style = Stroke(width = ring),
            )
        }
        drawRoundRect(
            brush = rim,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(barSize.width - strokeWidth, barSize.height - strokeWidth),
            cornerRadius = CornerRadius(radius - strokeWidth / 2f, radius - strokeWidth / 2f),
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * The tab row under the lifted pill, masked to [tint] when set, with no
 * refraction. Digambar diperbesar terhadap pusat pill sebesar [iconScale],
 * jadi ikon dan label di dalam lens ikut membesar saat pill terangkat,
 * seperti LiquidBottomTab Kyant (1 -> 1.2 mengikuti tekanan).
 */
private class PillTabsBackdrop(
    private val tabs: Backdrop,
    private val tint: Color,
    private val iconScale: () -> Float = { 1f },
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val s = iconScale()
        val draw: DrawScope.() -> Unit = {
            scale(s, pivot = center) {
                with(tabs) { drawBackdrop(density, coordinates, layerBlock) }
            }
        }
        if (tint != Color.Unspecified) drawTinted(tint, draw) else draw()
    }
}

/** Draws [block] and recolours everything it painted to [tint], keeping its alpha. */
private fun DrawScope.drawTinted(tint: Color, block: DrawScope.() -> Unit) {
    val paint = Paint().apply { colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn) }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f), paint)
    block()
    canvas.restore()
}
