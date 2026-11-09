package com.music.audify.data.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.playback.PodcastNetworkPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastFeedParserFixtureTest {
    @Test fun parsesRss20EnclosuresAndEpisodeMetadata() {
        val fixture = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel><title>Fixture RSS Show</title><description>RSS description</description>
                <link>https://podcast.example.com</link>
                <item><title>First RSS episode</title><guid>rss-episode-1</guid>
                  <description>Episode summary</description><pubDate>Mon, 02 Nov 2026 10:00:00 GMT</pubDate>
                  <enclosure url="https://cdn.example.com/episode.mp3" type="audio/mpeg" length="1234"/>
                  <itunes:duration>01:02:03</itunes:duration>
                </item>
              </channel>
            </rss>""".trimIndent()

        val (show, episodes) = PodcastRepository.parseFeed(
            "https://podcast.example.com/feed.xml", fixture.toByteArray(),
        )

        assertEquals("Fixture RSS Show", show.title)
        assertEquals("RSS description", show.description)
        assertEquals("https://podcast.example.com", show.siteUrl)
        assertEquals(1, episodes.size)
        assertEquals("rss-episode-1", episodes.single().ref.episodeId)
        assertEquals("First RSS episode", episodes.single().ref.episodeTitle)
        assertEquals("audio/mpeg", episodes.single().ref.mimeType)
        assertEquals(3_723_000L, episodes.single().ref.durationMs)
        assertEquals("https://cdn.example.com/episode.mp3", episodes.single().ref.enclosureUri.toString())
    }

    @Test fun parsesAtomEnclosureLinksAndNestedContent() {
        val fixture = """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Fixture Atom Show</title><subtitle>Atom description</subtitle>
              <link rel="alternate" href="https://atom.example.com"/>
              <entry><title>Atom episode</title><id>urn:episode:atom-1</id>
                <summary>Atom summary</summary><published>2026-11-03T10:00:00Z</published>
                <link rel="enclosure" href="https://cdn.example.com/atom.m4a" type="audio/mp4"/>
              </entry>
            </feed>""".trimIndent()

        val (show, episodes) = PodcastRepository.parseFeed(
            "https://atom.example.com/feed.xml", fixture.toByteArray(),
        )

        assertEquals("Fixture Atom Show", show.title)
        assertEquals("Atom description", show.description)
        assertEquals("https://atom.example.com", show.siteUrl)
        assertEquals(1, episodes.size)
        assertEquals("urn:episode:atom-1", episodes.single().ref.episodeId)
        assertEquals("Atom episode", episodes.single().ref.episodeTitle)
        assertEquals("audio/mp4", episodes.single().ref.mimeType)
    }

    @Test fun rejectsNonHttpsAndPrivateEnclosures() {
        val http = """<rss version="2.0"><channel><title>Unsafe</title><item>
            <title>HTTP</title><enclosure url="http://cdn.example.com/a.mp3" type="audio/mpeg"/>
            </item></channel></rss>"""
        val privateHost = """<rss version="2.0"><channel><title>Unsafe</title><item>
            <title>Private</title><enclosure url="https://printer.local/a.mp3" type="audio/mpeg"/>
            </item></channel></rss>"""
        assertTrue(PodcastRepository.parseFeed("https://feed.example.com/rss", http.toByteArray()).second.isEmpty())
        assertTrue(PodcastRepository.parseFeed("https://feed.example.com/rss", privateHost.toByteArray()).second.isEmpty())
        assertNotNull("HTTPS public-host fixture should pass URL policy", runCatching {
            PodcastNetworkPolicy.validateUrl("https://cdn.example.com/audio.mp3".toHttpUrl())
        }.getOrNull())
    }

    @Test fun malformedXmlIsRejected() {
        val malformed = """<rss version="2.0"><channel><title>Broken</title><item><title>Open"""
        val failure = runCatching {
            PodcastRepository.parseFeed("https://feed.example.com/broken.xml", malformed.toByteArray())
        }.exceptionOrNull()
        assertNotNull("Malformed XML must fail rather than create a partial subscription", failure)
    }

    @Test fun missingShowMetadataIsRejectedAndMissingEpisodeFieldsAreSkipped() {
        val missingTitle = """<rss version="2.0"><channel><description>No title</description></channel></rss>"""
        val titleFailure = runCatching {
            PodcastRepository.parseFeed("https://feed.example.com/no-title.xml", missingTitle.toByteArray())
        }.exceptionOrNull()
        assertNotNull("A feed without a show title must be rejected", titleFailure)

        val missingEpisodeFields = """<rss version="2.0"><channel><title>Metadata-light show</title>
            <item><description>Missing title</description><enclosure url="https://cdn.example.com/no-title.mp3" type="audio/mpeg"/></item>
            <item><title>Missing enclosure</title></item>
            <item><title>Valid episode without optional metadata</title><enclosure url="https://cdn.example.com/valid.mp3" type="audio/mpeg"/></item>
            </channel></rss>"""
        val (show, episodes) = PodcastRepository.parseFeed(
            "https://feed.example.com/minimal.xml", missingEpisodeFields.toByteArray(),
        )
        assertEquals("Metadata-light show", show.title)
        assertEquals(1, episodes.size)
        assertEquals("Valid episode without optional metadata", episodes.single().ref.episodeTitle)
        assertEquals("https://cdn.example.com/valid.mp3", episodes.single().ref.enclosureUri.toString())
    }
}
