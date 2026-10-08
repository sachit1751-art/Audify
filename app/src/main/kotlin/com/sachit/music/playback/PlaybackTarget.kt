/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import androidx.media3.common.Player

/**
 * A destination for playback commands.
 *
 * Collapses the 20 places in PlayerConnection that independently re-decide
 * Cast-vs-local, each with its own try/catch and logging. Instead, one target
 * is selected at connection time and all commands go through it.
 *
 * Plan 022 step 3.
 */
internal interface PlaybackTarget {
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun isCasting(): Boolean
}

/**
 * Guests in a Listen Together session cannot change playback.
 * Extracted from the 7 duplicated guard sites in MusicService.
 */
internal class GuestGuard(
    private val isGuest: () -> Boolean,
    private val shouldBlock: () -> Boolean?,
) {
    /**
     * Returns true when playback changes should be suppressed.
     * Call this at the front door of any playback mutation.
     */
    fun shouldBlockPlaybackChanges(): Boolean {
        if (!isGuest()) return false
        return shouldBlock() == true
    }
}

/** Local ExoPlayer target. */
internal class LocalPlayerTarget(
    private val player: Player,
    private val guestGuard: GuestGuard,
) : PlaybackTarget {
    override fun play() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.playWhenReady = true
    }

    override fun pause() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        player.pause()
    }

    override fun togglePlayPause() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        if (player.isPlaying) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        player.seekTo(positionMs)
    }

    override fun skipNext() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        player.seekToNextMediaItem()
    }

    override fun skipPrevious() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        player.seekToPreviousMediaItem()
    }

    override fun isCasting(): Boolean = false
}

/** Cast target — delegates to the CastConnectionHandler. */
internal class CastPlayerTarget(
    private val castHandler: CastConnectionHandler?,
    private val guestGuard: GuestGuard,
) : PlaybackTarget {
    override fun play() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        castHandler?.play()
    }

    override fun pause() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        castHandler?.pause()
    }

    override fun togglePlayPause() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        castHandler?.let { h ->
            if (h.castIsPlaying.value) h.pause() else h.play()
        }
    }

    override fun seekTo(positionMs: Long) {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        castHandler?.seekTo(positionMs)
    }

    override fun skipNext() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        castHandler?.skipToNext()
    }

    override fun skipPrevious() {
        if (guestGuard.shouldBlockPlaybackChanges()) return
        castHandler?.skipToPrevious()
    }

    override fun isCasting(): Boolean = castHandler?.isCasting?.value == true
}
