package com.music.audify.ui

import com.music.audify.data.model.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPipelineStateTest {
    @Test
    fun naturalLanguageSearchIntentSelectsOnlyConfidentLeadingFacets() {
        assertEquals(SearchFilter.SONGS, inferSearchFilter("songs by Radiohead"))
        assertEquals(SearchFilter.SONGS, inferSearchFilter("Find tracks by Daft Punk"))
        assertEquals(SearchFilter.ALBUMS, inferSearchFilter("show me albums by Radiohead"))
        assertEquals(SearchFilter.ARTISTS, inferSearchFilter("artist Radiohead"))
        assertEquals(SearchFilter.PLAYLISTS, inferSearchFilter("playlists with 90s rock"))
        assertEquals(SearchFilter.VIDEOS, inferSearchFilter("music videos by Beyonce"))
        assertEquals(SearchFilter.ARTISTS, inferSearchFilter("ARTIST Beyoncé"))
    }

    @Test
    fun ambiguousQueriesDoNotOverrideTheUsersSearchFacet() {
        assertNull(inferSearchFilter("Radiohead"))
        assertNull(inferSearchFilter("songs for studying"))
        assertNull(inferSearchFilter("album"))
        assertNull(inferSearchFilter("best playlists"))
        assertNull(inferSearchFilter("artistically inspired songs"))
    }

    @Test
    fun queryEditsResetOnlyInferredFacets() {
        val inferred = SearchFilterSelection().infer("songs by Radiohead", SearchSource.YOUTUBE)
        assertEquals(SearchFilter.SONGS, inferred.filter)
        assertTrue(inferred.inferred)

        val edited = inferred.onQueryChanged()
        assertEquals(SearchFilter.ALL, edited.filter)
        assertFalse(edited.inferred)

        val manual = SearchFilterSelection()
            .select(SearchFilter.ALBUMS)
            .infer("songs by Radiohead", SearchSource.YOUTUBE)
            .onQueryChanged()
        assertEquals(SearchFilter.ALBUMS, manual.filter)
        assertFalse(manual.inferred)
    }

    @Test
    fun explicitFacetSelectionWinsAndInferenceIsYoutubeOnly() {
        val manual = SearchFilterSelection()
            .select(SearchFilter.ARTISTS)
            .infer("songs by Radiohead", SearchSource.YOUTUBE)
        assertEquals(SearchFilter.ARTISTS, manual.filter)
        assertFalse(manual.inferred)

        val addon = SearchFilterSelection().infer("albums by Radiohead", SearchSource.ADDONS)
        assertEquals(SearchFilter.ALL, addon.filter)
        assertFalse(addon.inferred)
    }

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

    @Test
    fun submittedSearchStartsOnlyIfItIsStillTheNewestRequest() {
        assertTrue(shouldStartSearchRequest(12L, 12L))
        assertFalse(shouldStartSearchRequest(11L, 12L))
    }
}
