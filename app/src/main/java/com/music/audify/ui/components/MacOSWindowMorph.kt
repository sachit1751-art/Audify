package com.music.audify.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.music.audify.data.settings.AppSettings
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One progress value (0 = the trigger, 1 = the open window) drives a real
 * geometric morph: the card's rect is interpolated from the trigger's rect to
 * its own, the corner radius is interpolated alongside it, the content fades
 * in only once the container has grown, and on the way back the same values
 * run in reverse with the height collapsing ahead of the width, so the window
 * folds into the trigger rather than shrinking in place.
 */
internal data class MacOSPopupSpec(
    /** Smallest scale per axis, so a tiny trigger never produces a degenerate card. */
    val minScale: Float = 0.08f,
    /**
     * 0 follows the trigger's aspect ratio exactly; 1 starts from a uniform
     * scale. In between keeps a wide trigger from squashing the card flat.
     */
    val uniformity: Float = 0.45f,
    /** Height progress is width progress to this power: > 1 makes height lag, which reads as a fold. */
    val heightLag: Float = 1.35f,
    /** Perspective tilt, in degrees, at progress 0, toward the side the trigger is on. Negative flips it. */
    val foldTilt: Float = 7f,
    /** How fast the container's alpha reaches 1 (3 = opaque at a third of the way). */
    val alphaRamp: Float = 3f,
    /** The content fades in between these two progress values. */
    val contentFadeStart: Float = 0.22f,
    val contentFadeEnd: Float = 0.72f,
    /** Size of the stand-in trigger when only the last tap point is known. */
    val tapOriginSize: Dp = 44.dp,
    val openSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = 320f,
        visibilityThreshold = 0.001f,
    ),
    // Critically damped: no undershoot, and a coarser threshold so the card
    // is released as soon as it is invisible rather than after a long tail.
    val closeSpring: SpringSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = 480f,
        visibilityThreshold = 0.01f,
    ),
    val scrimColor: Color = Color.Black.copy(alpha = 0.32f),
)

/** A trigger element the popup grows out of and folds back into. */
@Stable
internal class PopupAnchor {
    internal var bounds by mutableStateOf<Rect?>(null)
}

@Composable
internal fun rememberPopupAnchor(): PopupAnchor = remember { PopupAnchor() }

/** Marks this element as the origin of a popup; bounds are kept in screen coordinates. */
internal fun Modifier.popupAnchor(anchor: PopupAnchor): Modifier = onGloballyPositioned { c ->
    if (c.isAttached) anchor.bounds = Rect(c.localToScreen(Offset.Zero), c.size.toSize())
}

/**
 * Where the last touch landed, for popups whose trigger registered no
 * [PopupAnchor]. Only a fresh touch counts: a popup opened by a timer or a
 * callback should not appear to come from an old tap.
 */
internal object PopupTapOrigin {
    private const val FRESH_MS = 1500L
    private var x = 0f
    private var y = 0f
    private var at = Long.MIN_VALUE

    fun record(rawX: Float, rawY: Float) {
        x = rawX
        y = rawY
        at = SystemClock.uptimeMillis()
    }

    fun take(): Offset? =
        if (at != Long.MIN_VALUE && SystemClock.uptimeMillis() - at <= FRESH_MS) Offset(x, y) else null
}

/** Records touches on the activity window into [PopupTapOrigin]; never consumes anything. */
@Composable
internal fun PopupTapOriginTracker() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.popupActivity()?.window
        val base = window?.callback
        if (window == null || base == null) return@DisposableEffect onDispose { }
        val tracking = object : Window.Callback by base {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_UP
                ) {
                    PopupTapOrigin.record(event.rawX, event.rawY)
                }
                return base.dispatchTouchEvent(event)
            }
        }
        window.callback = tracking
        onDispose { if (window.callback === tracking) window.callback = base }
    }
}

private fun Context.popupActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The animation state of one popup. [requestClose] plays the reverse morph and
 * only then calls the owner's `onClosed`, so the composable is released after
 * the motion, not before. A second request while closing is ignored, and
 * [reopen] turns a close around mid-flight from the current progress and velocity.
 */
@Stable
internal class MacOSPopupState internal constructor(
    private val scope: CoroutineScope,
    initialProgress: Float,
    private val tapOrigin: Offset?,
) {
    internal var spec: MacOSPopupSpec = MacOSPopupSpec()
    internal var animated: Boolean = true
    internal var onClosed: () -> Unit = {}
    internal var anchor: PopupAnchor? = null

    val progress = Animatable(initialProgress)

    var closing by mutableStateOf(false)
        private set

    /** The card's own rect, untransformed, in screen coordinates. */
    internal var bounds by mutableStateOf(Rect.Zero)

    /** The rect the card morphs from: its trigger, the last tap, or failing both a shrunken copy of itself. */
    internal fun originRect(card: Rect, d: Density): Rect {
        anchor?.bounds?.let { return it }
        tapOrigin?.let { t ->
            val h = with(d) { spec.tapOriginSize.toPx() } / 2f
            return Rect(t.x - h, t.y - h, t.x + h, t.y + h)
        }
        val c = card.center
        return Rect(
            c.x - card.width * 0.3f,
            c.y - card.height * 0.3f,
            c.x + card.width * 0.3f,
            c.y + card.height * 0.3f,
        )
    }

    internal suspend fun open() {
        if (animated) progress.animateTo(1f, spec.openSpring) else progress.snapTo(1f)
    }

    fun requestClose() {
        if (closing) return
        closing = true
        scope.launch {
            if (animated) progress.animateTo(0f, spec.closeSpring) else progress.snapTo(0f)
            onClosed()
            // An owner that declines (a guarded onDismiss) leaves the popup
            // composed; it is still here two frames later, so bring it back
            // rather than leave an invisible card on top of the page.
            withFrameNanos { }
            withFrameNanos { }
            if (closing) {
                closing = false
                progress.animateTo(1f, spec.openSpring)
            }
        }
    }

    internal fun reopen() {
        if (!closing) return
        closing = false
        scope.launch { progress.animateTo(1f, spec.openSpring) }
    }
}

/**
 * Creates the motion for a popup that is composed only while shown. It opens
 * on entry; [onClosed] runs once the exit morph has finished. "Reduce
 * animation" skips both.
 */
@Composable
internal fun rememberMacOSPopupState(
    spec: MacOSPopupSpec = MacOSPopupSpec(),
    anchor: PopupAnchor? = null,
    onClosed: () -> Unit,
): MacOSPopupState {
    val reduce by AppSettings.reduceAnimation.collectAsState()
    val scope = rememberCoroutineScope()
    val state = remember { MacOSPopupState(scope, if (reduce) 1f else 0f, PopupTapOrigin.take()) }
    state.spec = spec
    state.animated = !reduce
    state.anchor = anchor
    state.onClosed = onClosed
    LaunchedEffect(state) { state.open() }
    return state
}

/**
 * A rounded rect whose corners are elliptical, so that after the layer's
 * uneven scale they still read as circles of the radius being animated.
 */
private class MorphRoundedShape(private val rx: Float, private val ry: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val x = rx.coerceIn(0f, size.width / 2f)
        val y = ry.coerceIn(0f, size.height / 2f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(x, y)))
    }
}

/**
 * The window morph, applied to the card's own layer and read at draw time only,
 * so a frame is a redraw and never a recomposition:
 *
 * - scale X/Y from the trigger's size to 1, height lagging width;
 * - translation from the trigger's centre to the card's, so the rect travels
 *   from the trigger's position to its own;
 * - a clip whose corner radius runs from the trigger's to [cornerRadius];
 * - alpha, and a slight perspective tilt toward the trigger while folded.
 *
 * Put it before the card's clip and background, after its size. Pair it with
 * [macOSPopupContent] around the card's children and [macOSPopupScrim] behind it.
 */
internal fun Modifier.macOSPopupMotion(
    state: MacOSPopupState,
    cornerRadius: Dp = ALERT_CORNER,
): Modifier = this
    .onGloballyPositioned { c ->
        if (c.isAttached) state.bounds = Rect(c.localToScreen(Offset.Zero), c.size.toSize())
    }
    .graphicsLayer {
        val spec = state.spec
        val b = state.bounds
        if (b.width <= 0f || b.height <= 0f) {
            // Not measured yet: stay invisible instead of drawing a frame at the wrong place.
            alpha = 0f
            return@graphicsLayer
        }
        val p = state.progress.value
        val o = state.originRect(b, this)

        val pw = p.coerceAtLeast(0f)
        val ph = pw.pow(spec.heightLag)

        val rawX = (o.width / b.width).coerceIn(spec.minScale, 1f)
        val rawY = (o.height / b.height).coerceIn(spec.minScale, 1f)
        val uniform = sqrt(rawX * rawY)
        val sx = lerp(lerp(rawX, uniform, spec.uniformity), 1f, pw)
        val sy = lerp(lerp(rawY, uniform, spec.uniformity), 1f, ph)
        scaleX = sx
        scaleY = sy

        val rest = (1f - p).coerceAtLeast(0f)
        val dx = o.center.x - b.center.x
        val dy = o.center.y - b.center.y
        translationX = dx * rest
        translationY = dy * rest

        alpha = (p * spec.alphaRamp).coerceIn(0f, 1f)

        // Apparent radius, from the trigger's own curvature to the card's.
        val apparent = lerp(min(o.width, o.height) / 2f, cornerRadius.toPx(), p.coerceIn(0f, 1f))
        // Clip only while the card is still growing out of its trigger. Once it
        // is open the layer must not clip, or the jelly stretch of the popup
        // inside it is cut off at the card's resting edges.
        clip = p < 1f
        shape = MorphRoundedShape(apparent / sx, apparent / sy)

        cameraDistance = 16f * density
        rotationX = spec.foldTilt * rest * (dy / (abs(dy) + b.height * 0.5f))
    }

/**
 * Fades the card's children in after the container has grown, and out before
 * it folds, so a squashed layout is never what the eye sees.
 */
internal fun Modifier.macOSPopupContent(state: MacOSPopupState): Modifier = graphicsLayer {
    val spec = state.spec
    val span = (spec.contentFadeEnd - spec.contentFadeStart).coerceAtLeast(0.01f)
    alpha = ((state.progress.value - spec.contentFadeStart) / span).coerceIn(0f, 1f)
}

/**
 * The window morph of [macOSPopupMotion] for a popup that places its own
 * layer, such as [IosMenuPopover]. [origin] and [card] share one coordinate
 * space and [p] is the open progress: 0 is the trigger, 1 the open card.
 * With [clipToShape] the layer is clipped to a rounded rect whose radius runs
 * from the trigger's to [cornerRadius]; pass false for a layer that casts a shadow.
 */
internal fun GraphicsLayerScope.applyMacOSMorph(
    spec: MacOSPopupSpec,
    p: Float,
    origin: Rect,
    card: Rect,
    cornerRadius: Float,
    clipToShape: Boolean,
) {
    if (card.width <= 0f || card.height <= 0f) return
    val pw = p.coerceAtLeast(0f)
    val ph = pw.pow(spec.heightLag)

    val rawX = (origin.width / card.width).coerceIn(spec.minScale, 1f)
    val rawY = (origin.height / card.height).coerceIn(spec.minScale, 1f)
    val uniform = sqrt(rawX * rawY)
    val sx = lerp(lerp(rawX, uniform, spec.uniformity), 1f, pw)
    val sy = lerp(lerp(rawY, uniform, spec.uniformity), 1f, ph)
    scaleX = sx
    scaleY = sy

    val rest = (1f - p).coerceAtLeast(0f)
    val dx = origin.center.x - card.center.x
    val dy = origin.center.y - card.center.y
    translationX = dx * rest
    translationY = dy * rest

    alpha = (p * spec.alphaRamp).coerceIn(0f, 1f)

    if (clipToShape) {
        val apparent = lerp(min(origin.width, origin.height) / 2f, cornerRadius, p.coerceIn(0f, 1f))
        clip = p < 1f
        shape = MorphRoundedShape(apparent / sx, apparent / sy)
    }

    cameraDistance = 16f * density
    rotationX = spec.foldTilt * rest * (dy / (abs(dy) + card.height * 0.5f))
}

/**
 * The window morph for a popup whose progress is owned elsewhere ([progress]
 * is read at draw time). It grows out of the last tap, which is the button that
 * opened it, and folds back into it. Put it after the popup's size and before
 * its clip and background. "Reduce animation" turns it into a plain fade.
 */
@Composable
internal fun Modifier.macOSPopupMorph(
    progress: () -> Float,
    cornerRadius: Dp = ALERT_CORNER,
    spec: MacOSPopupSpec = MacOSPopupSpec(),
): Modifier {
    val reduce by AppSettings.reduceAnimation.collectAsState()
    val tap = remember { PopupTapOrigin.take() }
    var card by remember { mutableStateOf(Rect.Zero) }
    return this
        .onGloballyPositioned { c ->
            if (c.isAttached) card = Rect(c.localToScreen(Offset.Zero), c.size.toSize())
        }
        .graphicsLayer {
            val b = card
            val p = progress()
            if (b.width <= 0f || b.height <= 0f) {
                alpha = 0f
                return@graphicsLayer
            }
            if (reduce) {
                alpha = p.coerceIn(0f, 1f)
                return@graphicsLayer
            }
            val half = spec.tapOriginSize.toPx() / 2f
            val origin = tap?.let { Rect(it.x - half, it.y - half, it.x + half, it.y + half) }
                ?: Rect(
                    b.center.x - b.width * 0.3f,
                    b.center.y - b.height * 0.3f,
                    b.center.x + b.width * 0.3f,
                    b.center.y + b.height * 0.3f,
                )
            applyMacOSMorph(spec, p, origin, b, cornerRadius.toPx(), true)
        }
}

/**
 * A drop-in for Material's `AlertDialog` that opens with the same macOS morph
 * as the app's other alerts, in a window of its own ([GlassAlertDialog]).
 */
@Composable
internal fun MacOSAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    GlassAlertDialog(onDismiss = onDismissRequest) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            ) {
                if (title != null) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
                        title()
                    }
                    Spacer(Modifier.height(12.dp))
                }
                text?.invoke()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** The dim behind the card, fading with the same progress as the card. */
internal fun Modifier.macOSPopupScrim(state: MacOSPopupState, color: Color? = null): Modifier =
    drawBehind {
        drawRect(color ?: state.spec.scrimColor, alpha = state.progress.value.coerceIn(0f, 1f))
    }

/**
 * A window-like popup for any content: menu, queue, sleep timer, audio
 * settings, playlist picker, confirmation, context menu.
 *
 * Controlled by [visible]: it morphs out of [anchor] (or the last tap), and
 * when [visible] turns false it plays the reverse morph before leaving the
 * composition. Outside taps and Back call [onDismissRequest]; the owner decides
 * whether to hide it. [modifier] sizes the card, [cornerRadius] rounds it,
 * [container] paints it, and [spec] tunes the motion.
 */
@Composable
internal fun MacOSInspiredPopup(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier.width(ALERT_WIDTH),
    anchor: PopupAnchor? = null,
    spec: MacOSPopupSpec = MacOSPopupSpec(),
    cornerRadius: Dp = ALERT_CORNER,
    container: @Composable (Shape) -> Modifier = { s ->
        Modifier.background(MaterialTheme.colorScheme.surface, s)
    },
    dismissOnOutsideTap: Boolean = true,
    dismissOnBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var present by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) { if (visible) present = true }
    if (!visible && !present) return

    val state = rememberMacOSPopupState(spec = spec, anchor = anchor) {
        if (!visible) present = false
    }
    LaunchedEffect(visible) {
        if (visible) state.reopen() else state.requestClose()
    }
    // Kept enabled while closing so a second Back does not pop the page behind.
    BackHandler(enabled = dismissOnBack) { if (visible) onDismissRequest() }

    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .macOSPopupScrim(state)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { if (dismissOnOutsideTap && visible) onDismissRequest() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = modifier
                .macOSPopupMotion(state, cornerRadius)
                .clip(shape)
                .then(container(shape))
                .touchGlow()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(Modifier.fillMaxWidth().macOSPopupContent(state)) { content() }
        }
    }
}