package com.music.audify.data.social

/**
 * Everything the app needs from the social backend, and nothing about which
 * backend it is (Plan 091, sections 4 and 8).
 *
 * Screens and view models depend on this interface only. Whichever service is
 * chosen later (the existing Cloudflare Worker, Supabase or another) gets one
 * implementation of it, and no screen has to change.
 *
 * Reading public content works **without signing in**: [searchProfiles],
 * [profile], [followers] and [following] accept an anonymous caller, who is
 * treated as having no relationship ([Relationship.NONE]) to anyone. Everything
 * else needs a signed-in person and fails with [SocialError.NOT_SIGNED_IN]
 * otherwise. Signing in and out is a separate concern and is not part of this
 * interface. The first sign-in method is Google; the design must stay open to
 * email and other providers, so nothing here assumes a provider.
 *
 * ## Server acceptance criteria (mandatory)
 *
 * The types in this package describe intent and give fast feedback. They are
 * **not** a security boundary: a client can be modified, out of date or
 * written by someone else. Every backend behind this interface must enforce
 * the following itself, and none of it counts as done until it is tested
 * (Plan 091, sections 4, 7, 9 and 11):
 *
 * 1. **Session identity.** The acting user comes from a verified session or
 *    token, never from a user id, username, display name, provider account or
 *    device id the client supplies. Every account has its own permanent
 *    internal id, assigned by the server; a username or an email address is an
 *    attribute of an account, never its identity.
 * 2. **Privacy.** Discoverability and profile audience ([PrivacySettings]) are
 *    applied on the server. Private fields never appear in a response that a
 *    viewer is not allowed to receive.
 * 3. **Blocks.** A block in either direction removes the people from each
 *    other's search, profile, follow actions, lists and counts. The blocked
 *    person receives [SocialError.NOT_FOUND], the same answer as for someone
 *    who does not exist.
 * 4. **Relationship state.** [Relationship] is computed by the server from the
 *    follow graph and the block list on every request. It is never accepted
 *    from a client.
 * 5. **Username uniqueness.** Enforced by the database on the normalized form
 *    ([UsernameRules.normalize]), so concurrent sign-ups cannot both succeed.
 * 6. **Input rules.** The username and text rules ([UsernameRules],
 *    [ProfileTextRules], including the code-point length unit and the explicit
 *    trim set) and the page rules ([SocialPaging], including cursors) are
 *    applied again on the server, and the server passes every case in
 *    `shared/testvectors/social-v1.json`.
 * 7. **Public reads.** An anonymous caller sees only what [Audience.EVERYONE]
 *    allows, and only from discoverable profiles. Anonymous reads are rate
 *    limited per client. Because a block cannot apply to someone who is not
 *    signed in, blocking does not hide a **public** profile from a person who
 *    signs out; anything that must be hidden from specific people must not be
 *    public.
 * 8. **Username changes.** The cooldown ([UsernameRules.CHANGE_COOLDOWN_DAYS])
 *    is enforced with the server's clock and answered with
 *    [SocialError.USERNAME_COOLDOWN]. Registration and capitalization-only
 *    changes do not start it ([UsernameRules.planChange]). Uniqueness is
 *    checked on the key, in the database, at the moment of the change.
 * 9. **Released names.** A username that is given up is held for
 *    [UsernameRules.RELEASED_NAME_HOLD_DAYS] days; others get
 *    [SocialError.USERNAME_TAKEN] until the hold ends. Names that start with
 *    "audify" and the other reserved names are never available.
 * 10. **Search cost.** Search requires a prefix of at least
 *    [UsernameRules.SEARCH_MIN_PREFIX_LENGTH] characters, is rate limited per
 *    client without routing through a single global object, and public reads
 *    may be cached briefly.
 */
interface SocialRepository {
    suspend fun ownProfile(): SocialResult<OwnProfile?>

    suspend fun createProfile(
        username: String,
        displayName: String,
        bio: String = "",
    ): SocialResult<OwnProfile>

    suspend fun updateProfile(update: ProfileUpdate): SocialResult<OwnProfile>

    suspend fun searchProfiles(
        prefix: String,
        cursor: String? = null,
        limit: Int = SocialPaging.DEFAULT_PAGE_SIZE,
    ): SocialResult<SocialPage<ProfileSummary>>

    suspend fun profile(username: String): SocialResult<ProfileView>

    /** Safe to repeat: following someone already followed succeeds and changes nothing. */
    suspend fun follow(userId: String): SocialResult<Relationship>

    /** Safe to repeat, like [follow]. */
    suspend fun unfollow(userId: String): SocialResult<Relationship>

    suspend fun followers(
        userId: String,
        cursor: String? = null,
        limit: Int = SocialPaging.DEFAULT_PAGE_SIZE,
    ): SocialResult<SocialPage<ProfileSummary>>

    suspend fun following(
        userId: String,
        cursor: String? = null,
        limit: Int = SocialPaging.DEFAULT_PAGE_SIZE,
    ): SocialResult<SocialPage<ProfileSummary>>

    suspend fun block(userId: String): SocialResult<Unit>

    suspend fun unblock(userId: String): SocialResult<Unit>
}

/**
 * The one rule for page sizes, shared by every caller and every backend.
 *
 * A requested limit is **clamped** into `1..MAX_PAGE_SIZE`, never rejected:
 * below 1 becomes 1, above [MAX_PAGE_SIZE] becomes [MAX_PAGE_SIZE]. Callers
 * clamp before sending ([clamp]). A backend must enforce the same range
 * itself, independently of any client, and must never return more than
 * [MAX_PAGE_SIZE] items whatever it was asked for; an out-of-range limit is
 * not an error there either.
 *
 * Cursors are opaque to callers: never build or edit one. A backend answers a
 * malformed, forged or expired cursor with [SocialError.INVALID_INPUT]
 * ([isWellFormedCursor] defines "malformed").
 */
object SocialPaging {
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50

    /**
     * How long the app waits after the last keystroke before it searches, so
     * typing a name sends one request and not one per letter.
     */
    const val SEARCH_DEBOUNCE_MILLIS = 300L

    /** The longest cursor either side accepts. */
    const val MAX_CURSOR_LENGTH = 256

    fun clamp(limit: Int): Int = limit.coerceIn(1, MAX_PAGE_SIZE)

    /**
     * Whether [cursor] is well formed: null (the first page), or 1 to
     * [MAX_CURSOR_LENGTH] characters from `A-Z a-z 0-9 _ -`.
     *
     * Well formed is necessary, not sufficient. A server must still verify that
     * a cursor is one it issued and has not expired, and answers every bad
     * cursor, malformed or not, with [SocialError.INVALID_INPUT].
     */
    fun isWellFormedCursor(cursor: String?): Boolean =
        cursor == null || (
            cursor.isNotEmpty() && cursor.length <= MAX_CURSOR_LENGTH &&
                cursor.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
            )
}

/** One page of a list, and where the next one starts; null means the end. */
data class SocialPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
}

sealed interface SocialResult<out T> {
    data class Success<T>(val value: T) : SocialResult<T>

    data class Failure(val error: SocialError) : SocialResult<Nothing>
}

/**
 * Why a call failed, in terms a screen can act on. A person who is blocked
 * gets [NOT_FOUND], the same answer as for someone who does not exist, so the
 * block itself is not revealed.
 */
enum class SocialError {
    NOT_SIGNED_IN,
    SESSION_EXPIRED,
    NOT_FOUND,
    FORBIDDEN,
    USERNAME_TAKEN,

    /** The username was changed less than [UsernameRules.CHANGE_COOLDOWN_DAYS] days ago. */
    USERNAME_COOLDOWN,
    INVALID_INPUT,
    RATE_LIMITED,
    OFFLINE,
    SERVER,
}
