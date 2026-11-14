package com.music.audify.ui.player

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.LocalContext

internal actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

// RenderEffect, API 31+; `Modifier.blur` is a no-op below it.
internal actual val renderEffectBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// AGSL RuntimeShader, API 33+.
internal actual val fluidArtworkSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal actual class FluidArtworkShader actual constructor() {
    // Only built on 33+, and only ever referenced through this holder, so the
    // RuntimeShader class is never loaded on older devices.
    private val impl: FluidShaderImpl? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) FluidShaderImpl() else null

    actual fun effect(
        incoming: Boolean,
        progress: Float,
        direction: Float,
        punch: Float,
        seed: Float,
        width: Float,
        height: Float,
    ): RenderEffect? = impl?.effect(incoming, progress, direction, punch, seed, width, height)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class FluidShaderImpl {
    private val shader = RuntimeShader(FLUID_COVER_AGSL)

    fun effect(
        incoming: Boolean,
        progress: Float,
        direction: Float,
        punch: Float,
        seed: Float,
        width: Float,
        height: Float,
    ): RenderEffect? {
        if (width <= 0f || height <= 0f) return null
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("incoming", if (incoming) 1f else 0f)
        shader.setFloatUniform("dir", direction)
        shader.setFloatUniform("amp", punch)
        shader.setFloatUniform("seed", seed)
        return android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

/**
 * Morph cover cair. Dipakai di dua layer yang berbagi progres yang sama:
 * - lama (incoming = 0): UV-nya terdistorsi noise organik yang makin kuat, dan
 *   alfanya dibuka oleh mask noise radial dari tengah ke tepi, jadi cover lama
 *   "meleleh" memperlihatkan cover baru di bawahnya (bukan fade alfa rata);
 * - baru (incoming = 1): mulai terdistorsi lalu mengendap tajam ke bentuk asli.
 * Distorsi dibatasi ~4-5% tinggi supaya cover tetap dikenali.
 */
private const val FLUID_COVER_AGSL = """
uniform shader content;
uniform float2 size;
uniform float progress;
uniform float incoming;
uniform float dir;
uniform float amp;
uniform float seed;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
    float v = 0.5 * vnoise(p);
    p = p * 2.03 + 17.1;
    v += 0.25 * vnoise(p);
    p = p * 2.03 + 17.1;
    v += 0.125 * vnoise(p);
    return v / 0.875;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / size;
    float2 p = (uv - 0.5) * float2(size.x / size.y, 1.0);
    float t = clamp(progress, 0.0, 1.0);
    float flow = seed + t * 1.4;

    float2 q = float2(
        fbm(p * 1.7 + float2(flow, -flow * 0.6)),
        fbm(p * 1.7 + float2(-flow * 0.7, flow) + 8.3)
    ) - 0.5;
    float r = length(p);
    float2 swirl = float2(-p.y, p.x) * (1.0 - smoothstep(0.0, 0.8, r));

    float e = t * t * (3.0 - 2.0 * t);
    float strength = mix(e, 1.0 - e, incoming);
    float2 disp = (q * 2.0 + swirl * 0.35) * (0.075 * amp * strength);
    float2 sc = clamp(fragCoord + disp * size.y, float2(0.5), size - 0.5);
    float4 col = float4(content.eval(sc));
    if (incoming > 0.5) {
        return half4(col);
    }

    float n = fbm(p * 1.3 + float2(seed * 1.7, -seed) + q * 1.2);
    float f = 0.55 * n + 0.45 * smoothstep(0.0, 0.85, r) + dir * p.x * 0.18;
    float th = mix(-0.4, 1.45, t);
    float a = smoothstep(th - 0.2, th + 0.2, f);

    float lift = 4.0 * a * (1.0 - a) * 0.14 * amp;
    float alpha = col.a * a;
    float3 rgb = col.rgb * a + float3(lift) * alpha;
    return half4(float4(min(rgb, float3(alpha)), alpha));
}
"""

@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
internal actual fun appLanguageTag(): String {
    val context = LocalContext.current
    return AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
        ?.takeIf { it.isNotBlank() }
        ?: context.resources.configuration.locales.get(0).toLanguageTag()
}

/**
 * Back goes to [onBack] while [enabled], ahead of the sheet the player is drawn
 * in.
 *
 * The BackHandler can't do that on its own. The player is a ModalBottomSheet,
 * and from API 33 the sheet puts its own dismiss straight onto the window's
 * OnBackInvokedDispatcher at PRIORITY_DEFAULT — which is also where the dialog
 * dispatcher that every BackHandler feeds ends up, and at equal priority the
 * platform picks whichever registered last: the sheet's, every time. So on 33+
 * this outranks it with an overlay-priority callback for as long as [enabled]
 * holds, and only that long — otherwise the sheet keeps its own back handling
 * and its predictive-back shrink. Below 33 there is no window dispatcher to
 * outrank and the BackHandler is already the newest callback on the dialog's.
 *
 * Call order is priority order among these: a later call is the one back
 * reaches first while both are enabled.
 */
@Composable
actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, enabled) {
            val callback = if (enabled) OverlayBack.register(view, onBack) else null
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }
}

/**
 * A back callback that outranks whatever else the window has registered —
 * here, the sheet the player is drawn in. See the call site in
 * [NowPlayingScreen] for why it takes that.
 *
 * Everything that names an `android.window` type lives in this object so those
 * classes, which don't exist below API 33, are only ever *loaded* on a device
 * that has them: the callback comes back as [Any] rather than as the platform
 * interface for the same reason. Gating the calls on [Build.VERSION.SDK_INT]
 * is very likely enough by itself; this way it can't come down to how eagerly
 * a particular runtime resolves a reference it is never going to use.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object OverlayBack {
    /** The registered callback, to hand back to [unregister]; null if it couldn't be. */
    fun register(view: View, onBack: () -> Unit): Any? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        return callback
    }

    fun unregister(view: View, callback: Any?) {
        if (callback !is OnBackInvokedCallback) return
        view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
    }
}

/**
 * A touch drag is the only thing that can move this platform's drawer, and a
 * drag is also how the list inside it scrolls — so a finger that runs out of
 * list has to take the drawer with it. See [PlayerDrawer].
 */
internal actual val drawerFollowsListScroll: Boolean get() = true

internal actual fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}
