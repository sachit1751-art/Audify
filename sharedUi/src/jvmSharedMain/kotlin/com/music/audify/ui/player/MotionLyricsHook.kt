// Adapted from Zenith (https://github.com/rezapranata089-bit/Zenith), GPL-3.0.
// Audify is also distributed under GPL-3.0.
package com.music.audify.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.music.audify.data.lyrics.LyricLine

/** App-module renderer hook; null keeps the shared renderer's native fallback. */
val LocalMotionLyricLine = staticCompositionLocalOf<
    (@Composable (LyricLine, () -> Long, TextStyle, Color, Color, Modifier) -> Unit)?
> { null }
