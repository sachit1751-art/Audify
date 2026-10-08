package com.sachit.music.ui.player

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The artwork's own colours, upside down and reduced to a mesh, for the player
 * to stand on.
 *
 * [MeshGradientBackground] answers a different question: it asks the quantiser
 * what colours a sleeve is *about* and paints four blobs of them. That is why a
 * cover that is nine-tenths black with a red stripe came out as a red screen —
 * the quantiser reports red because red is the interesting answer, and nothing
 * downstream knows how little of the picture it was. It also has no idea *where*
 * in the frame that red was.
 *
 * This is the other approach: no quantiser at all. The sleeve is averaged into a
 * [MESH_GRID] square of means; the row against the seam is kept in place, and
 * everything below it is flipped and then rotated sideways — see
 * [rotatedBelowSeam]. What the backdrop holds is the cover's own colours,
 * roughly in the cover's own proportions (nine-tenths black stays nine-tenths
 * black) and with each cell's neighbours exactly what they were in the source,
 * but not lined up in the cover's own *position*: a flip on its own reads as a
 * reflection on any cover with real structure to it — a face, a horizon, a
 * logo — however coarse the grid, because the layout still lines up column for
 * column with what's on screen above it. The rotation is what actually breaks
 * that column-for-column match; the coarseness just keeps any one cell from
 * being recognisable on its own.
 *
 * Held as a tiny bitmap rather than a list of colours because that is exactly
 * what the hardware wants: one [DrawScope.drawImage] with bilinear filtering
 * interpolates the whole mesh in the sampler. The alternative — a blob per cell,
 * as the old backdrop draws — would be thirty-six full-screen radial gradients.
 *
 * Backdrop approach ported from the BitChord project (GPL-3.0).
 */
@Immutable
class ArtworkMesh internal constructor(internal val image: ImageBitmap)

/** One pre-blurred bitmap for the full-cover player backdrop. */
@Composable
fun rememberFullArtworkBlurImage(
    imageUrl: String?,
    artPx: Int = 480,
    prepare: Boolean = true,
): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(imageUrl) { mutableStateOf(imageUrl?.let(fullBlurCache::get)) }

    LaunchedEffect(imageUrl, artPx, prepare) {
        if (!prepare || imageUrl == null || image != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(artPx, artPx)
            .allowHardware(false)
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val blurred = withContext(Dispatchers.Default) { bitmap.boxBlurred(FULL_BLUR_PASSES) }
        val ready = blurred.asImageBitmap()
        fullBlurCache[imageUrl] = ready
        image = ready
    }
    return image
}

/** A full-surface, still-art backdrop for surfaces without an artwork edge. */
@Composable
fun FullArtworkBlurBackdrop(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val imageAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "preparedBackdropImage",
    )
    Box(modifier = modifier.fillMaxSize().background(FallbackBackdrop)) {
        image?.let { bitmap ->
            val painter = remember(bitmap) { BitmapPainter(bitmap) }
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = imageAlpha },
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.34f),
                    0.55f to Color.Black.copy(alpha = 0.48f),
                    1f to Color.Black.copy(alpha = 0.64f),
                ),
            )
        }
    }
}

/** The mesh for the artwork at [imageUrl], or null until one has been read. */
@Composable
fun rememberArtworkMesh(
    imageUrl: String?,
    artPx: Int = 120,
): ArtworkMesh? {
    val context = LocalContext.current
    val reduceAnimation = remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val heldMesh = remember { mutableStateOf<ArtworkMesh?>(null) }
    var mesh by remember(imageUrl) {
        mutableStateOf(imageUrl?.let(meshCache::get) ?: heldMesh.value)
    }
    var meshUrl by remember(imageUrl) {
        mutableStateOf(if (imageUrl != null && meshCache.get(imageUrl) != null) imageUrl else null)
    }
    LaunchedEffect(mesh) { heldMesh.value = mesh }

    LaunchedEffect(imageUrl, artPx) {
        if (imageUrl == null || meshUrl == imageUrl) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(artPx, artPx)
            .allowHardware(false)
            .build()
        repeat(MESH_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(MESH_RETRY_DELAY_MS)
            val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
            val bitmap = (result as? SuccessResult)?.image?.toBitmap()
            if (bitmap != null) {
                val found = withContext(Dispatchers.Default) { meshOf(bitmap, imageUrl.hashCode()) }
                if (found != null) {
                    meshCache[imageUrl] = found
                    mesh = found
                }
                meshUrl = imageUrl
                return@LaunchedEffect
            }
        }
    }

    return mesh
}

/** The player's backdrop: [mesh] hung from where the artwork stops, stretched below. */
@Composable
fun ArtworkMeshBackdrop(
    mesh: ArtworkMesh?,
    modifier: Modifier = Modifier,
    seam: Dp = 0.dp,
    fallback: Color = FallbackBackdrop,
    blurRadius: Dp = 32.dp,
) {
    val context = LocalContext.current
    val reduceAnimation = remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    val canBlur = !reduceAnimation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<ArtworkMesh?>(null) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(mesh) {
        val next = mesh ?: return@LaunchedEffect
        incoming?.let { shown = it }
        incoming = null
        val current = shown
        if (next === current) return@LaunchedEffect
        if (current == null || reduceAnimation) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(fallback)
            .then(if (canBlur) Modifier.blur(blurRadius) else Modifier),
    ) {
        val seamY = seam.toPx().coerceIn(0f, size.height)
        shown?.let { drawMesh(it, seamY, alpha = 1f) }
        incoming?.let { drawMesh(it, seamY, alpha = fade.value) }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.06f),
                    Color.Black.copy(alpha = 0.30f),
                ),
            ),
        )
    }
}

private fun DrawScope.drawMesh(mesh: ArtworkMesh, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val image = mesh.image
    val width = size.width.roundToInt()

    if (seamY > 0.5f) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, seamY.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }

    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(0, seamY.roundToInt()),
        dstSize = IntSize(width, (size.height - seamY).roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

private val FallbackBackdrop = Color(0xFF121212)

private const val FULL_BLUR_SOURCE_PX = 128
private const val FULL_BLUR_PASSES = 3

private val fullBlurCache = object : LinkedHashMap<String, ImageBitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>) = size > 8
}

private fun Bitmap.boxBlurred(passes: Int): Bitmap {
    val width = width
    val height = height
    if (width < 2 || height < 2) return this
    var source = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    var target = IntArray(source.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)

    repeat(passes) {
        boxBlurPass(source, target, width, height, radius, horizontal = true)
        boxBlurPass(target, source, width, height, radius, horizontal = false)
    }
    return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1
    repeat(minor) { fixed ->
        var red = 0
        var green = 0
        var blue = 0
        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) source[fixed * width + position]
            else source[position * width + fixed]
        }
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        repeat(major) { moving ->
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = argb(red / window, green / window, blue / window)
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

private val meshCache = object : LinkedHashMap<String, ArtworkMesh>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ArtworkMesh>) = size > MESH_CACHE_ENTRIES
}

private const val MESH_CACHE_ENTRIES = 64
private const val MESH_PX = 120
private const val MESH_ATTEMPTS = 4
private const val MESH_RETRY_DELAY_MS = 1_500L
private const val MESH_GRID = 6
private const val MESH_TEX = 32
private const val MESH_FADE_MS = 900
private const val MESH_SAMPLE = 128
private const val MESH_VIBRANCE = 1.12f
private const val MESH_FLOOR = 0.045f

private fun meshOf(source: Bitmap, seed: Int): ArtworkMesh? {
    val width = source.width
    val height = source.height
    if (width < 1 || height < 1) return null

    val cols = MESH_GRID.coerceAtMost(width)
    val rows = MESH_GRID.coerceAtMost(height)
    val rowStep = (height / MESH_SAMPLE).coerceAtLeast(1)
    val colStep = (width / MESH_SAMPLE).coerceAtLeast(1)

    val cells = rows * cols
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)

    val line = IntArray(width)
    var y = 0
    while (y < height) {
        source.getPixels(line, 0, width, 0, y, width, 1)
        val rowBase = ((height - 1 - y) * rows / height) * cols
        var x = 0
        while (x < width) {
            val cell = rowBase + x * cols / width
            val pixel = line[x]
            red[cell] += (pixel shr 16) and 0xFF
            green[cell] += (pixel shr 8) and 0xFF
            blue[cell] += pixel and 0xFF
            count[cell]++
            x += colStep
        }
        y += rowStep
    }

    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt()).lifted()
    }
    val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX)
    val bitmap = Bitmap.createBitmap(texels, MESH_TEX, MESH_TEX, Bitmap.Config.ARGB_8888)
    return ArtworkMesh(bitmap.asImageBitmap())
}

internal fun IntArray.rotatedBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

internal fun IntArray.resampled(cols: Int, rows: Int, size: Int): IntArray {
    val out = IntArray(size * size)
    for (ty in 0 until size) {
        val fy = (ty + 0.5f) / size * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until size) {
            val fx = (tx + 0.5f) / size * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * size + tx] = lerpArgb(top, bottom, wy)
        }
    }
    return out
}

internal fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

internal fun lerpArgb(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

internal fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private fun Int.lifted(): Int {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(this, it) }
    hsl[1] = (hsl[1] * MESH_VIBRANCE).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceAtLeast(MESH_FLOOR)
    return ColorUtils.HSLToColor(hsl)
}

/** Returns true when the system animator scale is set to 0 (accessibility reduce motion). */
private fun systemReduceAnimation(scale: Float): Boolean = scale == 0f
