package com.music.audify.data.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsernameRulesTest {

    private fun problemOf(raw: String): UsernameProblem? =
        (UsernameRules.validate(raw) as? UsernameCheck.Invalid)?.problem

    @Test
    fun normalizeTrimsAndLowercases() {
        assertEquals("zaid_99", UsernameRules.normalize("  Zaid_99 "))
    }

    @Test
    fun acceptsOrdinaryNamesAndReturnsTheNormalizedForm() {
        assertEquals(UsernameCheck.Valid("zaid_99"), UsernameRules.validate("Zaid_99"))
        assertEquals(UsernameCheck.Valid("abc"), UsernameRules.validate("abc"))
    }

    @Test
    fun acceptsTheLengthLimitsExactly() {
        assertEquals(UsernameCheck.Valid("a".repeat(UsernameRules.MAX_LENGTH)), UsernameRules.validate("a".repeat(UsernameRules.MAX_LENGTH)))
        assertEquals(UsernameCheck.Valid("a".repeat(UsernameRules.MIN_LENGTH)), UsernameRules.validate("a".repeat(UsernameRules.MIN_LENGTH)))
    }

    @Test
    fun rejectsEmptyBlankAndShortNames() {
        assertEquals(UsernameProblem.TOO_SHORT, problemOf(""))
        assertEquals(UsernameProblem.TOO_SHORT, problemOf("     "))
        assertEquals(UsernameProblem.TOO_SHORT, problemOf("  ab  "))
    }

    @Test
    fun rejectsNamesOverTheLimit() {
        assertEquals(UsernameProblem.TOO_LONG, problemOf("a".repeat(UsernameRules.MAX_LENGTH + 1)))
    }

    @Test
    fun rejectsSpacesPunctuationAndEmoji() {
        listOf("a b c", "zaid.diab", "zaid-diab", "@zaid", "zaid!", "zaid\uD83C\uDFB5").forEach {
            assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf(it), it)
        }
    }

    @Test
    fun rejectsLookalikeAndNonAsciiLetters() {
        // Cyrillic "а" (U+0430) looks exactly like Latin "a".
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\u0430dmin_x"))
        // Full-width Latin letters.
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\uFF21\uFF22\uFF23"))
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("za\u00EFd"))
    }

    @Test
    fun mustStartWithALetter() {
        assertEquals(UsernameProblem.MUST_START_WITH_LETTER, problemOf("1abc"))
        assertEquals(UsernameProblem.MUST_START_WITH_LETTER, problemOf("_abc"))
        assertEquals(UsernameProblem.MUST_START_WITH_LETTER, problemOf("___"))
    }

    @Test
    fun rejectsDoubleAndTrailingUnderscores() {
        assertEquals(UsernameProblem.CONSECUTIVE_UNDERSCORES, problemOf("a__b"))
        assertEquals(UsernameProblem.ENDS_WITH_UNDERSCORE, problemOf("abc_"))
    }

    @Test
    fun rejectsReservedNamesInAnyCase() {
        assertEquals(UsernameProblem.RESERVED, problemOf("admin"))
        assertEquals(UsernameProblem.RESERVED, problemOf("AUDIFY"))
        assertEquals(UsernameProblem.RESERVED, problemOf(" Support "))
    }

    @Test
    fun everyReservedNameIsReachableByTheRulesBeforeIt() {
        // A reserved name that an earlier rule already rejects (too short, say) is dead weight.
        UsernameRules.RESERVED.forEach {
            assertEquals(UsernameProblem.RESERVED, problemOf(it), it)
        }
    }

    @Test
    fun searchPrefixIgnoresLeadingAtSignAndCase() {
        assertEquals("zaid", UsernameRules.searchPrefix("@Zaid"))
        assertEquals("za", UsernameRules.searchPrefix("  ZA "))
    }

    @Test
    fun searchPrefixAllowsShortPrefixes() {
        assertEquals("z", UsernameRules.searchPrefix("z"))
    }

    @Test
    fun searchPrefixRejectsNothingToSearchOrUnsearchableInput() {
        assertNull(UsernameRules.searchPrefix(""))
        assertNull(UsernameRules.searchPrefix("   "))
        assertNull(UsernameRules.searchPrefix("@"))
        assertNull(UsernameRules.searchPrefix("a b"))
        assertNull(UsernameRules.searchPrefix("a".repeat(UsernameRules.MAX_LENGTH + 1)))
    }
}
