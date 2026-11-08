// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.auth.GoogleAccountSession
import com.music.audify.auth.YouTubeProfile
import com.music.audify.auth.adjacentProfile
import com.music.audify.auth.flattenedProfiles
import com.music.audify.auth.sessionsFromJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSessionsTest {
    @Test
    fun malformedAccountOrProfileDoesNotDiscardOtherSavedSessions() {
        val raw = """[
            {"id":"account-a","cookie":"SAPISID=a","activeProfileId":"missing-profile","profiles":[{"id":"a-personal","name":"Personal"},"bad-profile"]},
            "bad-account",
            {"id":"account-b","cookie":"SAPISID=b","profiles":[{"id":"b-brand","name":"Brand","brand":true}]}
        ]"""
        val sessions = sessionsFromJson(raw)

        assertEquals(listOf("account-a", "account-b"), sessions.map { it.accountId })
        assertEquals(listOf("a-personal"), sessions.first().profiles.map { it.profileId })
        assertEquals("a-personal", sessions.first().activeProfileId)
        assertEquals(listOf("b-brand"), sessions.last().profiles.map { it.profileId })
    }

    private val a = GoogleAccountSession("a", "cookie-a", profiles = listOf(
        YouTubeProfile("a-personal", "Personal"), YouTubeProfile("a-brand", "Brand"),
    ))
    private val b = GoogleAccountSession("b", "cookie-b", profiles = listOf(
        YouTubeProfile("b-personal", "Personal"), YouTubeProfile("b-brand", "Brand"),
    ))

    @Test fun `profiles retain account then profile ordering`() {
        assertEquals(listOf("a" to "a-personal", "a" to "a-brand", "b" to "b-personal", "b" to "b-brand"),
            flattenedProfiles(listOf(a, b)))
    }

    @Test fun `next previous and boundaries do not wrap`() {
        assertEquals("a" to "a-brand", adjacentProfile(listOf(a, b), "a", "a-personal", true))
        assertEquals("b" to "b-personal", adjacentProfile(listOf(a, b), "b", "b-brand", false))
        assertNull(adjacentProfile(listOf(a, b), "a", "a-personal", false))
        assertNull(adjacentProfile(listOf(a, b), "b", "b-brand", true))
    }

    @Test fun `one profile has no adjacent profile`() {
        val only = GoogleAccountSession("a", "cookie", profiles = listOf(YouTubeProfile("p", "Personal")))
        assertNull(adjacentProfile(listOf(only), "a", "p", true))
        assertNull(adjacentProfile(listOf(only), "a", "p", false))
    }
}
