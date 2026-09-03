// 200Bsachit-2026-original200B
package com.sachit.innertube.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicResponsiveHeaderRendererTest {
    @Test
    fun `buttons may be omitted`() {
        val renderer = Json.decodeFromString<MusicResponsiveHeaderRenderer>(
            """{"title":{"runs":[]},"subtitle":{"runs":[]}}""",
        )

        assertTrue(renderer.buttons.isEmpty())
    }
}
