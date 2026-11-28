package com.music.audify.data.social

/**
 * Limits and clean-up for the free-text parts of a profile (Plan 091, 5.1).
 *
 * **Proposal.** The numbers are open for the maintainer to change.
 *
 * ## The length unit: Unicode code points
 *
 * [DISPLAY_NAME_MAX] and [BIO_MAX] count Unicode **code points**, exactly as
 * [codePointCount] does. They do not count UTF-16 code units, which is what
 * Kotlin's `String.length` returns, and they do not count user-perceived
 * characters (grapheme clusters). The difference is visible:
 *
 * - An emoji such as U+1F600 is 1 code point but 2 UTF-16 units.
 * - "e" followed by U+0301 (a combining accent) is 2 code points, even though
 *   it looks like one letter.
 *
 * Code points are chosen because every likely backend agrees on them without
 * a Unicode library: Go's `utf8.RuneCountInString`, PostgreSQL's
 * `char_length`, and Rust's `chars().count()` all count code points. JavaScript
 * is the trap: `string.length` counts UTF-16 units, so a Worker must use
 * `[...text].length`. Counting grapheme clusters would need Unicode
 * segmentation tables that change between Unicode versions and are not in
 * common Kotlin, so two sides could disagree. The limit is an abuse and
 * storage guard, not a promise about visual width.
 *
 * No Unicode normalization is applied. UTF-8 needs at most 4 bytes per code
 * point, so storage must allow up to 4 times the limit in bytes.
 *
 * ## What the server must do
 *
 * Apply these same rules itself and never rely on this client copy: the same
 * unit, the same trimming, the same rejected characters. Lone surrogates are
 * rejected because they cannot be encoded as UTF-8, and different platforms
 * silently repair them in different ways.
 *
 * ## Trimming is explicit
 *
 * Every platform's built-in `trim()` removes a slightly different set of
 * characters, so none is used. [trim] removes exactly: U+0009 to U+000D,
 * U+0020, U+00A0, U+1680, U+2000 to U+200A, U+2028, U+2029, U+202F, U+205F and
 * U+3000. Anything else is left alone, and if it is a control or invisible
 * character it is then rejected rather than silently removed. For example
 * U+FEFF (which JavaScript's `trim()` strips), U+0085 (which Go's strips) and
 * U+001F (which Kotlin's strips) are all rejected, never trimmed. The shared
 * test vectors cover each of these.
 */
object ProfileTextRules {
    const val DISPLAY_NAME_MAX = 40
    const val BIO_MAX = 160

    /** Removes leading and trailing white space, using exactly the set listed in the class documentation. */
    fun trim(raw: String): String = raw.trim { it.isTrimmable() }

    /** The number of Unicode code points in [text]; a lone surrogate counts as one. */
    fun codePointCount(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            index += if (text.isSurrogatePairAt(index)) 2 else 1
            count++
        }
        return count
    }

    /** The trimmed display name, or null when it is empty, too long or unsafe. */
    fun cleanDisplayName(raw: String): String? {
        val name = trim(raw)
        if (name.isEmpty() || codePointCount(name) > DISPLAY_NAME_MAX) return null
        return name.takeIf { isSafe(it, allowLineBreak = false) }
    }

    /**
     * The trimmed bio, which may be empty, or null when it is too long or
     * unsafe. Line breaks are allowed in a bio; other control characters are not.
     */
    fun cleanBio(raw: String): String? {
        val bio = trim(raw)
        if (codePointCount(bio) > BIO_MAX) return null
        return bio.takeIf { isSafe(it, allowLineBreak = true) }
    }

    private fun isSafe(text: String, allowLineBreak: Boolean): Boolean =
        !text.hasUnpairedSurrogate() &&
            text.none { it.isUnsafe() || (it.isControlCode() && !(allowLineBreak && it == '\n')) }

    private fun String.isSurrogatePairAt(index: Int): Boolean =
        this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()

    private fun String.hasUnpairedSurrogate(): Boolean {
        var index = 0
        while (index < length) {
            val c = this[index]
            when {
                isSurrogatePairAt(index) -> index += 2
                c.isHighSurrogate() || c.isLowSurrogate() -> return true
                else -> index++
            }
        }
        return false
    }

    private fun Char.isTrimmable(): Boolean =
        code in 0x09..0x0D || code == 0x20 || code == 0xA0 || code == 0x1680 ||
            code in 0x2000..0x200A || code == 0x2028 || code == 0x2029 ||
            code == 0x202F || code == 0x205F || code == 0x3000

    /** The ASCII and C1 control characters, line breaks included. */
    private fun Char.isControlCode(): Boolean = code < 32 || code in 127..159

    /** Zero-width characters, the byte-order mark, and the bidirectional overrides. */
    private fun Char.isUnsafe(): Boolean =
        code in 0x200B..0x200F || code in 0x202A..0x202E || code in 0x2066..0x2069 || code == 0xFEFF
}
