package com.sachit.music.listentogether

import com.sachit.music.listentogether.proto.Listentogether
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure decision logic behind room synchronisation.
 *
 * The interesting parts of [ListenTogetherManager] — queue reconciliation, the seek tolerance,
 * and the codec's compression contract — are pure functions, so they can be exercised without a
 * device, a WebSocket, or a signed-in account. What remains untested is the glue that needs a
 * real player, which is noted as such rather than pretended away.
 */
class ListenTogetherSyncTest {

    // --- canonical queue ----------------------------------------------------

    @Test
    fun `canonical queue puts the current track first`() {
        val current = track("a", "Alpha")
        val queue = canonicalPlaybackQueue(current, listOf(track("b", "Bravo"), track("c", "Charlie")))

        assertEquals(listOf("a", "b", "c"), queue.map { it.id })
    }

    @Test
    fun `canonical queue drops a repeated current track from the upcoming list`() {
        // The host sends the current track as both `currentTrack` and the head of the upcoming
        // queue. Emitting it twice would make a guest skip a row on reconcile.
        val current = track("a", "Alpha")
        val queue = canonicalPlaybackQueue(current, listOf(track("a", "Alpha"), track("b", "Bravo")))

        assertEquals(listOf("a", "b"), queue.map { it.id })
    }

    @Test
    fun `canonical queue collapses duplicates further down the upcoming list`() {
        val queue =
            canonicalPlaybackQueue(
                currentTrack = track("a", "Alpha"),
                upcomingQueue = listOf(track("b", "Bravo"), track("c", "Charlie"), track("b", "Bravo")),
            )

        assertEquals(listOf("a", "b", "c"), queue.map { it.id })
    }

    @Test
    fun `canonical queue keeps the first occurrence of a duplicate`() {
        val queue =
            canonicalPlaybackQueue(
                currentTrack = track("a", "Alpha"),
                upcomingQueue = listOf(track("b", "Bravo first"), track("b", "Bravo second")),
            )

        assertEquals("Bravo first", queue[1].title)
    }

    @Test
    fun `canonical queue with nothing upcoming is just the current track`() {
        val queue = canonicalPlaybackQueue(track("a", "Alpha"), emptyList())

        assertEquals(1, queue.size)
        assertEquals("a", queue.first().id)
    }

    // --- upcoming window ----------------------------------------------------

    @Test
    fun `upcoming items start after the current index`() {
        val queue = listOf("a", "b", "c", "d")

        assertEquals(listOf("c", "d"), upcomingQueueItems(queue, currentIndex = 1))
    }

    @Test
    fun `upcoming items is empty when the current track is last`() {
        assertEquals(emptyList<String>(), upcomingQueueItems(listOf("a", "b"), currentIndex = 1))
    }

    @Test
    fun `upcoming items is empty for an out of bounds index`() {
        // A guest that has not yet adopted the host's queue can report index -1 or an index
        // past the end. Both must produce "no upcoming", never a crash or a partial list.
        val queue = listOf("a", "b", "c")

        assertEquals(emptyList<String>(), upcomingQueueItems(queue, currentIndex = -1))
        assertEquals(emptyList<String>(), upcomingQueueItems(queue, currentIndex = 99))
    }

    @Test
    fun `upcoming items is empty for an empty queue`() {
        assertEquals(emptyList<String>(), upcomingQueueItems(emptyList<String>(), currentIndex = 0))
    }

    // --- seek tolerance -----------------------------------------------------

    @Test
    fun `does not seek when playback is not ready`() {
        assertFalse(shouldSeekDuringActivePlayback(positionDifferenceMs = 30_000L, playbackReady = false))
    }

    @Test
    fun `does not seek for drift within tolerance`() {
        assertFalse(shouldSeekDuringActivePlayback(positionDifferenceMs = 0L, playbackReady = true))
        assertFalse(shouldSeekDuringActivePlayback(positionDifferenceMs = 1_999L, playbackReady = true))
    }

    @Test
    fun `seeks once drift passes tolerance`() {
        assertTrue(shouldSeekDuringActivePlayback(positionDifferenceMs = 2_001L, playbackReady = true))
        assertTrue(shouldSeekDuringActivePlayback(positionDifferenceMs = 30_000L, playbackReady = true))
    }

    @Test
    fun `seek tolerance boundary is exclusive`() {
        // Exactly at the tolerance must NOT seek: yanking a guest's playhead at 2.000s of drift
        // is audible and annoying, so the comparison is strictly greater-than.
        assertFalse(shouldSeekDuringActivePlayback(positionDifferenceMs = 2_000L, playbackReady = true))
    }

    // --- codec compression contract ----------------------------------------

    @Test
    fun `large payloads round trip when compression is enabled`() {
        val codec = MessageCodec(compressionEnabled = true)
        // A long queue forces the payload past the 100-byte compression threshold.
        val action =
            PlaybackActionPayload(
                action = PlaybackActions.SEEK,
                trackId = "t",
                position = 5_000L,
                serverTime = 1_000L,
                revision = 1L,
                capturedAtServerTime = 900L,
                queue = List(40) { track("queue-track-$it", "Queued track $it") },
            )

        val envelope = Listentogether.Envelope.parseFrom(codec.encode(MessageTypes.SYNC_PLAYBACK, action))
        val (_, payload) = codec.decode(codec.encode(MessageTypes.SYNC_PLAYBACK, action))
        val decoded = codec.decodePayload(MessageTypes.SYNC_PLAYBACK, payload) as PlaybackActionPayload

        assertTrue("payload this size should have been compressed", envelope.compressed)
        assertEquals("t", decoded.trackId)
        assertEquals(5_000L, decoded.position)
        assertEquals(action.queue.orEmpty().map { it.id }, decoded.queueList.orEmpty().map { it.id })
    }

    @Test
    fun `payloads are sent uncompressed when compression is disabled`() {
        val codec = MessageCodec(compressionEnabled = false)
        val action = PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = "t", position = 0L, queue = List(40) { track("queue-track-$it", "Queued track $it") })

        val envelope = Listentogether.Envelope.parseFrom(codec.encode(MessageTypes.SYNC_PLAYBACK, action))

        assertFalse(envelope.compressed)
    }

    @Test
    fun `small payloads are not compressed`() {
        val codec = MessageCodec(compressionEnabled = true)
        val action = PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = "t", position = 0L)

        val envelope = Listentogether.Envelope.parseFrom(codec.encode(MessageTypes.SYNC_PLAYBACK, action))

        assertFalse("under the threshold, compression is not worth it", envelope.compressed)
    }

    @Test
    fun `a payload round trips unchanged with compression off`() {
        val codec = MessageCodec(compressionEnabled = false)
        val action = PlaybackActionPayload(action = PlaybackActions.PAUSE, trackId = "abc", position = 42L, serverTime = 7L, revision = 3L, capturedAtServerTime = 6L)

        val (type, payload) = codec.decode(codec.encode(MessageTypes.SYNC_PLAYBACK, action))
        val decoded = codec.decodePayload(MessageTypes.SYNC_PLAYBACK, payload) as PlaybackActionPayload

        assertEquals(type, MessageTypes.SYNC_PLAYBACK)
        assertEquals(action.action, decoded.action)
        assertEquals(action.position, decoded.position)
    }

    @Test
    fun `a null payload decodes to null rather than throwing`() {
        val codec = MessageCodec(compressionEnabled = true)

        val (type, payload) = codec.decode(codec.encode(MessageTypes.SYNC_PLAYBACK, null))

        assertEquals(MessageTypes.SYNC_PLAYBACK, type)
        assertArrayEquals(ByteArray(0), payload)
        assertNull(codec.decodePayload(MessageTypes.SYNC_PLAYBACK, payload))
    }

    @Test
    fun `an unknown message type decodes to null instead of crashing`() {
        // A newer client may send a message type this build does not know. The room must keep
        // working rather than tearing down the socket on an unrecognised frame.
        val codec = MessageCodec(compressionEnabled = true)
        val payload = PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = "t", position = 0L, serverTime = 0L, revision = 0L, capturedAtServerTime = 0L)

        assertNull(codec.decodePayload("SOME_FUTURE_MESSAGE", payload.toByteArray()))
    }

    @Test
    fun `a populated payload decodes to the right concrete type`() {
        val codec = MessageCodec(compressionEnabled = true)
        val action = PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = "t", position = 1L, revision = 2L)

        val decoded = codec.decodePayload(MessageTypes.SYNC_PLAYBACK, action.toByteArray())

        assertNotNull(decoded)
        assertTrue(decoded is PlaybackActionPayload)
    }

    @Test
    fun `an empty payload is treated as absent for every message type`() {
        // decodePayload short-circuits on empty bytes before dispatch, so an empty frame is
        // never mistaken for a default-valued message. Applies uniformly to all types.
        val codec = MessageCodec(compressionEnabled = false)

        assertNull(codec.decodePayload(MessageTypes.SYNC_PLAYBACK, ByteArray(0)))
        assertNull(codec.decodePayload(MessageTypes.ROOM_CREATED, ByteArray(0)))
        assertNull(codec.decodePayload(MessageTypes.ERROR, ByteArray(0)))
    }

    // --- helpers ------------------------------------------------------------

    private fun track(
        id: String,
        title: String,
    ) = TrackInfo(id = id, title = title, artist = "Artist", duration = 1_000L)
}