package com.music.audify.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WearQueueIdentityTest {

    @Test
    fun `watch queue keys use occurrence identity rather than mutable index`() {
        val firstOccurrence = WearProtocol.queueEntryKey("entry-a", "same-track", 2)
        val secondOccurrence = WearProtocol.queueEntryKey("entry-b", "same-track", 3)

        assertEquals("entry-a", firstOccurrence)
        assertEquals("entry-b", secondOccurrence)
        assertNotEquals(firstOccurrence, secondOccurrence)
    }

    @Test
    fun `older phone payloads get a deterministic per-index fallback`() {
        assertEquals("legacy:same-track:2", WearProtocol.queueEntryKey(null, "same-track", 2))
        assertNotEquals(
            WearProtocol.queueEntryKey(null, "same-track", 2),
            WearProtocol.queueEntryKey(null, "same-track", 3),
        )
    }
}
