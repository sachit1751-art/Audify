package com.music.audify.data.discord

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscordPresenceFieldsTest {
    @Test
    fun leadWithDetailsPublishesTheTrackLineAsDetails() {
        assertEquals(
            DiscordPresenceFields(details = "Track, Artist", state = null),
            discordPresenceFields("Track, Artist", useDetails = true),
        )
    }

    @Test
    fun alternateDisplayPublishesTheSameTrackLineAsState() {
        assertEquals(
            DiscordPresenceFields(details = null, state = "Track, Artist"),
            discordPresenceFields("Track, Artist", useDetails = false),
        )
    }

    @Test
    fun blankPresenceTextIsNeverPublished() {
        assertEquals(DiscordPresenceFields(null, null), discordPresenceFields(" ", useDetails = true))
        assertEquals(DiscordPresenceFields(null, null), discordPresenceFields(" ", useDetails = false))
    }
}
