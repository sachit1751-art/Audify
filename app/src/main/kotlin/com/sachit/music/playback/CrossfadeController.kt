/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

/**
 * Decides *when* a crossfade should fire, and arms a single trigger for it.
 *
 * This module owns the parts of crossfade that are pure policy: the user-facing configuration
 * (enabled, duration, gapless suppression) and the guards that decide whether a given track is
 * eligible to crossfade at all. It deliberately does **not** own the player swap or the volume
 * ramp — those need an `ExoPlayer` and the service's audio-effect session, and remain in
 * [MusicService].
 *
 * Keeping the policy here means it can be exercised without a player, which is where the real
 * bugs live: an off-by-one in the trigger time surfaces as a crossfade that starts late, and a
 * missing guard surfaces as a crossfade firing into silence.
 */
internal class CrossfadeController(
    private val host: CrossfadeHost,
) {

    /** The outcome of evaluating one track for crossfade eligibility. */
    sealed interface Decision {
        /** No crossfade for this track; [reason] exists for logging and tests. */
        data class Skip(val reason: Reason) : Decision

        /** Crossfade at [triggerPositionMs] into the current track. */
        data class Fire(val triggerPositionMs: Long) : Decision
    }

    /** Why a track was not eligible. Ordered roughly from most to least common. */
    enum class Reason {
        /** Crossfade is switched off, or unavailable for the current context. */
        DISABLED,

        /** Duration is unset or non-positive. */
        INVALID_DURATION,

        /** The track is no longer than the crossfade window, so there is nothing to overlap. */
        TRACK_TOO_SHORT,

        /** The next track belongs to the same album and the user asked for gapless there. */
        GAPLESS_SAME_ALBUM,

        /** Nothing follows, and we are not looping a single track. */
        NO_NEXT_TRACK,

        /** The trigger point has already passed, so a message could never be delivered. */
        TRIGGER_ALREADY_PASSED,
    }

    private var _enabled = false
    private var _durationMs = DEFAULT_DURATION_MS
    private var _gapless = true

    /** Applies configuration changes. Duration is given in seconds, matching the stored preference. */
    fun updateConfig(enabled: Boolean, durationSeconds: Float, gapless: Boolean) {
        _enabled = enabled
        _durationMs = (durationSeconds * 1000f).toLong().coerceAtLeast(0L)
        _gapless = gapless
    }

    /** The configured crossfade window in ms. The volume ramp is scaled to match real elapsed time. */
    fun durationMs(): Long = _durationMs

    /**
     * Evaluates the current track and returns what should happen, without side effects.
     *
     * Exposed separately from [onTransition] so tests can assert the policy directly and so
     * callers that need to explain a skip can do so without arming anything.
     */
    fun evaluate(): Decision {
        if (!_enabled) return Decision.Skip(Reason.DISABLED)
        if (_durationMs <= 0L) return Decision.Skip(Reason.INVALID_DURATION)

        // A track whose length is not yet known reports a sentinel (Long.MIN_VALUE + 1 in
        // media3). Treat any negative duration as "unknown" rather than comparing it, so the
        // `TRACK_TOO_SHORT` branch cannot swallow it.
        val trackDurationMs = host.currentTrackDurationMs()
        if (trackDurationMs < 0L) return Decision.Skip(Reason.INVALID_DURATION)
        if (trackDurationMs <= _durationMs) return Decision.Skip(Reason.TRACK_TOO_SHORT)

        if (_gapless && isNextItemSameAlbum()) return Decision.Skip(Reason.GAPLESS_SAME_ALBUM)
        if (!host.hasNextMediaItem() && !host.isRepeatOne()) return Decision.Skip(Reason.NO_NEXT_TRACK)

        val triggerPositionMs = trackDurationMs - _durationMs
        if (triggerPositionMs - host.currentPositionMs() <= 0L) {
            return Decision.Skip(Reason.TRIGGER_ALREADY_PASSED)
        }

        return Decision.Fire(triggerPositionMs)
    }

    /**
     * Re-evaluates and (re)arms the trigger. Call on any event that could change eligibility —
     * a transition, a seek, a play/pause change.
     */
    fun onTransition() {
        cancel()
        when (val decision = evaluate()) {
            is Decision.Skip -> Unit
            is Decision.Fire -> arm(decision.triggerPositionMs)
        }
    }

    /** Alias for [onTransition]; a seek invalidates the armed trigger for the same reasons a transition does. */
    fun onSeek() = onTransition()

    /** Cancels any armed trigger. Safe to call when nothing is armed. */
    fun cancel() {
        host.cancelTrigger()
    }

    /** Cancels the armed trigger when it fires if the playback context has since changed. */
    fun onTriggerReached() {
        val timerPausesAtEnd = host.pausesAtSongEnd()
        if (host.isPlaying() && !timerPausesAtEnd) {
            host.beginCrossfade()
        }
        cancel()
    }

    private fun arm(triggerPositionMs: Long) {
        val targetMediaId = host.currentMediaId()
        host.scheduleTrigger(triggerPositionMs) {
            // The track may have changed between arming and firing. Only crossfade into the
            // track we scheduled for, otherwise a queued-up skip would crossfade into the
            // wrong item.
            if (host.currentMediaId() == targetMediaId) {
                onTriggerReached()
            }
        }
    }

    private fun isNextItemSameAlbum(): Boolean {
        val currentAlbum = host.currentAlbumTitle() ?: return false
        return host.nextAlbumTitle() == currentAlbum
    }

    companion object {
        const val DEFAULT_DURATION_MS = 5_000L
    }
}

/**
 * The player facts [CrossfadeController] needs, and the three actions it takes.
 *
 * Implemented by `MusicService`. Kept deliberately narrow: every member here is a fact or an
 * effect the policy genuinely requires, so the controller stays testable with a fake and the
 * service is not re-entered through a broad interface.
 */
internal interface CrossfadeHost {
    /** Duration of the current track in ms, or a negative value when not yet known. */
    fun currentTrackDurationMs(): Long

    /** Current playhead position in ms. */
    fun currentPositionMs(): Long

    fun hasNextMediaItem(): Boolean

    fun isRepeatOne(): Boolean

    fun isPlaying(): Boolean

    /** Id of the current media item, used to detect a track change before the trigger fires. */
    fun currentMediaId(): String?

    /** Album title of the current item, or null when unknown. */
    fun currentAlbumTitle(): CharSequence?

    /** Album title of the next item, or null when unknown. */
    fun nextAlbumTitle(): CharSequence?

    /** True when a sleep timer will pause playback at the end of this track. */
    fun pausesAtSongEnd(): Boolean

    /** Arms [action] to run once the playhead reaches [positionMs]. */
    fun scheduleTrigger(positionMs: Long, action: () -> Unit)

    /** Cancels any armed trigger. */
    fun cancelTrigger()

    /** Performs the swap. Implemented by the service; the controller never does this itself. */
    fun beginCrossfade()
}