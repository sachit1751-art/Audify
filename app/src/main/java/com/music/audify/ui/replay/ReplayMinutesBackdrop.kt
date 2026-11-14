package com.music.audify.ui.replay

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import org.intellij.lang.annotations.Language

/**
 * Latar ungu atmosferik untuk Story 02 (menit mendengarkan): ungu tengah malam,
 * plum gelap, lavender, violet lembut dan dusty pink di atas dasar hampir hitam.
 * Sengaja redup supaya angka dan statistik tetap terbaca.
 *
 * API 33+ memakai satu AGSL RuntimeShader (distorsi gradien + grain animasi);
 * di bawahnya jatuh ke radial gradient yang di-cache per ukuran. Waktu hanya
 * dibaca saat draw, jadi tiap frame hanya redraw, tanpa rekomposisi.
 */
@Composable
internal fun MinutesAtmosphere(
    modifier: Modifier = Modifier,
    active: Boolean,
) {
    var seconds by remember { mutableFloatStateOf(MINUTES_START_SECONDS) }
    val shader = remember {
        if (MinutesShaderSupport.supported) runCatching { MinutesAgsl() }.getOrNull() else null
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) seconds += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
        }
    }

    Box(modifier) {
        if (shader != null) {
            Spacer(Modifier.fillMaxSize().drawBehind { shader.draw(this, seconds) })
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val lights = MinutesGlows.map { l ->
                            val center = Offset(w * l.fx, h * l.fy)
                            val radius = w * l.r
                            MinutesCachedGlow(
                                brush = Brush.radialGradient(
                                    colors = listOf(l.color.copy(alpha = l.k), Color.Transparent),
                                    center = center,
                                    radius = radius,
                                ),
                                center = center,
                                radius = radius,
                            )
                        }
                        val shade = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color(0x26000000),
                                0.25f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color(0xCC07020D),
                            ),
                            startY = 0f,
                            endY = h,
                        )
                        onDrawBehind {
                            drawRect(MinutesBase)
                            val t = seconds
                            for (i in lights.indices) {
                                val l = lights[i]
                                val phase = MinutesGlows[i].phase
                                translate(
                                    left = sin(t * 0.10f + phase) * w * 0.05f,
                                    top = cos(t * 0.08f + phase) * h * 0.03f,
                                ) {
                                    drawCircle(l.brush, l.radius, l.center)
                                }
                            }
                            drawRect(shade)
                        }
                    },
            )
        }
        Spacer(Modifier.fillMaxSize().drawBehind { drawDust(seconds) })
    }
}

private const val MINUTES_START_SECONDS = 8f
private val MinutesBase = Color(0xFF0B0414)

private class MinutesCachedGlow(val brush: Brush, val center: Offset, val radius: Float)

private class MinutesGlow(
    val fx: Float,
    val fy: Float,
    val r: Float,
    val color: Color,
    val k: Float,
    val phase: Float,
)

private val MinutesGlows = listOf(
    MinutesGlow(0.20f, 0.14f, 1.00f, Color(0xFF5B34B8), 0.75f, 0.0f),
    MinutesGlow(0.90f, 0.32f, 0.85f, Color(0xFF4A1A55), 0.90f, 1.3f),
    MinutesGlow(0.72f, 0.10f, 0.50f, Color(0xFF9C8AD6), 0.30f, 2.5f),
    MinutesGlow(0.12f, 0.62f, 0.80f, Color(0xFF1F0F4D), 0.90f, 3.7f),
    MinutesGlow(0.85f, 0.78f, 0.75f, Color(0xFFB5698F), 0.30f, 4.9f),
)

// ── Partikel ────────────────────────────────────────────────────────────────

private class Dust(
    val x: Float,
    val y: Float,
    val r: Float,
    val speed: Float,
    val sway: Float,
    val phase: Float,
    val a: Float,
)

private val MinutesDust: List<Dust> = run {
    val random = Random(1126)
    List(22) {
        Dust(
            x = random.nextFloat(),
            y = random.nextFloat(),
            r = 0.8f + random.nextFloat() * 1.5f,
            speed = 0.003f + random.nextFloat() * 0.006f,
            sway = 0.01f + random.nextFloat() * 0.02f,
            phase = random.nextFloat() * 6.28f,
            a = 0.08f + random.nextFloat() * 0.12f,
        )
    }
}

private val DustColor = Color(0xFFE3D6FF)

/** Debu lavender yang naik sangat pelan; tanpa alokasi per frame. */
private fun DrawScope.drawDust(t: Float) {
    for (i in MinutesDust.indices) {
        val m = MinutesDust[i]
        val y = ((m.y - t * m.speed) % 1f + 1f) % 1f
        val fade = (min(y, 1f - y) / 0.12f).coerceIn(0f, 1f)
        val x = m.x + sin(t * 0.30f + m.phase) * m.sway
        drawCircle(
            color = DustColor.copy(alpha = m.a * fade),
            radius = m.r.dp.toPx(),
            center = Offset(size.width * x, size.height * y),
        )
    }
}

// ── AGSL ────────────────────────────────────────────────────────────────────

private object MinutesShaderSupport {
    val supported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { RuntimeShader(MINUTES_AGSL) }.isSuccess
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class MinutesAgsl {
    private val shader = RuntimeShader(MINUTES_AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, time: Float) {
        shader.setFloatUniform("iResolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("iTime", time)
        scope.drawRect(brush = brush)
    }
}

@Language("AGSL")
private const val MINUTES_AGSL = """
uniform float2 iResolution;
uniform float iTime;

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float3 blob(float3 col, float2 p, float2 c, float r, float3 tint, float k) {
    float2 d = (p - c) / r;
    float a = exp(-dot(d, d) * 2.2) * k;
    return mix(col, tint, clamp(a, 0.0, 1.0));
}

half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float asp = iResolution.x / iResolution.y;
    float2 p = float2(uv.x * asp, uv.y);
    float t = iTime;

    // Distorsi gradien yang sangat lambat: bentuk cahaya terasa organik.
    float2 warp = float2(
        vnoise(p * 2.0 + float2(0.0, t * 0.035)),
        vnoise(p * 2.0 + float2(5.1, -t * 0.03))
    ) - 0.5;
    float2 w = p + warp * 0.18;

    float3 almostBlack = float3(0.040, 0.014, 0.075);
    float3 midnight = float3(0.12, 0.06, 0.30);
    float3 plum = float3(0.29, 0.10, 0.33);
    float3 lavender = float3(0.61, 0.54, 0.84);
    float3 violet = float3(0.36, 0.22, 0.72);
    float3 dusty = float3(0.71, 0.41, 0.56);

    float3 col = almostBlack;
    col = blob(col, w, float2(asp * (0.20 + 0.06 * sin(t * 0.12)), 0.14 + 0.04 * cos(t * 0.09)), 0.78, violet, 0.78);
    col = blob(col, w, float2(asp * (0.92 + 0.05 * cos(t * 0.10)), 0.34 + 0.05 * sin(t * 0.11)), 0.64, plum, 0.92);
    col = blob(col, w, float2(asp * (0.72 + 0.06 * sin(t * 0.08 + 1.0)), 0.10 + 0.03 * cos(t * 0.07)), 0.34, lavender, 0.28);
    col = blob(col, w, float2(asp * (0.10 + 0.05 * cos(t * 0.09 + 2.0)), 0.62 + 0.04 * sin(t * 0.10)), 0.62, midnight, 0.92);
    col = blob(col, w, float2(asp * (0.88 + 0.04 * sin(t * 0.07 + 3.0)), 0.80 + 0.03 * cos(t * 0.08)), 0.46, dusty, 0.26);
    col = blob(col, w, float2(asp * 0.40, 1.02), 0.70, almostBlack, 0.90);

    // Redam kaki halaman: area statistik harus tetap kontras tinggi.
    float lower = smoothstep(0.52, 1.0, uv.y);
    col *= 1.0 - 0.50 * lower;
    float top = 1.0 - smoothstep(0.0, 0.20, uv.y);
    col *= 1.0 - 0.16 * top;

    // Butir film berkedip ~12 fps dan noise halus berfrekuensi rendah.
    float grain = hash(fc + floor(t * 12.0) * 13.0) - 0.5;
    col += grain * 0.055;
    col += (vnoise(fc * 0.55) - 0.5) * 0.018;

    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""