package com.music.audify.data.social

/**
 * How two people are connected, seen from the viewer's side.
 *
 * It is worked out from the follow graph and the block list each time; there
 * is no stored "mutual" flag to fall out of date (Plan 091, section 5.3).
 */
enum class Relationship {
    /** The viewer is looking at their own profile. */
    SELF,

    /** Either person has blocked the other. Always wins over any follow. */
    BLOCKED,

    /** They follow each other. */
    MUTUAL,

    /** The viewer follows the owner; the owner does not follow back. */
    FOLLOWING,

    /** The owner follows the viewer; the viewer does not follow back. */
    FOLLOWED_BY,

    NONE;

    /** True when the viewer should be offered a Follow button. */
    val canFollow: Boolean get() = this == NONE || this == FOLLOWED_BY

    val isFollowing: Boolean get() = this == FOLLOWING || this == MUTUAL

    companion object {
        fun of(
            viewerId: String,
            ownerId: String,
            viewerFollowsOwner: Boolean,
            ownerFollowsViewer: Boolean,
            blockedEitherWay: Boolean,
        ): Relationship = when {
            viewerId == ownerId -> SELF
            blockedEitherWay -> BLOCKED
            viewerFollowsOwner && ownerFollowsViewer -> MUTUAL
            viewerFollowsOwner -> FOLLOWING
            ownerFollowsViewer -> FOLLOWED_BY
            else -> NONE
        }
    }
}

/**
 * Who may see something on a profile.
 *
 * There is deliberately no "followers only" choice yet. Following is open to
 * everyone, so "followers" would not keep anyone out: a stranger could follow
 * and then look. "Mutual followers" is a real boundary, because both sides
 * have to agree. A "followers" choice makes sense once follow requests exist.
 */
enum class Audience {
    EVERYONE,
    MUTUAL_FOLLOWERS,
    NOBODY;

    /**
     * Whether someone in [relationship] to the owner is allowed through.
     * The owner always sees their own things; a block always shuts the door.
     */
    fun allows(relationship: Relationship): Boolean = when (relationship) {
        Relationship.SELF -> true
        Relationship.BLOCKED -> false
        else -> when (this) {
            EVERYONE -> true
            MUTUAL_FOLLOWERS -> relationship == Relationship.MUTUAL
            NOBODY -> false
        }
    }
}

/**
 * A person's privacy choices.
 *
 * **Proposal.** The defaults are an open decision (Plan 091, section 9; audit
 * section 7, question 4). These rules describe intent. The server is the
 * authority: it must check them itself and must never rely on a client to hide
 * something. Things added later, such as a pinned playlist or listening
 * statistics, must each carry an explicit [Audience] control and default to
 * [Audience.NOBODY] (private).
 */
data class PrivacySettings(
    /** Whether the profile can turn up in username search. */
    val discoverable: Boolean = true,
    /** Who may open the profile page. */
    val profile: Audience = Audience.EVERYONE,
) {
    fun canAppearInSearchFor(relationship: Relationship): Boolean = when (relationship) {
        Relationship.SELF -> true
        Relationship.BLOCKED -> false
        else -> discoverable
    }

    fun canOpenProfile(relationship: Relationship): Boolean = profile.allows(relationship)
}

/**
 * The few fields shown in search results and in follower or following lists.
 *
 * [userId] is the account's permanent identity: random, assigned by the server
 * once, never reused and never derived from the username, an email address or
 * any provider's account id. Follows, blocks and links must use it, because a
 * username can change.
 *
 * [username] is shown with the person's own capitalization. Compare and match
 * on [usernameKey].
 */
data class ProfileSummary(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
) {
    val usernameKey: String get() = UsernameRules.normalize(username)
}

/**
 * What a viewer sees when they open someone's profile.
 *
 * Never carries an email address, a music-provider account or any other
 * private identifier (Plan 091, section 5.2).
 */
data class ProfileView(
    val summary: ProfileSummary,
    val bio: String = "",
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val relationship: Relationship = Relationship.NONE,
)

/** The signed-in person's own profile, including the settings only they see. */
data class OwnProfile(
    val summary: ProfileSummary,
    val bio: String = "",
    val privacy: PrivacySettings = PrivacySettings(),
    /**
     * When the username may be changed again, in epoch milliseconds on the
     * **server's** clock; null means it can be changed now. Show this value;
     * do not work it out from the phone's clock.
     */
    val usernameChangeAvailableAtMillis: Long? = null,
)

/** A change to the signed-in person's profile; null means "leave as it is". */
data class ProfileUpdate(
    /** A new username; subject to the change cooldown ([UsernameRules.CHANGE_COOLDOWN_DAYS]). */
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val privacy: PrivacySettings? = null,
)
