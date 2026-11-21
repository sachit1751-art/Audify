package com.music.audify.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearBridgeQueueIdentityTest {
    @Test
    fun `unique occurrence id resolves to its current timeline index`() {
        assertEquals(1, uniqueQueueEntryIndex("entry-b", listOf("entry-a", "entry-b", "entry-c")))
    }

    @Test
    fun `duplicate occurrence ids are rejected instead of jumping to the first match`() {
        assertNull(uniqueQueueEntryIndex("duplicate", listOf("duplicate", "other", "duplicate")))
    }

    @Test
    fun `missing or blank occurrence ids do not resolve`() {
        assertNull(uniqueQueueEntryIndex("missing", listOf("a", "b")))
        assertNull(uniqueQueueEntryIndex(" ", listOf("a", "b")))
    }
}
