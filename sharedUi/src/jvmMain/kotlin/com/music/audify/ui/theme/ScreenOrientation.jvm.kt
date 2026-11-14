package com.music.audify.ui.theme

import androidx.compose.runtime.Composable

private object NoScreenOrientation : ScreenOrientation {
    override fun landscape() = Unit
    override fun portrait() = Unit
}

@Composable
actual fun rememberScreenOrientation(): ScreenOrientation = NoScreenOrientation
