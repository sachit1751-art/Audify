package com.music.audify.data.social

/**
 * The rules for an AUDIFY username (Plan 091, section 5.1).
 *
 * **Proposal.** The limits and the reserved list are open decisions for the
 * maintainer (Phase 0 audit, section 7, question 4). They live only in this
 * file, so changing them is a one-line edit; the tests read the constants.
 *
 * Usernames are ASCII on purpose. Letters from other scripts can look
 * identical to Latin ones, which makes impersonation trivial and makes
 * "unique" meaningless. A display name can hold any text; the username that
 * is stored, searched and made unique cannot. Because every allowed character
 * is ASCII, a username's length is the same in characters, UTF-16 units, code
 * points and bytes, so no length-unit disagreement is possible here.
 *
 * The server must apply the same rules and must also enforce uniqueness on
 * the normalized form. This client-side copy only gives fast feedback.
 */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 24

    /** Names nobody may register, so no one can pose as the app or its staff. */
    val RESERVED: Set<String> = setOf(
        "admin", "administrator", "api", "audify", "help", "moderator",
        "mod", "null", "official", "root", "security", "settings", "staff",
        "support", "system", "undefined",
    )

    /** The canonical form: what is stored, compared and kept unique. */
    fun normalize(raw: String): String = raw.trim().lowercase()

    fun validate(raw: String): UsernameCheck {
        val name = normalize(raw)
        val problem = when {
            name.length < MIN_LENGTH -> UsernameProblem.TOO_SHORT
            name.length > MAX_LENGTH -> UsernameProblem.TOO_LONG
            !name.all { it.isAllowed() } -> UsernameProblem.INVALID_CHARACTERS
            name.first() !in 'a'..'z' -> UsernameProblem.MUST_START_WITH_LETTER
            "__" in name -> UsernameProblem.CONSECUTIVE_UNDERSCORES
            name.last() == '_' -> UsernameProblem.ENDS_WITH_UNDERSCORE
            name in RESERVED -> UsernameProblem.RESERVED
            else -> null
        }
        return if (problem == null) UsernameCheck.Valid(name) else UsernameCheck.Invalid(problem)
    }

    /**
     * Turns what someone typed into the search box into a prefix to look up,
     * or null when there is nothing sensible to search for. A leading "@" is
     * ignored, so "@Zaid" finds "zaid".
     */
    fun searchPrefix(raw: String): String? {
        val prefix = normalize(raw).removePrefix("@")
        if (prefix.isEmpty() || prefix.length > MAX_LENGTH) return null
        return if (prefix.all { it.isAllowed() }) prefix else null
    }

    private fun Char.isAllowed(): Boolean = this in 'a'..'z' || this in '0'..'9' || this == '_'
}

sealed interface UsernameCheck {
    /** [username] is the normalized form, the one to send to the server. */
    data class Valid(val username: String) : UsernameCheck

    data class Invalid(val problem: UsernameProblem) : UsernameCheck
}

enum class UsernameProblem {
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    MUST_START_WITH_LETTER,
    CONSECUTIVE_UNDERSCORES,
    ENDS_WITH_UNDERSCORE,
    RESERVED,
}
