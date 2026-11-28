package com.music.audify.data.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsernameRulesTest {

    private fun problemOf(raw: String): UsernameProblem? =
        (UsernameRules.validate(raw) as? UsernameCheck.Invalid)?.problem

    @Test
    fun keepsTheCapitalizationForDisplayAndLowercasesTheKey() {
        assertEquals(UsernameCheck.Valid(display = "Zaid_99", key = "zaid_99"), UsernameRules.validate("Zaid_99"))
    }

    @Test
    fun trimsSurroundingWhiteSpace() {
        assertEquals(UsernameCheck.Valid("Zaid", "zaid"), UsernameRules.validate("  Zaid  "))
        assertEquals(UsernameCheck.Valid("zaid", "zaid"), UsernameRules.validate("\u00A0zaid\u00A0"))
    }

    @Test
    fun acceptsTheLengthLimitsExactly() {
        assertTrue(UsernameRules.validate("a".repeat(UsernameRules.MIN_LENGTH)) is UsernameCheck.Valid)
        assertTrue(UsernameRules.validate("a".repeat(UsernameRules.MAX_LENGTH)) is UsernameCheck.Valid)
    }

    @Test
    fun rejectsEmptyBlankShortAndLongNames() {
        assertEquals(UsernameProblem.TOO_SHORT, problemOf(""))
        assertEquals(UsernameProblem.TOO_SHORT, problemOf("     "))
        assertEquals(UsernameProblem.TOO_SHORT, problemOf("  ab  "))
        assertEquals(UsernameProblem.TOO_LONG, problemOf("a".repeat(UsernameRules.MAX_LENGTH + 1)))
    }

    @Test
    fun lettersDigitsAndUnderscoresAreAllowedAnywhere() {
        // The maintainer's rule is "letters, numbers and underscores only": no further restrictions.
        listOf("_ab_", "1abc", "a__b", "___", "A1_b2").forEach {
            assertTrue(UsernameRules.validate(it) is UsernameCheck.Valid, it)
        }
    }

    @Test
    fun rejectsSpacesPunctuationAndEmoji() {
        listOf("a b c", "zaid.diab", "zaid-diab", "@zaid", "zaid!", "zaid\uD83C\uDFB5").forEach {
            assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf(it), it)
        }
    }

    @Test
    fun rejectsLookalikeAndNonAsciiLetters() {
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\u0430dmin_x")) // Cyrillic a
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\uFF21\uFF22\uFF23")) // full-width
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("za\u00EFd"))
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\uFB01sh")) // fi ligature
    }

    @Test
    fun charactersAreCheckedBeforeCaseFolding() {
        // U+212A KELVIN SIGN lowercases to ASCII "k". Folding first would let it through.
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\u212Aelvin"))
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\u0130stanbul"))
    }

    @Test
    fun charactersAreCheckedBeforeLengthSoTheAnswerNeverDependsOnTheLengthUnit() {
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("a".repeat(UsernameRules.MAX_LENGTH) + "!"))
        assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf("\uD83D\uDE00".repeat(11)))
    }

    @Test
    fun onlyTheExplicitWhiteSpaceSetIsTrimmed() {
        // These are rejected, never silently removed, because platforms disagree about them.
        listOf("zaid\uFEFF", "\u001Fzaid", "\u0085zaid").forEach {
            assertEquals(UsernameProblem.INVALID_CHARACTERS, problemOf(it), it)
        }
    }

    @Test
    fun rejectsReservedNamesInAnyCase() {
        assertEquals(UsernameProblem.RESERVED, problemOf("admin"))
        assertEquals(UsernameProblem.RESERVED, problemOf("AuDiFy"))
        assertEquals(UsernameProblem.RESERVED, problemOf(" Support "))
        assertTrue(UsernameRules.validate("admins") is UsernameCheck.Valid)
    }

    @Test
    fun everyReservedNameIsReachableByTheRulesBeforeIt() {
        // A reserved name that an earlier rule already rejects (too short, say) is dead weight.
        UsernameRules.RESERVED.forEach {
            assertEquals(UsernameProblem.RESERVED, problemOf(it), it)
        }
    }

    @Test
    fun normalizeIsTheCaseInsensitiveKey() {
        assertEquals("zaid_99", UsernameRules.normalize("  Zaid_99 "))
    }

    @Test
    fun searchPrefixIgnoresLeadingAtSignAndCase() {
        assertEquals("zaid", UsernameRules.searchPrefix("@Zaid"))
        assertEquals("za", UsernameRules.searchPrefix("  ZA "))
        assertEquals("za", UsernameRules.searchPrefix("za"))
    }

    @Test
    fun searchPrefixRejectsNothingToSearchOrUnsearchableInput() {
        assertNull(UsernameRules.searchPrefix(""))
        assertNull(UsernameRules.searchPrefix("   "))
        assertNull(UsernameRules.searchPrefix("@"))
        assertNull(UsernameRules.searchPrefix("z")) // shorter than SEARCH_MIN_PREFIX_LENGTH
        assertNull(UsernameRules.searchPrefix("@z"))
        assertNull(UsernameRules.searchPrefix("a b"))
        assertNull(UsernameRules.searchPrefix("a".repeat(UsernameRules.MAX_LENGTH + 1)))
        assertNull(UsernameRules.searchPrefix("\u212A")) // must not fold to "k"
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aNeverChangedUsernameCanBeChangedAtOnce() {
        assertTrue(UsernameRules.canChange(lastChangedAtMillis = null, nowMillis = 0))
        assertNull(UsernameRules.nextChangeAllowedAt(null))
    }

    @Test
    fun theCooldownIsThirtyDaysAndInclusiveAtTheBoundary() {
        assertEquals(30L * day, UsernameRules.CHANGE_COOLDOWN_MILLIS)
        assertFalse(UsernameRules.canChange(lastChangedAtMillis = 0, nowMillis = 30 * day - 1))
        assertTrue(UsernameRules.canChange(lastChangedAtMillis = 0, nowMillis = 30 * day))
        assertEquals(1_000L + 30 * day, UsernameRules.nextChangeAllowedAt(1_000))
    }

    @Test
    fun aClockThatMovedBackwardsNeverUnlocksAChange() {
        assertFalse(UsernameRules.canChange(lastChangedAtMillis = 1_000, nowMillis = 500))
    }

    @Test
    fun theAudifyNamespaceIsReserved() {
        listOf("audify", "AUDIFY", "audify_support", "AudifyTeam", "audify2").forEach {
            assertEquals(UsernameProblem.RESERVED, problemOf(it), it)
        }
        // Only a prefix is reserved.
        assertTrue(UsernameRules.validate("myaudify") is UsernameCheck.Valid)
        assertTrue(UsernameRules.validate("au_dify") is UsernameCheck.Valid)
    }

    // --- changing a username -------------------------------------------------

    private val cooldown = UsernameRules.CHANGE_COOLDOWN_MILLIS

    @Test
    fun anUnchangedRequestChangesNothing() {
        assertEquals(UsernameChange.Unchanged, UsernameRules.planChange("Zaid", "Zaid", null, 0))
        assertEquals(UsernameChange.Unchanged, UsernameRules.planChange("Zaid", "  Zaid ", null, 0))
    }

    @Test
    fun aCapitalizationOnlyChangeIsAlwaysAllowedAndDoesNotCountAsARename() {
        // Even one second after a real rename.
        assertEquals(
            UsernameChange.CapitalizationOnly("zAID"),
            UsernameRules.planChange("Zaid", "zAID", lastRenamedAtMillis = 0, nowMillis = 1_000),
        )
    }

    @Test
    fun theFirstRenameIsAllowedBecauseRegistrationDoesNotStartTheCooldown() {
        assertEquals(
            UsernameChange.Rename("Zaid2", "zaid2"),
            UsernameRules.planChange("Zaid", "Zaid2", lastRenamedAtMillis = null, nowMillis = 0),
        )
    }

    @Test
    fun aRenameDuringTheCooldownIsRefusedWithTheTimeItEnds() {
        assertEquals(
            UsernameChange.CoolingDown(cooldown),
            UsernameRules.planChange("Zaid", "Zaid2", lastRenamedAtMillis = 0, nowMillis = cooldown - 1),
        )
    }

    @Test
    fun aRenameIsAllowedExactlyWhenTheCooldownEnds() {
        assertEquals(
            UsernameChange.Rename("Zaid2", "zaid2"),
            UsernameRules.planChange("Zaid", "Zaid2", lastRenamedAtMillis = 0, nowMillis = cooldown),
        )
    }

    @Test
    fun anInvalidNameIsReportedAsInvalidEvenDuringTheCooldown() {
        assertEquals(
            UsernameChange.Invalid(UsernameProblem.INVALID_CHARACTERS),
            UsernameRules.planChange("Zaid", "a b", lastRenamedAtMillis = 0, nowMillis = 1),
        )
        assertEquals(
            UsernameChange.Invalid(UsernameProblem.RESERVED),
            UsernameRules.planChange("Zaid", "audify_team", lastRenamedAtMillis = null, nowMillis = 1),
        )
    }

    // --- released names ------------------------------------------------------

    @Test
    fun aReleasedNameIsHeldForThirtyDays() {
        assertEquals(30, UsernameRules.RELEASED_NAME_HOLD_DAYS)
        assertTrue(UsernameRules.isHeld(releasedAtMillis = 0, nowMillis = 0))
        assertTrue(UsernameRules.isHeld(releasedAtMillis = 0, nowMillis = 30 * day - 1))
        assertFalse(UsernameRules.isHeld(releasedAtMillis = 0, nowMillis = 30 * day))
        assertEquals(1_000L + 30 * day, UsernameRules.holdEndsAt(1_000))
    }

    @Test
    fun aClockThatMovedBackwardsNeverEndsAHold() {
        assertTrue(UsernameRules.isHeld(releasedAtMillis = 1_000, nowMillis = 500))
    }
}
