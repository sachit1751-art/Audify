package com.music.audify.data.social

/**
 * The rules for an AUDIFY username (Plan 091, section 5.1), as decided by the
 * maintainer:
 *
 * - 3 to 20 characters: letters, digits and underscores only.
 * - Matching is case-insensitive; the person's own capitalization is kept for
 *   display. [Valid.display][UsernameCheck.Valid.display] is what to show,
 *   [Valid.key][UsernameCheck.Valid.key] is what to store, compare and keep unique.
 * - A username can be changed once every [CHANGE_COOLDOWN_DAYS] days. Choosing
 *   the first username at registration does not start that cooldown, and a
 *   change of capitalization only (for example "Zaid" to "zAID") is not a
 *   change: it is allowed at any time and does not start the cooldown.
 * - A username that someone gives up is held for [RELEASED_NAME_HOLD_DAYS] days
 *   before anyone else can take it, so it cannot be used to impersonate its
 *   former owner or to swap names between accounts around the cooldown.
 * - System names, and every name that starts with "audify", are reserved for
 *   official use.
 *
 * Usernames are ASCII on purpose. Letters from other scripts can look
 * identical to Latin ones, which makes impersonation trivial and makes
 * "unique" meaningless. A display name can hold any text; the username cannot.
 *
 * **Order of checks.** Characters are checked on the text exactly as typed,
 * before any case folding and before length. Case folding first would let some
 * non-ASCII characters turn into ASCII (the Kelvin sign U+212A lowercases to
 * "k"). Checking characters before length means the answer never depends on how
 * a platform counts length, because only ASCII ever gets as far as being counted.
 *
 * The server must apply the same rules and must enforce uniqueness on [Valid.key]
 * in the database. This client-side copy only gives fast feedback. The shared
 * test vectors (`shared/testvectors/social-v1.json`) are the contract between
 * the two.
 */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20
    const val CHANGE_COOLDOWN_DAYS = 30
    const val CHANGE_COOLDOWN_MILLIS = CHANGE_COOLDOWN_DAYS * 24L * 60L * 60L * 1000L
    const val RELEASED_NAME_HOLD_DAYS = 30
    const val RELEASED_NAME_HOLD_MILLIS = RELEASED_NAME_HOLD_DAYS * 24L * 60L * 60L * 1000L

    /**
     * The shortest search prefix the app sends and the server accepts. One
     * letter would match a large share of all users for no benefit, and every
     * search costs a request from a limited daily budget.
     */
    const val SEARCH_MIN_PREFIX_LENGTH = 2

    /** Names nobody may register, so no one can pose as the app or its staff. Compared on the key. */
    val RESERVED: Set<String> = setOf(
        "admin", "administrator", "api", "help", "moderator",
        "mod", "null", "official", "root", "security", "settings", "staff",
        "support", "system", "undefined",
    )

    /** Names that start with any of these are reserved too. Compared on the key. */
    val RESERVED_PREFIXES: List<String> = listOf("audify")

    /** The case-insensitive form used for matching and uniqueness. Does not validate. */
    fun normalize(raw: String): String = ProfileTextRules.trim(raw).lowercase()

    fun validate(raw: String): UsernameCheck {
        val typed = ProfileTextRules.trim(raw)
        val problem = when {
            !typed.all { it.isAllowed() } -> UsernameProblem.INVALID_CHARACTERS
            typed.length < MIN_LENGTH -> UsernameProblem.TOO_SHORT
            typed.length > MAX_LENGTH -> UsernameProblem.TOO_LONG
            typed.lowercase().isReserved() -> UsernameProblem.RESERVED
            else -> null
        }
        return if (problem == null) {
            UsernameCheck.Valid(display = typed, key = typed.lowercase())
        } else {
            UsernameCheck.Invalid(problem)
        }
    }

    /**
     * Turns what someone typed into the search box into a prefix to look up,
     * or null when it is too short ([SEARCH_MIN_PREFIX_LENGTH]), too long or
     * not a possible username prefix. A leading "@" is ignored, so "@Zaid"
     * finds "zaid".
     */
    fun searchPrefix(raw: String): String? {
        val typed = ProfileTextRules.trim(raw).removePrefix("@")
        if (typed.length < SEARCH_MIN_PREFIX_LENGTH || typed.length > MAX_LENGTH) return null
        return if (typed.all { it.isAllowed() }) typed.lowercase() else null
    }

    /**
     * When the username may next be changed, in epoch milliseconds, or null if
     * it has never been changed (the first choice, at sign-up, is free).
     *
     * Always use the **server's** clock for both values. A phone's clock can be
     * wrong or changed on purpose, so the app should show the time the server
     * reports (`OwnProfile.usernameChangeAvailableAtMillis`) and not work it out.
     */
    fun nextChangeAllowedAt(lastChangedAtMillis: Long?): Long? =
        lastChangedAtMillis?.let { it + CHANGE_COOLDOWN_MILLIS }

    fun canChange(lastChangedAtMillis: Long?, nowMillis: Long): Boolean {
        val next = nextChangeAllowedAt(lastChangedAtMillis) ?: return true
        return nowMillis >= next
    }

    /**
     * What to do with a request to change the username from [current] to
     * [requested]. [lastRenamedAtMillis] is when the username was last really
     * renamed, or null if it never was; registration and capitalization-only
     * changes do not count. Both times must come from the server's clock.
     *
     * The request is validated first, so an invalid name is reported as
     * invalid and not as "cooling down".
     */
    fun planChange(
        current: String,
        requested: String,
        lastRenamedAtMillis: Long?,
        nowMillis: Long,
    ): UsernameChange {
        val check = validate(requested)
        if (check is UsernameCheck.Invalid) return UsernameChange.Invalid(check.problem)
        check as UsernameCheck.Valid
        if (check.key == normalize(current)) {
            return if (check.display == ProfileTextRules.trim(current)) {
                UsernameChange.Unchanged
            } else {
                UsernameChange.CapitalizationOnly(check.display)
            }
        }
        val next = nextChangeAllowedAt(lastRenamedAtMillis)
        return if (next != null && nowMillis < next) {
            UsernameChange.CoolingDown(next)
        } else {
            UsernameChange.Rename(check.display, check.key)
        }
    }

    /** When a username given up at [releasedAtMillis] becomes available to everyone else. */
    fun holdEndsAt(releasedAtMillis: Long): Long = releasedAtMillis + RELEASED_NAME_HOLD_MILLIS

    /** Whether a username given up at [releasedAtMillis] is still held at [nowMillis]. */
    fun isHeld(releasedAtMillis: Long, nowMillis: Long): Boolean = nowMillis < holdEndsAt(releasedAtMillis)

    private fun String.isReserved(): Boolean = this in RESERVED || RESERVED_PREFIXES.any { startsWith(it) }

    private fun Char.isAllowed(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_'
}

sealed interface UsernameCheck {
    /**
     * [display] keeps the person's capitalization; [key] is the lowercase form
     * that is stored, matched and made unique. Send both to the server, which
     * recomputes the key itself.
     */
    data class Valid(val display: String, val key: String) : UsernameCheck

    data class Invalid(val problem: UsernameProblem) : UsernameCheck
}

enum class UsernameProblem {
    INVALID_CHARACTERS,
    TOO_SHORT,
    TOO_LONG,
    RESERVED,
}

/** The outcome of [UsernameRules.planChange]. */
sealed interface UsernameChange {
    /** The same text as now; nothing to do. */
    data object Unchanged : UsernameChange

    /** Only the capitalization differs. Allowed at any time; does not start the cooldown. */
    data class CapitalizationOnly(val display: String) : UsernameChange

    /** A different name. The cooldown starts when the change is made. */
    data class Rename(val display: String, val key: String) : UsernameChange

    /** A different name, but the last rename was too recent. */
    data class CoolingDown(val nextChangeAllowedAtMillis: Long) : UsernameChange

    data class Invalid(val problem: UsernameProblem) : UsernameChange
}
