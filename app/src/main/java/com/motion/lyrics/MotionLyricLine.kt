package com.motion.lyrics

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

internal class MotionRuntime {
    var layout: MotionLayout? = null
    var stepLayout: MotionLayout? = null
    var measuredW = -1
    var fontPx = 0f
    var rise = FloatArray(0); var riseVel = FloatArray(0)
    var swell = FloatArray(0); var swellVel = FloatArray(0)
    val bloomA = FloatArray(1); val bloomV = FloatArray(1)
    var head = 0f
    var words: List<MotionWord> = emptyList()
    var maxSwell = 0f
    var timeSec = 0f
    var sparkle = true
    var headX = 0f; var headY = 0f; var headRtl = false
    var lastPos = Long.MIN_VALUE
    var idle = -1
    var tick by mutableLongStateOf(0L)
    private val out = HeadOut()

    val bloom: Float get() = MotionTuning.BloomStrength * bloomA[0] * (1f + 0.5f * maxSwell)

    fun step(lay: MotionLayout, words: List<MotionWord>, pos: Long, dt: Float, snapIn: Boolean) {
        var snap = snapIn
        this.words = words
        val n = lay.count
        if (rise.size != n) {
            rise = FloatArray(n); riseVel = FloatArray(n)
            swell = FloatArray(n); swellVel = FloatArray(n)
            snap = true
        }
        computeHead(words, pos, out)
        val h = out.head
        head = h
        val step = dt.coerceIn(0.001f, 0.05f)
        timeSec += step
        var maxS = 0f
        for (l in 0 until n) {
            val wi = lay.wordIndex[l]
            var riseTarget = 1f
            var swellTarget = 0f
            if (wi >= 0) {
                val w = words[wi]
                val c = lay.charIndex[l].toFloat()
                val through = (h - w.charStart) / (w.charEnd - w.charStart).coerceAtLeast(1).toFloat()
                val wordRise = smoothstep(through / MotionTuning.RiseCompleteAt)
                val letterRise = if (h >= w.charEnd) 1f else
                    smoothstep((h - c + MotionTuning.LetterRiseLeadChars) / MotionTuning.LetterRiseSpanChars)
                riseTarget = wordRise + (letterRise - wordRise) * MotionTuning.LetterRiseShare
                val hold = smoothstep(((w.endMs - w.startMs) - MotionTuning.HoldMinMs) / MotionTuning.HoldRangeMs)
                val tail = if (pos > w.endMs) (pos - w.endMs) / 1000f * MotionTuning.SwellTailCharsPerSec else 0f
                val sw = (h - c + MotionTuning.LetterSwellLeadChars + tail) / MotionTuning.LetterSwellSpanChars
                swellTarget = hold *
                    smoothstep(sw / MotionTuning.SwellAttackFraction) *
                    smoothstep((1f - sw) / MotionTuning.SwellReleaseFraction)
            }
            // Baris belum mulai: head diam di huruf pertama, jangan swell/partikel dulu.
            if (pos < words.first().startMs) swellTarget = 0f
            if (snap) {
                rise[l] = riseTarget; riseVel[l] = 0f
                swell[l] = swellTarget; swellVel[l] = 0f
            } else {
                dampedStep(rise, riseVel, l, riseTarget, MotionTuning.RiseFollowSec, step)
                dampedStep(swell, swellVel, l, swellTarget, MotionTuning.SwellFollowSec, step)
            }
            if (swell[l] > maxS) maxS = swell[l]
        }
        maxSwell = maxS
        if (snap) { bloomA[0] = out.sung; bloomV[0] = 0f }
        else dampedStep(bloomA, bloomV, 0, out.sung, MotionTuning.BloomFollowSec, step)

        // Posisi head dari geometri glyph nyata (kontinu melewati spasi).
        val res = lay.result
        val len = res.layoutInput.text.text.length
        if (len > 0) {
            val ci = floor(h).toInt().coerceIn(0, len - 1)
            val frac = (h - ci).coerceIn(0f, 1f)
            val box = res.getBoundingBox(ci)
            headRtl = res.getBidiRunDirection(ci) == ResolvedTextDirection.Rtl
            headX = if (!headRtl) box.left + frac * box.width else box.right - frac * box.width
            headY = box.center.y
        }
    }
}

@Composable
fun MotionLyricLine(
    text: String,
    words: List<MotionWord>,
    positionProvider: () -> Long,
    style: TextStyle,
    inactiveColor: Color,
    activeColor: Color,
    modifier: Modifier = Modifier,
    sparkle: Boolean = true
) {
    if (words.isEmpty() || text.isEmpty()) {
        BasicText(text = text, modifier = modifier, style = style.copy(color = inactiveColor))
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val motionStyle = remember(style) {
        style.copy(
            fontFeatureSettings = MotionTuning.NoLigatures,
            color = Color.Unspecified,
            // Teks beranimasi (naik + membesar): matikan hinting & snap piksel
            // supaya huruf tidak bergetar saat di-scale/translate tiap frame.
            textMotion = TextMotion.Animated
        )
    }
    val rt = remember(text, words, motionStyle) { MotionRuntime() }
    rt.sparkle = sparkle
    // Bloom kini digambar langsung per huruf (tanpa RenderEffect) agar tidak ada ghosting.

    LaunchedEffect(rt, words) {
        var last = -1L
        val firstMs = words.first().startMs
        val lastMs = words.last().endMs
        while (true) {
            val lay = rt.layout
            if (lay == null) { delay(50); continue }
            val pos = positionProvider()
            val inRange = pos >= firstMs - 600 && pos <= lastMs + 1500
            if (!inRange) {
                val s = if (pos < firstMs) 1 else 2
                if (rt.idle != s || rt.stepLayout !== lay) {
                    rt.step(lay, words, pos, 0f, true)
                    rt.idle = s; rt.stepLayout = lay; rt.tick++
                }
                last = -1L; rt.lastPos = Long.MIN_VALUE
                delay(100)
                continue
            }
            rt.idle = 0
            withFrameNanos { now ->
                val p = positionProvider()
                val dt = if (last < 0) 0.016f else (now - last) / 1_000_000_000f
                last = now
                val jump = rt.lastPos != Long.MIN_VALUE && abs(p - rt.lastPos) > 1200
                rt.step(lay, words, p, dt, jump || rt.stepLayout !== lay)
                rt.stepLayout = lay
                rt.lastPos = p
                rt.tick++
            }
        }
    }

    val bleedX = with(density) { MotionTuning.BleedXDp.dp.roundToPx() }
    val bleedY = with(density) { MotionTuning.BleedYDp.dp.roundToPx() }

    Spacer(
        modifier
            .layout { measurable, c ->
                val maxW = if (c.hasBoundedWidth) c.maxWidth else Constraints.Infinity
                val res = measurer.measure(
                    AnnotatedString(text), style = motionStyle,
                    constraints = Constraints(maxWidth = maxW)
                )
                if (rt.layout == null || rt.measuredW != maxW) {
                    rt.measuredW = maxW
                    rt.fontPx = if (motionStyle.fontSize.isSpecified) motionStyle.fontSize.toPx() else 18.sp.toPx()
                    rt.layout = buildMotionLayout(res, measurer, motionStyle, words)
                }
                val w = res.size.width
                val h = res.size.height
                val p = measurable.measure(Constraints.fixed(w + 2 * bleedX, h + 2 * bleedY))
                layout(w, h) { p.place(-bleedX, -bleedY) }
            }
            .graphicsLayer { clip = false }
            .drawBehind {
                val lay = rt.layout ?: return@drawBehind
                if (rt.tick < 0) return@drawBehind
                translate(bleedX.toFloat(), bleedY.toFloat()) {
                    drawMotion(lay, rt, inactiveColor, activeColor)
                }
            }
    )
}

private fun hash01(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

private fun DrawScope.drawParticles(
    i: Int,
    cx: Float,
    top: Float,
    w: Float,
    rt: MotionRuntime,
    color: Color,
    sw: Float
) {
    val fp = rt.fontPx
    for (k in 0 until MotionTuning.ParticleCount) {
        val seed = i * 31 + k * 17
        val phase = (rt.timeSec * MotionTuning.ParticleSpeed + hash01(seed)) % 1f
        val px = cx + (hash01(seed + 7) - 0.5f) * (w + fp * 0.5f)
        val py = top + fp * 0.2f - phase * fp * MotionTuning.ParticleRiseEm
        val a = sin(PI * phase).toFloat() * sw
        val rad = fp * 0.028f * (0.6f + hash01(seed + 13))
        drawCircle(color, rad * 2.6f, Offset(px, py), alpha = (a * 0.12f).coerceIn(0f, 1f))
        drawCircle(color, rad, Offset(px, py), alpha = (a * 0.75f).coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawMotion(
    lay: MotionLayout,
    rt: MotionRuntime,
    inactive: Color,
    active: Color
) {
    val riseDist = rt.fontPx * MotionTuning.RiseDistanceEm
    // Lapisan glow: gradien radial lembut, digambar SEBELUM teks dan tanpa clip,
    // alpha kontinu (tanpa ambang) sehingga tidak berkedip dan tepinya memudar penuh.
    val glowR = rt.fontPx * MotionTuning.GlowRadiusEm
    // Batas atas radius: tidak melewati area bleed (kecil dari X dan Y), jadi
    // pada kata yang ditahan lama glow tidak membesar sampai terpotong.
    val glowMaxR = minOf(MotionTuning.BleedXDp, MotionTuning.BleedYDp).dp.toPx() *
        MotionTuning.GlowMaxBleedShare
    for (i in 0 until lay.count) {
        if (i >= rt.rise.size) break
        val sw0 = rt.swell[i]
        // swell dijepit ke 0..1 supaya durasi panjang tidak melipatgandakan glow.
        val swc = sw0.coerceIn(0f, 1f)
        val prox0 = 1f - smoothstep(abs(rt.head - lay.charIndex[i] - 1f) / MotionTuning.GlowSpanChars)
        val a = (rt.bloomA[0] * prox0 * (0.35f + 0.65f * swc) * MotionTuning.GlowAlphaScale).coerceIn(0f, 1f)
        if (a < 0.004f) continue
        val cx = (lay.left[i] + lay.right[i]) / 2f
        val cy = (lay.top[i] + lay.bottom[i]) / 2f + (1f - rt.rise[i]) * riseDist
        val rad = minOf(glowR * (1f + 0.15f * swc), glowMaxR)
        drawCircle(
            brush = Brush.radialGradient(
                0f to active.copy(alpha = a),
                0.45f to active.copy(alpha = a * 0.45f),
                1f to active.copy(alpha = 0f),
                center = Offset(cx, cy), radius = rad
            ),
            radius = rad, center = Offset(cx, cy)
        )
    }
    for (i in 0 until lay.count) {
        if (i >= rt.rise.size) break
        val l = lay.left[i]; val r = lay.right[i]
        val w = r - l
        val sw = rt.swell[i]
        val dy = (1f - rt.rise[i]) * riseDist
        val s = 1f + MotionTuning.SwellScale * sw
        val pivot = Offset(l + w / 2f, (lay.top[i] + lay.bottom[i]) / 2f)
        val g = lay.glyphs[i]
        val tl = Offset(l, lay.glyphY[i])
        val dist = rt.head - lay.charIndex[i]
        val shadow: Shadow? = null
        // Kata sudah selesai dinyanyikan: huruf terakhirnya wajib menyala penuh.
        val wIdx = lay.wordIndex[i]
        val wordDone = wIdx >= 0 && wIdx < rt.words.size &&
            rt.head >= rt.words[wIdx].charEnd - 0.001f
        withTransform({
            translate(0f, dy)
            scale(s, s, pivot)
        }) {
            drawText(g, color = inactive, topLeft = tl)
            val feather = (rt.fontPx * MotionTuning.SweepFeatherEm).coerceAtLeast(1f)
            val rtl = lay.rtl[i]
            // Posisi kepala karaoke di ruang lokal huruf (0..w); bisa di luar rentang
            // sehingga pudarannya menjalar mulus melewati huruf-huruf di sekitarnya.
            val hx = if (rtl) w - dist * w else dist * w
            val front = feather * 0.6f
            val back = feather * 0.4f
            val reached = if (rtl) hx - front < w else hx + front > 0f
            val settled = if (rtl) hx + back <= 0f else hx - back >= w
            if (settled || wordDone) {
                drawText(g, color = active, topLeft = tl, shadow = shadow)
            } else if (reached) {
                val x0 = if (rtl) hx + back else hx - back
                val x1 = if (rtl) hx - front else hx + front
                val sweepBrush = Brush.horizontalGradient(
                    0f to active,
                    0.72f to active,
                    1f to active.copy(alpha = 0f),
                    startX = x0, endX = x1
                )
                drawText(g, brush = sweepBrush, topLeft = tl, shadow = shadow)
            }
        }
        if (sw > 0.01f && rt.sparkle) {
            drawParticles(i, l + w / 2f, lay.top[i] + dy, w, rt, active, smoothstep(sw / 0.35f))
        }
    }
}