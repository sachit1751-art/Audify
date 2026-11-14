package com.music.audify.ui.replay

import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.intellij.lang.annotations.Language

/**
 * Latar cairan khusus Replay, terinspirasi kartu Replay Apple Music: blob besar,
 * jenuh, dengan dasar gelap sehingga kontrasnya tinggi. Terpisah dari
 * OrganicFluidBackground supaya tempat lain yang memakainya tidak ikut berubah.
 *
 * Di bawah API 33 atau bila shader gagal dikompilasi, tidak ada yang digambar
 * (lihat [ReplayFluidSupport]); pemanggil jatuh ke backdrop gradien.
 */
@Composable
fun ReplayFluidBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1f,
) {
    val context = LocalContext.current
    val motionAllowed = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val speed = rememberUpdatedState(if (motionAllowed) animationSpeed else 0f)
    val palette = rememberUpdatedState(colors.ifEmpty { listOf(Color(0xFFFF2D55)) })
    var seconds by remember { mutableDoubleStateOf(ReplayFluidTimeline.phase) }
    val fluid = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ReplayFluidSupport.shaderSupported) {
            runCatching { ReplayAgsl() }.getOrNull()
        } else {
            null
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { speed.value > 0f }.collectLatest { running ->
                if (!running) return@collectLatest
                while (true) {
                    val frameNanos = withFrameNanos { it }
                    seconds = ReplayFluidTimeline.tick(frameNanos, speed.value)
                }
            }
        }
    }

    if (fluid != null) {
        // Satu-satunya state yang dibaca saat draw: tiap frame hanya redraw.
        Spacer(modifier.drawBehind { fluid.draw(this, seconds.toFloat(), palette.value) })
    }
}

/** Apakah shader Replay bisa dipakai di perangkat ini. Dicek sekali, lalu di-cache. */
object ReplayFluidSupport {
    val shaderSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { RuntimeShader(REPLAY_FLUID_AGSL) }.isSuccess
    }
}

/** Jam bersama: fase hanya maju saat ada yang berjalan, dan lanjut dari frame terakhir. */
private object ReplayFluidTimeline {
    var phase: Double = 12.0
        private set
    private var lastFrameNanos = 0L

    fun tick(frameNanos: Long, speed: Float): Double {
        if (frameNanos > lastFrameNanos) {
            val dt = if (lastFrameNanos == 0L) 0.0 else ((frameNanos - lastFrameNanos) / 1_000_000_000.0).coerceAtMost(0.1)
            lastFrameNanos = frameNanos
            phase += dt * speed
        }
        return phase
    }
}

private val REPLAY_UNIFORMS = arrayOf("c0", "c1", "c2", "c3", "c4")

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ReplayAgsl {
    private val shader = RuntimeShader(REPLAY_FLUID_AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, time: Float, colors: List<Color>) {
        shader.setFloatUniform("iResolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("iTime", time)
        val n = colors.size
        for (i in 0 until 5) {
            val c = colors[i % n]
            shader.setFloatUniform(REPLAY_UNIFORMS[i], c.red, c.green, c.blue)
        }
        scope.drawRect(brush = brush)
    }
}

@Language("AGSL")
private const val REPLAY_FLUID_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 c4;

float3 pal(float x) {
    x = mod(x, 5.0);
    float3 a = c0;
    float3 b = c1;
    float f = x;
    if (x >= 4.0) { a = c4; b = c0; f = x - 4.0; }
    else if (x >= 3.0) { a = c3; b = c4; f = x - 3.0; }
    else if (x >= 2.0) { a = c2; b = c3; f = x - 2.0; }
    else if (x >= 1.0) { a = c1; b = c2; f = x - 1.0; }
    return mix(a, b, f * f * (3.0 - 2.0 * f));
}

float2 rot(float2 v, float a) {
    float s = sin(a);
    float c = cos(a);
    return float2(c * v.x - s * v.y, s * v.x + c * v.y);
}

half4 main(float2 fc) {
    float2 p = (fc - 0.5 * iResolution) / min(iResolution.x * 1.25, iResolution.y);
    float t = iTime;

    // Lekukan lembut: dua lapis aliran saja supaya bentuk blob tetap besar dan terbaca.
    float2 w = p;
    w += 0.22 * float2(sin(p.y * 1.9 + t * 0.21), cos(p.x * 1.7 - t * 0.17));
    w += 0.12 * float2(sin(w.y * 3.1 - t * 0.29 + 1.3), cos(w.x * 2.8 + t * 0.25 + 2.1));

    // Dasar gelap dari warna palet terdalam, memberi ruang napas di antara blob.
    float g = clamp(0.5 + w.y * 0.7, 0.0, 1.0);
    float3 col = mix(pal(1.0 + t * 0.01), pal(3.0 + t * 0.01), g) * 0.42;

    // Blob besar jenuh dengan tepi smoothstep: tegas di pinggir, lembut di dalam.
    for (int i = 0; i < 7; i++) {
        float k = float(i) + 1.0;
        float h1 = fract(sin(k * 91.7) * 437.5);
        float h2 = fract(sin(k * 37.3) * 951.1);
        float h3 = fract(sin(k * 53.9) * 173.3);
        float h4 = fract(sin(k * 17.1) * 683.7);
        float2 c = float2(
            0.42 * sin(t * (0.07 + 0.07 * h1) + k * 2.3) + 0.14 * sin(t * (0.16 + 0.09 * h2) + k * 5.1),
            0.62 * sin(t * (0.06 + 0.07 * h2) + k * 1.7 + 1.0) + 0.16 * sin(t * (0.14 + 0.09 * h3) + k * 3.3)
        );
        float2 r = float2(
            0.42 + 0.34 * h3 + 0.06 * sin(t * (0.10 + 0.08 * h4) + k),
            0.30 + 0.22 * h4 + 0.05 * sin(t * (0.12 + 0.08 * h1) + k * 2.0)
        );
        float dir = h2 > 0.5 ? 1.0 : -1.0;
        float ang = t * (0.05 + 0.06 * h1) * dir + k * 1.3;
        float2 d = rot(w - c, ang) / r;
        float q = clamp(1.0 - dot(d, d), 0.0, 1.0);
        float a = q * q * (3.0 - 2.0 * q);
        float3 tint = pal(k * 0.83 + t * 0.02 + h1 * 2.0);
        col = mix(col, tint * 1.10, a * 0.92);
    }

    // Sorot terang tipis, lalu kurva-S ringan untuk menambah kontras.
    float ridge = pow(0.5 + 0.5 * sin(w.x * 2.4 + w.y * 1.7 + t * 0.15), 4.0);
    col += 0.10 * ridge * (1.0 - col);
    col = clamp(col, 0.0, 1.0);
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.35);

    // Dither agar tidak ada banding pada gradien halus.
    col += (fract(sin(dot(fc, float2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""