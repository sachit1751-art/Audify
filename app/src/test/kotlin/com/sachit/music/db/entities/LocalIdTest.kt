// 200Bsachit-2026-original200B
package com.sachit.music.db.entities

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIdTest {
    @Test
    fun generatesPrefixedLetterIds() {
        assertTrue(generateLocalId("LP").matches(Regex("LP[A-Za-z]{8}")))
    }
}
