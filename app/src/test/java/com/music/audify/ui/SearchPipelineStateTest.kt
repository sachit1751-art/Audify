package com.music.audify.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPipelineStateTest {
    @Test
    fun youtubeTypeaheadPublishesOnlyForTheCurrentUnsubmittedYoutubeQuery() {
        assertTrue(shouldPublishYouTubeTypeahead("arijit", "arijit", false, SearchSource.YOUTUBE, 8L, 8L))
        assertFalse(shouldPublishYouTubeTypeahead("arijit singh", "arijit", false, SearchSource.YOUTUBE, 8L, 8L))
        assertFalse(shouldPublishYouTubeTypeahead("arijit", "arijit", true, SearchSource.YOUTUBE, 8L, 8L))
        assertFalse(shouldPublishYouTubeTypeahead("arijit", "arijit", false, SearchSource.LIBRARY, 8L, 8L))
        assertFalse(shouldPublishYouTubeTypeahead("arijit", "arijit", false, SearchSource.ADDONS, 8L, 8L))
        // The user can type the same query again; an older response must not
        // become valid merely because its text matches the new field value.
        assertFalse(shouldPublishYouTubeTypeahead("arijit", "arijit", false, SearchSource.YOUTUBE, 7L, 8L))
    }

    @Test
    fun localSearchPublishesOnlyForTheLatestRequestWhileLibraryIsSelected() {
        assertTrue(shouldPublishLibrarySearch(4L, 4L, SearchSource.LIBRARY))
        assertFalse(shouldPublishLibrarySearch(3L, 4L, SearchSource.LIBRARY))
        assertFalse(shouldPublishLibrarySearch(4L, 4L, SearchSource.YOUTUBE))
        assertFalse(shouldPublishLibrarySearch(4L, 4L, SearchSource.ADDONS))
    }
}
