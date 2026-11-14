package com.music.audify.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.SweepGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.audify.R
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.artworkAt
import com.music.audify.data.stats.ReplaySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Replay as one picture, drawn to a bitmap you can send.
 *
 * ## Why this is drawn rather than screenshotted
 *
 * The obvious way to share a story card is to capture the one on screen. It is
 * also the wrong picture: a story card is one fact, and the thing people
 * actually want to send is the whole year — the minutes *and* the five songs
 * *and* the five artists. A screenshot would also carry the status bar, the
 * progress segments and whatever letterboxing that particular phone needed, so
 * the same Replay would come out looking different on every device.
 *
 * ## Why it isn't Compose
 *
 * Composing offscreen means laying out a composition that is not attached to a
 * window and then reading pixels back out of it, which on this app's minimum SDK
 * is a set of caveats about hardware bitmaps and layer support rather than an
 * API. A canvas is a canvas on every version, and the layout below is fixed —
 * nothing here reflows, so nothing here needs a layout system.
 *
 * The size is 1080 × 1920: exactly the 9:16 the story cards are held to (see
 * `StoryFrame`), so what gets shared is the shape of what was being looked at,
 * and it is the size every messaging app and story surface expects.
 */
suspend fun renderReplayPoster(
    context: Context,
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
    /**
     * Which story card to draw, or null for the whole Replay.
     *
     * A card's share button sends the card. That is not the same picture as the
     * summary and should not be: somebody taps share on the genre card because
     * the genre surprised them, and receiving a table of every chart instead is
     * a different message. The Replay page's own button is where "all of it"
     * lives.
     */
    page: ReplayStoryPage? = null,
): Bitmap = coroutineScope {
    // The artwork this needs is a dozen small fetches that are all in Coil's
    // disk cache already — the page they came from has been on screen. Started
    // together rather than in a loop so a cold cache costs one round trip
    // rather than twelve.
    val songs = summary.songRows(POSTER_ROWS)
    val artists = summary.artistRows(POSTER_ROWS)
    val albums = summary.albumRows(POSTER_ALBUMS)
    val covers = (songs + artists + albums)
        .mapNotNull { it.artworkUrl }
        .distinct()
        .associateWith { url -> async(Dispatchers.IO) { loadBitmap(context, url) } }
        .mapValues { it.value.await() }

    withContext(Dispatchers.Default) {
        val lit = page?.let { summary.storyArtwork(it) }
            ?: songs.firstOrNull()?.artworkUrl
        val bitmap = Bitmap.createBitmap(POSTER_W, POSTER_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val type = Fonts(context)

        // Recap penuh (dan kartu akhir) memakai layout baru ala ringkasan Replay.
        if (page == null || page == ReplayStoryPage.SUMMARY) {
            drawRecapPoster(canvas, context, type, summary, covers)
            return@withContext bitmap
        }

        // The same cover and the same turn of the colour wheel the card on
        // screen was drawn with, so the picture is recognisably that card.
        drawBackdrop(canvas, covers[lit], page?.let(::storyHue) ?: 0f)
        drawAtmosphere(canvas, page)
        var y = drawHeader(canvas, context, type, summary, holder, memberSince)
        if (page == null) {
            y = drawTotals(canvas, context, type, summary, y)
            y = drawColumns(canvas, context, type, songs, artists, covers, y)
            y = drawAlbums(canvas, context, type, albums, covers, y)
            drawGenres(canvas, context, type, summary, y)
        } else {
            y = drawRuns(
                canvas,
                type,
                summary.storyHeadline(context, page),
                MARGIN,
                y,
                CONTENT_W,
                serif = page in SerifPages,
            )
            drawCard(canvas, context, type, summary, page, covers, y + 40f)
        }
        drawFooter(canvas, context, type)
        bitmap
    }
}

// ── One story card ──────────────────────────────────────────────────────────

/**
 * The body of [page], under its headline.
 *
 * Deliberately not a pixel copy of the composable — a card on screen fills its
 * height with weighted spacers and this cannot, since it has no measurement pass
 * to spend. What it copies is the *content*: the same sentence, the same hero,
 * the same four runners-up, in the same order. Somebody who sends a card and
 * somebody who saw it are looking at the same facts.
 */
private fun drawCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    page: ReplayStoryPage,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    when (page) {
        ReplayStoryPage.INTRO -> drawIntroCard(canvas, context, type, summary, covers, top)
        ReplayStoryPage.MINUTES -> drawMinutesCard(canvas, context, type, summary, covers, top)
        ReplayStoryPage.SONGS ->
            drawSongsCard(canvas, context, type, summary.songRows(CARD_ROWS), covers, top)
        ReplayStoryPage.ARTISTS ->
            drawArtistsCard(canvas, context, type, summary.artistRows(CARD_ROWS), covers, top)
        ReplayStoryPage.ALBUMS ->
            drawAlbumCard(canvas, context, type, summary.albumRows(CARD_ROWS), covers, top)
        ReplayStoryPage.GENRES -> drawPersonalityCard(canvas, context, type, summary, top)
        ReplayStoryPage.HABITS -> drawHabitCards(canvas, context, type, summary, top)
        ReplayStoryPage.SUMMARY -> drawLinerNotes(canvas, context, type, summary, top)
    }
}

/**
 * Poster recap: latar hitam dengan glow warna dari sampul teratas, tahun dan
 * menit di tengah, tiga teratas di kiri, artwork bertumpuk keluar dari tepi kanan.
 */
private fun drawRecapPoster(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    covers: Map<String, Bitmap?>,
) {
    val w = POSTER_W.toFloat()
    val h = POSTER_H.toFloat()
    canvas.drawColor(Color.BLACK)
    val songs = summary.songRows(3)
    val artists = summary.artistRows(3)
    val albums = summary.albumRows(3)
    val colors = paletteOf(songs.firstOrNull()?.artworkUrl?.let { covers[it] })
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun glow(cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(ColorUtils.setAlphaComponent(color, alpha), ColorUtils.setAlphaComponent(color, 0)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }
    glow(w * 0.5f, 0f, w * 0.95f, colors[0], 235)
    glow(0f, h * 0.35f, w * 0.75f, colors[1], 200)
    glow(w, h * 0.55f, w * 0.8f, colors[2], 210)
    glow(w, h, w * 0.7f, colors[3], 150)

    val label = summary.localizedLabel(context)
    val title = if (label.length == 4 && label.all { it.isDigit() }) "Replay'${label.takeLast(2)}" else "Replay · $label"
    canvas.drawText(title, MARGIN, 150f, type.heading(62f, Color.WHITE))
    val brand = type.heading(46f, 0xE6FFFFFF.toInt()).apply { textAlign = Paint.Align.RIGHT }
    canvas.drawText("Audify", w - MARGIN, 150f, brand)
    drawLogo(canvas, context, w - MARGIN - brand.measureText("Audify") - LOGO_GAP, 150f)

    val year = type.heading(190f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
    canvas.drawText(ellipsised(label, year, CONTENT_W), w / 2f, 430f, year)
    val minutes = type.body(52f, 0x99FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    canvas.drawText(
        "${formatMinutes(summary.totalMs)} ${context.getString(R.string.minutes).lowercase(Locale.getDefault())}",
        w / 2f,
        510f,
        minutes,
    )

    fun section(name: String, rows: List<ReplayRow>, subtitles: Boolean, top: Float): Float {
        if (rows.isEmpty()) return top
        canvas.drawText(name, MARGIN, top + 56f, type.heading(64f, Color.WHITE))
        var y = top + 78f
        val rowH = if (subtitles) 96f else 62f
        rows.forEach { row ->
            canvas.drawText(row.rank.toString(), MARGIN, y + 46f, type.body(40f, 0x66FFFFFF))
            val titlePaint = type.body(44f, Color.WHITE)
            canvas.drawText(ellipsised(row.title, titlePaint, 500f), MARGIN + 80f, y + 46f, titlePaint)
            val sub = row.subtitle
            if (subtitles && sub != null) {
                val subPaint = type.body(34f, 0x80FFFFFF.toInt())
                canvas.drawText(ellipsised(sub, subPaint, 500f), MARGIN + 80f, y + 86f, subPaint)
            }
            y += rowH
        }
        return y + 40f
    }
    var y = 700f
    y = section(context.getString(R.string.top_artists), artists, false, y)
    y = section(context.getString(R.string.top_songs), songs, true, y)
    section(context.getString(R.string.top_albums), albums, true, y)

    val size = 440f
    val x = 690f
    albums.firstOrNull()?.let {
        drawShadow(canvas, x, 1400f, size, false, 0.08f)
        drawArtwork(canvas, it.artworkUrl?.let { u -> covers[u] }, it.title, x, 1400f, size, false, 0.08f)
    }
    songs.firstOrNull()?.let {
        drawShadow(canvas, x, 1030f, size, false, 0.08f)
        drawArtwork(canvas, it.artworkUrl?.let { u -> covers[u] }, it.title, x, 1030f, size, false, 0.08f)
    }
    artists.firstOrNull()?.let {
        drawShadow(canvas, x, 660f, size, true)
        drawArtwork(canvas, it.artworkUrl?.let { u -> covers[u] }, it.title, x, 660f, size, true)
        canvas.drawCircle(x + size / 2f, 660f + size / 2f, size / 2f, strokePaint(0x4DFFFFFF, 3f))
    }
    drawFooter(canvas, context, type)
}

/** Kartu kepribadian: orb suara dari 24 jam, nama, deskripsi, genre dan kebiasaan. */
private fun drawPersonalityCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    top: Float,
) {
    val persona = summary.personality()
    val cx = POSTER_W / 2f
    val cy = top + 330f
    val inner = 130f
    val reach = 250f
    val hours = summary.hourOfDay
    val peak = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, 420f, intArrayOf(0x66EBD9A8, 0), null, Shader.TileMode.CLAMP)
    }
    canvas.drawCircle(cx, cy, 420f, glow)
    val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }
    val bars = 90
    for (i in 0 until bars) {
        val t = i / bars.toFloat()
        val a = t * 2f * PI.toFloat() - PI.toFloat() / 2f
        val pos = t * 24f
        val h0 = pos.toInt() % 24
        val h1 = (h0 + 1) % 24
        val f = pos - pos.toInt()
        val p = if (hours.size >= 24) (hours[h0] * (1f - f) + hours[h1] * f) / peak else 0.5f
        val len = (0.14f + 0.86f * p) * reach * (0.7f + 0.3f * sin(a * 5f))
        bar.color = ColorUtils.setAlphaComponent(Color.WHITE, (110 + 145 * p).toInt().coerceIn(0, 255))
        canvas.drawLine(
            cx + cos(a) * inner,
            cy + sin(a) * inner,
            cx + cos(a) * (inner + len),
            cy + sin(a) * (inner + len),
            bar,
        )
    }
    canvas.drawCircle(cx, cy, inner * 0.9f, strokePaint(0x80FFFFFF.toInt(), 3f))

    var y = cy + 400f
    y = drawWrapped(canvas, persona.title(context), type.heading(104f, Color.WHITE), MARGIN, y, CONTENT_W, 112f, 2)
    y = drawWrapped(canvas, persona.description(context), type.body(44f, 0xCCFFFFFF.toInt()), MARGIN, y + 8f, CONTENT_W, 58f, 3)
    val genres = summary.genres.take(3).joinToString("  ·  ") { it.title }
    if (genres.isNotEmpty()) {
        val g = type.serif(46f, Color.WHITE, italic = true, bold = false)
        canvas.drawText(ellipsised(genres, g, CONTENT_W), MARGIN, y + 36f, g)
        y += 72f
    }
    val line = summary.behaviorLines(context, persona).firstOrNull()
    if (line != null) {
        val b = type.body(38f, 0xB3FFFFFF.toInt())
        canvas.drawText(ellipsised(line, b, CONTENT_W), MARGIN, y + 44f, b)
    }
}

private const val CHAMPAGNE = 0xFFEBD9A8.toInt()
private const val PAPER = 0xFFF4ECDD.toInt()
private const val INK = 0xFF2A2118.toInt()

/** The cards set in a serif, the same ones the story sets that way. */
private val SerifPages = setOf(
    ReplayStoryPage.INTRO,
    ReplayStoryPage.ALBUMS,
    ReplayStoryPage.HABITS,
    ReplayStoryPage.SUMMARY,
)

private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
}

/** A frosted panel: a faint vertical wash and a hairline edge. */
private fun drawGlass(canvas: Canvas, rect: RectF, radius: Float, edge: Int = 0x2EFFFFFF) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0x29FFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(rect, radius, radius, paint)
    canvas.drawRoundRect(rect, radius, radius, strokePaint(edge, 2.4f))
}

private fun drawGoldRule(canvas: Canvas, x: Float, y: Float, width: Float) {
    val paint = Paint().apply {
        shader = LinearGradient(
            x,
            y,
            x + width,
            y,
            intArrayOf(0, 0x80EBD9A8.toInt(), 0),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(x, y, x + width, y + 2f, paint)
}

private fun drawShadow(
    canvas: Canvas,
    x: Float,
    y: Float,
    size: Float,
    circular: Boolean,
    corner: Float = 0.10f,
) {
    val radius = if (circular) size / 2f else size * corner
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        setShadowLayer(54f, 0f, 20f, 0x99000000.toInt())
    }
    canvas.drawRoundRect(RectF(x, y, x + size, y + size), radius, radius, paint)
}

/** Greedy word wrap. Returns the baseline the next line would sit on. */
private fun drawWrapped(
    canvas: Canvas,
    text: String,
    paint: Paint,
    x: Float,
    top: Float,
    maxWidth: Float,
    leading: Float,
    maxLines: Int,
): Float {
    val lines = ArrayList<String>()
    var line = ""
    for (word in text.split(' ').filter { it.isNotEmpty() }) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (line.isEmpty() || paint.measureText(candidate) <= maxWidth) {
            line = candidate
        } else {
            lines += line
            line = word
        }
    }
    if (line.isNotEmpty()) lines += line
    val shown = if (lines.size > maxLines) {
        lines.take(maxLines - 1) + lines.drop(maxLines - 1).joinToString(" ")
    } else {
        lines
    }
    var y = top
    shown.forEach {
        canvas.drawText(ellipsised(it, paint, maxWidth), x, y, paint)
        y += leading
    }
    return y
}

/**
 * Each card's own atmosphere, drawn over the mesh and under the type: the same
 * light leaks, beams, grooves and halftones the story cards draw, then the same
 * vignette and film grain on all of them.
 */
private fun drawAtmosphere(canvas: Canvas, page: ReplayStoryPage?) {
    val w = POSTER_W.toFloat()
    val h = POSTER_H.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun glow(cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(ColorUtils.setAlphaComponent(color, alpha), ColorUtils.setAlphaComponent(color, 0)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }
    when (page) {
        ReplayStoryPage.INTRO -> {
            glow(w, 0f, w * 1.05f, 0xFFFFC27A.toInt(), 102)
            glow(0f, h, w * 0.95f, 0xFFFF8FA3.toInt(), 56)
            val hole = fillPaint(0x1AFFFFFF)
            var y = 45f
            while (y < h) {
                canvas.drawRoundRect(RectF(15f, y, 32f, y + 24f), 6f, 6f, hole)
                canvas.drawRoundRect(RectF(w - 32f, y, w - 15f, y + 24f), 6f, 6f, hole)
                y += 90f
            }
        }
        ReplayStoryPage.MINUTES -> glow(w * 0.5f, h * 0.52f, w * 0.85f, Color.WHITE, 36)
        ReplayStoryPage.SONGS -> {
            glow(w * 0.5f, 0f, w * 0.95f, Color.WHITE, 51)
            glow(w, h, w * 0.9f, CHAMPAGNE, 36)
        }
        ReplayStoryPage.ARTISTS -> {
            val tints = intArrayOf(0xFFFFD6A5.toInt(), 0xFFA5C8FF.toInt())
            for (side in 0..1) {
                val x0 = if (side == 0) w * 0.12f else w * 0.88f
                val beam = Path().apply {
                    moveTo(x0 - 30f, 0f)
                    lineTo(x0 + 30f, 0f)
                    lineTo(w * 0.76f, h * 0.72f)
                    lineTo(w * 0.24f, h * 0.72f)
                    close()
                }
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    h * 0.72f,
                    ColorUtils.setAlphaComponent(tints[side], 46),
                    0,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawPath(beam, paint)
                paint.shader = null
            }
        }
        ReplayStoryPage.ALBUMS -> {
            val groove = strokePaint(0x12FFFFFF, 2.4f)
            var r = 66f
            while (r < w * 1.1f) {
                canvas.drawCircle(w * 1.02f, h * 0.9f, r, groove)
                r += 39f
            }
        }
        ReplayStoryPage.GENRES -> {
            val dot = fillPaint(0x1AFFFFFF)
            val step = 72f
            var row = 0
            var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else step / 2f
                while (x < w) {
                    val d = (x + (h - y) * 0.6f) / (w + h * 0.6f)
                    val r = step * 0.36f * (1f - d).coerceIn(0f, 1f)
                    if (r > 1.8f) canvas.drawCircle(x, y, r, dot)
                    x += step
                }
                y += step * 0.86f
                row++
            }
        }
        ReplayStoryPage.HABITS -> {
            val rule = strokePaint(0x12FFFFFF, 3f)
            var y = h * 0.26f
            while (y < h) {
                canvas.drawLine(0f, y, w, y, rule)
                y += 102f
            }
            canvas.drawLine(w * 0.12f, 0f, w * 0.12f, h, strokePaint(0x42FA2D48, 3.6f))
        }
        null, ReplayStoryPage.SUMMARY -> {
            glow(w * 0.5f, h * 0.12f, w * 0.9f, 0xFFF2D9A0.toInt(), 77)
            glow(w * 0.5f, h, w, CHAMPAGNE, 30)
        }
    }

    paint.shader = RadialGradient(
        w / 2f,
        h / 2f,
        h * 0.78f,
        intArrayOf(0, 0x6B000000),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null

    val random = Random(26)
    val light = fillPaint(0x0FFFFFFF)
    val dark = fillPaint(0x17000000)
    repeat(900) { index ->
        canvas.drawCircle(
            random.nextFloat() * w,
            random.nextFloat() * h,
            1.2f + random.nextFloat() * 2.7f,
            if (index % 2 == 0) light else dark,
        )
    }
}

/** A square photo in a paper frame, tilted, optionally taped on. */
private fun drawPolaroid(
    canvas: Canvas,
    bitmap: Bitmap?,
    x: Float,
    y: Float,
    cover: Float,
    tilt: Float,
    tape: Boolean,
) {
    val pad = cover * 0.07f
    val frame = RectF(x, y, x + cover + pad * 2f, y + cover + pad * 4.2f)
    canvas.save()
    canvas.rotate(tilt, frame.centerX(), frame.centerY())
    val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PAPER
        setShadowLayer(42f, 0f, 16f, 0x80000000.toInt())
    }
    canvas.drawRoundRect(frame, 9f, 9f, paper)
    drawArtwork(canvas, bitmap, "", x + pad, y + pad, cover, false, 0.01f)
    if (tape) {
        val tapeW = cover * 0.34f
        canvas.save()
        canvas.rotate(-5f, frame.centerX(), y)
        canvas.drawRect(
            frame.centerX() - tapeW / 2f,
            y - 30f,
            frame.centerX() + tapeW / 2f,
            y + 18f,
            fillPaint(0x8CEBD9A8.toInt()),
        )
        canvas.restore()
    }
    canvas.restore()
}

/** A record: grooves, a sheen that stays put, and the cover as its label. */
private fun drawVinyl(canvas: Canvas, label: Bitmap?, cx: Float, cy: Float, radius: Float) {
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111114.toInt()
        setShadowLayer(72f, 0f, 24f, 0xAA000000.toInt())
    }
    canvas.drawCircle(cx, cy, radius, shadow)
    val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                0xFF151518.toInt(),
                0xFF2B2B31.toInt(),
                0xFF111114.toInt(),
                0xFF2E2E34.toInt(),
                0xFF151518.toInt(),
            ),
            null,
        )
    }
    canvas.drawCircle(cx, cy, radius, disc)
    val groove = strokePaint(0x0EFFFFFF, 2f)
    var r = radius * 0.40f
    while (r < radius * 0.97f) {
        canvas.drawCircle(cx, cy, r, groove)
        r += 10f
    }
    val labelR = radius * 0.34f
    drawArtwork(canvas, label, "", cx - labelR, cy - labelR, labelR * 2f, true)
    canvas.drawCircle(cx, cy, radius * 0.05f, fillPaint(0xFF0B0B0D.toInt()))
    val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            intArrayOf(0x29FFFFFF, 0, 0x12FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(cx, cy, radius, sheen)
}

/** Intro: a pile of polaroids and round stickers. */
private fun drawIntroCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val squares = summary.songs.mapNotNull { it.song.thumbnailUrl }.distinct().take(3)
    val faces = summary.artists.mapNotNull { it.artworkUrl }.distinct()
        .filterNot { it in squares }.take(3)
    val height = 900f
    // Fraction of the content box across, fraction down, cover size, tilt.
    val polaroids = listOf(
        floatArrayOf(0.27f, 0.30f, 470f, -4f),
        floatArrayOf(0.03f, 0.05f, 270f, -10f),
        floatArrayOf(0.64f, 0.00f, 235f, 8f),
    )
    squares.forEachIndexed { index, url ->
        val spec = polaroids[index]
        drawPolaroid(
            canvas,
            covers[url],
            MARGIN + CONTENT_W * spec[0],
            top + height * spec[1],
            spec[2],
            spec[3],
            index == 0,
        )
    }
    val stickers = listOf(
        floatArrayOf(0.02f, 0.64f, 200f),
        floatArrayOf(0.72f, 0.38f, 240f),
        floatArrayOf(0.46f, 0.74f, 210f),
    )
    faces.forEachIndexed { index, url ->
        val spec = stickers[index]
        val x = MARGIN + CONTENT_W * spec[0]
        val y = top + height * spec[1]
        drawShadow(canvas, x, y, spec[2], true)
        drawArtwork(canvas, covers[url], "", x, y, spec[2], true)
        canvas.drawCircle(x + spec[2] / 2f, y + spec[2] / 2f, spec[2] / 2f, strokePaint(PAPER, 8f))
    }
    val caption = type.serif(46f, 0x9EFFFFFF.toInt(), italic = true, bold = false)
        .apply { textAlign = Paint.Align.CENTER }
    canvas.drawText(
        ellipsised(context.getString(R.string.replay_private_counting), caption, CONTENT_W),
        POSTER_W / 2f,
        top + height + 60f,
        caption,
    )
}

/** Minutes: a record inside a ring of the 24 hours, the peak hour in gold. */
private fun drawMinutesCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val cx = POSTER_W / 2f
    val ring = 380f
    val cy = top + ring + 20f
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, ring, intArrayOf(0x38EBD9A8, 0), null, Shader.TileMode.CLAMP)
    }
    canvas.drawCircle(cx, cy, ring, glow)
    val peak = summary.peakHour
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    for (i in 0 until 24) {
        val angle = (i / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val isPeak = peak != null && i == peak
        val major = i % 6 == 0
        val length = if (isPeak) 72f else if (major) 42f else 24f
        val outer = ring - 6f
        tick.color = if (isPeak) CHAMPAGNE else if (major) 0x66FFFFFF else 0x33FFFFFF
        tick.strokeWidth = if (isPeak) 9.6f else 4.2f
        canvas.drawLine(
            cx + cos(angle) * (outer - length),
            cy + sin(angle) * (outer - length),
            cx + cos(angle) * outer,
            cy + sin(angle) * outer,
            tick,
        )
    }
    drawVinyl(
        canvas,
        summary.songRows(1).firstOrNull()?.artworkUrl?.let { covers[it] },
        cx,
        cy,
        ring * 0.70f,
    )

    val boxTop = cy + ring + 60f
    drawGlass(canvas, RectF(MARGIN, boxTop, POSTER_W - MARGIN, boxTop + 240f), 60f)
    val text = buildString {
        if (summary.hours >= 1) {
            append(context.getString(R.string.replay_hours_across, grouped(summary.hours)))
        } else {
            append(context.getString(R.string.replay_across))
            append(" ")
        }
        if (summary.hours >= 1) append(", ")
        append(
            context.resources.getQuantityString(
                R.plurals.replay_play_count,
                summary.totalPlays,
                grouped(summary.totalPlays.toLong()),
            ),
        )
        val hour = summary.peakHour
        if (hour != null) {
            append(", mostly around ")
            append(formatHour(context, hour))
            append(".")
        } else {
            append(".")
        }
    }
    val body = type.serif(52f, 0xD1FFFFFF.toInt(), bold = false)
    drawWrapped(canvas, text, body, MARGIN + 54f, boxTop + 96f, CONTENT_W - 108f, 70f, 2)
}

/** Songs: a spotlit number one over a tracklist set in gold numerals. */
private fun drawSongsCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    rows: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val lead = rows.firstOrNull() ?: return
    val hero = 360f
    val heroY = top + 20f
    val center = MARGIN + hero / 2f
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            center,
            heroY + hero / 2f,
            hero * 0.95f,
            intArrayOf(0x66EBD9A8, 0),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(center, heroY + hero / 2f, hero * 0.95f, glow)
    drawShadow(canvas, MARGIN, heroY, hero, false, 0.06f)
    drawArtwork(canvas, lead.artworkUrl?.let { covers[it] }, lead.title, MARGIN, heroY, hero, false, 0.06f)
    canvas.drawRoundRect(
        RectF(MARGIN, heroY, MARGIN + hero, heroY + hero),
        hero * 0.06f,
        hero * 0.06f,
        strokePaint(0x8CEBD9A8.toInt(), 3f),
    )

    val textX = MARGIN + hero + 54f
    val textWidth = POSTER_W - MARGIN - textX
    val tag = type.label(30f, CHAMPAGNE, tracking = 0.2f)
    canvas.drawText("NO. 1", textX, heroY + 62f, tag)
    canvas.drawRect(
        textX + tag.measureText("NO. 1") + 24f,
        heroY + 50f,
        POSTER_W - MARGIN,
        heroY + 52f,
        fillPaint(0x73EBD9A8),
    )
    var y = drawWrapped(canvas, lead.title, type.serif(70f, Color.WHITE), textX, heroY + 140f, textWidth, 78f, 3)
    lead.subtitle?.let {
        val sub = type.body(46f, 0xB3FFFFFF.toInt())
        canvas.drawText(ellipsised(it, sub, textWidth), textX, y, sub)
        y += 58f
    }
    val stats = type.body(40f, 0x8CFFFFFF.toInt())
    canvas.drawText(
        ellipsised(
            "${formatListening(context, lead.ms)} · ${context.replayCount(lead.plays, R.plurals.replay_play_count)}",
            stats,
            textWidth,
        ),
        textX,
        y,
        stats,
    )

    var rowY = heroY + hero + 110f
    rows.drop(1).forEachIndexed { index, row ->
        if (index > 0) drawGoldRule(canvas, MARGIN, rowY - 12f, CONTENT_W)
        canvas.drawText(
            "%02d".format(row.rank),
            MARGIN,
            rowY + 88f,
            type.serif(78f, CHAMPAGNE, italic = true, bold = false),
        )
        val artX = MARGIN + 150f
        drawArtwork(canvas, row.artworkUrl?.let { covers[it] }, row.title, artX, rowY + 8f, 120f, false, 0.12f)
        val nameX = artX + 120f + 40f
        val name = type.body(50f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(row.title, name, POSTER_W - MARGIN - nameX), nameX, rowY + 62f, name)
        canvas.drawText(
            formatListening(context, row.ms),
            nameX,
            rowY + 112f,
            type.body(38f, 0x80FFFFFF.toInt()),
        )
        rowY += 150f
    }
}

/** Artists: a headliner inside pulsing rings, the runners-up as a row of faces. */
private fun drawArtistsCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    rows: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val lead = rows.firstOrNull() ?: return
    val cx = POSTER_W / 2f
    val size = 380f
    val cy = top + 40f + 240f
    for (i in 0 until 3) {
        val t = i / 3f
        val alpha = ((1f - t) * 86f).toInt()
        canvas.drawCircle(
            cx,
            cy,
            size / 2f * (1.05f + t * 0.75f),
            strokePaint(ColorUtils.setAlphaComponent(CHAMPAGNE, alpha), 3.6f),
        )
    }
    drawShadow(canvas, cx - size / 2f, cy - size / 2f, size, true)
    drawArtwork(canvas, lead.artworkUrl?.let { covers[it] }, lead.title, cx - size / 2f, cy - size / 2f, size, true)
    canvas.drawCircle(cx, cy, size / 2f, strokePaint(0xD9FFFFFF.toInt(), 6f))

    val name = type.heading(84f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
    canvas.drawText(ellipsised(lead.title, name, CONTENT_W), cx, cy + size / 2f + 100f, name)
    val stats = type.body(42f, 0x94FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    canvas.drawText(
        ellipsised(
            "${formatListening(context, lead.ms)} · ${context.replayCount(lead.plays, R.plurals.replay_play_count)}",
            stats,
            CONTENT_W,
        ),
        cx,
        cy + size / 2f + 160f,
        stats,
    )

    val others = rows.drop(1).take(4)
    if (others.isEmpty()) return
    val gap = 24f
    val colW = (CONTENT_W - gap * 3f) / 4f
    val avatar = 150f
    val rowTop = cy + size / 2f + 240f
    others.forEachIndexed { index, row ->
        val mid = MARGIN + index * (colW + gap) + colW / 2f
        val rank = type.mono(32f, CHAMPAGNE).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("%02d".format(row.rank), mid, rowTop, rank)
        drawArtwork(canvas, row.artworkUrl?.let { covers[it] }, row.title, mid - avatar / 2f, rowTop + 24f, avatar, true)
        val label = type.body(36f, 0xE6FFFFFF.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
        val labelTop = rowTop + 24f + avatar + 52f
        drawWrapped(canvas, row.title, label, mid, labelTop, colW, 42f, 2)
        val time = type.body(30f, 0x80FFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(formatListening(context, row.ms), mid, labelTop + 90f, time)
    }
}

/** Albums: a sleeve with its record sliding out, the runners-up as a shelf. */
private fun drawAlbumCard(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    rows: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val lead = rows.firstOrNull() ?: return
    val art = lead.artworkUrl?.let { covers[it] }
    val sleeve = 340f
    val group = sleeve * 1.42f
    val x0 = (POSTER_W - group) / 2f
    val y0 = top + 20f
    val discR = sleeve * 0.98f / 2f
    drawVinyl(canvas, art, x0 + group - discR, y0 + sleeve / 2f, discR)
    drawShadow(canvas, x0, y0, sleeve, false, 0.03f)
    drawArtwork(canvas, art, lead.title, x0, y0, sleeve, false, 0.03f)

    var y = y0 + sleeve + 96f
    y = drawWrapped(canvas, lead.title, type.serif(64f, Color.WHITE), MARGIN, y, CONTENT_W, 70f, 2)
    lead.subtitle?.let {
        val sub = type.body(44f, 0xB3FFFFFF.toInt())
        canvas.drawText(ellipsised(it, sub, CONTENT_W), MARGIN, y, sub)
        y += 54f
    }
    val stats = type.body(38f, 0x8CFFFFFF.toInt())
    canvas.drawText(
        "${formatListening(context, lead.ms)} · ${context.replayCount(lead.plays, R.plurals.replay_play_count)}",
        MARGIN,
        y,
        stats,
    )
    y += 56f

    val cardW = (CONTENT_W - 24f) / 2f
    val cardH = 150f
    rows.drop(1).take(4).forEachIndexed { index, row ->
        val left = MARGIN + (index % 2) * (cardW + 24f)
        val cardTop = y + 10f + (index / 2) * (cardH + 22f)
        drawGlass(canvas, RectF(left, cardTop, left + cardW, cardTop + cardH), 48f)
        drawArtwork(canvas, row.artworkUrl?.let { covers[it] }, row.title, left + 24f, cardTop + 21f, 108f, false, 0.14f)
        val textX = left + 24f + 108f + 28f
        drawWrapped(
            canvas,
            row.title,
            type.body(38f, Color.WHITE, bold = true),
            textX,
            cardTop + 62f,
            cardW - (textX - left) - 24f,
            44f,
            2,
        )
        canvas.drawText(
            formatListening(context, row.ms),
            textX,
            cardTop + 126f,
            type.body(30f, 0x8CFFFFFF.toInt()),
        )
    }
}

/** Habits: each number on a paper ticket stub. */
private fun drawHabitCards(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    top: Float,
) {
    val tickets = buildList<Pair<String, String>> {
        if (summary.distinctAlbums > 0) {
            add(grouped(summary.distinctAlbums.toLong()) to context.getString(R.string.different_albums))
        }
        summary.busiestDay?.let {
            add(
                formatDay(context, it) to
                    context.getString(R.string.your_biggest_day, formatListening(context, summary.busiestDayMs)),
            )
        }
        summary.peakHour?.let {
            add(formatHour(context, it) to context.getString(R.string.when_you_listen_the_most))
        }
    }
    val height = 270f
    tickets.forEachIndexed { index, (value, label) ->
        val y = top + 40f + index * (height + 84f)
        val rect = RectF(MARGIN, y, POSTER_W - MARGIN, y + height)
        canvas.save()
        canvas.rotate(if (index % 2 == 0) -1.6f else 1.3f, rect.centerX(), rect.centerY())
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PAPER
            setShadowLayer(40f, 0f, 14f, 0x66000000)
        }
        canvas.drawRoundRect(rect, 42f, 42f, paper)
        val cut = rect.left + rect.width() * 0.78f
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(INK, 90)
            style = Paint.Style.STROKE
            strokeWidth = 3.6f
            pathEffect = DashPathEffect(floatArrayOf(18f, 15f), 0f)
        }
        canvas.drawLine(cut, rect.top + 30f, cut, rect.bottom - 30f, dash)
        val punch = fillPaint(0x66000000)
        canvas.drawCircle(cut, rect.top, 21f, punch)
        canvas.drawCircle(cut, rect.bottom, 21f, punch)

        val valuePaint = type.serif(96f, INK)
        canvas.drawText(
            ellipsised(value, valuePaint, cut - rect.left - 108f),
            rect.left + 54f,
            rect.top + 130f,
            valuePaint,
        )
        val labelPaint = type.body(40f, ColorUtils.setAlphaComponent(INK, 168))
        canvas.drawText(
            ellipsised(label, labelPaint, cut - rect.left - 108f),
            rect.left + 54f,
            rect.top + 200f,
            labelPaint,
        )
        val stubX = cut + (rect.right - cut) / 2f
        canvas.drawText(
            "NO.",
            stubX,
            rect.centerY() - 8f,
            type.mono(34f, ColorUtils.setAlphaComponent(INK, 128)).apply { textAlign = Paint.Align.CENTER },
        )
        canvas.drawText(
            "%02d".format(index + 1),
            stubX,
            rect.centerY() + 56f,
            type.mono(60f, ColorUtils.setAlphaComponent(INK, 178)).apply { textAlign = Paint.Align.CENTER },
        )
        canvas.restore()
    }
}

/** Summary: the whole year as liner notes in a champagne-edged panel. */
private fun drawLinerNotes(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    top: Float,
) {
    val items = buildList<Pair<String, String>> {
        summary.songs.firstOrNull()?.let { add(context.getString(R.string.top_song) to it.song.title) }
        summary.artists.firstOrNull()?.let { add(context.getString(R.string.top_artist) to it.title) }
        summary.albums.firstOrNull()?.let { add(context.getString(R.string.top_album) to it.title) }
        summary.genres.firstOrNull()?.let { add(context.getString(R.string.top_genre) to it.title) }
    }
    val cardTop = top + 10f
    val rect = RectF(MARGIN, cardTop, POSTER_W - MARGIN, cardTop + 400f + items.size * 190f)
    drawGlass(canvas, rect, 78f, 0x80EBD9A8.toInt())
    val inner = MARGIN + 60f
    val innerW = CONTENT_W - 120f
    canvas.drawText("LINER NOTES", inner, cardTop + 110f, type.label(32f, CHAMPAGNE, tracking = 0.3f))
    canvas.drawText(formatMinutes(summary.totalMs), inner, cardTop + 290f, type.serif(190f, Color.WHITE))
    canvas.drawText(
        context.getString(R.string.minutes).uppercase(Locale.ROOT),
        inner,
        cardTop + 356f,
        type.label(32f, 0xB3FFFFFF.toInt(), tracking = 0.2f),
    )
    drawGoldRule(canvas, inner, cardTop + 410f, innerW)
    var y = cardTop + 500f
    items.forEach { (label, value) ->
        canvas.drawText(
            label.uppercase(Locale.ROOT),
            inner,
            y,
            type.label(28f, CHAMPAGNE, tracking = 0.22f),
        )
        val v = type.serif(62f, Color.WHITE)
        canvas.drawText(ellipsised(value, v, innerW), inner, y + 76f, v)
        y += 190f
    }
}

// ── Background ──────────────────────────────────────────────────────────────

/**
 * The mesh, by hand.
 *
 * Four wide radial gradients off the leading sleeve's own colours, laid over a
 * dark base and then flattened under a vertical scrim — the same recipe the
 * player's backdrop uses, at a size where the radii can simply be written down
 * instead of derived from a layout.
 *
 * [height] is the canvas it is being drawn on: the poster passes the full
 * frame, a card that grows with its own contents passes whatever it turned out
 * to be. The anchors are fractions of it, so a short card gets the same
 * composition squeezed into its rows rather than the bottom half of a poster
 * left blank.
 */
internal fun drawBackdrop(
    canvas: Canvas,
    lead: Bitmap?,
    hue: Float,
    height: Float = POSTER_H.toFloat(),
) {
    val colors = paletteOf(lead).map { rotated(it, hue) }
    canvas.drawColor(dimmed(colors.first()))

    val anchors = listOf(
        0.20f to 0.16f,
        0.84f to 0.22f,
        0.76f to 0.66f,
        0.18f to 0.78f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    colors.forEachIndexed { index, color ->
        val (fx, fy) = anchors[index]
        val cx = POSTER_W * fx
        val cy = height * fy
        val radius = POSTER_W * 0.95f
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 210),
                ColorUtils.setAlphaComponent(color, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }
    paint.shader = null

    // Ink, weighted to the foot where the smaller type is.
    paint.shader = android.graphics.LinearGradient(
        0f,
        0f,
        0f,
        height,
        intArrayOf(0x8C000000.toInt(), 0x59000000, 0xCC000000.toInt()),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, POSTER_W.toFloat(), height, paint)
    paint.shader = null
}

/**
 * Four colours off the sleeve, saturated and held to a mid lightness so any
 * artwork yields a rich backdrop rather than a muddy or a blown-out one — the
 * same treatment `MeshGradient` gives its own palette, restated here because
 * this runs nowhere near a composition.
 */
internal fun paletteOf(bitmap: Bitmap?): List<Int> {
    val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
    val source = bitmap ?: return fallback
    val swatches = runCatching {
        Palette.from(source).maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
    }.getOrNull().orEmpty()
    if (swatches.isEmpty()) return fallback
    return (swatches + fallback).take(4).map(::tuned)
}

/** [color] turned [degrees] around the wheel, tone untouched — see [storyHue]. */
private fun rotated(color: Int, degrees: Float): Int {
    if (degrees == 0f) return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}

private fun tuned(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return ColorUtils.HSLToColor(hsl)
}

private fun dimmed(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = 0.10f
    return ColorUtils.HSLToColor(hsl)
}

// ── Bands ───────────────────────────────────────────────────────────────────

private fun drawHeader(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
): Float {
    val label = summary.localizedLabel(context)
    val title = if (label.length == 4 && label.all { it.isDigit() }) {
        "Replay ${label.takeLast(2)}"
    } else {
        "Replay · $label"
    }
    canvas.drawText(title, MARGIN, 132f, type.serif(50f, 0xFFFFFFFF.toInt(), italic = true))
    val brand = type.heading(46f, 0xE6FFFFFF.toInt()).apply {
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("Audify", POSTER_W - MARGIN, 132f, brand)
    // The mark, to the left of the word, exactly as the story header and the
    // card carry it. Without it the one artefact of this app that ends up in
    // somebody else's chat was the only place the logo didn't appear.
    val wordWidth = brand.measureText("Audify")
    drawLogo(canvas, context, POSTER_W - MARGIN - wordWidth - LOGO_GAP, 132f)

    val credit = listOfNotNull(
        holder.ifBlank { context.getString(R.string.default_replay_holder) },
        memberSince?.let { context.getString(R.string.member_since_value, it) },
    ).joinToString(" · ")
    canvas.drawText(
        credit.uppercase(Locale.ROOT),
        MARGIN,
        182f,
        type.label(24f, 0xB3EBD9A8.toInt(), tracking = 0.18f),
    )
    return 300f
}

private fun drawTotals(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    top: Float,
): Float {
    canvas.drawText(
        formatMinutes(summary.totalMs),
        MARGIN,
        top + 120f,
        type.serif(156f, Color.WHITE),
    )
    canvas.drawText(
        context.getString(R.string.minutes_listened).uppercase(context.resources.configuration.locales[0]),
        MARGIN,
        top + 176f,
        type.label(30f, 0xCCEBD9A8.toInt(), tracking = 0.20f),
    )
    canvas.drawText(
        "${context.replayCount(summary.totalPlays, R.plurals.replay_play_count)} · " +
            "${context.replayCount(summary.distinctSongs, R.plurals.replay_song_count)} · " +
            context.replayCount(summary.distinctArtists, R.plurals.replay_artist_count),
        MARGIN,
        top + 232f,
        type.body(30f, 0x99FFFFFF.toInt()),
    )
    return top + 320f
}

/**
 * The two charts side by side.
 *
 * Songs on the left because that is what people go looking for first, and
 * artists on the right as circles — the one distinction that needs no heading,
 * since every music app on the device makes it.
 */
private fun drawColumns(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    songs: List<ReplayRow>,
    artists: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
): Float {
    val columnWidth = (POSTER_W - MARGIN * 2 - COLUMN_GAP) / 2f
    val right = MARGIN + columnWidth + COLUMN_GAP

    val panelRows = maxOf(songs.size, artists.size)
    drawGlass(
        canvas,
        RectF(MARGIN - 28f, top - 64f, POSTER_W - MARGIN + 28f, top + 58f + panelRows * ROW_HEIGHT),
        44f,
    )
    val locale = context.resources.configuration.locales[0]
    canvas.drawText(
        context.getString(R.string.top_songs).uppercase(locale),
        MARGIN,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )
    canvas.drawText(
        context.getString(R.string.top_artists).uppercase(locale),
        right,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )

    val rows = maxOf(songs.size, artists.size)
    var y = top + 54f
    repeat(rows) { index ->
        songs.getOrNull(index)?.let {
            drawRow(canvas, context, type, it, covers, MARGIN, y, columnWidth, false)
        }
        artists.getOrNull(index)?.let {
            drawRow(canvas, context, type, it, covers, right, y, columnWidth, true)
        }
        y += ROW_HEIGHT
    }
    return y + 40f
}

private fun drawRow(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    row: ReplayRow,
    covers: Map<String, Bitmap?>,
    x: Float,
    y: Float,
    width: Float,
    circular: Boolean,
) {
    canvas.drawText(
        row.rank.toString(),
        x,
        y + ART * 0.68f,
        type.heading(34f, if (row.rank == 1) ACCENT else 0x73FFFFFF),
    )
    val artX = x + 44f
    val art = row.artworkUrl?.let { covers[it] }
    drawArtwork(canvas, art, row.title, artX, y, ART, circular)

    val textX = artX + ART + 20f
    val textWidth = width - (textX - x)
    val title = type.body(30f, Color.WHITE, bold = true)
    canvas.drawText(ellipsised(row.title, title, textWidth), textX, y + 36f, title)
    val sub = type.body(25f, 0x99FFFFFF.toInt())
    val detail = listOfNotNull(row.subtitle, formatListening(context, row.ms)).joinToString(" · ")
    canvas.drawText(ellipsised(detail, sub, textWidth), textX, y + 72f, sub)
}

private fun drawAlbums(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    albums: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
): Float {
    if (albums.isEmpty()) return top
    drawGlass(canvas, RectF(MARGIN - 28f, top - 64f, POSTER_W - MARGIN + 28f, top + 326f), 44f)
    canvas.drawText(
        context.getString(R.string.top_albums).uppercase(context.resources.configuration.locales[0]),
        MARGIN,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )
    val size = 200f
    val gap = (POSTER_W - MARGIN * 2 - size * POSTER_ALBUMS) / (POSTER_ALBUMS - 1)
    val y = top + 40f
    albums.take(POSTER_ALBUMS).forEachIndexed { index, album ->
        val x = MARGIN + index * (size + gap)
        drawArtwork(canvas, album.artworkUrl?.let { covers[it] }, album.title, x, y, size, false)
        val name = type.body(25f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(album.title, name, size), x, y + size + 38f, name)
        val sub = type.body(22f, 0x8CFFFFFF.toInt())
        canvas.drawText(
            ellipsised(album.subtitle ?: formatListening(context, album.ms), sub, size),
            x,
            y + size + 70f,
            sub,
        )
    }
    return y + size + 130f
}

private fun drawGenres(canvas: Canvas, context: Context, type: Fonts, summary: ReplaySummary, top: Float) {
    val genres = summary.genreRows(POSTER_GENRES)
    if (genres.isEmpty()) return
    canvas.drawText(
        context.getString(R.string.top_genres).uppercase(context.resources.configuration.locales[0]),
        MARGIN,
        top,
        type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f),
    )
    val paint = type.body(32f, Color.WHITE, bold = true)
    val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    var x = MARGIN
    val y = top + 44f
    genres.forEach { genre ->
        val width = paint.measureText(genre.title) + 44f
        if (x + width > POSTER_W - MARGIN) return
        canvas.drawRoundRect(RectF(x, y, x + width, y + 62f), 31f, 31f, chip)
        canvas.drawRoundRect(
            RectF(x, y, x + width, y + 62f),
            31f,
            31f,
            strokePaint(0x66EBD9A8, 2.4f),
        )
        canvas.drawText(genre.title, x + 22f, y + 42f, paint)
        x += width + 14f
    }
}

private fun drawFooter(canvas: Canvas, context: Context, type: Fonts) {
    canvas.drawText(
        context.getString(R.string.counted_on_device_with_audify),
        MARGIN,
        POSTER_H - 64f,
        type.label(24f, 0x73FFFFFF, tracking = 0.08f),
    )
}

// ── Pieces ──────────────────────────────────────────────────────────────────

/**
 * A cover, cropped square and rounded — or, where one never loaded, the same
 * lettered stand-in the lists use, so a missing sleeve is a deliberate-looking
 * tile rather than a hole.
 */
internal fun drawArtwork(
    canvas: Canvas,
    bitmap: Bitmap?,
    fallback: String,
    x: Float,
    y: Float,
    size: Float,
    circular: Boolean,
    corner: Float = 0.10f,
) {
    val bounds = RectF(x, y, x + size, y + size)
    val radius = if (circular) size / 2f else size * corner
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bitmap != null) {
        val scale = size / minOf(bitmap.width, bitmap.height).toFloat()
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                x - (bitmap.width * scale - size) / 2f,
                y - (bitmap.height * scale - size) / 2f,
            )
        }
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
    } else {
        val hue = (fallback.hashCode().toFloat() % 360f + 360f) % 360f
        paint.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.45f))
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}

/**
 * A card's sentence, wrapped, with the emphasised runs in the heavier face.
 *
 * Laid out by hand because a canvas has no notion of a paragraph made of two
 * typefaces: the runs are broken into words, each word keeps the weight of the
 * run it came from, and they are placed greedily until the next one would not
 * fit. Measuring per word rather than per run is what lets a line break in the
 * middle of the bold part — which every one of these sentences does.
 */
private fun drawRuns(
    canvas: Canvas,
    type: Fonts,
    runs: List<HeadlineRun>,
    x: Float,
    top: Float,
    maxWidth: Float,
    serif: Boolean = false,
): Float {
    val bold = if (serif) {
        type.serif(HEADLINE_SIZE, Color.WHITE)
    } else {
        type.heading(HEADLINE_SIZE, Color.WHITE)
    }
    val plain = if (serif) {
        type.serif(HEADLINE_SIZE, 0x9EFFFFFF.toInt())
    } else {
        type.body(HEADLINE_SIZE, 0x9EFFFFFF.toInt(), bold = true)
    }

    // Flattened first, with a weight remembered per character, and only then
    // split on whitespace. Splitting the runs individually looked equivalent and
    // was not: a run ending mid-word — "4 songs" bold followed by ", one was…"
    // plain — became two tokens with a space invented between them, and the
    // sentence read "4 songs , one was your anthem". A word that straddles a
    // weight boundary takes the weight of its first letter, which is why the
    // comma there comes out bold; that is the right way to be wrong, since the
    // alternative is a comma floating a space away from what it punctuates.
    val text = StringBuilder()
    val weights = ArrayList<Boolean>()
    runs.forEach { run ->
        text.append(run.text)
        repeat(run.text.length) { weights += run.bold }
    }

    var lineX = x
    var y = top + HEADLINE_SIZE
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            index++
            continue
        }
        var end = index
        while (end < text.length && !text[end].isWhitespace()) end++
        val word = text.substring(index, end)
        val paint = if (weights[index]) bold else plain
        val width = paint.measureText("$word ")
        if (lineX + width - x > maxWidth && lineX > x) {
            lineX = x
            y += HEADLINE_LEADING
        }
        canvas.drawText(word, lineX, y, paint)
        lineX += width
        index = end
    }
    return y + 24f
}

/**
 * The Audify mark, baseline-aligned with the word beside it.
 *
 * The same vector the app draws everywhere, tinted and given bounds rather than
 * rasterised to a PNG first — a vector drawable renders into an ordinary canvas
 * on every version this app supports, so there is nothing to be gained by
 * keeping a second copy of the logo around at a fixed size.
 */
internal fun drawLogo(
    canvas: Canvas,
    context: Context,
    right: Float,
    baseline: Float,
    width: Float = LOGO_W,
    height: Float = LOGO_H,
) {
    val logo = runCatching {
        ResourcesCompat.getDrawable(context.resources, R.drawable.ic_logo, null)
    }.getOrNull() ?: return
    val left = right - width
    val top = baseline - height
    logo.setTint(0xE6FFFFFF.toInt())
    logo.setBounds(
        Math.round(left),
        Math.round(top),
        Math.round(left + width),
        Math.round(top + height),
    )
    logo.draw(canvas)
}

/** Trims [text] to [width], with an ellipsis, the way a single-line row would. */
internal fun ellipsised(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
    return text.take(end).trimEnd() + "…"
}

/**
 * The app's own face, on a canvas.
 *
 * The poster is the one thing from this app that ends up somewhere else, so it
 * has more reason than any screen to be set in the type the app is set in.
 * Falls back to the platform sans if a weight can't be loaded, which keeps a
 * missing font resource a slightly plainer picture rather than a crash on the
 * share button.
 */
internal class Fonts(context: Context) {
    private val heavy = font(context, R.font.sf_pro_display_heavy) ?: Typeface.DEFAULT_BOLD
    private val semibold = font(context, R.font.sf_pro_display_semibold) ?: Typeface.DEFAULT_BOLD
    private val regular = font(context, R.font.sf_pro_display_regular) ?: Typeface.DEFAULT

    fun heading(size: Float, color: Int) = paint(heavy, size, color)

    fun body(size: Float, color: Int, bold: Boolean = false) =
        paint(if (bold) semibold else regular, size, color)

    fun label(size: Float, color: Int, tracking: Float = 0.10f) =
        paint(semibold, size, color).apply { letterSpacing = tracking }

    fun serif(size: Float, color: Int, italic: Boolean = false, bold: Boolean = true) =
        paint(
            Typeface.create(
                Typeface.SERIF,
                when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                },
            ),
            size,
            color,
        )

    fun mono(size: Float, color: Int) = paint(Typeface.MONOSPACE, size, color)

    private fun paint(face: Typeface, size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }

    private fun font(context: Context, id: Int): Typeface? =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
}

internal suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url.artworkAt(CARD_ART_PX))
        // Palette needs pixel access, and a hardware bitmap cannot be drawn
        // into a software canvas at all — which is the whole of this file.
        .allowHardware(false)
        .build()
    (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}.getOrNull()

/** 9:16, the shape the story cards are held to — see `StoryFrame`. */
private const val POSTER_W = 1080
private const val POSTER_H = 1920

private const val MARGIN = 72f

/** The mark's drawn size. 730×484 in the vector, so this keeps its proportions. */
private const val LOGO_W = 66f
private const val LOGO_H = 44f
private const val LOGO_GAP = 20f

/** The width type and artwork are laid out in. */
private const val CONTENT_W = POSTER_W - MARGIN * 2

/** Matches the 30sp the card on screen sets its sentence at, at 3x. */
private const val HEADLINE_SIZE = 88f
private const val HEADLINE_LEADING = 108f

/** How far down a chart a shared card goes — the same five the card shows. */
private const val CARD_ROWS = 5
private const val COLUMN_GAP = 36f
private const val ROW_HEIGHT = 116f
private const val ART = 84f
private const val ACCENT = 0xFFFA2D48.toInt()

private const val POSTER_ROWS = 5
private const val POSTER_ALBUMS = 3
private const val POSTER_GENRES = 4
