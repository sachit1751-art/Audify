/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import android.app.Notification
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sachit.music.R
import com.sachit.music.constants.MediaSessionConstants
import com.sachit.music.models.MediaMetadata
import com.sachit.music.utils.InnerTubeXPlayer
import androidx.core.app.NotificationCompat

/**
 * Presents the MediaSession: notification layout, widget state, Cast sync.
 *
 * Extracted from MusicService (Plan 022 step 2) — owns the presentation layer
 * that today lives inline in [MusicService.updateNotification], widget update
 * calls, and Cast state propagation.
 *
 * Keeps the policy (what buttons to show, when to update) separate from the
 * service (which session, which player, which notification manager).
 *
 * Plan 022 step 2.
 */
internal class MediaSessionPresenter(
    private val mediaSession: MediaSession?,
    private val player: Player,
    private val currentSong: MediaMetadata?,
    private val isPlaying: Boolean,
    private val isLiked: Boolean?,
    private val appContext: android.content.Context,
) {
    fun updateNotification() {
        mediaSession?.setCustomLayout(
            buildCommandButtons(),
        )
    }

    private fun buildCommandButtons(): List<CommandButton> {
        val context = appContext
        return listOf(
            likeButton(context),
            repeatButton(context),
            shuffleButton(context),
            radioButton(context),
            addToPlaylistButton(context),
        )
    }

    private fun likeButton(context: android.content.Context) = CommandButton
        .Builder()
        .setDisplayName(
            context.getString(
                if (isLiked == true) R.string.action_remove_like else R.string.action_like,
            ),
        )
        .setIconResId(if (isLiked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        .setSessionCommand(MediaSessionConstants.CommandToggleLike)
        .setEnabled(currentSong != null)
        .build()

    private fun repeatButton(context: android.content.Context) = CommandButton
        .Builder()
        .setDisplayName(
            context.getString(
                when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> R.string.repeat_mode_off
                    Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                    Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                    else -> throw IllegalStateException()
                },
            ),
        )
        .setIconResId(
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> R.drawable.repeat
                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                else -> throw IllegalStateException()
            },
        )
        .setSessionCommand(MediaSessionConstants.CommandToggleRepeatMode)
        .build()

    private fun shuffleButton(context: android.content.Context) = CommandButton
        .Builder()
        .setDisplayName(context.getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
        .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
        .setSessionCommand(MediaSessionConstants.CommandToggleShuffle)
        .build()

    private fun radioButton(context: android.content.Context) = CommandButton
        .Builder()
        .setDisplayName(context.getString(R.string.start_radio))
        .setIconResId(R.drawable.radio)
        .setSessionCommand(MediaSessionConstants.CommandToggleStartRadio)
        .setEnabled(currentSong != null)
        .build()

    private fun addToPlaylistButton(context: android.content.Context) = CommandButton
        .Builder()
        .setDisplayName(context.getString(R.string.android_auto_target_playlist))
        .setIconResId(R.drawable.playlist_add)
        .setSessionCommand(MediaSessionConstants.CommandAddToTargetPlaylist)
        .setEnabled(currentSong != null)
        .build()

    companion object {
        /**
         * Creates a fallback foreground notification when the media session
         * notification provider has not yet produced one.
         */
        fun createFallbackForegroundNotification(
            context: android.content.Context,
            notificationManager: android.app.NotificationManager,
        ): Notification {
            return NotificationCompat.Builder(context, "media_player")
                .setContentTitle(context.getString(R.string.now_playing))
                .setSmallIcon(R.drawable.small_icon)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }
    }
}

/**
 * Result type for radio start operations.
 * Replaces MusicService.RadioStartResult usage across the codebase.
 */
internal enum class RadioStartResult {
    Started,
    Failed,
}
