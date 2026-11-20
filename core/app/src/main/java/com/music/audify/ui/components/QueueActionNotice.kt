package com.music.audify.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings

/** A unique queue result, so repeating the same action restarts its lifetime. */
data class QueueActionNotice(
    val id: Int,
    val message: String,
)

/** Brief queue confirmation shown immediately above the mini player. */
@Composable
fun QueueActionNoticeHost(
    notice: QueueActionNotice?,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            if (reduceAnimation) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                // Fade saja, tanpa geser. Ukuran tidak dianimasikan supaya
                // muncul/hilang tidak terlihat memuai atau menyusut.
                (fadeIn(tween(200)) togetherWith fadeOut(tween(200)))
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            }
        },
        contentKey = { it?.id },
        label = "queueActionNotice",
        // Tinggi layout selalu 0: kapsul digambar tepat di atas bar tanpa
        // memakai ruang. Sebelumnya Column bar ikut membesar/mengecil saat
        // notifikasi muncul/hilang, sehingga bar dan konten terdorong naik-turun.
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, 0) {
                    placeable.place(0, -placeable.height)
                }
            },
    ) { current ->
        if (current != null) {
            // Kapsul kecil di tengah, bukan kotak selebar layar.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 10.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Surface(
                    color = Color(0xEB2C2C2E),
                    contentColor = Color.White,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
