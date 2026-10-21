package com.sachit.music

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import com.sachit.music.db.MusicDatabase
import com.sachit.music.listentogether.ListenTogetherManager
import com.sachit.music.playback.PlayerConnection
import com.sachit.music.playback.DownloadUtil
import com.sachit.music.utils.ArtistNameAliases
import com.sachit.music.sync.SyncUtils
import com.sachit.music.utils.ChangelogState
import androidx.compose.foundation.layout.WindowInsets

// ─── Local providers ──────────────────────────────────────────────────────
// These are the composition locals that the UI layer reads via .current.
// They are provided at the root of the app (SachitApp / MainActivity content).

/** MusicDatabase Room database */
@JvmField
val LocalDatabase = staticCompositionLocalOf<MusicDatabase> {
    error("No LocalDatabase provided. Make sure SachitApp provides it.")
}

/** NavController for the main navigation graph */
@JvmField
val LocalNavController = staticCompositionLocalOf<NavController> {
    error("No LocalNavController provided. Make sure SachitApp provides it.")
}

/** PlayerConnection to the media service */
@JvmField
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> {
    null
}

/** DownloadUtil for download management */
@JvmField
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil?> {
    null
}

/** ListenTogetherManager for synced playback */
@JvmField
val LocalListenTogetherManager = staticCompositionLocalOf<ListenTogetherManager?> {
    null
}

/** ArtistNameAliases for artist name resolution */
@JvmField
val LocalArtistNameAliases = staticCompositionLocalOf<ArtistNameAliases> {
    ArtistNameAliases
}

/** WindowInsets that are aware of the player UI (mini player, transport etc.) */
@JvmField
val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<androidx.compose.foundation.layout.WindowInsets> {
    androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
}

/** Changelog state (whether user has seen the latest changelog) */
@JvmField
val LocalChangelogState = staticCompositionLocalOf<ChangelogState> {
    ChangelogState()
}

/** Sync utilities for library/podcast operations */
@JvmField
val LocalSyncUtils = staticCompositionLocalOf<com.sachit.music.sync.SyncUtils> {
    com.sachit.music.sync.SyncUtils
}

/** Audio export utility */
@JvmField
val LocalAudioExporter = staticCompositionLocalOf<com.sachit.music.utils.AudioExporter> {
    com.sachit.music.utils.AudioExporter
}

/** Whether the player is expanded (fullscreen) */
@JvmField
val LocalIsPlayerExpanded = staticCompositionLocalOf<Boolean> {
    false
}
