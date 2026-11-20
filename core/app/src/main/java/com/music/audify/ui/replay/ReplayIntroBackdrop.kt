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
 * Latar atmosferik khusus Story 01 (Intro): lampu radial merah tua, jingga,
 * emas, zaitun dan cokelat yang saling menimpa, lengkap dengan butir film dan
 * partikel halus yang melayang. Tidak memakai gradien linear untuk warna.
 *
 * API 33+ memakai satu AGSL RuntimeShader (domain warp + grain). Di bawah itu
 * atau bila shader gagal dikompilasi, jatuh ke radial gradient yang di-cache
 * per ukuran. Waktu hanya dibaca saat draw, jadi tiap frame hanya redraw.
 */
@Composable
internal fun IntroAtmosphere(
    modifier: Modifier = Modifier,
    active: Boolean,
) {
    var seconds by remember { mutableFloatStateOf(INTRO_START_SECONDS) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && IntroShaderSupport.supported) {
            runCatching { IntroAgsl() }.getOrNull()
        } else null
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            Spacer(Modifier.fillMaxSize().drawBehind { shader.draw(this, seconds) })
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val lights = IntroLights.map { l ->
                            val center = Offset(w * l.fx, h * l.fy)
                            val radius = w * l.r
                            FallbackLight(
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
                                0f to Color(0x33000000),
                                0.22f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color(0xCC1A0805),
                            ),
                            startY = 0f,
                            endY = h,
                        )
                        onDrawBehind {
                            drawRect(IntroBase)
                            val t = seconds
                            for (i in lights.indices) {
                                val l = lights[i]
                                val phase = IntroLights[i].phase
                                translate(
                                    left = sin(t * 0.11f + phase) * w * 0.05f,
                                    top = cos(t * 0.09f + phase) * h * 0.03f,
                                ) {
                                    drawCircle(l.brush, l.radius, l.center)
                                }
                            }
                            drawRect(shade)
                        }
                    },
            )
        }
        Spacer(Modifier.fillMaxSize().drawBehind { drawMotes(seconds) })
    }
}

private const val INTRO_START_SECONDS = 3f
private val IntroBase = Color(0xFF240B07)

private class FallbackLight(val brush: Brush, val center: Offset, val radius: Float)

private class IntroLight(
    val fx: Float,
    val fy: Float,
    val r: Float,
    val color: Color,
    val k: Float,
    val phase: Float,
)

private val IntroLights = listOf(
    IntroLight(0.18f, 0.16f, 0.95f, Color(0xFF9E0D1F), 0.95f, 0.0f),
    IntroLight(0.92f, 0.36f, 0.85f, Color(0xFFEB5C12), 0.90f, 1.4f),
    IntroLight(0.62f, 0.64f, 0.72f, Color(0xFFFABD2E), 0.80f, 2.6f),
    IntroLight(0.08f, 0.84f, 0.80f, Color(0xFF616619), 0.85f, 3.9f),
    IntroLight(0.95f, 0.98f, 0.80f, Color(0xFF3A140A), 0.90f, 5.1f),
)

// ── Partikel ────────────────────────────────────────────────────────────────

private class Mote(
    val x: Float,
    val y: Float,
    val r: Float,
    val speed: Float,
    val sway: Float,
    val phase: Float,
    val a: Float,
)

private val IntroMotes: List<Mote> = run {
    val random = Random(2026)
    List(26) {
        Mote(
            x = random.nextFloat(),
            y = random.nextFloat(),
            r = 0.8f + random.nextFloat() * 1.6f,
            speed = 0.004f + random.nextFloat() * 0.007f,
            sway = 0.01f + random.nextFloat() * 0.02f,
            phase = random.nextFloat() * 6.28f,
            a = 0.10f + random.nextFloat() * 0.16f,
        )
    }
}

private val MoteColor = Color(0xFFFFE2B3)

/** Naik pelan dan memudar di tepi atas/bawah; tanpa alokasi per frame. */
private fun DrawScope.drawMotes(t: Float) {
    for (i in IntroMotes.indices) {
        val m = IntroMotes[i]
        val y = ((m.y - t * m.speed) % 1f + 1f) % 1f
        val fade = (min(y, 1f - y) / 0.12f).coerceIn(0f, 1f)
        val x = m.x + sin(t * 0.35f + m.phase) * m.sway
        drawCircle(
            color = MoteColor.copy(alpha = m.a * fade),
            radius = m.r.dp.toPx(),
            center = Offset(size.width * x, size.height * y),
        )
    }
}

// ── AGSL ────────────────────────────────────────────────────────────────────

private object IntroShaderSupport {
    val supported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { RuntimeShader(INTRO_AGSL) }.isSuccess
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class IntroAgsl {
    private val shader = RuntimeShader(INTRO_AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, time: Float) {
        shader.setFloatUniform("iResolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("iTime", time)
        scope.drawRect(brush = brush)
    }
}

@Language("AGSL")
private const val INTRO_AGSL = """
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
    float a = exp(-dot(d, d) * 2.4) * k;
    return mix(col, tint, clamp(a, 0.0, 1.0));
}

half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float asp = iResolution.x / iResolution.y;
    float2 p = float2(uv.x * asp, uv.y);
    float t = iTime;

    float2 warp = float2(
        vnoise(p * 2.4 + float2(0.0, t * 0.04)),
        vnoise(p * 2.4 + float2(7.3, -t * 0.035))
    ) - 0.5;
    float2 w = p + warp * 0.16;

    float3 crimson = float3(0.60, 0.04, 0.12);
    float3 orange = float3(0.93, 0.37, 0.07);
    float3 gold = float3(0.99, 0.75, 0.19);
    float3 olive = float3(0.36, 0.38, 0.09);
    float3 brown = float3(0.13, 0.055, 0.04);

    float3 col = brown;
    col = blob(col, w, float2(asp * (0.18 + 0.06 * sin(t * 0.13)), 0.16 + 0.04 * cos(t * 0.10)), 0.70, crimson, 0.98);
    col = blob(col, w, float2(asp * (0.90 + 0.05 * cos(t * 0.11)), 0.34 + 0.05 * sin(t * 0.12)), 0.62, orange, 0.92);
    col = blob(col, w, float2(asp * (0.64 + 0.08 * sin(t * 0.09 + 1.0)), 0.64 + 0.05 * cos(t * 0.08)), 0.52, gold, 0.85);
    col = blob(col, w, float2(asp * (0.08 + 0.05 * cos(t * 0.10 + 2.0)), 0.82 + 0.04 * sin(t * 0.11)), 0.56, olive, 0.80);
    col = blob(col, w, float2(asp * (0.50 + 0.06 * sin(t * 0.07)), 0.38), 0.30, gold, 0.30);
    col = blob(col, w, float2(asp * 0.98, 1.0), 0.55, brown * 0.6, 0.85);

    // Kaki lebih gelap untuk keterbacaan tombol, ujung atas sedikit diredam.
    float lower = smoothstep(0.50, 1.0, uv.y);
    col *= 1.0 - 0.45 * lower;
    float top = 1.0 - smoothstep(0.0, 0.22, uv.y);
    col *= 1.0 - 0.18 * top;

    // Butir film (berkedip ~14 fps) dan noise halus berfrekuensi rendah.
    float grain = hash(fc + floor(t * 14.0) * 17.0) - 0.5;
    col += grain * 0.06;
    col += (vnoise(fc * 0.6) - 0.5) * 0.02;

    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
