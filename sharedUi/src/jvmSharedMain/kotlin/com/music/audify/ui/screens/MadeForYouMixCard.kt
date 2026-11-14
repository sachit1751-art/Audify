package com.music.audify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.audify.sharedui.resources.*
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.MeshPalette
import org.jetbrains.compose.resources.stringResource

/*
 * Kartu "Made for You Mix" di Top Picks: latar blob cairan (warna per mix) dan
 * teks ala sampul Apple Music: wordmark kecil di kiri atas, nama mix besar dua
 * baris di bawahnya ("Chill" / "Mix"), dan satu baris artis di kaki kartu.
 */

/** Latar cairan; Android memasok AGSL lewat [LocalMixCoverBackground], lainnya mesh gradient. */
@Composable
fun MixFluidArt(title: String, modifier: Modifier = Modifier) {
    val palette = remember(title) { mixContrastPalette(mixPalette(title)) }
    val background = LocalMixCoverBackground.current
    val motion = LocalArtworkMotion.current
    if (background != null) {
        background(palette, motion, modifier)
    } else {
        MeshGradientBackground(
            palette = remember(palette) { MeshPalette(palette.take(4)) },
            modifier = modifier,
            trackKey = title,
            blurRadius = 32.dp,
        )
    }
}

/**
 * Palet bertingkat gelap-terang untuk kartu Top Picks: dua warna digelapkan dan
 * satu diputihkan, supaya blob cairan punya bayangan dan sorot, bukan satu warna rata.
 * Urutan mengikuti shader: dasar mengambil warna ke-2 dan ke-3, blob berputar di kelimanya.
 */
fun mixContrastPalette(base: List<Color>): List<Color> {
    fun at(i: Int) = base[i % base.size]
    return listOf(
        lerp(at(3), Color.Black, 0.62f),
        at(1),
        lerp(at(2), Color.White, 0.45f),
        at(0),
        lerp(at(0), Color.Black, 0.35f),
    )
}

/** Deskripsi singkat per jenis mix, dipakai kalau mix tidak punya subtitle (daftar artis). */
private fun mixDescription(title: String): String {
    val t = title.lowercase()
    return when {
        "new music" in t || "new release" in t -> "Fresh releases picked for you, updated often."
        "chill" in t -> "Laid-back tracks to unwind to, picked from what you love."
        "friends" in t -> "Songs to share with friends, tuned to your taste."
        "get up" in t -> "High-energy tracks to get you moving."
        "favorite" in t -> "The songs you keep coming back to, all in one mix."
        "replay" in t -> "Your recent listening, ready to play again."
        else -> "A mix made just for you, from the music you love."
    }
}

/** Teks di atas [MixFluidArt]; bayangan lembut menjaga keterbacaan di atas warna terang. */
@Composable
fun MixCoverOverlay(title: String, subtitle: String, modifier: Modifier = Modifier, band: Boolean = true) {
    val (name, tail) = remember(title) { splitMixTitle(title) }
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        shadow = Shadow(Color(0x45000000), Offset(0f, 2f), 12f),
    )
    val madeForYou = stringResource(Res.string.top_pick_made_for_you)
    Box(modifier) {
        // Band gelap di kaki kartu, seperti kartu Top Picks lain.
        if (band) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
        Wordmark(
            logoHeight = 14.dp,
            fontSize = 16.sp,
            color = Color.White,
            gap = 2.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
        )
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                color = Color.White,
                style = style.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (tail != null) {
                Text(
                    text = tail,
                    color = Color.White.copy(alpha = 0.92f),
                    style = style.copy(fontWeight = FontWeight.Normal),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
        ) {
            Text(
                text = madeForYou,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = Shadow(Color(0x40000000), Offset(0f, 1f), 6f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = subtitle.ifBlank { mixDescription(title) }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        shadow = Shadow(Color(0x40000000), Offset(0f, 1f), 6f),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
