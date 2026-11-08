package com.music.audify.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeExplicitBadgeTest {
    @Test
    fun homeShelfKeepsCatalogueExplicitBadgeOnItsTrackCard() {
        val response = Json.parseToJsonElement(
            """
            {
              "musicCarouselShelfRenderer": {
                "header": {
                  "musicCarouselShelfBasicHeaderRenderer": {
                    "title": { "runs": [{ "text": "New releases" }] }
                  }
                },
                "contents": [{
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Marked track" }] },
                    "subtitle": { "runs": [{ "text": "Artist" }] },
                    "navigationEndpoint": { "watchEndpoint": { "videoId": "explicit-id" } },
                    "thumbnailRenderer": {
                      "musicThumbnailRenderer": {
                        "thumbnail": { "thumbnails": [{ "url": "https://example.test/cover.jpg" }] }
                      }
                    },
                    "badges": [{
                      "musicInlineBadgeRenderer": {
                        "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" }
                      }
                    }]
                  }
                }]
              }
            }
            """.trimIndent(),
        )

        val shelf = InnertubeParser.parseHomeContinuation(response).single()
        assertEquals(true, shelf.items.single().isExplicit)
    }

    @Test
    fun homeShelfLeavesUnlabelledTrackExplicitStateUnknown() {
        val response = Json.parseToJsonElement(
            """
            {
              "musicCarouselShelfRenderer": {
                "header": { "musicCarouselShelfBasicHeaderRenderer": { "title": { "runs": [{ "text": "Mix" }] } } },
                "contents": [{
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Unlabelled" }] },
                    "navigationEndpoint": { "watchEndpoint": { "videoId": "unknown-id" } },
                    "thumbnailRenderer": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [{ "url": "https://example.test/cover.jpg" }] } } }
                  }
                }]
              }
            }
            """.trimIndent(),
        )

        val shelf = InnertubeParser.parseHomeContinuation(response).single()
        assertEquals(null, shelf.items.single().isExplicit)
    }
}
