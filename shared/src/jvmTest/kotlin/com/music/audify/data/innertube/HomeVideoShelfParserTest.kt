package com.music.audify.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeVideoShelfParserTest {
    @Test
    fun trendingVideoWithBrowseEndpointKeepsLandscapeVideoClassification() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": { "content": { "sectionListRenderer": { "contents": [{
                      "musicCarouselShelfRenderer": {
                        "header": { "musicCarouselShelfBasicHeaderRenderer": {
                          "title": { "runs": [{ "text": "Trending" }] }
                        }},
                        "contents": [{ "musicTwoRowItemRenderer": {
                          "title": { "runs": [{ "text": "Trending video" }] },
                          "subtitle": { "runs": [{ "text": "Artist" }] },
                          "navigationEndpoint": {
                            "watchEndpoint": { "videoId": "video-123" },
                            "browseEndpoint": { "browseId": "MPREvideo-release" }
                          },
                          "thumbnailRenderer": { "musicThumbnailRenderer": { "thumbnail": {
                            "thumbnails": [{ "url": "https://example.test/video.jpg", "width": 1280, "height": 720 }]
                          }}}
                        }}]
                      }
                    }]}}}
                  }]
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val item = InnertubeParser.parseHome(response).single().items.single()

        assertEquals("video-123", item.videoId)
        assertTrue(item.isVideo)
    }
}
