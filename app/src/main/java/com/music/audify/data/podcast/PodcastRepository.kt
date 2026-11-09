// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.podcast

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.util.Xml
import com.music.audify.playback.PodcastEpisodeRef
import com.music.audify.playback.PodcastPlaybackRepository
import com.music.audify.playback.PodcastNetworkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** A locally subscribed RSS/Atom show. */
data class PodcastShow(
    val id: String,
    val feedUrl: String,
    val title: String,
    val description: String?,
    val artworkUrl: String?,
    val siteUrl: String?,
    val lastRefreshedMs: Long = 0L,
)

data class PodcastEpisode(
    val ref: PodcastEpisodeRef,
    val published: String?,
    val playbackPositionMs: Long = 0L,
    val completed: Boolean = false,
)

/** RSS 2.0 / Atom feed reader with bounded downloads, hardened XML and local subscriptions. */
object PodcastRepository : PodcastPlaybackRepository {
    private const val PREFS = "audify_podcast_library_v1"
    private const val SHOWS = "shows"
    private const val MAX_FEED_BYTES = 5 * 1024 * 1024
    private const val MAX_EPISODES = 500
    private lateinit var appContext: Context
    private val shows = linkedMapOf<String, PodcastShow>()
    private val episodes = linkedMapOf<String, List<PodcastEpisode>>()
    private val progress = linkedMapOf<String, Pair<Long, Boolean>>()

    @Synchronized fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SHOWS, "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val show = PodcastShow(
                    id = item.getString("id"), feedUrl = item.getString("feedUrl"),
                    title = item.optString("title", "Untitled podcast"),
                    description = item.optString("description").takeIf(String::isNotBlank),
                    artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                    siteUrl = item.optString("siteUrl").takeIf(String::isNotBlank),
                    lastRefreshedMs = item.optLong("lastRefreshedMs"),
                )
                shows[show.id] = show
            }
            val cached = JSONArray(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("episode_cache", "[]") ?: "[]")
            for (i in 0 until cached.length()) {
                val item = cached.getJSONObject(i)
                val showId = item.getString("showId")
                val enclosure = item.getString("enclosure").toUri()
                val ref = PodcastEpisodeRef(
                    feedId = showId, episodeId = item.getString("episodeId"), enclosureUri = enclosure,
                    mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
                    showTitle = item.getString("showTitle"), episodeTitle = item.getString("episodeTitle"),
                    description = item.optString("description").takeIf(String::isNotBlank),
                    artworkUri = item.optString("artwork").takeIf(String::isNotBlank)?.toUri(),
                    durationMs = item.optLong("durationMs", -1L).takeIf { it >= 0L },
                )
                val episode = PodcastEpisode(ref, item.optString("published").takeIf(String::isNotBlank))
                episodes[showId] = episodes[showId].orEmpty() + episode
            }
            val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("progress", "{}")
            val saved = JSONObject(p ?: "{}")
            saved.keys().forEach { key -> saved.optJSONObject(key)?.let { progress[key] = it.optLong("position") to it.optBoolean("completed") } }
        }
        com.music.audify.playback.PodcastPlaybackBridge.repository = this
    }

    @Synchronized fun subscribedShows(): List<PodcastShow> = shows.values.toList()
    @Synchronized fun episodes(showId: String): List<PodcastEpisode> = episodes[showId].orEmpty().map { e ->
        val p = progress[e.ref.stableId]
        e.copy(playbackPositionMs = p?.first ?: 0L, completed = p?.second ?: false)
    }
    @Synchronized fun isSubscribed(feedUrl: String): Boolean = shows.values.any { it.feedUrl == feedUrl }

    suspend fun subscribe(feedUrl: String): PodcastShow = withContext(Dispatchers.IO) {
        val parsed = fetchFeed(feedUrl)
        synchronized(this@PodcastRepository) {
            shows[parsed.first.id] = parsed.first
            episodes[parsed.first.id] = parsed.second
            persistShows()
        }
        parsed.first
    }

    suspend fun refresh(showId: String): PodcastShow = withContext(Dispatchers.IO) {
        val existing = synchronized(this@PodcastRepository) { shows[showId] } ?: error("Podcast is not subscribed")
        val (updated, items) = fetchFeed(existing.feedUrl)
        synchronized(this@PodcastRepository) {
            shows[showId] = updated.copy(id = showId)
            episodes[showId] = items
            persistShows()
            shows.getValue(showId)
        }
    }

    @Synchronized fun unsubscribe(showId: String) {
        shows.remove(showId)
        episodes.remove(showId)
        persistShows()
    }

    private fun persistShows() {
        val array = JSONArray()
        shows.values.forEach { s -> array.put(JSONObject().put("id", s.id).put("feedUrl", s.feedUrl)
            .put("title", s.title).put("description", s.description).put("artworkUrl", s.artworkUrl)
            .put("siteUrl", s.siteUrl).put("lastRefreshedMs", s.lastRefreshedMs)) }
        val cached = JSONArray()
        episodes.forEach { (showId, list) ->
            list.forEach { episode ->
            val ref = episode.ref
            cached.put(JSONObject().put("showId", showId).put("episodeId", ref.episodeId)
                .put("enclosure", ref.enclosureUri.toString()).put("mimeType", ref.mimeType)
                .put("showTitle", ref.showTitle).put("episodeTitle", ref.episodeTitle)
                .put("description", ref.description).put("artwork", ref.artworkUri?.toString())
                .put("durationMs", ref.durationMs ?: -1L).put("published", episode.published))
            }
        }
        val p = JSONObject()
        progress.forEach { (id, state) -> p.put(id, JSONObject().put("position", state.first).put("completed", state.second)) }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SHOWS, array.toString()).putString("episode_cache", cached.toString())
            .putString("progress", p.toString()).apply()
    }

    private fun fetchFeed(rawUrl: String): Pair<PodcastShow, List<PodcastEpisode>> {
        val url = rawUrl.trim().toHttpUrlOrNull() ?: throw IOException("Enter a valid HTTPS podcast feed URL")
        PodcastNetworkPolicy.validateUrl(url)
        val request = Request.Builder().url(url).header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            .header("User-Agent", "Audify Podcast Reader/1.0").build()
        PodcastNetworkPolicy.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Podcast feed returned HTTP ${response.code}")
            val body = response.body ?: throw IOException("Podcast feed was empty")
            if (body.contentLength() > MAX_FEED_BYTES) throw IOException("Podcast feed exceeds the 5 MB limit")
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192); var total = 0
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; if (total > MAX_FEED_BYTES) throw IOException("Podcast feed exceeds the 5 MB limit")
                    output.write(buffer, 0, count)
                }
            }
            return parseFeed(url.toString(), output.toByteArray())
        }
    }

    internal fun parseFeed(feedUrl: String, bytes: ByteArray): Pair<PodcastShow, List<PodcastEpisode>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        runCatching { parser.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        runCatching { parser.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        parser.setInput(ByteArrayInputStream(bytes), null)
        var event = parser.eventType
        var rootDepth = -1
        var rootClosed = false
        var channelTitle = ""; var channelDescription: String? = null
        var channelArt: String? = null; var site: String? = null
        var inItem = false; var itemDepth = -1; var isAtom = false
        var title = ""; var description: String? = null; var guid: String? = null
        var enclosure: String? = null; var mime: String? = null; var duration: Long? = null
        var published: String? = null; var image: String? = null
        val found = mutableListOf<PodcastEpisode>()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (rootDepth == -1) rootDepth = parser.depth
                val tag = parser.name.lowercase(Locale.ROOT)
                if (tag == "feed") isAtom = true
                if ((tag == "item" || tag == "entry") && !inItem) {
                    inItem = true; itemDepth = parser.depth; title = ""; description = null; guid = null
                    enclosure = null; mime = null; duration = null; published = null; image = null
                } else if (!inItem) {
                    when (tag) {
                        "title" -> if (channelTitle.isBlank()) channelTitle = readElementText(parser).trim()
                        "description", "subtitle" -> if (channelDescription == null) channelDescription = readElementText(parser).trim()
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            val rel = parser.getAttributeValue(null, "rel")
                            if (href != null && (rel == null || rel == "alternate")) site = href
                            else if (href == null && site == null) site = readElementText(parser).trim()
                        }
                        "url" -> if (channelArt == null) channelArt = readElementText(parser).trim()
                        "logo" -> if (channelArt == null) channelArt = readElementText(parser).trim()
                        "image" -> if (parser.isEmptyElementTag.not()) { /* RSS image URL is read below. */ }
                    }
                } else {
                    when (tag) {
                        "title" -> title = readElementText(parser).trim()
                        "description", "summary", "content", "encoded" -> if (description == null) description = readElementText(parser).trim().take(12000)
                        "guid", "id" -> if (guid == null) guid = readElementText(parser).trim()
                        "pubdate", "published", "updated" -> if (published == null) published = readElementText(parser).trim()
                        "enclosure" -> {
                            enclosure = parser.getAttributeValue(null, "url")
                            mime = parser.getAttributeValue(null, "type")
                        }
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null && (parser.getAttributeValue(null, "rel") == "enclosure")) {
                                enclosure = href; mime = parser.getAttributeValue(null, "type")
                            }
                        }
                        "duration" -> duration = parseDuration(readElementText(parser))
                        "image" -> image = parser.getAttributeValue(null, "href") ?: image
                        "thumbnail" -> image = parser.getAttributeValue("http://search.yahoo.com/mrss/", "url") ?: parser.getAttributeValue(null, "url") ?: image
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.depth == rootDepth) {
                rootClosed = true
            } else if (event == XmlPullParser.END_TAG && inItem && parser.depth == itemDepth &&
                (parser.name.equals("item", true) || parser.name.equals("entry", true))) {
                val enclosureUrl = enclosure?.toHttpUrlOrNull()
                if (title.isNotBlank() && enclosureUrl != null && enclosureUrl.isHttps) {
                    runCatching { PodcastNetworkPolicy.validateUrl(enclosureUrl) }.getOrNull()?.let {
                        val episodeId = guid?.takeIf(String::isNotBlank) ?: enclosureUrl.toString()
                        val ref = PodcastEpisodeRef(
                            feedId = stableId(feedUrl), episodeId = episodeId,
                            enclosureUri = Uri.parse(enclosureUrl.toString()), mimeType = mime,
                            showTitle = channelTitle.ifBlank { "Podcast" }, episodeTitle = title,
                            description = description, artworkUri = (image ?: channelArt)?.let(Uri::parse), durationMs = duration,
                        )
                        found += PodcastEpisode(ref, published)
                    }
                }
                inItem = false
                if (found.size >= MAX_EPISODES) break
            }
            event = parser.next()
        }
        if (rootDepth == -1 || !rootClosed || inItem) {
            throw IOException("Podcast feed contains incomplete or malformed XML")
        }
        if (channelTitle.isBlank()) throw IOException("This URL is not a supported RSS 2.0 or Atom podcast feed")
        val id = stableId(feedUrl)
        val show = PodcastShow(id, feedUrl, channelTitle, channelDescription, channelArt, site, System.currentTimeMillis())
        val normalized = found.map { e -> e.copy(ref = e.ref.copy(showTitle = channelTitle, artworkUri = e.ref.artworkUri ?: channelArt?.let(Uri::parse))) }
        return show to normalized
    }

    /** Reads simple text and nested Atom XHTML without allowing markup to break the feed scan. */
    private fun readElementText(parser: XmlPullParser): String {
        val parentDepth = parser.depth
        val text = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.depth == parentDepth) break
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return text.toString()
    }
    private fun parseDuration(raw: String): Long? = runCatching {
        val parts = raw.trim().split(":").map(String::toLong)
        val seconds = when (parts.size) { 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; 2 -> parts[0] * 60 + parts[1]; 1 -> parts[0]; else -> return null }
        (seconds * 1000).takeIf { it >= 0 }
    }.getOrNull()

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    @Synchronized override fun findEpisode(feedId: String, episodeId: String): PodcastEpisodeRef? =
        episodes[feedId]?.firstOrNull { it.ref.episodeId == episodeId }?.ref

    @Synchronized override fun onPlaybackStarted(episode: PodcastEpisodeRef) {
        val previous = progress[episode.stableId]
        progress[episode.stableId] = (previous?.first ?: 0L) to false
        persistShows()
    }
    @Synchronized override fun onPlaybackProgress(episode: PodcastEpisodeRef, positionMs: Long, durationMs: Long?) {
        progress[episode.stableId] = positionMs.coerceAtLeast(0L) to (progress[episode.stableId]?.second ?: false)
        persistShows()
    }
    @Synchronized override fun onPlaybackCompleted(episode: PodcastEpisodeRef, positionMs: Long, durationMs: Long?) {
        progress[episode.stableId] = positionMs.coerceAtLeast(0L) to true
        persistShows()
    }
}
