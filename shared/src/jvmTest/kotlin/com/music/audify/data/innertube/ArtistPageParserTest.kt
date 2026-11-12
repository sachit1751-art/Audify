package com.music.audify.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistPageParserTest {
    @Test
    fun artistLandingRowsAreDeduplicatedAndMalformedRowsAreSkipped() {
        val response = artistResponse(
            """
            [
              ${trackRow("same-track", "First title")},
              ${trackRow("same-track", "Duplicate title")},
              { "musicResponsiveListItemRenderer": { "playlistItemData": [] } }
            ]
            """.trimIndent(),
        )

        val page = InnertubeParser.parseArtistPage(response)

        assertEquals(listOf("same-track"), page.songs.map { it.videoId })
        assertEquals("First title", page.songs.single().title)
        assertEquals("VLtop-songs", page.moreSongsBrowseId)
    }

    @Test
    fun missingArtistSectionsReturnAnEmptyPageInsteadOfThrowing() {
        val page = InnertubeParser.parseArtistPage(Json.parseToJsonElement("{}").jsonObject)

        assertEquals(emptyList(), page.songs)
        assertEquals(emptyList(), page.sections)
        assertNull(page.moreSongsBrowseId)
    }

    private fun artistResponse(rows: String) = Json.parseToJsonElement(
        """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicShelfRenderer": {
                          "title": { "runs": [{
                            "text": "Top songs",
                            "navigationEndpoint": { "browseEndpoint": { "browseId": "VLtop-songs" } }
                          }] },
                          "contents": $rows
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent(),
    ).jsonObject

    private fun trackRow(id: String, title: String) =
        """{"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$id"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Drake"}]}}}
          ]
        }}"""
}
