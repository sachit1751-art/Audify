package com.music.audify.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.music.audify.data.lyrics.LyricLine

/**
 * Pengait renderer lirik Motion. Diisi oleh modul app (MainActivity);
 * bila null, panel memakai SweptLyricLine bawaan.
 * Argumen: baris, posisi playback, style, warna belum dinyanyikan, warna aktif, modifier.
 */
val LocalMotionLyricLine = staticCompositionLocalOf<
    (@Composable (LyricLine, () -> Long, TextStyle, Color, Color, Modifier) -> Unit)?
> { null }