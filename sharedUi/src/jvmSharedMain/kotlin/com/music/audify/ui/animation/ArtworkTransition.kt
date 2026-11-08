// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Shared artwork transition context for the outgoing and incoming navigation entries. */
@OptIn(ExperimentalSharedTransitionApi::class)
data class ArtworkTransitionContext(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val reduceMotion: Boolean,
)

val LocalArtworkTransition = staticCompositionLocalOf<ArtworkTransitionContext?> { null }

/** Joins matching browse artwork across a navigation transition; null ids remain ordinary images. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun sharedArtworkModifier(browseId: String?): Modifier {
    val context = LocalArtworkTransition.current
    if (browseId.isNullOrBlank() || context == null || context.reduceMotion) return Modifier
    return with(context.sharedScope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState("audify-artwork:$browseId"),
            animatedVisibilityScope = context.visibilityScope,
        )
    }
}
