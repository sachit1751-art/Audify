package com.music.audify.ui.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import com.music.audify.ui.player.CanvasFrameGrab
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlin.math.exp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.music.audify.data.canvas.CanvasArtwork
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * A cover that flies from the card that was tapped to the sleeve of the page it
 * opens — the same idea as the mini player's pill growing into the player
 * (PlayerDock): measure where the cover is in the window, measure where it is
 * going, and move ONE picture between the two on a single eased curve.
 *
 * Nothing flies unless the page reports its sleeve within a moment; then the
 * card simply stays as it is and the page cross-fades in as before.
 */

/**
 * True di dalam gambar hidup yang terbang atau sudah mendarat: mosaic tidak
 * memutar animasi masuk tile-nya lagi, karena ia sudah tampil utuh di kartu.
 */
val LocalSettledMosaic = compositionLocalOf { false }

private val FlightEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val FLIGHT_MS = 780
private const val TARGET_TIMEOUT_MS = 700L
private const val RETURN_TIMEOUT_MS = 900L

/** How long a landed cover waits, once the page's clip shows, for that clip's own fade-in to finish under it. */
private const val CLIP_REVEAL_MS = 180L

/** How long the cover takes to dissolve into the page's clip. */
private const val CLIP_DISSOLVE_MS = 220

/** How long the flying cover dissolves into a sleeve that has no clip, once it has landed. */
private const val LAND_DISSOLVE_MS = 180

/**
 * Berapa lama gambar hidup (mosaic) yang baru dikomposisi diberi waktu memuat
 * tile-nya sebelum kartu/sleeve asal disembunyikan. Tanpa jeda ini cover
 * terbang masih kosong beberapa frame saat aslinya sudah hilang, dan itu
 * terlihat sebagai blink.
 */
private const val CONTENT_READY_MS = 64L

/** Peak drop shadow of the flying cover, reached mid-flight. */
private const val FLIGHT_SHADOW_DP = 30

/** How long a landed cover waits on the page's own clip before it lets go anyway. */
private const val CLIP_HANDOFF_TIMEOUT_MS = 1500L

/** How fast the flight's idea of its destination follows the real one, per second. */
private const val CHASE_RATE = 16f

/** A return is let off more: the card it lands on may sit half under the bars. */
private const val MIN_RETURN_VISIBLE = 0.25f

private class FlightSource(
    var bounds: Rect = Rect.Zero,
    var model: Any? = null,
    var cornerPx: Float = 0f,
    var footFade: Boolean = false,
    /** The clip the card is playing right now, if any: it flies with the cover. */
    var canvas: CanvasArtwork? = null,
    /** Gambar hidup (mosaic) yang terbang menggantikan satu foto. */
    var content: (@Composable (Modifier) -> Unit)? = null,
)

private class Flight(
    val browseId: String,
    /** True when the page is closing and the cover flies back to its card. */
    val reverse: Boolean,
    val from: Rect,
    val fromCornerPx: Float,
    val fromWash: Color = Color.Unspecified,
    /** How far the cover had settled on its page when a return began: 1 from rest, less if it turned back mid-flight. */
    val settled: Float = 1f,
) {
    var model: Any? = null
    var content: (@Composable (Modifier) -> Unit)? = null
    var toCornerPx: Float = 0f
    var footFade: Boolean = false
    var canvas: CanvasArtwork? = null

    /** Where the destination was last seen, so a card that scrolls away mid-flight does not send the cover elsewhere. */
    var lastDestination: Rect? = null

    /** The frame the clip was showing when the cover took off: it flies instead of the still. */
    var frame: ImageBitmap? = null

    /** The card the cover leaves from or returns to. Set last: the layer waits on it. */
    var token by mutableStateOf<Any?>(null)

    /** The flying picture has been drawn: only then do the card and the sleeve stand aside for it. */
    var imageReady by mutableStateOf(false)
}

private object CoverFlightState {
    val sources = HashMap<Any, FlightSource>()
    val ids = HashMap<Any, String>()
    var flight by mutableStateOf<Flight?>(null)
    var target by mutableStateOf<Rect?>(null)
    var targetCornerPx by mutableStateOf(0f)
    var targetWash by mutableStateOf(Color.Unspecified)
    var origin by mutableStateOf(Offset.Zero)
    var host: Rect = Rect.Zero

    /** The clip a card was playing when its cover took off, by page, for the page to start from. */
    val clips = HashMap<String, CanvasArtwork>()

    /** The page whose own clip has put its first frame up. */
    var clipRendered by mutableStateOf<String?>(null)

    /** The cover has landed and waits on the page's clip: the sleeve below may draw meanwhile. */
    var holding by mutableStateOf(false)

    /** The pages' live frame readers, so a cover flying back starts from what the page is showing. */
    val grabs = HashMap<String, CanvasFrameGrab>()

    /** Where the cover is right now while it flies in (plain fields: only a turn-back mid-flight reads them). */
    var airFlight: Flight? = null
    var airRect: Rect = Rect.Zero
    var airProgress: Float = 0f

    /** 0 to 1 over the current flight. Read only inside draw and graphicsLayer blocks. */
    var flightProgress by mutableFloatStateOf(0f)

    /** The pages flown into and not yet closed: only those are flown back out of. */
    val landed = HashSet<String>()

    /** The release pages still on the back stack: a page that leaves while on it is covered, not closed. */
    var openPages: Set<String> = emptySet()

    /** The cover is in the air: the card and the sleeve both stand aside for it. */
    val flying: Boolean
        get() {
            val f = flight ?: return false
            return f.token != null && f.imageReady && (f.reverse || target != null)
        }

    fun hidesSource(token: Any): Boolean = flying && flight?.token === token

    /**
     * Seberapa terlihat cover kartu. Selama terbang hanya ada SATU gambar: kartu
     * tetap tersembunyi, lalu muncul utuh tepat di bawah gambar terbang ketika
     * penerbangan pulang hampir mendarat (tanpa crossfade dua cover).
     * Dibaca hanya di blok draw.
     */
    fun sourceAlpha(token: Any): Float {
        if (!hidesSource(token)) return 1f
        val f = flight ?: return 1f
        return if (f.reverse && flightProgress >= LAND_AT) 1f else 0f
    }

    /**
     * Teks di sekitar cover: memudar pelan saat berangkat dan muncul pelan
     * saat pulang, mengikuti kemajuan penerbangan. Dibaca hanya di blok draw.
     */
    fun captionAlpha(token: Any): Float {
        val f = flight ?: return 1f
        if (f.token !== token) return 1f
        return if (f.reverse) {
            revealAt(flightProgress)
        } else {
            1f - smooth(flightProgress / DEPART_FADE_SPAN)
        }
    }

    fun hidesTarget(browseId: String): Boolean {
        val f = flight ?: return false
        if (holding || f.browseId != browseId) return false
        // Jalan masuk: sleeve minggir begitu posisinya dilaporkan, tidak menunggu
        // gambar terbang selesai dimuat, supaya sleeve tidak sempat muncul lalu
        // hilang lagi di tujuan.
        return if (f.reverse) flying else f.token != null && target != null
    }

    /**
     * A sleeve standing aside for a flight that carries a clip stays all but
     * invisible rather than undrawn, so its own clip goes on decoding under the
     * cover and is already playing when the cover lands.
     */
    fun targetAlpha(browseId: String): Float = when {
        !hidesTarget(browseId) -> 1f
        flight?.canvas != null -> 0.01f
        else -> 0f
    }

    fun destination(f: Flight): Rect? {
        val live = if (f.reverse) {
            f.token?.let { sources[it]?.bounds }?.takeIf { !it.isEmpty }
        } else {
            target
        }
        // The card can leave composition while the page behind it scrolls; keep
        // heading for where it was last seen instead of jumping to a stale rect.
        if (live != null) f.lastDestination = live
        return live ?: f.lastDestination
    }

    /** Finds the card the page was opened from (the new one, after a back), if it is on screen. */
    fun resolveReturn(f: Flight): Boolean {
        var bestToken: Any? = null
        var bestShare = MIN_RETURN_VISIBLE
        for ((token, source) in sources) {
            if (ids[token] != f.browseId) continue
            val share = visibleShare(source.bounds)
            if (share >= bestShare) {
                bestShare = share
                bestToken = token
            }
        }
        val token = bestToken ?: return false
        val source = sources[token] ?: return false
        f.model = source.model
        f.content = source.content
        f.toCornerPx = source.cornerPx
        f.footFade = source.footFade
        if (f.canvas == null) f.canvas = source.canvas
        f.token = token
        return true
    }
}

private fun LayoutCoordinates.whole(): Rect = Rect(positionInWindow(), size.toSize())

private fun visibleShare(r: Rect): Float {
    if (r.isEmpty) return 0f
    val host = CoverFlightState.host
    if (host.isEmpty) return 1f
    val i = r.intersect(host)
    if (i.isEmpty) return 0f
    return (i.width * i.height) / (r.width * r.height)
}

private fun startFlight(
    browseId: String,
    token: Any,
    frame: ImageBitmap? = null,
    from: Rect? = null,
    fromCornerPx: Float? = null,
) {
    val source = CoverFlightState.sources[token] ?: return
    if (source.bounds.isEmpty) return
    CoverFlightState.target = null
    CoverFlightState.targetWash = Color.Unspecified
    if (CoverFlightState.landed.size > 32) CoverFlightState.landed.clear()
    CoverFlightState.landed.add(browseId)
    val clip = source.canvas
    if (clip != null) CoverFlightState.clips[browseId] = clip else CoverFlightState.clips.remove(browseId)
    CoverFlightState.clipRendered = null
    CoverFlightState.holding = false
    // Sisa 1f dari penerbangan sebelumnya membuat teks berkedip satu frame.
    CoverFlightState.flightProgress = 0f
    CoverFlightState.flight = Flight(browseId, false, from ?: source.bounds, fromCornerPx ?: source.cornerPx).also {
        it.model = source.model
        it.footFade = source.footFade
        it.canvas = source.canvas
        it.frame = frame
        it.content = source.content
        it.imageReady = frame != null
        it.token = token
    }
}

private fun startReturn(browseId: String, from: Rect, cornerPx: Float, wash: Color) {
    if (browseId !in CoverFlightState.landed) return
    // Covered by a page opened over it (an artist from an album), not closed:
    // it stays landed so its cover still flies home when it really closes.
    if (browseId in CoverFlightState.openPages) return
    // Out again before the cover has landed: it turns round where it is in the
    // air instead of jumping to the sleeve it was heading for.
    val air = CoverFlightState.airFlight?.takeIf {
        it === CoverFlightState.flight && !it.reverse && it.browseId == browseId
    }
    val inAir = air != null && !CoverFlightState.airRect.isEmpty
    // Out again before the cover had left its card (the page was still opening):
    // nothing was lifted off the card, so there is nothing to fly back. Flying
    // one anyway showed the card in full while its cover was still elsewhere.
    val pending = CoverFlightState.flight?.takeIf { !it.reverse && it.browseId == browseId }
    if (pending != null && !inAir) {
        CoverFlightState.landed.remove(browseId)
        CoverFlightState.flight = null
        CoverFlightState.target = null
        CoverFlightState.holding = false
        CoverFlightState.clips.remove(browseId)
        return
    }
    // Sleeve scrolled out of view (its lazy item disposed): not a real exit, so
    // `landed` stays set for the flight when the page actually closes.
    if (!inAir && visibleShare(from) < MIN_RETURN_VISIBLE) return
    CoverFlightState.landed.remove(browseId)
    val settled = if (inAir) CoverFlightState.airProgress.coerceIn(0f, 1f) else 1f
    val start = if (inAir) CoverFlightState.airRect else from
    val startCorner = air?.takeIf { inAir }?.let { lerpF(it.fromCornerPx, cornerPx, settled) } ?: cornerPx
    val next = Flight(browseId, true, start, startCorner, wash, settled)
    next.canvas = CoverFlightState.clips[browseId]
    // What the page is showing right now, read before its sleeve steps aside;
    // a turn-back mid-flight carries on with the frame already in the air.
    next.frame = (if (inAir) air?.frame else null) ?: CoverFlightState.grabs[browseId]?.grab?.invoke()
    // Turned round in the air: that picture is already up, so the card stays
    // aside from this very frame instead of showing until the new one is drawn.
    next.imageReady = next.frame != null || (inAir && air?.imageReady == true)
    CoverFlightState.holding = false
    // The card is usually still there: hide it from this very frame so it does
    // not show for a moment and blink out as the cover leaves.
    CoverFlightState.resolveReturn(next)
    // Sisa 1f dari penerbangan masuk membuat teks kartu berkedip penuh satu frame.
    CoverFlightState.flightProgress = 0f
    CoverFlightState.flight = next
}

/**
 * Flies the cover of the card on screen that stands for [browseId], for a tap
 * that did not land on the card itself — the lifted copy in a hold's menu.
 */
fun startCoverFlightFor(browseId: String?) {
    if (browseId.isNullOrEmpty()) return
    val token = CoverFlightState.ids.entries.firstOrNull { it.value == browseId }?.key ?: return
    val source = CoverFlightState.sources[token] ?: return
    if (source.bounds.isEmpty) return
    val lift = LiftedPreview
    if (lift.held.isEmpty) {
        startFlight(browseId, token)
        return
    }
    // The card's cover, mapped onto where the lifted picture of the card sits.
    val b = source.bounds
    val s = lift.scale
    val from = Rect(
        lift.pictureLeft + (b.left - lift.held.left) * s,
        lift.pictureTop + (b.top - lift.held.top) * s,
        lift.pictureLeft + (b.right - lift.held.left) * s,
        lift.pictureTop + (b.bottom - lift.held.top) * s,
    )
    startFlight(browseId, token, null, from, source.cornerPx * s)
}

/**
 * Drops the flight in the air at once. Dipanggil saat tab berganti: kartu yang
 * dituju (atau sleeve yang ditinggalkan) tidak lagi di tempat ia terukur, dan
 * menyelesaikan penerbangan hanya menggambar cover yang meluncur di atas
 * halaman yang tidak ada hubungannya dengannya.
 */
fun cancelCoverFlight() {
    val flight = CoverFlightState.flight ?: return
    if (flight.reverse) CoverFlightState.clips.remove(flight.browseId)
    CoverFlightState.holding = false
    CoverFlightState.flight = null
    CoverFlightState.target = null
    CoverFlightState.airFlight = null
}

/** A cover is on its way out of a tapped card; the page it opens may not have answered yet. */
fun coverFlightPending(): Boolean = CoverFlightState.flight?.reverse == false

/** That cover is in the air: the card it left has stood aside for it. */
fun coverFlightAirborne(): Boolean = CoverFlightState.flying

/** Release pages still on the back stack; a page leaving while listed here is covered by another, so no flight home. */
fun coverFlightPagesOpen(browseIds: Collection<String>) {
    CoverFlightState.openPages = browseIds.toHashSet()
}

/** Where the held-item menu has lifted the card to, written by that menu's layout. */
object LiftedPreview {
    var held: Rect = Rect.Zero
    var pictureLeft: Float = 0f
    var pictureTop: Float = 0f
    var scale: Float = 1f
}

private fun lerpF(a: Float, b: Float, p: Float): Float = a + (b - a) * p

private fun lerpRect(a: Rect, b: Rect, p: Float): Rect = Rect(
    lerpF(a.left, b.left, p),
    lerpF(a.top, b.top, p),
    lerpF(a.right, b.right, p),
    lerpF(a.bottom, b.bottom, p),
)

/**
 * Marks a card's cover as where a flight starts. [model] is what the picture
 * is loaded from; the card's own size and corner are measured here. [footFade]
 * is for a cover whose lower edge dissolves into the card behind it.
 */
@Composable
fun Modifier.coverFlightSource(
    token: Any,
    model: Any?,
    corner: Dp = 12.dp,
    footFade: Boolean = false,
    canvas: CanvasArtwork? = null,
    content: (@Composable (Modifier) -> Unit)? = null,
): Modifier {
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val info = remember(token) { FlightSource() }
    info.model = model
    info.cornerPx = cornerPx
    info.footFade = footFade
    info.canvas = canvas
    info.content = content
    DisposableEffect(token) {
        CoverFlightState.sources[token] = info
        onDispose { CoverFlightState.sources.remove(token) }
    }
    return this
        .graphicsLayer { alpha = CoverFlightState.sourceAlpha(token) }
        // Whole, not clipped to what shows: a card half off the row still flies as itself.
        .onGloballyPositioned { info.bounds = it.whole() }
}

/**
 * Marks the page's sleeve as where the flight lands, and where it leaves from
 * again when the page closes. [wash] is the colour a photo page dissolves into
 * at its foot, so the flying picture can take it on as it lands.
 */
@Composable
fun Modifier.coverFlightTarget(
    browseId: String?,
    corner: Dp = 12.dp,
    wash: Color = Color.Unspecified,
): Modifier {
    if (browseId.isNullOrEmpty()) return this
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val visibility = LocalCoverVisibilityScope.current
    val leaving = visibility?.transition?.targetState == EnterExitState.PostExit
    val lastBounds = remember { arrayOfNulls<Rect>(1) }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        val from = holder[0]?.takeIf { it.isAttached }?.whole() ?: lastBounds[0] ?: return@LaunchedEffect
        startReturn(browseId, from, cornerPx, wash)
    }
    // Backstop: if the page went without the exit being seen, fly back from
    // where it was last measured. Harmless after a flight has already started.
    DisposableEffect(browseId) {
        onDispose { lastBounds[0]?.let { startReturn(browseId, it, cornerPx, wash) } }
    }
    // The page's colour is still settling when the flight lands, and the wash
    // is only read when the sleeve is placed. Keep it current, or the picture
    // dissolves into a stale colour beside a page that has moved on.
    LaunchedEffect(browseId, wash) {
        val flight = CoverFlightState.flight
        if (flight != null && !flight.reverse && flight.browseId == browseId) {
            CoverFlightState.targetWash = wash
        }
    }
    return this
        .graphicsLayer { alpha = CoverFlightState.targetAlpha(browseId) }
        .onGloballyPositioned { coordinates ->
            holder[0] = coordinates
            lastBounds[0] = coordinates.whole()
            val flight = CoverFlightState.flight
            if (flight != null && !flight.reverse && flight.browseId == browseId) {
                CoverFlightState.target = coordinates.whole()
                CoverFlightState.targetCornerPx = cornerPx
                CoverFlightState.targetWash = wash
            }
        }
}

/** Whatever sits on a card around its cover (captions) leaves with it. */
fun Modifier.hideDuringCoverFlight(token: Any): Modifier =
    this.graphicsLayer { alpha = CoverFlightState.captionAlpha(token) }

/**
 * Teks kartu yang muncul pelan SESUDAH cover pulang mendarat. Cover terbang
 * digambar di atas seluruh halaman, jadi teks yang naik selama penerbangan
 * pulang tersembunyi di bawahnya lalu muncul mendadak saat cover lepas.
 * Selama penerbangan pulang teks ditahan di 0, begitu cover lepas teks
 * memudar masuk (dan naik sedikit) di depan kartu.
 */
@Composable
fun Modifier.revealAfterCoverLanding(
    token: Any,
    delayMs: Int = 80,
    rise: Dp = 8.dp,
): Modifier {
    val returning = CoverFlightState.flight?.let { it.reverse && it.token === token } == true
    val shown by animateFloatAsState(
        targetValue = if (returning) 0f else 1f,
        animationSpec = if (returning) {
            snap()
        } else {
            tween(durationMillis = 480, delayMillis = delayMs, easing = FastOutSlowInEasing)
        },
        label = "coverLandingReveal",
    )
    return this.graphicsLayer {
        alpha = shown
        translationY = (1f - shown) * rise.toPx()
    }
}

/** Pulang: teks dan cover kartu mulai muncul di sini (0..1 dari penerbangan). */
private const val REVEAL_START = 0.82f

/** Pulang: kemajuan penerbangan (eased) saat cover kartu boleh muncul di bawah gambar terbang. */
private const val LAND_AT = 0.98f

/** Berangkat: teks sudah hilang sepenuhnya setelah sekian bagian awal penerbangan. */
private const val DEPART_FADE_SPAN = 0.22f

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun revealAt(progress: Float): Float = smooth((progress - REVEAL_START) / (1f - REVEAL_START))

/** The clip the card was playing when its cover took off for [browseId]'s page, for the page to start from. */
fun coverFlightClip(browseId: String?): CanvasArtwork? =
    browseId?.takeIf { it.isNotEmpty() }?.let { CoverFlightState.clips[it] }

/** The page's own clip has its first frame up: the cover that flew in may let go of the sleeve. */
fun coverFlightClipRendered(browseId: String?) {
    if (!browseId.isNullOrEmpty()) CoverFlightState.clipRendered = browseId
}

/** Lets a flight back to the card read the frame the page's clip is showing. */
@Composable
fun RegisterCoverFlightGrab(browseId: String?, grab: CanvasFrameGrab) {
    if (browseId.isNullOrEmpty()) return
    DisposableEffect(browseId, grab) {
        CoverFlightState.grabs[browseId] = grab
        onDispose { if (CoverFlightState.grabs[browseId] === grab) CoverFlightState.grabs.remove(browseId) }
    }
}

/** A card's tap that opens a page: starts the flight, then runs [action]. */
@Composable
fun rememberCoverFlightTap(
    browseId: String?,
    token: Any,
    action: () -> Unit,
    /** The frame the card's clip is showing at the moment of the tap, if it has one. */
    grabFrame: (() -> ImageBitmap?)? = null,
): () -> Unit {
    val latest = rememberUpdatedState(action)
    // Lets a flight back find the card again, which is a new composition after the page closes.
    DisposableEffect(token, browseId) {
        if (!browseId.isNullOrEmpty()) CoverFlightState.ids[token] = browseId
        onDispose { CoverFlightState.ids.remove(token) }
    }
    return {
        if (!browseId.isNullOrEmpty()) startFlight(browseId, token, grabFrame?.invoke())
        latest.value()
    }
}

/** The area the page lives in, with the flying cover drawn over it. */
@Composable
fun CoverFlightHost(
    modifier: Modifier = Modifier,
    /** False when the host places [CoverFlightLayer] itself, under its own bars. */
    overlay: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        if (overlay) CoverFlightOverlay()
    }
}

/** The flying cover, for a host that draws it between its page and its bars. */
@Composable
fun CoverFlightLayer() {
    CoverFlightOverlay()
}

@Composable
private fun CoverFlightOverlay() {
    val flight = CoverFlightState.flight
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                CoverFlightState.origin = it.positionInWindow()
                CoverFlightState.host = it.whole()
            },
    ) {
        if (flight != null) FlightLayer(flight)
    }
}

@Composable
private fun FlightLayer(flight: Flight) {
    val progress = remember(flight) { Animatable(0f) }
    // Where the flight believes it is heading. It trails the real destination a
    // little, so a page that settles or shifts while the cover is in the air
    // bends the path instead of making the picture jump.
    val chase = remember(flight) { mutableStateOf<Rect?>(null) }
    // How much of the flying cover is left once it has landed: it dissolves into the page's own clip.
    val fade = remember(flight) { Animatable(1f) }
    // Never held up for long by a picture that is slow or will not load.
    LaunchedEffect(flight) {
        // Gambar baru diminta setelah tujuan diketahui, jadi masa tunggunya
        // dihitung sejak saat itu. Kalau dihitung dari awal, halaman yang lambat
        // menjawab menghabiskan 150 ms dan kartu diangkat sebelum gambar
        // terbang tampil satu frame pun.
        if (!flight.reverse) {
            withTimeoutOrNull(TARGET_TIMEOUT_MS) {
                snapshotFlow { CoverFlightState.target }.filterNotNull().first()
            }
        }
        delay(if (flight.content != null) CONTENT_READY_MS else 150L)
        flight.imageReady = true
    }
    LaunchedEffect(flight) {
        CoverFlightState.flightProgress = 0f
        val ready = if (flight.reverse) {
            withTimeoutOrNull(RETURN_TIMEOUT_MS) {
                while (flight.token == null && !CoverFlightState.resolveReturn(flight)) withFrameNanos { }
                true
            }
        } else {
            // Wait for the page to say where its sleeve is; if it never does, the
            // card was not opening a page this flight can follow — stand down.
            withTimeoutOrNull(TARGET_TIMEOUT_MS) {
                snapshotFlow { CoverFlightState.target }.filterNotNull().first()
            }
        }
        if (ready != null) {
            var current = CoverFlightState.destination(flight) ?: flight.from
            chase.value = current
            coroutineScope {
                val follower = launch {
                    var last = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val dt = (now - last) / 1_000_000_000f
                        last = now
                        // The card it was heading for scrolled out of composition:
                        // pick up another on screen for the same page, if there is one.
                        if (flight.reverse && flight.token?.let { CoverFlightState.sources[it] } == null) {
                            CoverFlightState.resolveReturn(flight)
                        }
                        val live = CoverFlightState.destination(flight) ?: continue
                        current = lerpRect(current, live, 1f - exp(-CHASE_RATE * dt))
                        chase.value = current
                    }
                }
                progress.animateTo(1f, tween(durationMillis = FLIGHT_MS, easing = FlightEasing)) {
                    CoverFlightState.flightProgress = value
                }
                follower.cancel()
            }
            // Landed with a clip aboard: stay on the sleeve, as the frame it was, until
            // the page's own clip has its first frame up, then dissolve into it.
            if (!flight.reverse && flight.canvas != null) {
                CoverFlightState.holding = true
                val shown = withTimeoutOrNull(CLIP_HANDOFF_TIMEOUT_MS) {
                    snapshotFlow { CoverFlightState.clipRendered }.first { it == flight.browseId }
                }
                // The clip's own fade-in finishes under the cover before the cover lifts.
                if (shown != null) delay(CLIP_REVEAL_MS)
                fade.animateTo(0f, tween(durationMillis = CLIP_DISSOLVE_MS))
            } else if (!flight.reverse) {
                // Mendarat di halaman tanpa klip: sleeve sudah digambar di bawah
                // cover terbang, lalu cover memudar pelan. Tanpa ini cover
                // lenyap seketika dan sleeve yang belum tergambar terlihat berkedip.
                CoverFlightState.holding = true
                fade.animateTo(0f, tween(durationMillis = LAND_DISSOLVE_MS))
            } else if (flight.frame != null || flight.token?.let { CoverFlightState.sources[it] } == null) {
                // Back on the card with the page's frame: it dissolves into the card's own picture.
                // Also when the card is gone (scrolled away): fade out instead of vanishing mid-air.
                fade.animateTo(0f, tween(durationMillis = CLIP_DISSOLVE_MS))
            }
        }
        if (flight.reverse) CoverFlightState.clips.remove(flight.browseId)
        if (CoverFlightState.flight === flight) {
            CoverFlightState.holding = false
            CoverFlightState.flight = null
            CoverFlightState.target = null
        }
    }
    val live = CoverFlightState.destination(flight) ?: return
    // One picture at the card's own size (so it is the one already in the cache),
    // centre-cropped by the moving frame and scaled to cover it.
    val cardSide = if (flight.reverse) max(live.width, live.height) else max(flight.from.width, flight.from.height)
    val sidePx = cardSide.coerceAtLeast(1f)
    val sideDp = with(LocalDensity.current) { sidePx.toDp() }
    val wash = if (flight.reverse) flight.fromWash else CoverFlightState.targetWash
    // The frame at progress p: from where it started to the (trailing) destination,
    // landing exactly on the real one at the very end.
    val rectAt = { p: Float ->
        val end = CoverFlightState.destination(flight) ?: live
        val chased = chase.value ?: end
        lerpRect(flight.from, lerpRect(chased, end, p * p), p)
    }

    Box(
        Modifier
            .layout { measurable, _ ->
                val rect = rectAt(progress.value)
                if (!flight.reverse) {
                    CoverFlightState.airFlight = flight
                    CoverFlightState.airRect = rect
                    CoverFlightState.airProgress = progress.value
                }
                val w = rect.width.roundToInt().coerceAtLeast(1)
                val h = rect.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                val origin = CoverFlightState.origin
                layout(w, h) {
                    placeable.place(
                        (rect.left - origin.x).roundToInt(),
                        (rect.top - origin.y).roundToInt(),
                    )
                }
            }
            .graphicsLayer {
                val toCorner = if (flight.reverse) flight.toCornerPx else CoverFlightState.targetCornerPx
                shape = RoundedCornerShape(lerpF(flight.fromCornerPx, toCorner, progress.value))
                // Drop shadow: naik saat cover lepas, puncak di tengah penerbangan,
                // hilang saat mendarat. Cover dengan kaki memudar ikut meredup
                // supaya bayangannya tidak membentuk pita gelap di kaki.
                val p = progress.value
                val lift = sin(PI.toFloat() * p)
                val foot = if (!flight.footFade) 1f else if (flight.reverse) flight.settled * (1f - p) else p
                shadowElevation = FLIGHT_SHADOW_DP.dp.toPx() * lift * foot
                ambientShadowColor = Color.Black.copy(alpha = 0.45f)
                spotShadowColor = Color.Black.copy(alpha = 0.85f)
                clip = true
                // Gambar terbang tetap utuh selama di udara; hanya memudar
                // setelah mendarat (lihat [fade]), bukan bersilang dengan kartu.
                alpha = fade.value
            },
        // The picture is a square of the card's size inside a frame that grows
        // past it: left to the corner it sat up-left of the frame's centre and
        // only snapped to the middle when the real sleeve took over.
        contentAlignment = Alignment.Center,
    ) {
        val pictureModifier = Modifier
            .requiredSize(sideDp)
            .graphicsLayer {
                val rect = rectAt(progress.value)
                val scale = max(rect.width, rect.height) / sidePx
                scaleX = scale
                scaleY = scale
                if (flight.footFade) compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                if (flight.footFade) {
                    // The card's own cover dissolves into the blur under it;
                    // the picture keeps that foot at the card and loses it on the page.
                    val fade = if (flight.reverse) 1f - flight.settled * (1f - progress.value) else 1f - progress.value
                    drawRect(
                        Brush.verticalGradient(
                            0.78f to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - fade),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
        val frame = flight.frame
        if (frame != null) {
            // The frame the clip was showing when the cover took off, not the
            // still behind it: nothing but the clip's own picture is ever seen.
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = pictureModifier,
            )
        } else if (flight.content != null) {
            // Gambar hidup (mosaic) terbang sebagai dirinya sendiri, tetap bergerak.
            CompositionLocalProvider(LocalSettledMosaic provides true) {
                flight.content?.invoke(pictureModifier)
            }
        } else {
            AsyncImage(
                model = flight.model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = {
                    if (it is AsyncImagePainter.State.Success || it is AsyncImagePainter.State.Error) {
                        flight.imageReady = true
                    }
                },
                modifier = pictureModifier,
            )
        }
        if (wash != Color.Unspecified) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val a = if (flight.reverse) flight.settled * (1f - progress.value) else progress.value
                        drawRect(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.65f to wash.copy(alpha = 0.30f),
                                0.82f to wash.copy(alpha = 0.72f),
                                0.94f to wash.copy(alpha = 0.95f),
                                1.00f to wash,
                            ),
                            alpha = a,
                        )
                    },
            )
        }
    }
}
