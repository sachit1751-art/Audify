package com.music.audify.ui.components.floatingtabbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Tarikan karet pada accessory: 0 = diam, positif = tertarik ke atas dan
 * memanjang, negatif = terlepas ke bawah dan memipih. Transformasi layer saja,
 * tanpa shader, jadi tidak ada yang terpotong oleh batas layer. Dibaca di
 * graphicsLayer, jadi satu frame animasi tidak merekomposisi apa pun.
 */
internal fun Modifier.rubberStretch(liftPx: Float, stretch: () -> Float): Modifier = graphicsLayer {
    val p = stretch()
    transformOrigin = TransformOrigin(0.5f, if (p >= 0f) 1f else 0f)
    translationY = -liftPx * p
    scaleY = 1f + 0.12f * kotlin.math.abs(p)
    scaleX = 1f - 0.05f * kotlin.math.abs(p)
    clip = false
}

private val NEIGHBOUR_LIFT = 8.dp
private val NEIGHBOUR_TUG = 6.dp

/**
 * Tab kiri dan kanan ikut tertarik sedikit oleh tarikan accessory di tengah:
 * turun mengikutinya, dan bergeser ke arahnya. [sign] -1 untuk tab kiri, 1 untuk kanan.
 */
internal fun Modifier.neighbourPull(pull: () -> Float, sign: Float): Modifier = graphicsLayer {
    val p = pull()
    translationY = -NEIGHBOUR_LIFT.toPx() * p
    translationX = -sign * NEIGHBOUR_TUG.toPx() * kotlin.math.abs(p)
}
