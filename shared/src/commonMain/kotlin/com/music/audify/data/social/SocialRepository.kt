package com.music.audify.data.social

/**
 * Everything the app needs from the social backend, and nothing about which
 * backend it is (Plan 091, sections 4 and 8).
 *
 * Screens and view models depend on this interface only. Whichever service is
 * chosen later (the existing Cloudflare Worker, Supabase or another) gets one
 * implementation of it, and no screen has to change.
 *
 * Every call acts as the signed-in person. Signing in and out is a separate
 * concern and is not part of this interface.
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
 *    device id the client supplies.
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
 *    [ProfileTextRules], including the code-point length unit) and the page
 *    limits ([SocialPaging]) are applied again on the server.
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
 * malformed or expired cursor with [SocialError.INVALID_INPUT].
 */
object SocialPaging {
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50

    fun clamp(limit: Int): Int = limit.coerceIn(1, MAX_PAGE_SIZE)
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
    INVALID_INPUT,
    RATE_LIMITED,
    OFFLINE,
    SERVER,
}
