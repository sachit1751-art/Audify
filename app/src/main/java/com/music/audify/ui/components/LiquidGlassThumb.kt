package com.music.audify.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.audify.ui.components.backdrop.drawBackdrop
import com.music.audify.ui.components.backdrop.effects.blur
import com.music.audify.ui.components.backdrop.effects.colorControls
import com.music.audify.ui.components.backdrop.effects.blur
import com.music.audify.ui.components.backdrop.effects.colorControls
import com.music.audify.ui.components.backdrop.effects.lens
import com.music.audify.ui.components.backdrop.highlight.Highlight
import com.music.audify.ui.components.backdrop.shadow.Shadow

/**
 * The thumb of iOS 26's switch and slider: a white pill at rest that, once
 * lifted, goes clear and refracts whatever is under it.
 *
 * [backdrop] is a recording of the control's own track, taken by the caller
 * with `layerBackdrop` — the glass looks through to the track and nothing
 * else, so it costs a tiny lens rather than a capture of the page. [glass] is
 * how much of the material is on, 0 at rest to 1 lifted; it is read at draw
 * time, so a frame of the animation is a redraw and not a recomposition.
 *
 * Without glass support, or with "Reduce dynamic blur" on, it is the plain
 * white pill the controls had before.
 */
@Composable
internal fun LiquidGlassThumb(
    backdrop: LayerBackdrop,
    glass: () -> Float,
    modifier: Modifier = Modifier,
    restColor: Color = Color.White,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (!isGlassSupported() || reduceDynamicBlur || !LocalLiquidGlassEnabled.current) {
        RestingThumb(modifier)
        return
    }
    val shape = remember { RoundedCornerShape(percent = 50) }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                val g = glass().coerceIn(0f, 1f)
                if (g > 0.01f) {
                    colorControls(saturation = 1f + 0.35f * g)
                    // Blur nyaris nol: blur tebal meratakan tepi yang justru dibelokkan lensa.
                    blur(0.5.dp.toPx() * g)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Zona tepi dibatasi di bawah separuh tinggi thumb (30-32dp),
                        // jadi bagian tengahnya tetap jernih dan hanya tepinya membelokkan track.
                        lens(
                            refractionHeight = 9.dp.toPx() * g,
                            refractionAmount = 16.dp.toPx() * g,
                            depthEffect = true,
                        )
                    }
                }
            },
            highlight = {
                val g = glass().coerceIn(0f, 1f)
                if (g > 0.02f) Highlight.Default.copy(alpha = g) else null
            },
            shadow = {
                val g = glass().coerceIn(0f, 1f)
                Shadow(
                    radius = (3f + 8f * g).dp,
                    offset = DpOffset(0.dp, (1f + 3f * g).dp),
                    color = Color.Black.copy(alpha = 0.22f - 0.02f * g),
                )
            },
            onDrawSurface = {
                // Putih pekat saat diam; terangkat, hampir bening dengan sedikit
                // kabut putih dan pinggiran terang, supaya refraksi track di bawahnya
                // terlihat dan thumb tetap terbaca sebagai pill.
                val g = glass().coerceIn(0f, 1f)
                val fill = 1f - 0.86f * g
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            restColor.copy(alpha = fill),
                            restColor.copy(alpha = (fill - 0.06f * g).coerceAtLeast(0f)),
                        ),
                    ),
                    size = size,
                )
                if (g > 0.02f) {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.7f * g),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(w),
                    )
                }
            },
        ),
    )
}

/** The thumb at rest: plain drawing, so a column of switches stays cheap to scroll. */
@Composable
internal fun RestingThumb(modifier: Modifier = Modifier) {
    Box(modifier.shadow(2.dp, CircleShape).background(Color.White, CircleShape))
}
