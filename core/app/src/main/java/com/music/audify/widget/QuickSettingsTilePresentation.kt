// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.widget

/** A small, deterministic projection of the authoritative media-session state. */
internal data class QuickSettingsTilePresentation(
    val isActive: Boolean,
    val subtitle: String,
    val stateDescription: String,
)

internal fun quickSettingsTilePresentation(
    hasTrack: Boolean,
    playWhenReady: Boolean,
    title: String?,
): QuickSettingsTilePresentation {
    if (!hasTrack) {
        return QuickSettingsTilePresentation(
            isActive = false,
            subtitle = "Tap to open Audify",
            stateDescription = "No active track",
        )
    }

    val track = title?.trim().orEmpty().ifBlank { "Now playing" }
    return if (playWhenReady) {
        QuickSettingsTilePresentation(
            isActive = true,
            subtitle = track,
            stateDescription = "Playing",
        )
    } else {
        QuickSettingsTilePresentation(
            isActive = false,
            subtitle = "Paused · $track",
            stateDescription = "Paused",
        )
    }
}
