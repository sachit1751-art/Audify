package com.music.audify.data.social

/**
 * Limits and clean-up for the free-text parts of a profile (Plan 091, 5.1).
 *
 * **Proposal.** The numbers are open for the maintainer to change. As with
 * usernames, the server must apply the same rules; this is the client's copy.
 *
 * Invisible and direction-changing characters are refused because they let
 * one person's name pretend to be another's, or flip the text around it.
 */
object ProfileTextRules {
    const val DISPLAY_NAME_MAX = 40
    const val BIO_MAX = 160

    /** The trimmed display name, or null when it is empty, too long or unsafe. */
    fun cleanDisplayName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.length > DISPLAY_NAME_MAX) return null
        return name.takeIf { text -> text.none { it.isUnsafe() || it.isControlCode() } }
    }

    /**
     * The trimmed bio, which may be empty, or null when it is too long or
     * unsafe. Line breaks are allowed in a bio; other control characters are not.
     */
    fun cleanBio(raw: String): String? {
        val bio = raw.trim()
        if (bio.length > BIO_MAX) return null
        return bio.takeIf { text -> text.none { it.isUnsafe() || (it.isControlCode() && it != '\n') } }
    }

    private fun Char.isControlCode(): Boolean = code < 32 || code == 127

    /** Zero-width characters, the byte-order mark, and the bidirectional overrides. */
    private fun Char.isUnsafe(): Boolean =
        code in 0x200B..0x200F || code in 0x202A..0x202E || code in 0x2066..0x2069 || code == 0xFEFF
}
