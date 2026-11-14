package com.music.audify.ui.screens

import android.graphics.Bitmap
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.graphics.forPixelAccess
import com.music.audify.ui.graphics.toImageBitmap
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/*
 * Animated album collage untuk hero Liked Music.
 *
 * Satu clock (AlbumAnimationState.seconds) dibaca HANYA di fase draw, jadi tiap
 * frame cuma redraw satu Canvas: tanpa recomposition, tanpa animator per cover,
 * tanpa bitmap baru per frame, tanpa blur real-time.
 */

/** Cover dekoratif maksimum; cover utama = covers[0]. */
internal const val ALBUM_MAX_DECOR = 8

private const val ALBUM_THUMB_PX = 192
private const val ALBUM_MAIN_PX = 600
private const val ALBUM_SOFT_PX = 32
private const val ALBUM_SOFT_BELOW = 0.85f
private const val ALBUM_REDUCED_COUNT = 5
private const val ALBUM_MAIN_SHAPE = 4

/** Pecahan radius sudut (kiri-atas, kanan-atas, kanan-bawah, kiri-bawah) per bentuk. */
private val ALBUM_RADII = arrayOf(
    floatArrayOf(0.22f, 0.22f, 0.22f, 0.22f),
    floatArrayOf(0.50f, 0.50f, 0.50f, 0.50f),
    floatArrayOf(0.45f, 0.08f, 0.45f, 0.08f),
    floatArrayOf(0.08f, 0.42f, 0.08f, 0.42f),
    floatArrayOf(0.30f, 0.10f, 0.30f, 0.12f),
)

/**
 * Satu slot cover dekoratif. x/y = pusat sebagai bagian lebar / tinggi area terlihat
 * (boleh keluar 0..1 supaya terpotong layar), size = sisi sebagai bagian lebar,
 * depth = kedalaman (1 dekat, kecil = jauh: gerak lebih kecil, lebih redup, ter-"blur").
 */
private class AlbumSlot(
    val x: Float,
    val y: Float,
    val size: Float,
    val rot: Float,
    val depth: Float,
    val shape: Int,
    val period: Float,
    val phase: Float,
    val ampX: Float,
    val ampY: Float,
    val ampR: Float,
)

private val ALBUM_SLOTS = listOf(
    AlbumSlot(-0.02f, 0.20f, 0.46f, -9f, 0.85f, 2, 31f, 0.4f, 0.030f, 0.022f, 2.2f),
    AlbumSlot(0.50f, 0.02f, 0.44f, 0f, 0.60f, 1, 27f, 2.1f, 0.024f, 0.030f, 3.0f),
    AlbumSlot(1.00f, 0.06f, 0.36f, 14f, 0.70f, 0, 34f, 4.0f, 0.026f, 0.020f, 2.5f),
    AlbumSlot(0.98f, 0.42f, 0.38f, 9f, 1.00f, 3, 23f, 5.2f, 0.030f, 0.026f, 3.0f),
    AlbumSlot(0.02f, 0.62f, 0.40f, -5f, 0.90f, 0, 29f, 1.3f, 0.028f, 0.030f, 2.5f),
    AlbumSlot(0.90f, 0.76f, 0.30f, 7f, 0.55f, 1, 37f, 3.3f, 0.022f, 0.024f, 3.0f),
    AlbumSlot(0.16f, 0.86f, 0.26f, -12f, 0.50f, 0, 33f, 0.9f, 0.020f, 0.020f, 3.0f),
    AlbumSlot(0.60f, 0.92f, 0.24f, 6f, 0.45f, 2, 39f, 2.7f, 0.020f, 0.020f, 3.0f),
)

/** Digambar dari yang paling jauh ke yang paling dekat. */
private val ALBUM_DRAW_ORDER = ALBUM_SLOTS.indices.sortedBy { ALBUM_SLOTS[it].depth }

/** State animasi, terpisah dari state halaman. Semua dibaca di fase draw. */
@Stable
internal class AlbumAnimationState {
    var seconds by mutableDoubleStateOf(0.0)
    var visible by mutableStateOf(true)
    var reduced by mutableStateOf(false)
    var main by mutableStateOf<ImageBitmap?>(null)
    var prevMain by mutableStateOf<ImageBitmap?>(null)
    var mainFade by mutableFloatStateOf(1f)
    var tiles by mutableStateOf<List<ImageBitmap?>>(emptyList())
}

/** Geometri (Path) dihitung ulang hanya saat ukuran berubah, bukan tiap frame. */
private class AlbumGeometry {
    private var w = -1f
    private var h = -1f
    val sides = FloatArray(ALBUM_SLOTS.size)
    val paths = Array(ALBUM_SLOTS.size) { Path() }
    var mainSide = 0f
    var mainPath = Path()

    fun ensure(width: Float, visibleHeight: Float) {
        if (width == w && visibleHeight == h) return
        w = width
        h = visibleHeight
        for (i in ALBUM_SLOTS.indices) {
            sides[i] = evenPx(width * ALBUM_SLOTS[i].size)
            paths[i] = albumPath(ALBUM_SLOTS[i].shape, sides[i])
        }
        mainSide = evenPx(min(width * 0.64f, visibleHeight * 0.62f))
        mainPath = albumPath(ALBUM_MAIN_SHAPE, mainSide)
    }
}

private fun evenPx(v: Float): Float = ((v / 2f).roundToInt() * 2).coerceAtLeast(2).toFloat()

private fun albumPath(kind: Int, side: Float): Path {
    val h = side / 2f
    val r = ALBUM_RADII[kind]
    fun cr(f: Float) = CornerRadius(side * f)
    return Path().apply {
        addRoundRect(RoundRect(-h, -h, h, h, cr(r[0]), cr(r[1]), cr(r[2]), cr(r[3])))
    }
}

/**
 * Collage album: cover utama di tengah + cover dekoratif mengambang di sekitarnya,
 * semuanya di satu Canvas. [enter] dibaca di fase draw (0..1) untuk fade/scale saat dibuka.
 */
@Composable
internal fun AnimatedAlbumCollage(
    covers: List<String>,
    topInset: Dp,
    enter: () -> Float,
    modifier: Modifier = Modifier,
) {
    val state = remember { AlbumAnimationState() }
    val geometry = remember { AlbumGeometry() }
    val density = LocalDensity.current
    val insetPx = with(density) { topInset.toPx() }
    val borderPx = with(density) { 1.dp.toPx() }

    AlbumArtworkProvider(state, covers)
    AlbumCollageController(state)

    Spacer(
        modifier
            .onGloballyPositioned { c ->
                val v = c.isAttached && c.boundsInWindow().let { !it.isEmpty && it.bottom > 0f }
                if (v != state.visible) state.visible = v
            }
            .drawBehind { drawAlbumCollage(state, geometry, enter(), insetPx, borderPx) },
    )
}

/**
 * Memuat bitmap kecil di luar main thread, sekali per daftar cover. Coil yang mengurus
 * cache memori/disk, jadi membuka halaman lagi tidak men-decode ulang.
 */
@Composable
internal fun AlbumArtworkProvider(state: AlbumAnimationState, covers: List<String>) {
    val context = LocalPlatformContext.current
    val mainUrl = covers.firstOrNull()
    val decorUrls = covers.drop(1).take(ALBUM_MAX_DECOR)

    LaunchedEffect(mainUrl) {
        val bmp = withContext(Dispatchers.Default) { loadAlbumBitmap(context, mainUrl, ALBUM_MAIN_PX) }
            ?: return@LaunchedEffect
        if (state.main == null) {
            state.main = bmp
            state.mainFade = 1f
        } else {
            // Crossfade: cover lama tetap digambar di bawah, cover baru memudar masuk.
            state.mainFade = 0f
            state.prevMain = state.main
            state.main = bmp
            animate(0f, 1f, animationSpec = tween(600, easing = FastOutSlowInEasing)) { v, _ ->
                state.mainFade = v
            }
            state.prevMain = null
        }
    }

    LaunchedEffect(decorUrls) {
        val loaded = withContext(Dispatchers.Default) {
            coroutineScope {
                decorUrls.mapIndexed { i, url ->
                    async {
                        val sharp = loadAlbumBitmap(context, url, ALBUM_THUMB_PX)
                        if (sharp != null && ALBUM_SLOTS[i].depth < ALBUM_SOFT_BELOW) {
                            albumSoftCopy(sharp) ?: sharp
                        } else {
                            sharp
                        }
                    }
                }.awaitAll()
            }
        }
        state.tiles = loaded
    }
}

private suspend fun loadAlbumBitmap(context: PlatformContext, url: String?, px: Int): ImageBitmap? {
    val data = url.artworkAt(px) ?: return null
    val request = ImageRequest.Builder(context).data(data).size(px).forPixelAccess().build()
    val result = SingletonImageLoader.get(context).execute(request)
    return (result as? SuccessResult)?.image?.toImageBitmap()
}

/**
 * Efek blur untuk cover jauh: dibuat SEKALI dengan mengecilkan ke bitmap mungil,
 * lalu diskalakan halus saat digambar. Tidak ada Gaussian blur per frame.
 */
private fun albumSoftCopy(src: ImageBitmap): ImageBitmap? = runCatching {
    val b = src.asAndroidBitmap()
    val m = min(b.width, b.height)
    val square = Bitmap.createBitmap(b, (b.width - m) / 2, (b.height - m) / 2, m, m)
    Bitmap.createScaledBitmap(square, ALBUM_SOFT_PX, ALBUM_SOFT_PX, true).asImageBitmap()
}.getOrNull()

/**
 * Satu-satunya penggerak: satu loop frame untuk semua cover. Berhenti total (tanpa
 * frame callback) saat halaman tidak terlihat, app di bawah STARTED, player menutup
 * halaman, atau animasi sistem dimatikan. Frame drop atau mode hemat daya
 * menurunkan kompleksitas.
 */
@Composable
internal fun AlbumCollageController(state: AlbumAnimationState) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionAllowed = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val powerSave = remember(context) {
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }

    LaunchedEffect(lifecycle, state, motionAllowed) {
        if (!motionAllowed) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { state.visible && !HeroMotion.frozen }.collectLatest { running ->
                if (!running) return@collectLatest
                var last = withFrameNanos { it }
                var frames = 0
                var slow = 0
                var reducedUntil = 0L
                var pending = 0.0
                var odd = false
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = (now - last) / 1_000_000_000.0
                    last = now
                    frames++
                    if (dt > 0.024) slow++
                    if (frames >= 90) {
                        if (slow >= 24) reducedUntil = now + 20_000_000_000L
                        frames = 0
                        slow = 0
                    }
                    val reduced = powerSave || now < reducedUntil
                    if (reduced != state.reduced) state.reduced = reduced
                    pending += dt.coerceAtMost(0.1)
                    if (reduced) {
                        odd = !odd
                        if (odd) continue
                    }
                    state.seconds += pending
                    pending = 0.0
                }
            }
        }
    }
}

/** Renderer: murni menggambar dari state; tidak mengalokasikan bitmap. */
private fun DrawScope.drawAlbumCollage(
    state: AlbumAnimationState,
    geo: AlbumGeometry,
    enter: Float,
    insetPx: Float,
    borderPx: Float,
) {
    val w = size.width
    val visH = (size.height - insetPx).coerceAtLeast(1f)
    geo.ensure(w, visH)
    if (enter <= 0.01f) return

    val t = state.seconds
    val tiles = state.tiles
    val limit = if (state.reduced) ALBUM_REDUCED_COUNT else ALBUM_SLOTS.size

    for (i in ALBUM_DRAW_ORDER) {
        if (i >= limit) continue
        val img = tiles.getOrNull(i) ?: continue
        val s = ALBUM_SLOTS[i]
        val omega = 2.0 * PI / s.period
        val dx = (s.ampX * w * s.depth * sin(omega * t + s.phase)).toFloat()
        val dy = (s.ampY * w * s.depth * sin(omega * 0.83 * t + s.phase * 1.7 + 1.1)).toFloat()
        val rot = s.rot + (s.ampR * sin(omega * 0.61 * t + s.phase * 0.9)).toFloat()
        val sc = 1f + 0.03f * sin(omega * 1.2 * t + s.phase * 2.3).toFloat()
        drawAlbumTile(
            img = img,
            path = geo.paths[i],
            side = geo.sides[i],
            cx = s.x * w + dx,
            cy = insetPx + s.y * visH + dy,
            rot = rot,
            sc = sc,
            alpha = enter,
            dim = (1f - s.depth) * 0.45f,
            borderPx = borderPx,
        )
    }

    // Cover utama: paling depan, gerak paling kecil, scale halus saat dibuka.
    val main = state.main ?: return
    val om = 2.0 * PI / 26.0
    val rot = -7f + (1.6 * sin(om * t)).toFloat()
    val cx = w / 2f + (0.012 * w * sin(om * 0.7 * t + 1.0)).toFloat()
    val cy = insetPx + visH * 0.48f + (0.012 * w * sin(om * 0.9 * t + 2.0)).toFloat()
    val sc = (0.94f + 0.06f * enter) * (1f + 0.012f * sin(om * 1.3 * t).toFloat())

    withTransform({
        translate(cx, cy + 0.02f * w)
        rotate(rot, Offset.Zero)
        scale(sc * 1.02f, sc * 1.02f, Offset.Zero)
    }) {
        drawPath(geo.mainPath, Color.Black.copy(alpha = 0.22f * enter))
    }
    val prev = state.prevMain
    val fade = state.mainFade
    if (prev != null && fade < 1f) {
        drawAlbumTile(prev, geo.mainPath, geo.mainSide, cx, cy, rot, sc, enter, 0f, 0f)
    }
    drawAlbumTile(
        img = main,
        path = geo.mainPath,
        side = geo.mainSide,
        cx = cx,
        cy = cy,
        rot = rot,
        sc = sc,
        alpha = enter * (if (prev != null) fade else 1f),
        dim = 0f,
        borderPx = borderPx,
    )
}

private fun DrawScope.drawAlbumTile(
    img: ImageBitmap,
    path: Path,
    side: Float,
    cx: Float,
    cy: Float,
    rot: Float,
    sc: Float,
    alpha: Float,
    dim: Float,
    borderPx: Float,
) {
    val m = min(img.width, img.height)
    val sideI = side.toInt().coerceAtLeast(2)
    withTransform({
        translate(cx, cy)
        rotate(rot, Offset.Zero)
        scale(sc, sc, Offset.Zero)
        clipPath(path)
    }) {
        // Crop persegi dari tengah (thumbnail bisa 16:9).
        drawImage(
            image = img,
            srcOffset = IntOffset((img.width - m) / 2, (img.height - m) / 2),
            srcSize = IntSize(m, m),
            dstOffset = IntOffset(-sideI / 2, -sideI / 2),
            dstSize = IntSize(sideI, sideI),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
        if (dim > 0f) drawPath(path, Color.Black.copy(alpha = dim * alpha))
        if (borderPx > 0f) {
            drawPath(path, Color.White.copy(alpha = 0.22f * alpha), style = Stroke(borderPx * 2f))
        }
    }
}