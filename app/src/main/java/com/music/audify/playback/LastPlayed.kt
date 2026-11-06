// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import androidx.core.content.edit

import android.content.Context
import android.content.SharedPreferences
import com.music.audify.data.model.Song
import com.music.audify.data.TrackLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A bounded queue snapshot for restoring playback after process death.
 *
 * Queue contents are serialized only when Media3 reports that the playlist
 * changed. The frequently changing index and position are stored separately as
 * primitives, so progress sampling never walks or serializes the song list.
 */
object LastPlayed {

    class Snapshot(val playables: List<PlayableMedia>, val index: Int, val positionMs: Long) {
        /** Music-only compatibility projection for callers that still require Songs. */
        val songs: List<Song> get() = playables.mapNotNull { (it as? PlayableMedia.Music)?.song }
    }

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return start until (safeIndex + 1 + KEEP_AHEAD).coerceAtMost(size)
    }

    /** Persist at most 25 previous entries, the current one, and 50 upcoming entries. */
    fun saveQueue(songs: List<Song>, index: Int) {
        savePlayableQueue(songs.map(PlayableMedia::Music), index)
    }

    /** Persist the bounded physical queue without erasing media kind. */
    fun savePlayableQueue(playables: List<PlayableMedia>, index: Int) {
        if (playables.isEmpty()) {
            clear()
            return
        }
        val safeIndex = index.coerceIn(playables.indices)
        val bounds = window(playables.size, safeIndex)
        val stored = StoredQueue(
            version = SNAPSHOT_VERSION,
            tracks = playables.subList(bounds.first, bounds.last + 1).map { StoredTrack.from(it) },
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        prefs.edit {
                putString(KEY_QUEUE, encoded)
                .putInt(KEY_INDEX, safeIndex - bounds.first)
            }
    }

    /**
     * Atomically replaces the restart snapshot and waits until it reaches disk.
     * Used at an explicit queue boundary, where restoring the queue from before
     * that boundary would be worse than the small cost of a synchronous write.
     */
    fun saveQueueImmediately(songs: List<Song>, index: Int, positionMs: Long) {
        savePlayableQueueImmediately(songs.map(PlayableMedia::Music), index, positionMs)
    }

    /** Synchronous physical queue boundary, retaining typed podcast identity. */
    fun savePlayableQueueImmediately(playables: List<PlayableMedia>, index: Int, positionMs: Long) {
        if (playables.isEmpty()) {
            clearImmediately()
            return
        }
        val safeIndex = index.coerceIn(playables.indices)
        val bounds = window(playables.size, safeIndex)
        val stored = StoredQueue(
            version = SNAPSHOT_VERSION,
            tracks = playables.subList(bounds.first, bounds.last + 1).map { StoredTrack.from(it) },
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        prefs.edit(commit = true) {
                putString(KEY_QUEUE, encoded)
                .putInt(KEY_INDEX, safeIndex - bounds.first)
                .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            }
    }

    /** Update only cheap scalar state; this performs no queue conversion or JSON work. */
    fun savePlaybackState(index: Int, positionMs: Long) {
        if (!prefs.contains(KEY_QUEUE)) return
        prefs.edit {
                putInt(KEY_INDEX, index.coerceAtLeast(0))
                .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            }
    }

    /** True when a durable podcast queue is waiting for its local repository to become available. */
    fun hasPendingPodcastSnapshot(): Boolean {
        if (PodcastPlaybackBridge.repository != null) return false
        val raw = prefs.getString(KEY_QUEUE, null) ?: return false
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull() ?: return false
        return stored.tracks.any { it.kind == "podcast" }
    }

    fun load(): Snapshot? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull()
            ?: return null
        if (stored.version != LEGACY_SNAPSHOT_VERSION && stored.version != SNAPSHOT_VERSION) return null
        if (stored.tracks.isEmpty()) return null
        val requestedIndex = if (prefs.contains(KEY_INDEX)) {
            prefs.getInt(KEY_INDEX, 0)
        } else {
            stored.legacyIndex ?: 0
        }
        val positionMs = if (prefs.contains(KEY_POSITION)) {
            prefs.getLong(KEY_POSITION, 0L)
        } else {
            stored.legacyPositionMs ?: 0L
        }
        // The feed repository is installed after its local store is ready. Do
        // not turn a temporarily unavailable repository into a music-only
        // restore: leave the durable mixed snapshot untouched so the caller can
        // retry once the repository has been attached. A present repository
        // returning no matching episode still means that entry is genuinely gone.
        if (stored.tracks.any { it.kind == "podcast" } && PodcastPlaybackBridge.repository == null) {
            return null
        }
        val decoded = stored.tracks.mapIndexedNotNull { originalIndex, track ->
            track.toPlayableMedia()?.let { originalIndex to it }
        }
        val skipped = stored.tracks.size - decoded.size
        if (skipped > 0) {
            TrackLog.w("Audify", "Skipped $skipped queue item(s) that could not be restored from the local podcast catalogue")
        }
        if (decoded.isEmpty()) return null
        val selected = decoded.indexOfFirst { it.first == requestedIndex }
        val next = if (selected >= 0) selected else decoded.indexOfFirst { it.first > requestedIndex }
        val resolvedIndex = when {
            selected >= 0 -> selected
            next >= 0 -> next
            else -> decoded.lastIndex
        }
        return Snapshot(
            playables = decoded.map { it.second },
            index = resolvedIndex,
            positionMs = if (selected >= 0) positionMs.coerceAtLeast(0L) else 0L,
        )
    }

    fun clear() {
        prefs.edit {clear()}
    }

    /** Remove a stale queue before another one is installed. */
    fun clearImmediately() {
        prefs.edit(commit = true) {clear()}
    }

    @Serializable
    private data class StoredQueue(
        /** Snapshot format version; missing on legacy music-only queues. */
        val version: Int = LEGACY_SNAPSHOT_VERSION,
        val tracks: List<StoredTrack>,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val index: Int? = null,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val positionMs: Long? = null,
    ) {
        val legacyIndex: Int? get() = index
        val legacyPositionMs: Long? get() = positionMs
    }

    @Serializable
    private data class StoredTrack(
        /** Missing on legacy snapshots, which are therefore interpreted as music. */
        val kind: String = "music",
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val tier: String? = null,
        val entryId: String? = null,
        val local: String? = null,
        val path: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val explicit: Boolean? = null,
        val video: Boolean = false,
        val radio: String? = null,
        val source: String? = null,
        val sourceType: String? = null,
        val sourceId: String? = null,
        val feedId: String? = null,
        val episodeId: String? = null,
        val enclosure: String? = null,
        val mimeType: String? = null,
        val description: String? = null,
        val durationMs: Long? = null,
    ) {
        fun toSong(): Song {
            val resolvedTier = when (tier) {
                "USER_QUEUE" -> com.music.audify.data.model.QueueTier.USER_QUEUE
                "CONTEXT" -> com.music.audify.data.model.QueueTier.CONTEXT
                "AUTOPLAY" -> com.music.audify.data.model.QueueTier.AUTOPLAY
                else -> if (auto) com.music.audify.data.model.QueueTier.AUTOPLAY else com.music.audify.data.model.QueueTier.CONTEXT
            }
            return Song(
                videoId = id,
                title = title,
                artist = artist,
                thumbnailUrl = artwork,
                durationText = duration,
                albumName = album,
                isExplicit = explicit,
                isVideo = video,
                queueTier = resolvedTier,
                queueEntryId = entryId,
                radioName = radio,
                playbackSource = source,
                playbackSourceType = sourceType?.let {
                    runCatching { com.music.audify.data.model.PlaybackSourceType.valueOf(it) }.getOrNull()
                },
                playbackSourceId = sourceId,
                localUri = local,
                localPath = path,
            )
        }

        companion object {
            fun from(playable: PlayableMedia): StoredTrack = when (playable) {
                is PlayableMedia.Music -> from(playable.song)
                is PlayableMedia.Podcast -> StoredTrack(
                    kind = "podcast",
                    id = playable.stableId,
                    title = playable.title,
                    artist = playable.artist,
                    artwork = playable.artworkUri?.toString(),
                    tier = playable.queueTier.name,
                    entryId = playable.queueEntryId,
                    feedId = playable.episode.feedId,
                    episodeId = playable.episode.episodeId,
                    enclosure = playable.episode.enclosureUri.toString(),
                    mimeType = playable.episode.mimeType,
                    description = playable.episode.description,
                    durationMs = playable.episode.durationMs,
                )
            }

            fun from(song: Song) = StoredTrack(
                kind = "music",
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                tier = song.queueTier.name,
                entryId = song.queueEntryId,
                local = song.localUri,
                path = song.localPath,
                duration = song.durationText,
                album = song.albumName,
                explicit = song.isExplicit,
                video = song.isVideo,
                radio = song.radioName,
                source = song.playbackSource,
                sourceType = song.playbackSourceType?.name,
                sourceId = song.playbackSourceId,
            )
        }

        fun toPlayableMedia(): PlayableMedia? {
            return when (kind) {
                "music" -> PlayableMedia.Music(toSong())
                "podcast" -> {
                    val feed = feedId ?: return null
                    val episode = episodeId ?: return null
                    val canonical = PodcastPlaybackBridge.resolve(feed, episode) ?: return null
                    if (canonical.stableId != id) return null
                    PlayableMedia.Podcast(
                        episode = canonical,
                        queueEntryId = entryId,
                        queueTier = tier?.let {
                            runCatching { com.music.audify.data.model.QueueTier.valueOf(it) }.getOrNull()
                        } ?: com.music.audify.data.model.QueueTier.USER_QUEUE,
                    )
                }
                else -> null
            }
        }
    }

    private const val KEEP_AHEAD = 50
    private const val LEGACY_SNAPSHOT_VERSION = 0
    private const val SNAPSHOT_VERSION = 1
    private const val PREFS_NAME = "audify_last_played"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
}
