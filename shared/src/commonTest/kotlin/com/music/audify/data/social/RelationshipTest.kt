package com.music.audify.data.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelationshipTest {

    private fun between(
        viewerFollowsOwner: Boolean = false,
        ownerFollowsViewer: Boolean = false,
        blocked: Boolean = false,
        viewer: String = "viewer",
        owner: String = "owner",
    ) = Relationship.of(viewer, owner, viewerFollowsOwner, ownerFollowsViewer, blocked)

    @Test
    fun noConnectionIsNone() {
        assertEquals(Relationship.NONE, between())
    }

    @Test
    fun oneWayFollowsAreToldApart() {
        assertEquals(Relationship.FOLLOWING, between(viewerFollowsOwner = true))
        assertEquals(Relationship.FOLLOWED_BY, between(ownerFollowsViewer = true))
    }

    @Test
    fun followingEachOtherIsMutual() {
        assertEquals(Relationship.MUTUAL, between(viewerFollowsOwner = true, ownerFollowsViewer = true))
    }

    @Test
    fun aBlockWinsOverAnyFollow() {
        assertEquals(Relationship.BLOCKED, between(viewerFollowsOwner = true, ownerFollowsViewer = true, blocked = true))
        assertEquals(Relationship.BLOCKED, between(blocked = true))
    }

    @Test
    fun yourOwnProfileIsSelfWhateverElseIsSet() {
        assertEquals(
            Relationship.SELF,
            between(viewerFollowsOwner = true, ownerFollowsViewer = true, blocked = true, viewer = "x", owner = "x"),
        )
    }

    @Test
    fun followButtonIsOfferedOnlyWhereFollowingIsPossibleAndNotYetDone() {
        assertTrue(Relationship.NONE.canFollow)
        assertTrue(Relationship.FOLLOWED_BY.canFollow)
        assertFalse(Relationship.FOLLOWING.canFollow)
        assertFalse(Relationship.MUTUAL.canFollow)
        assertFalse(Relationship.SELF.canFollow)
        assertFalse(Relationship.BLOCKED.canFollow)
    }

    @Test
    fun isFollowingCoversBothDirectionsOfYourOwnFollow() {
        assertTrue(Relationship.FOLLOWING.isFollowing)
        assertTrue(Relationship.MUTUAL.isFollowing)
        assertFalse(Relationship.FOLLOWED_BY.isFollowing)
        assertFalse(Relationship.NONE.isFollowing)
    }

    @Test
    fun everyoneAudienceAdmitsEveryoneExceptBlockedPeople() {
        Relationship.entries.forEach {
            assertEquals(it != Relationship.BLOCKED, Audience.EVERYONE.allows(it), it.name)
        }
    }

    @Test
    fun mutualAudienceAdmitsOnlyMutualsAndTheOwner() {
        Relationship.entries.forEach {
            val expected = it == Relationship.MUTUAL || it == Relationship.SELF
            assertEquals(expected, Audience.MUTUAL_FOLLOWERS.allows(it), it.name)
        }
    }

    @Test
    fun nobodyAudienceAdmitsOnlyTheOwner() {
        Relationship.entries.forEach {
            assertEquals(it == Relationship.SELF, Audience.NOBODY.allows(it), it.name)
        }
    }

    @Test
    fun defaultPrivacyIsDiscoverableAndOpen() {
        val defaults = PrivacySettings()
        assertTrue(defaults.discoverable)
        assertEquals(Audience.EVERYONE, defaults.profile)
    }

    @Test
    fun anUndiscoverableProfileHidesFromSearchButNotFromItsOwner() {
        val hidden = PrivacySettings(discoverable = false)
        assertFalse(hidden.canAppearInSearchFor(Relationship.NONE))
        assertFalse(hidden.canAppearInSearchFor(Relationship.MUTUAL))
        assertTrue(hidden.canAppearInSearchFor(Relationship.SELF))
    }

    @Test
    fun aBlockedPersonNeverFindsOrOpensTheProfileWhateverTheSettings() {
        val open = PrivacySettings(discoverable = true, profile = Audience.EVERYONE)
        assertFalse(open.canAppearInSearchFor(Relationship.BLOCKED))
        assertFalse(open.canOpenProfile(Relationship.BLOCKED))
    }

    @Test
    fun aPrivateProfileOpensForMutualsOnly() {
        val private = PrivacySettings(profile = Audience.MUTUAL_FOLLOWERS)
        assertTrue(private.canOpenProfile(Relationship.MUTUAL))
        assertFalse(private.canOpenProfile(Relationship.FOLLOWING))
        assertFalse(private.canOpenProfile(Relationship.NONE))
        assertTrue(private.canOpenProfile(Relationship.SELF))
    }

    @Test
    fun pageReportsWhetherThereIsMore() {
        assertTrue(SocialPage(listOf(1), nextCursor = "c").hasMore)
        assertFalse(SocialPage(listOf(1)).hasMore)
    }
}
