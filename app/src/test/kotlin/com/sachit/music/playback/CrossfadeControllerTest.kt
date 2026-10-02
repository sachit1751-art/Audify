/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import com.sachit.music.playback.CrossfadeController.Decision
import com.sachit.music.playback.CrossfadeController.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the crossfade policy that used to be unreachable: the guards and the trigger-time
 * arithmetic lived inside `MusicService.scheduleCrossfade()`, which needs a bound service and a
 * real `ExoPlayer` to reach.
 */
class CrossfadeControllerTest {

    private val host = FakeCrossfadeHost()
    private val controller = CrossfadeController(host)

    private fun controllerWith(
        enabled: Boolean = true,
        durationSeconds: Float = 5f,
        gapless: Boolean = true,
    ): CrossfadeController =
        CrossfadeController(host).apply {
            updateConfig(enabled, durationSeconds, gapless)
        }

    // --- the happy path -----------------------------------------------------

    @Test
    fun `arms a trigger one crossfade window before the track ends`() {
        host.trackDurationMs = 200_000L
        host.positionMs = 0L

        val decision = controllerWith().evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 195_000L), decision)
    }

    @Test
    fun `arming stores the trigger position`() {
        host.trackDurationMs = 200_000L

        controllerWith(durationSeconds = 5f).onTransition()

        assertEquals(195_000L, host.scheduledPositionMs)
    }

    @Test
    fun `onTransition replaces any previously armed trigger`() {
        host.trackDurationMs = 200_000L
        val controller = controllerWith()

        controller.onTransition()
        controller.onTransition()

        assertEquals(2, host.armCount)
        assertEquals("each re-arm must cancel the previous trigger first", 2, host.cancelCount)
    }

    @Test
    fun `a longer configured window triggers earlier`() {
        host.trackDurationMs = 200_000L

        val decision = controllerWith(durationSeconds = 10f).evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 190_000L), decision)
    }

    // --- the guards ---------------------------------------------------------

    @Test
    fun `skips when crossfade is disabled`() {
        host.trackDurationMs = 200_000L

        val decision = controllerWith(enabled = false).evaluate()

        assertEquals(Decision.Skip(Reason.DISABLED), decision)
    }

    @Test
    fun `skips when duration is zero`() {
        host.trackDurationMs = 200_000L

        val decision = controllerWith(durationSeconds = 0f).evaluate()

        assertEquals(Decision.Skip(Reason.INVALID_DURATION), decision)
    }

    @Test
    fun `skips when duration is negative`() {
        host.trackDurationMs = 200_000L

        val decision = controllerWith(durationSeconds = -3f).evaluate()

        assertEquals(Decision.Skip(Reason.INVALID_DURATION), decision)
    }

    @Test
    fun `skips when the track is shorter than the crossfade window`() {
        // Exactly equal is the boundary: there is no room to overlap.
        host.trackDurationMs = 5_000L

        val decision = controllerWith(durationSeconds = 5f).evaluate()

        assertEquals(Decision.Skip(Reason.TRACK_TOO_SHORT), decision)
    }

    @Test
    fun `still arms for a track barely longer than the window`() {
        // Preserves the pre-extraction behaviour: a track 1ms over the window gets a trigger at
        // position 1ms rather than being skipped. Recorded here so a future "optimisation" that
        // skips it is a deliberate change.
        host.trackDurationMs = 5_001L
        host.positionMs = 0L

        val decision = controllerWith(durationSeconds = 5f).evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 1L), decision)
    }

    @Test
    fun `skips when duration is unknown`() {
        // media3 reports an unknown duration as Long.MIN_VALUE + 1. It must not be read as
        // "very short" or, worse, produce a wildly negative trigger position.
        host.trackDurationMs = Long.MIN_VALUE + 1

        val decision = controllerWith().evaluate()

        assertEquals(Decision.Skip(Reason.INVALID_DURATION), decision)
    }

    @Test
    fun `suppresses the crossfade inside an album when gapless is on`() {
        host.trackDurationMs = 200_000L
        host.currentAlbum = "OK Computer"
        host.nextAlbum = "OK Computer"

        val decision = controllerWith(gapless = true).evaluate()

        assertEquals(Decision.Skip(Reason.GAPLESS_SAME_ALBUM), decision)
    }

    @Test
    fun `crossfades within an album when gapless is off`() {
        host.trackDurationMs = 200_000L
        host.currentAlbum = "OK Computer"
        host.nextAlbum = "OK Computer"

        val decision = controllerWith(gapless = false).evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 195_000L), decision)
    }

    @Test
    fun `crossfades across albums even when gapless is on`() {
        host.trackDurationMs = 200_000L
        host.currentAlbum = "OK Computer"
        host.nextAlbum = "Kid A"

        val decision = controllerWith(gapless = true).evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 195_000L), decision)
    }

    @Test
    fun `does not suppress when the next track has no album`() {
        host.trackDurationMs = 200_000L
        host.currentAlbum = "OK Computer"
        host.nextAlbum = null

        val decision = controllerWith(gapless = true).evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 195_000L), decision)
    }

    @Test
    fun `skips the last track when there is nothing after it`() {
        host.trackDurationMs = 200_000L
        host.hasNext = false
        host.repeatOne = false

        val decision = controllerWith().evaluate()

        assertEquals(Decision.Skip(Reason.NO_NEXT_TRACK), decision)
    }

    @Test
    fun `crossfades the last track when repeating one`() {
        host.trackDurationMs = 200_000L
        host.hasNext = false
        host.repeatOne = true

        val decision = controllerWith().evaluate()

        assertEquals(Decision.Fire(triggerPositionMs = 195_000L), decision)
    }

    @Test
    fun `skips when the trigger point is already behind the playhead`() {
        // Re-arming mid-track, after the trigger position has gone by.
        host.trackDurationMs = 200_000L
        host.positionMs = 199_000L

        val decision = controllerWith().evaluate()

        assertEquals(Decision.Skip(Reason.TRIGGER_ALREADY_PASSED), decision)
    }

    @Test
    fun `skips rather than arming when the playhead is exactly at the trigger`() {
        host.trackDurationMs = 200_000L
        host.positionMs = 195_000L

        val decision = controllerWith().evaluate()

        assertEquals(Decision.Skip(Reason.TRIGGER_ALREADY_PASSED), decision)
    }

    // --- what happens when the trigger fires --------------------------------

    @Test
    fun `starts the crossfade when the trigger fires during playback`() {
        host.trackDurationMs = 200_000L
        host.playing = true
        val controller = controllerWith()
        controller.onTransition()

        host.fireTrigger()

        assertEquals(1, host.crossfadesStarted)
    }

    @Test
    fun `does not crossfade when playback was paused before the trigger`() {
        host.trackDurationMs = 200_000L
        host.playing = false
        val controller = controllerWith()
        controller.onTransition()

        host.fireTrigger()

        assertEquals(0, host.crossfadesStarted)
    }

    @Test
    fun `does not crossfade when a sleep timer will pause at the end of the track`() {
        host.trackDurationMs = 200_000L
        host.playing = true
        host.pausesAtEnd = true
        val controller = controllerWith()
        controller.onTransition()

        host.fireTrigger()

        assertEquals(0, host.crossfadesStarted)
    }

    @Test
    fun `does not crossfade into a different track if the item changed after arming`() {
        host.trackDurationMs = 200_000L
        host.mediaId = "track-a"
        host.playing = true
        val controller = controllerWith()
        controller.onTransition()

        // The user skipped; the armed message is still pending.
        host.mediaId = "track-b"
        host.fireTrigger()

        assertEquals(0, host.crossfadesStarted)
    }

    @Test
    fun `clears the trigger after it fires`() {
        host.trackDurationMs = 200_000L
        host.playing = true
        val controller = controllerWith()
        controller.onTransition()

        host.fireTrigger()

        assertTrue("a spent trigger must not stay armed", host.trigger == null)
    }

    // --- cancellation -------------------------------------------------------

    @Test
    fun `cancel disarms a pending trigger`() {
        host.trackDurationMs = 200_000L
        val controller = controllerWith()
        controller.onTransition()

        controller.cancel()
        host.fireTrigger()

        assertEquals(0, host.crossfadesStarted)
    }

    @Test
    fun `cancel is safe when nothing is armed`() {
        controllerWith().cancel()

        assertEquals(null, host.trigger)
    }

    @Test
    fun `evaluating has no side effects`() {
        host.trackDurationMs = 200_000L
        val controller = controllerWith()

        controller.evaluate()

        assertEquals("evaluate must not arm anything", null, host.trigger)
        assertEquals(0, host.armCount)
    }

    // --- configuration ------------------------------------------------------

    @Test
    fun `duration in seconds is exposed in milliseconds for the volume ramp`() {
        val controller = controllerWith(durationSeconds = 7.5f)

        assertEquals(7_500L, controller.durationMs())
    }

    @Test
    fun `duration defaults to five seconds before any preference arrives`() {
        assertEquals(5_000L, CrossfadeController(host).durationMs())
    }

    @Test
    fun `a negative configured duration is clamped rather than propagated`() {
        val controller = controllerWith(durationSeconds = -1f)

        assertEquals(0L, controller.durationMs())
    }

    @Test
    fun `config changes take effect on the next evaluation`() {
        host.trackDurationMs = 200_000L
        val controller = controllerWith(enabled = true)

        controller.updateConfig(enabled = false, durationSeconds = 5f, gapless = true)

        assertEquals(Decision.Skip(Reason.DISABLED), controller.evaluate())
    }

    // --- test double --------------------------------------------------------

    private class FakeCrossfadeHost : CrossfadeHost {
        var trackDurationMs: Long = 200_000L
        var positionMs: Long = 0L
        var hasNext: Boolean = true
        var repeatOne: Boolean = false
        // Not named `isPlaying`: a `var isPlaying` would generate an `isPlaying()` getter that
        // clashes with the interface method below on the JVM.
        var playing: Boolean = false
        var mediaId: String? = "track-a"
        var currentAlbum: String? = null
        var nextAlbum: String? = null
        var pausesAtEnd: Boolean = false

        var trigger: (() -> Unit)? = null
        var scheduledPositionMs: Long? = null
        var armCount = 0
        var cancelCount = 0
        var crossfadesStarted = 0

        override fun currentTrackDurationMs(): Long = trackDurationMs

        override fun currentPositionMs(): Long = positionMs

        override fun hasNextMediaItem(): Boolean = hasNext

        override fun isRepeatOne(): Boolean = repeatOne

        override fun isPlaying(): Boolean = playing

        override fun currentMediaId(): String? = mediaId

        override fun currentAlbumTitle(): CharSequence? = currentAlbum

        override fun nextAlbumTitle(): CharSequence? = nextAlbum

        override fun pausesAtSongEnd(): Boolean = pausesAtEnd

        override fun scheduleTrigger(positionMs: Long, action: () -> Unit) {
            armCount++
            scheduledPositionMs = positionMs
            trigger = action
        }

        override fun cancelTrigger() {
            cancelCount++
            trigger = null
        }

        override fun beginCrossfade() {
            crossfadesStarted++
        }

        /** Simulates media3 delivering the pending message. */
        fun fireTrigger() {
            trigger?.invoke()
        }
    }
}