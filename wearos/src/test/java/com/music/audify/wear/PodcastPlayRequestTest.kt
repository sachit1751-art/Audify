package com.music.audify.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastPlayRequestTest {
    @Test fun roundTripsIdsContainingSeparatorsAndUnicode() {
        val request = PodcastPlayRequest("feed:α:42", "episode/one?x=2&y=3")
        assertEquals(request, PodcastPlayRequest.decode(request.encode()))
    }

    @Test fun roundTripsYouTubeMusicDiscoveryEpisodeRequests() {
        val request = PodcastPlayRequest("ytm:FEpodcast_show_123", "videoId_abc123")
        assertEquals(request, PodcastPlayRequest.decode(request.encode()))
    }

    @Test fun rejectsMissingOrMalformedIds() {
        assertNull(PodcastPlayRequest.decode(""))
        assertNull(PodcastPlayRequest.decode("abc"))
        assertNull(PodcastPlayRequest.decode("0:episode"))
        assertNull(PodcastPlayRequest.decode("4:feed"))
        assertNull(PodcastPlayRequest.decode("4:feed" + ""))
    }

    @Test fun rejectsEmptyEpisodeAfterFeed() {
        assertNull(PodcastPlayRequest.decode("4:feed"))
        assertNull(PodcastPlayRequest.decode("4:feed" + " "))
    }
}
