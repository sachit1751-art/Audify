package com.music.audify.data.social

/**
 * Everything the app needs from the social backend, and nothing about which
 * backend it is (Plan 091, sections 4 and 8).
 *
 * Screens and view models depend on this interface only. Whichever service is
 * chosen later (the existing Cloudflare Worker, Supabase or another) gets one
 * implementation of it, and no screen has to change.
 *
 * Every call acts as the signed-in person. The implementation must send a
 * verified session and must never put a user id of its own choosing on the
 * wire as proof of who is asking. Signing in and out is a separate concern and
 * is not part of this interface.
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

object SocialPaging {
    const val DEFAULT_PAGE_SIZE = 20

    /** The server enforces its own cap; this is what a client should never exceed. */
    const val MAX_PAGE_SIZE = 50
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
