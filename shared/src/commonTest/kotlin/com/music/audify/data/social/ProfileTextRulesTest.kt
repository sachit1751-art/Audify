package com.music.audify.data.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileTextRulesTest {

    @Test
    fun displayNameIsTrimmed() {
        assertEquals("Zaid Shaker", ProfileTextRules.cleanDisplayName("  Zaid Shaker  "))
    }

    @Test
    fun displayNameMayUseAnyScript() {
        assertEquals("\u0632\u064A\u062F", ProfileTextRules.cleanDisplayName("\u0632\u064A\u062F"))
    }

    @Test
    fun displayNameRejectsEmptyAndOverlongText() {
        assertNull(ProfileTextRules.cleanDisplayName(""))
        assertNull(ProfileTextRules.cleanDisplayName("   "))
        assertEquals(
            "a".repeat(ProfileTextRules.DISPLAY_NAME_MAX),
            ProfileTextRules.cleanDisplayName("a".repeat(ProfileTextRules.DISPLAY_NAME_MAX)),
        )
        assertNull(ProfileTextRules.cleanDisplayName("a".repeat(ProfileTextRules.DISPLAY_NAME_MAX + 1)))
    }

    @Test
    fun displayNameRejectsControlAndDirectionTricks() {
        assertNull(ProfileTextRules.cleanDisplayName("Zaid\nShaker"))
        assertNull(ProfileTextRules.cleanDisplayName("Zaid\u0000"))
        assertNull(ProfileTextRules.cleanDisplayName("Za\u200Bid"))
        assertNull(ProfileTextRules.cleanDisplayName("\u202Edaiz"))
        assertNull(ProfileTextRules.cleanDisplayName("Zaid\uFEFF"))
    }

    @Test
    fun bioMayBeEmptyAndIsTrimmed() {
        assertEquals("", ProfileTextRules.cleanBio("   "))
        assertEquals("hello", ProfileTextRules.cleanBio("  hello "))
    }

    @Test
    fun bioAllowsLineBreaksButNotOtherControlCharacters() {
        assertEquals("one\ntwo", ProfileTextRules.cleanBio("one\ntwo"))
        assertNull(ProfileTextRules.cleanBio("one\u0007two"))
    }

    @Test
    fun bioEnforcesItsLimit() {
        assertEquals(
            "a".repeat(ProfileTextRules.BIO_MAX),
            ProfileTextRules.cleanBio("a".repeat(ProfileTextRules.BIO_MAX)),
        )
        assertNull(ProfileTextRules.cleanBio("a".repeat(ProfileTextRules.BIO_MAX + 1)))
    }

    @Test
    fun bioRejectsDirectionTricks() {
        assertNull(ProfileTextRules.cleanBio("hi \u202E there"))
    }
}
