package com.music.audify.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PodcastProviderParserTest {
    @Test
    fun searchReturnsOnlyProviderPodcastShowBrowseIds() {
        val response = Json.parseToJsonElement(
            """
            {
              "musicTwoRowItemRenderer": {
                "title": { "runs": [{ "text": "Verified show" }] },
                "subtitle": { "runs": [{ "text": "Publisher" }] },
                "navigationEndpoint": { "browseEndpoint": { "browseId": "MPSPshow123" } },
                "thumbnailRenderer": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
                  { "url": "https://example.test/show.jpg" }
                ] } } }
              },
              "other": { "musicTwoRowItemRenderer": {
                "title": { "runs": [{ "text": "Playlist named podcast" }] },
                "subtitle": { "runs": [{ "text": "User playlist" }] },
                "navigationEndpoint": { "browseEndpoint": { "browseId": "VLplaylist123" } },
                "thumbnailRenderer": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [] } } }
              } }
            }
            """.trimIndent(),
        )

        val shows = InnertubeParser.parsePodcastSearchShows(response)

        assertEquals(1, shows.size)
        assertEquals("MPSPshow123", shows.single().browseId)
        assertEquals("Verified show", shows.single().title)
    }

    @Test
    fun podcastEpisodeParserKeepsPodcastEpisodeAndRejectsOrdinaryMusicRows() {
        val response = Json.parseToJsonElement(
            """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                    { "text": "Real episode", "navigationEndpoint": { "browseEndpoint": { "browseId": "MPEDepisode123" } } }
                  ] } } },
                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Verified show · 12 Jun 2026" }] } } }
                ],
                "playlistItemData": { "videoId": "episode123" },
                "overlay": { "musicItemThumbnailOverlayRenderer": { "content": { "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": { "watchEndpoint": {
                    "videoId": "episode123",
                    "watchEndpointMusicSupportedConfigs": { "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
                    } }
                  } }
                } } } },
                "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
                  { "url": "https://example.test/episode.jpg" }
                ] } } }
              },
              "ordinaryTrack": { "musicResponsiveListItemRenderer": {
                "flexColumns": [ { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Podcast theme song" }] } } } ],
                "playlistItemData": { "videoId": "ordinary123" },
                "overlay": { "musicItemThumbnailOverlayRenderer": { "content": { "musicPlayButtonRenderer": {
                  "playNavigationEndpoint": { "watchEndpoint": {
                    "videoId": "ordinary123",
                    "watchEndpointMusicSupportedConfigs": { "watchEndpointMusicConfig": {
                      "musicVideoType": "MUSIC_VIDEO_TYPE_ATV"
                    } }
                  } }
                } } } }
              } }
            }
            """.trimIndent(),
        )

        val episodes = InnertubeParser.parsePodcastEpisodePage(response).episodes

        assertEquals(1, episodes.size)
        assertEquals("episode123", episodes.single().videoId)
        assertEquals("MPEDepisode123", episodes.single().browseId)
        assertTrue(episodes.single().subtitle.contains("Verified show"))
    }
}
