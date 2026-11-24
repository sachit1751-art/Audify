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

    // The unit is Unicode code points, not UTF-16 units (String.length) and not grapheme clusters.

    private val grinning = "\uD83D\uDE00" // U+1F600: one code point, two UTF-16 units

    @Test
    fun codePointCountCountsCodePointsNotUtf16Units() {
        assertEquals(0, ProfileTextRules.codePointCount(""))
        assertEquals(3, ProfileTextRules.codePointCount("abc"))
        assertEquals(1, ProfileTextRules.codePointCount(grinning))
        assertEquals(2, grinning.length)
        assertEquals(3, ProfileTextRules.codePointCount("a${grinning}b"))
    }

    @Test
    fun combiningSequencesCountEveryCodePoint() {
        // "e" + U+0301 looks like one letter but is two code points; graphemes are not the unit.
        assertEquals(2, ProfileTextRules.codePointCount("e\u0301"))
    }

    @Test
    fun displayNameLimitCountsEmojiAsOneEach() {
        val atLimit = grinning.repeat(ProfileTextRules.DISPLAY_NAME_MAX)
        assertEquals(atLimit, ProfileTextRules.cleanDisplayName(atLimit))
        assertNull(ProfileTextRules.cleanDisplayName(grinning.repeat(ProfileTextRules.DISPLAY_NAME_MAX + 1)))
    }

    @Test
    fun displayNameLimitCountsCombiningMarksAsCodePoints() {
        val atLimit = "e\u0301".repeat(ProfileTextRules.DISPLAY_NAME_MAX / 2)
        assertEquals(atLimit, ProfileTextRules.cleanDisplayName(atLimit))
        assertNull(ProfileTextRules.cleanDisplayName("e\u0301".repeat(ProfileTextRules.DISPLAY_NAME_MAX / 2 + 1)))
    }

    @Test
    fun bioLimitCountsEmojiAsOneEach() {
        val atLimit = grinning.repeat(ProfileTextRules.BIO_MAX)
        assertEquals(atLimit, ProfileTextRules.cleanBio(atLimit))
        assertNull(ProfileTextRules.cleanBio(grinning.repeat(ProfileTextRules.BIO_MAX + 1)))
    }

    @Test
    fun rejectsUnpairedSurrogates() {
        listOf("\uD83D", "\uDE00", "ab\uDE00\uD83D", "x\uD83Dy").forEach {
            assertNull(ProfileTextRules.cleanDisplayName(it), it)
            assertNull(ProfileTextRules.cleanBio(it), it)
        }
    }

    @Test
    fun acceptsWellFormedSurrogatePairsNextToText() {
        assertEquals("hi ${grinning}!", ProfileTextRules.cleanDisplayName("hi ${grinning}!"))
    }

    @Test
    fun rejectsC1ControlCharacters() {
        assertNull(ProfileTextRules.cleanDisplayName("a\u0085b"))
        assertNull(ProfileTextRules.cleanDisplayName("a\u009Fb"))
        assertNull(ProfileTextRules.cleanBio("a\u0080b"))
    }
}
