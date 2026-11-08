// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.download

import androidx.core.content.edit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.music.audify.R
import com.music.audify.data.DebugLog as Log
import androidx.core.content.ContextCompat
import com.music.audify.data.innertube.StreamResolver
import com.music.audify.data.model.Song
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.DownloadQuality
import com.music.audify.data.sources.SourceResolver
import com.music.audify.data.sources.SourceStream
import com.music.audify.data.sources.StreamFormat
import com.music.audify.data.sources.TrackMatcher
import com.music.audify.playback.AudioCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.OutputStream
import java.io.File
import java.util.Locale

/** Where a track is between "not on this device" and "on it". */
sealed interface DownloadState {

    /** Accepted, waiting for the one in front of it. */
    data object Queued : DownloadState

    /** [fraction] is 0f until the length is known, which is the first thing asked for. */
    data class Running(val fraction: Float) : DownloadState

    data class Failed(val reason: String) : DownloadState
}

/**
 * The download queue, and the record of what came out of it.
 *
 * Split deliberately into two pieces of state that look similar and behave
 * nothing alike:
 *
 *  - [active] is what is happening now — queued, running, just failed. It lives
 *    in memory, is driven by [DownloadService], and is empty on a cold start
 *    because a download interrupted by the process dying did not happen.
 *  - [saved] is what exists on disk, keyed by videoId and remembered across
 *    launches. It is the only way the app can answer "do I already have this?"
 *    without a media-store query per row, and the only way it knows *which*
 *    file a track corresponds to when asked to delete it.
 *
 * [saved] is a claim about a folder this app does not own. The user is expected
 * to manage Downloads with a file manager, so an entry here can outlive the
 * file it names — which is why every read of it goes through [savedUri], and
 * why that verifies before it answers.
 */
object Downloads {

    private const val TAG = "Audify"
    private const val KEY_SAVED_METADATA = "downloaded_tracks_metadata"
    private const val KEY_SAVED_COLLECTIONS = "downloaded_collections"
    private const val KEY_STORAGE_BUDGET_BYTES = "download_storage_budget_bytes"
    internal const val UNLIMITED_STORAGE_BUDGET = 0L
    internal val STORAGE_BUDGET_OPTIONS = listOf(
        500L * 1024 * 1024,
        1024L * 1024 * 1024,
        2L * 1024 * 1024 * 1024,
        5L * 1024 * 1024 * 1024,
        UNLIMITED_STORAGE_BUDGET,
    )

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val metadataSerializer = MapSerializer(String.serializer(), SavedSongMetadata.serializer())
    private val collectionSerializer = MapSerializer(String.serializer(), SavedCollection.serializer())

    private val _active = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val active: StateFlow<Map<String, DownloadState>> = _active.asStateFlow()

    /**
     * Ids asked for as part of a release's own download tap, by the browseId
     * that was tapped.
     *
     * [active] is one flat map for the whole app — a track queued from one
     * release is still the same row if it happens to sit in another release
     * too, and correctly so. But that means a release page can't tell "one of
     * my tracks is queued" apart from "one of my tracks is queued *because I
     * was asked for*" just by scanning [active] for its own ids: two releases
     * that happen to share a track would both read as downloading the moment
     * either one is. This is what lets a release's header ask the narrower
     * question instead — never pruned explicitly, since a stale id here is
     * harmless once it drops out of [active].
     */
    private val _requested = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val requested: StateFlow<Map<String, Set<String>>> = _requested.asStateFlow()

    /** Record that [videoIds] were asked for as [browseId]'s own release. */
    fun markRequested(browseId: String, videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        _requested.update { it + (browseId to (it[browseId].orEmpty() + videoIds)) }
    }

    private val _saved = MutableStateFlow<Map<String, String>>(emptyMap())

    /** videoId to the uri of the file saved for it. */
    val saved: StateFlow<Map<String, String>> = _saved.asStateFlow()

    private val _savedMetadata = MutableStateFlow<Map<String, SavedSongMetadata>>(emptyMap())

    private val _fileChecks = MutableStateFlow<Map<String, DownloadFileCheckStatus>>(emptyMap())
    /** Per-id file accessibility results for download management surfaces. */
    internal val fileChecks: StateFlow<Map<String, DownloadFileCheckStatus>> = _fileChecks.asStateFlow()

    private val _collections = MutableStateFlow<Map<String, SavedCollection>>(emptyMap())

    private val _storageBudgetBytes = MutableStateFlow(UNLIMITED_STORAGE_BUDGET)
    val storageBudgetBytes: StateFlow<Long> = _storageBudgetBytes.asStateFlow()

    private val _storagePrompt = MutableStateFlow<DownloadStoragePrompt?>(null)
    internal val storagePrompt: StateFlow<DownloadStoragePrompt?> = _storagePrompt.asStateFlow()
    private val storageBudgetLock = Any()
    private val storageReservations = mutableMapOf<Long, Long>()

    /**
     * The releases that were downloaded *as* releases, by the id they were asked
     * for under.
     *
     * Read by the Downloads page so a batch download reads back as the thing
     * that was tapped. Exposed rather than kept private because the page is a
     * snapshot and has to be retaken when this changes — see
     * [collectionsAmong], which is what turns it into something drawable.
     */
    val collections: StateFlow<Map<String, SavedCollection>> = _collections.asStateFlow()

    /** Waiting, in the order asked for. Guarded by [lock]. */
    private val pending = LinkedHashMap<String, Song>()

    private val lock = Any()

    /**
     * The tracks taken off the queue and not yet finished, to their unique
     * claim token and the job fetching each — null in the gap between a worker
     * claiming a track and its job existing.
     *
     * A map rather than the single slot this used to be, because several
     * downloads run at once now. That plurality is the only reason it is here:
     * [cancel] has to find *this* track's job among several, and a worker
     * claiming the next track must not be able to step on another worker's.
     * Guarded by [lock].
     */
    private val running = LinkedHashMap<String, RunningDownloadClaim>()
    /** Cancelled claims remain here until their worker has fully unwound. */
    private val retiring = LinkedHashMap<String, MutableSet<Long>>()
    private var nextClaimToken = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences("audify_settings", Context.MODE_PRIVATE)
        _saved.value = readPersistedMap(KEY_SAVED, String.serializer())
        _savedMetadata.value = readPersistedMap(KEY_SAVED_METADATA, SavedSongMetadata.serializer())
        _fileChecks.value = _savedMetadata.value.mapValues { (_, metadata) ->
            DownloadFileCheckStatus(
                state = DownloadFileCheckState.fromPersisted(metadata.fileCheckState),
                checkedAtMillis = metadata.fileCheckAtMillis,
            )
        }
        _collections.value = readPersistedMap(KEY_SAVED_COLLECTIONS, SavedCollection.serializer())
        _storageBudgetBytes.value = prefs.getLong(KEY_STORAGE_BUDGET_BYTES, UNLIMITED_STORAGE_BUDGET)
        DownloadVerificationScheduler.schedule(context.applicationContext)
    }

    private fun <T> readPersistedMap(key: String, valueSerializer: KSerializer<T>): Map<String, T> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        val decoded = decodePersistedDownloadMap(raw, valueSerializer, json)
        if (decoded.invalidRoot || decoded.invalidEntries > 0) {
            // Preserve the original before a later successful download writes a
            // clean map over it. Valid entries remain available immediately;
            // malformed records can still be recovered from this backup key.
            val backupKey = "$key.corrupt_backup"
            if (prefs.getString(backupKey, null) == null) {
                prefs.edit { putString(backupKey, raw) }
            }
            Log.w(TAG, "loaded ${decoded.values.size} valid $key entries; skipped ${decoded.invalidEntries} malformed")
        }
        return decoded.values
    }

    /** Change the opt-in cap. Zero means unlimited and is the compatibility default. */
    fun setStorageBudgetBytes(bytes: Long) {
        val safe = bytes.coerceAtLeast(0L)
        _storageBudgetBytes.value = safe
        if (::prefs.isInitialized) prefs.edit {putLong(KEY_STORAGE_BUDGET_BYTES, safe)}
        _storagePrompt.value = null
    }

    /** Cycle the fixed, user-visible budget presets. */
    fun cycleStorageBudget() {
        val index = STORAGE_BUDGET_OPTIONS.indexOf(_storageBudgetBytes.value)
        val next = STORAGE_BUDGET_OPTIONS[if (index < 0) 0 else (index + 1) % STORAGE_BUDGET_OPTIONS.size]
        setStorageBudgetBytes(next)
    }

    /**
     * Reserve exact projected bytes before a destination is created. Unknown
     * source or existing-file sizes block while the cap is enabled.
     */
    internal fun reserveStorageBudget(
        context: Context,
        song: Song,
        projectedBytes: Long?,
        claimToken: Long,
    ): Boolean =
        synchronized(storageBudgetLock) {
            val budget = _storageBudgetBytes.value
            if (budget == UNLIMITED_STORAGE_BUDGET) return@synchronized true
            if (_storagePrompt.value != null) return@synchronized false

            val grouped = _savedMetadata.value.entries.groupBy { it.value.uri }
            val files = mutableListOf<StorageBudgetFile>()
            for ((uri, aliases) in grouped) {
                val actualBytes = DownloadStore.sizeBytes(context, Uri.parse(uri))
                if (actualBytes == null) {
                    _storagePrompt.value = DownloadStoragePrompt(
                        songTitle = song.title,
                        expectedBytes = projectedBytes,
                        reason = context.getString(R.string.download_storage_actual_size_unknown),
                        plan = null,
                    )
                    return@synchronized false
                }
                val oldest = aliases.minByOrNull { it.value.dateAddedSeconds ?: 0L } ?: aliases.first()
                files += StorageBudgetFile(
                    uri = uri,
                    videoIds = aliases.map { it.key },
                    title = oldest.value.title,
                    artist = oldest.value.artist,
                    sizeBytes = actualBytes,
                    addedAtMillis = oldest.value.dateAddedSeconds?.times(1_000L)
                        ?: DownloadStore.lastModifiedMillis(context, Uri.parse(uri))
                        ?: 0L,
                )
            }
            if (projectedBytes == null || projectedBytes <= 0L) {
                _storagePrompt.value = DownloadStoragePrompt(
                    songTitle = song.title,
                    expectedBytes = null,
                    reason = context.getString(R.string.download_storage_unknown_size),
                    plan = null,
                )
                return@synchronized false
            }
            var reserved = 0L
            for (size in storageReservations.values) {
                if (Long.MAX_VALUE - reserved < size) {
                    _storagePrompt.value = DownloadStoragePrompt(
                        songTitle = song.title,
                        expectedBytes = projectedBytes,
                        reason = context.getString(R.string.download_storage_actual_size_unknown),
                        plan = null,
                    )
                    return@synchronized false
                }
                reserved += size
            }
            when (val decision = evaluateStorageBudget(files, projectedBytes, budget, reserved)) {
                StorageBudgetDecision.Allowed -> {
                    storageReservations[claimToken] = projectedBytes
                    true
                }
                StorageBudgetDecision.SizeUnknown -> {
                    _storagePrompt.value = DownloadStoragePrompt(
                        songTitle = song.title,
                        expectedBytes = projectedBytes,
                        reason = context.getString(R.string.download_storage_total_unknown),
                        plan = null,
                    )
                    false
                }
                is StorageBudgetDecision.CannotFit -> {
                    _storagePrompt.value = DownloadStoragePrompt(
                        songTitle = song.title,
                        expectedBytes = projectedBytes,
                        reason = context.getString(R.string.download_storage_no_evictable_space),
                        plan = null,
                        unknown = false,
                        files = decision.oldestFiles,
                    )
                    false
                }
                is StorageBudgetDecision.RequiresConfirmation -> {
                    _storagePrompt.value = DownloadStoragePrompt(song.title, projectedBytes, null, decision.plan)
                    false
                }
            }
        }

    internal fun releaseStorageReservation(claimToken: Long) {
        synchronized(storageBudgetLock) { storageReservations.remove(claimToken) }
    }

    /** Called only from the second, explicit confirmation dialog. */
    internal suspend fun confirmStorageDeletion(context: Context, files: List<StorageBudgetFile>): Int =
        withContext(Dispatchers.IO) {
            synchronized(storageBudgetLock) {
                val prompt = _storagePrompt.value ?: return@synchronized 0
                val projected = prompt.expectedBytes ?: return@synchronized 0
                val currentFiles = mutableListOf<StorageBudgetFile>()
                for ((uri, aliases) in _savedMetadata.value.entries.groupBy { it.value.uri }) {
                    val actualBytes = DownloadStore.sizeBytes(context, Uri.parse(uri))
                        ?: run {
                            _storagePrompt.value = DownloadStoragePrompt(
                                prompt.songTitle,
                                projected,
                                context.getString(R.string.download_storage_actual_size_unknown),
                                null,
                            )
                            return@synchronized 0
                        }
                    val oldest = aliases.minByOrNull { it.value.dateAddedSeconds ?: 0L } ?: aliases.first()
                    currentFiles += StorageBudgetFile(
                        uri = uri,
                        videoIds = aliases.map { it.key },
                        title = oldest.value.title,
                        artist = oldest.value.artist,
                        sizeBytes = actualBytes,
                        addedAtMillis = oldest.value.dateAddedSeconds?.times(1_000L)
                            ?: DownloadStore.lastModifiedMillis(context, Uri.parse(uri)) ?: 0L,
                    )
                }
                var reserved = 0L
                for (size in storageReservations.values) {
                    if (Long.MAX_VALUE - reserved < size) return@synchronized 0
                    reserved += size
                }
                val refreshed = evaluateStorageBudget(
                    currentFiles,
                    projected,
                    _storageBudgetBytes.value,
                    reserved,
                )
                if (refreshed !is StorageBudgetDecision.RequiresConfirmation) {
                    _storagePrompt.value = DownloadStoragePrompt(
                        prompt.songTitle,
                        projected,
                        context.getString(R.string.download_storage_changed_review),
                        null,
                        unknown = false,
                    )
                    return@synchronized 0
                }
                val currentSelection = refreshed.plan.oldestFiles.map { it.uri to it.sizeBytes }
                val confirmedSelection = files.map { it.uri to it.sizeBytes }
                if (currentSelection != confirmedSelection) {
                    _storagePrompt.value = DownloadStoragePrompt(
                        prompt.songTitle,
                        projected,
                        context.getString(R.string.download_storage_changed_review),
                        null,
                        unknown = false,
                    )
                    return@synchronized 0
                }

                val removedIds = mutableSetOf<String>()
                refreshed.plan.oldestFiles.forEach { file ->
                    if (DownloadStore.delete(context, Uri.parse(file.uri))) removedIds += file.videoIds
                }
                if (removedIds.isNotEmpty()) {
                    record(
                        saved = { current -> current - removedIds },
                        meta = { current -> current - removedIds },
                    )
                }
                val selectedIds = refreshed.plan.oldestFiles.sumOf { it.videoIds.size }
                if (removedIds.size == selectedIds) {
                    _storagePrompt.value = null
                } else {
                    _storagePrompt.value = DownloadStoragePrompt(
                        prompt.songTitle,
                        projected,
                        context.getString(
                            if (removedIds.isEmpty()) R.string.download_storage_delete_failed
                            else R.string.download_storage_partial_delete,
                        ),
                        null,
                        unknown = false,
                    )
                }
                removedIds.size
            }
        }

    fun dismissStoragePrompt() { _storagePrompt.value = null }

    // ---- Asking -------------------------------------------------------------

    /**
     * Queue [song], and make sure something is draining the queue.
     *
     * A track already saved, queued or running is left alone rather than
     * doubled — the menu row shows which of those it is, but a second tap
     * before the sheet updates should still be a no-op.
     *
     * The Wi-Fi-only check is here rather than only at the tap because this is
     * the one door into the queue, and a setting that can be bypassed by a
     * caller that forgot about it is not a setting. Callers that can say
     * something better than a failed row — a single toast for a whole album, say
     * — check [AppSettings.downloadsAllowedNow] themselves first; this is what
     * catches the rest.
     *
     * @param from what release this track was asked for as part of, when it was
     *   one of many. Carried no further than [DownloadSession], which is the
     *   only thing that has to say *why* forty tracks are in the queue.
     */
    fun enqueue(context: Context, song: Song, from: String? = null) {
        val id = song.videoId
        // A copy straight out of the song cache spends no data, so the Wi-Fi
        // rule has nothing to say about it.
        if (!AppSettings.downloadsAllowedNow && !servableFromCache(id)) {
            // Distinct from the duplicate-tap no-op below: nothing is in flight
            // here to leave alone, and a refusal nobody is told about reads as a
            // dead button. A download already queued or running started on a
            // connection that allowed it and is none of this check's business.
            synchronized(lock) {
                val inFlight = _active.value[id]
                if (inFlight !is DownloadState.Queued && inFlight !is DownloadState.Running) {
                    DownloadSession.queued(song, from)
                    fail(id, WIFI_ONLY_REFUSAL)
                }
            }
            return
        }
        synchronized(lock) {
            if (id in pending || id in running) return
            pending[id] = song
            _active.update { it + (id to DownloadState.Queued) }
            DownloadSession.queued(song, from)
        }

        val app = context.applicationContext
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        }.onFailure {
            // Refused only when the app has no window and no exemption, which
            // means the queue has nothing to drain it and would sit there
            // looking accepted forever.
            Log.w(TAG, "could not start the download service: ${it.message}")
            synchronized(lock) {
                if (pending.remove(id) != null) {
                    _active.update { it + (id to DownloadState.Failed("Downloads can't start right now")) }
                    DownloadSession.failed(id, "Downloads can't start right now")
                }
            }
        }
    }

    /**
     * Tracks asked for from the Cached songs folder, which are saved from the
     * cache's own complete copy when it holds one — see [AudioCache.completeCopyOf].
     *
     * Kept apart from every other download on purpose. Anywhere else the
     * listener is asking for the Download quality setting, which can be a
     * lossless file a source holds; from the folder they are asking for the
     * song they already have, and taking that copy is instant and works
     * offline. Any download still falls back to the cache when the network
     * route fails — see [prepare].
     */
    private val fromCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Marks [videoIds] as wanted from the song cache; see [fromCache]. Call before [enqueue]. */
    fun preferCachedCopy(videoIds: Collection<String>) {
        fromCache += videoIds
    }

    /**
     * Whether [videoId] was asked for from the cache and the cache can serve
     * it whole — a download that needs no network at all. Reads the cache's
     * index and the first bytes of one file.
     */
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    fun servableFromCache(videoId: String): Boolean =
        videoId in fromCache && AudioCache.completeCopyOf(videoId)?.let(::cachedRoute) != null

    /**
     * Drop [videoId] from the queue, or stop it if it is one of the ones
     * running.
     *
     * A cancelled claim moves to [retiring] until its worker has unwound. A
     * re-enqueued copy waits there rather than racing the old worker's file
     * cleanup, and the unique token prevents that worker from touching the new
     * claim's progress or completion state.
     */
    fun cancel(videoId: String) {
        val job = synchronized(lock) {
            pending.remove(videoId)
            val claimed = running.remove(videoId)
            claimed?.let { retiring.getOrPut(videoId) { mutableSetOf() }.add(it.token) }
            _active.update { it - videoId }
            DownloadSession.forget(videoId)
            claimed?.job
        }
        job?.cancel()
    }

    // ---- The record ---------------------------------------------------------

    /**
     * The file saved for [videoId], or null — pruning the record if the file
     * has been deleted from under it.
     *
     * Touches the filesystem, so call it off the main thread.
     */
    suspend fun savedUri(context: Context, videoId: String): Uri? = withContext(Dispatchers.IO) {
        val recorded = _saved.value[videoId] ?: return@withContext null
        val uri = recorded.toUri()
        if (DownloadStore.exists(context, uri)) return@withContext uri
        Log.d(TAG, "$videoId was downloaded but the file is gone; forgetting it")
        forget(videoId)
        null
    }

    /**
     * True when [uriString] names a `file://` path that is not there.
     *
     * Deliberately answers only for `file://`, and deliberately cheaply: this is
     * called from [Song.toMediaItem], which runs on the main thread once per
     * item for a whole queue. A `stat` is a few microseconds and safe at that
     * rate; the `openFileDescriptor` a `content://` uri would need is a binder
     * round trip, and three hundred of those while building a queue is a frame
     * budget gone. Stale `content://` records are left to
     * [PlaybackService.recoverFrom], which catches every scheme at the moment a
     * read actually fails and costs nothing until then.
     *
     * False for anything unparseable, which keeps "I could not tell" out of the
     * "the file is missing" answer — the caller drops a uri on a true here.
     */
    fun isMissingLocalFile(uriString: String): Boolean {
        if (!uriString.startsWith("file://")) return false
        val path = runCatching { uriString.toUri().path }.getOrNull() ?: return false
        return !File(path).exists()
    }

    /**
     * As [savedUri], but synchronous and without a [Context] parameter — for
     * [Song.toMediaItem], which builds a [MediaItem] on whatever thread that
     * happens to run on and has neither a suspend context nor a [Context] in
     * hand to reach [DownloadStore.exists] with.
     *
     * Without this, a record surviving the file it names — deleted by a file
     * manager, or a folder wiped out from under the app — sent the player a
     * `file://` uri to a path that is simply not there. Nothing downstream
     * checks that either: [AudioCache.playbackFactory] hands `file://` and
     * `content://` uris straight to [androidx.media3.datasource.FileDataSource],
     * which fails with `ERROR_CODE_IO_FILE_NOT_FOUND` — retried a handful of
     * times and then given up on, so the track just refuses to play, with
     * nothing to say why.
     *
     * Prunes the record on the way past, the same as [savedUri]: a claim that
     * has just been shown to be false is not worth keeping to be shown false
     * again on the next play.
     */
    fun verifiedSavedUri(videoId: String): String? {
        val recorded = _saved.value[videoId] ?: return null
        if (!isMissingLocalFile(recorded)) return recorded
        Log.d(TAG, "$videoId was downloaded but the file is gone; forgetting it")
        forget(videoId)
        return null
    }

    /** Delete the file saved for [videoId] and forget it. */
    suspend fun delete(context: Context, videoId: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(storageBudgetLock) {
            val uri = _saved.value[videoId]?.toUri() ?: return@synchronized false
            val deleted = DownloadStore.delete(context, uri)
            if (deleted) forget(videoId)
            deleted
        }
    }

    private fun forget(videoId: String) {
        record(saved = { it - videoId }, meta = { it - videoId })
    }

    /**
     * Drop the record for [videoId] because a read of the file it names has
     * just failed.
     *
     * The public counterpart to [forget], for [PlaybackService.recoverFrom] —
     * the one caller that does not need to check anything first, because the
     * player has already done better than a check: it opened the file and got
     * `ENOENT`. That covers the `content://` records [isMissingLocalFile]
     * deliberately declines to answer for, which is the whole reason this is
     * reachable from outside.
     *
     * Named for what it asserts rather than what it does, so a caller that has
     * *not* established the file is missing has no business calling it.
     */
    fun forgetMissing(videoId: String) {
        if (videoId !in _saved.value) return
        Log.d(TAG, "$videoId could not be opened; forgetting the download")
        forget(videoId)
    }

    // ---- Releases -----------------------------------------------------------

    /**
     * Remember that [songs] were asked for as one release rather than one at a
     * time.
     *
     * The reason this exists at all: a batch download used to be indistinguishable
     * from forty separate ones the moment it finished. What reached the Downloads
     * page was forty rows, and the only thing that could group them back up was
     * whatever album tag each row happened to carry — which an album page's rows
     * don't carry at all (the release is billed once, in the header) and a
     * playlist's rows *never* can, because a playlist is not an album and its
     * tracks are off forty different ones. So the thing the user tapped was the
     * one thing not written down anywhere.
     *
     * Recorded at the tap rather than on completion, and keyed by the id that
     * was tapped, so re-downloading the same release updates one entry instead
     * of accumulating near-duplicates. The order is the running order the page
     * had, which is what makes the entry read back as the release rather than as
     * a bag of tracks.
     *
     * Nothing here asserts the files exist. That is deliberate and matches
     * [saved]: this is a record of what was *asked* for, and which of those
     * tracks is actually on disk is answered where it is read — see
     * [collectionsAmong].
     */
    fun rememberCollection(target: DownloadTarget, songs: List<Song>) {
        if (songs.isEmpty()) return
        val ids = songs.map { it.videoId }.distinct()
        recordCollections { current ->
            val existing = current[target.id]
            val record = SavedCollection(
                id = target.id,
                title = target.title,
                subtitle = target.subtitle,
                // Once the remote cover has been cached, do not replace it with
                // the YouTube URL when the same collection is downloaded again.
                thumbnailUrl = existing?.thumbnailUrl?.takeIf(::isLocalArtwork)
                    ?: target.thumbnailUrl
                    ?: existing?.thumbnailUrl,
                playlist = target.playlist,
                // A release fetched in pages can be downloaded twice from two
                // different depths of the same page, so the two asks are merged
                // rather than the second replacing the first — but the new order
                // leads, since it is the one just seen on screen.
                videoIds = (ids + (existing?.videoIds ?: emptyList())).distinct(),
            )
            current + (target.id to record)
        }
    }

    /** Replace a collection's remote artwork URL with its durable local copy. */
    fun rememberCollectionArtwork(id: String, thumbnailUrl: String) {
        recordCollections { current ->
            val existing = current[id] ?: return@recordCollections current
            if (existing.thumbnailUrl == thumbnailUrl) current
            else current + (id to existing.copy(thumbnailUrl = thumbnailUrl))
        }
    }

    private fun isLocalArtwork(url: String): Boolean =
        url.startsWith("file:") || url.startsWith("content:")

    /** Drop a release from the record without touching the files under it. */
    fun forgetCollection(id: String) {
        if (id !in _collections.value) return
        recordCollections { it - id }
    }

    /**
     * Delete every file downloaded for release [id] and drop the record of it.
     *
     * The counterpart to [forgetCollection]: that one is for a record whose
     * files are already gone, this one is what actually takes them off the
     * device — the "delete download" a whole album or playlist card offers,
     * where a single track only ever offers [delete].
     */
    suspend fun deleteCollection(context: Context, id: String): Boolean {
        val record = _collections.value[id] ?: return false
        var any = false
        record.videoIds.forEach { videoId -> if (delete(context, videoId)) any = true }
        forgetCollection(id)
        return any
    }

    /**
     * How one downloaded playlist is addressed as a page of its own.
     *
     * Under `local:` deliberately: that prefix is how the rest of the app asks
     * "is this already on the device?", and it is what keeps the download button
     * off such a page's header and out of its menu. What it must not be taken
     * for is one of the two device *folders* — `local:downloads` and `local:all`
     * open the tabbed Songs / Artists / Albums view, and this opens a plain
     * track listing — so it gets a segment of its own rather than an id in the
     * same namespace.
     *
     * Built for playlists, which is why the word is in the prefix: a playlist's
     * tracks are off forty different releases and no tag on any of them names
     * it, so it is the one that needs a page. A downloaded album opens through
     * it as well, from its card on the Library's On Device shelf — the record
     * is the same either way — though the Albums tab still groups one up on
     * its own.
     */
    const val PLAYLIST_PREFIX = "local:playlist:"

    /** The page id for the release recorded under [id]. */
    fun pageIdFor(id: String): String = PLAYLIST_PREFIX + id

    /** The release [pageIdFor] built [browseId] from, or null if it didn't. */
    fun recordIdOf(browseId: String): String? =
        browseId.removePrefix(PLAYLIST_PREFIX).takeIf { it != browseId && it.isNotEmpty() && !it.startsWith("sp_local_") }

    /**
     * The playlists and albums downloaded whole, in name order, without their
     * tracks.
     *
     * What the Library page's On Device shelf draws a card from. Unlike
     * [collectionsAmong] there is no track list here to prune against — that
     * page never reads the folder — so this prunes against [onDisk] instead,
     * which drops a playlist once the last file recorded for it has been deleted
     * *through this app* and keeps the rest. That is the same claim [saved] makes
     * everywhere else, and opening the card is what settles it either way.
     *
     * It is also what keeps a playlist off the shelf between the tap that queues
     * it and the first track landing: [rememberCollection] writes the record at
     * the tap, and a playlist with nothing downloaded yet is not on the device.
     *
     * [onDisk] is a parameter for the same reason [collectionsAmong] takes its
     * songs: the rule is worth stating on a known folder rather than only on
     * whatever this process happens to have recorded.
     */
    fun savedReleases(onDisk: Map<String, String> = _saved.value): List<SavedCollection> {
        if (_collections.value.isEmpty()) return emptyList()
        return _collections.value.values
            .filter { record -> record.videoIds.any { it in onDisk } }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    /**
     * The releases at least one of [songs] belongs to, each with its own tracks
     * picked out of that list.
     *
     * Given the page's own songs rather than reading the disk itself, because
     * the page has already done that work — every row in it is a file that was
     * there when it was taken — and a release is only worth drawing for the
     * tracks that survived. A release whose files have all been deleted from a
     * file manager therefore disappears from the page without anything having to
     * notice it went.
     *
     * The lookup goes through [saved] as well as by id because one file answers
     * to two of them: a music video is swapped for its catalogue track on the
     * way down (see [remember]) and the page keeps whichever of the pair it
     * listed first, which is not necessarily the id the release named.
     */
    fun collectionsAmong(songs: List<Song>): List<DownloadedCollection> {
        if (songs.isEmpty() || _collections.value.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.videoId }
        val byUri = songs.mapNotNull { song -> song.localUri?.let { it to song } }.toMap()
        val uris = _saved.value
        return _collections.value.values
            .mapNotNull { record ->
                val tracks = record.videoIds
                    .mapNotNull { id -> byId[id] ?: uris[id]?.let(byUri::get) }
                    .distinctBy { it.localUri ?: it.videoId }
                if (tracks.isEmpty()) {
                    null
                } else {
                    DownloadedCollection(
                        id = record.id,
                        title = record.title,
                        subtitle = record.subtitle,
                        thumbnailUrl = record.thumbnailUrl ?: tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                        playlist = record.playlist,
                        songs = tracks,
                    )
                }
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private fun recordCollections(update: (Map<String, SavedCollection>) -> Map<String, SavedCollection>) {
        synchronized(collectionLock) {
            val map = update(_collections.value)
            _collections.value = map
            if (::prefs.isInitialized) {
                prefs.edit {
                        putString(KEY_SAVED_COLLECTIONS, json.encodeToString(collectionSerializer, map))
                    }
            }
        }
    }

    private val collectionLock = Any()

    /**
     * Record one file under every id it could be asked about.
     *
     * [asked] is the row the user tapped and [fetched] is what was actually
     * downloaded, and for a music video those are two different tracks. Filing
     * it under both is what lets the same song, found later through search,
     * still know it is already on the device. A stale id costs nothing: the
     * verification in [savedUri] prunes whichever one stops resolving.
     */
    private fun remember(
        asked: Song,
        fetched: Song,
        uri: Uri,
        downloadFormat: String? = null,
        artworkUri: String? = null,
    ) {
        val ids = setOf(asked.videoId, fetched.videoId)
        // MediaStore supplies this for exported downloads, but app-private and
        // legacy file:// downloads never pass through that index. Keep the
        // creation time in our own record so "Date added" remains stable if a
        // file is subsequently retagged or otherwise modified. An adopted file
        // predating this field falls back to its last-modified time.
        val fileModifiedMillis = uri.takeIf { it.scheme == "file" }
            ?.path
            ?.let(::File)
            ?.lastModified()
            ?.takeIf { it > 0 }
        val existingAdded = ids.firstNotNullOfOrNull { _savedMetadata.value[it]?.dateAddedSeconds }
        val dateAddedSeconds = resolvedDownloadDates(existingAdded, fileModifiedMillis).first
            ?: System.currentTimeMillis() / 1_000
        // HLS packages cannot carry MP4 tags. Point the app's own metadata at
        // the cover saved beside their playlist so Downloads remains fully
        // offline even though another player cannot open that package.
        val savedArtwork = uri.takeIf { it.scheme == "file" && it.lastPathSegment == "playlist.m3u8" }
            ?.path?.let(::File)?.parentFile
            ?.listFiles()?.firstOrNull { it.nameWithoutExtension == "cover" }
            ?.let(Uri::fromFile)?.toString()
        // Either row may be the one that knew the release: a music video is
        // swapped for the catalogue track before this, and it is the catalogue
        // row that usually carries the album — but a search hit tapped directly
        // is both, and an album page's rows are neither.
        val album = fetched.albumName?.takeIf { it.isNotBlank() }
            ?: asked.albumName?.takeIf { it.isNotBlank() }
        val metaAsked = SavedSongMetadata(
            videoId = asked.videoId,
            title = asked.title,
            artist = asked.artist,
            thumbnailUrl = savedArtwork ?: artworkUri ?: asked.thumbnailUrl,
            durationText = asked.durationText,
            albumName = album,
            uri = uri.toString(),
            downloadFormat = downloadFormat,
            dateAddedSeconds = dateAddedSeconds,
            fileCheckState = DownloadFileCheckState.UNCHECKED.persistedValue,
            isExplicit = asked.isExplicit,
        )
        val metaFetched = SavedSongMetadata(
            videoId = fetched.videoId,
            title = fetched.title,
            artist = fetched.artist,
            thumbnailUrl = savedArtwork ?: artworkUri ?: fetched.thumbnailUrl,
            durationText = fetched.durationText,
            albumName = album,
            uri = uri.toString(),
            downloadFormat = downloadFormat,
            dateAddedSeconds = dateAddedSeconds,
            fileCheckState = DownloadFileCheckState.UNCHECKED.persistedValue,
            isExplicit = fetched.isExplicit,
        )
        record(
            saved = { it + ids.associateWith { id -> uri.toString() } },
            meta = {
                it + mapOf(asked.videoId to metaAsked, fetched.videoId to metaFetched)
            },
        )
    }

    /**
     * Apply [saved] and [meta] to the two records and write the result down.
     *
     * Takes transforms rather than finished maps because several downloads
     * finish at once now, and "read the map, add my track, store it back" run
     * from two threads loses one of the two tracks — silently, and permanently,
     * since this is the only record that a file was written. Both flows are
     * updated compare-and-set, and the persist is serialised so the copy that
     * reaches disk is never older than one already written.
     */
    private fun record(
        saved: (Map<String, String>) -> Map<String, String>,
        meta: (Map<String, SavedSongMetadata>) -> Map<String, SavedSongMetadata>,
    ) {
        val savedMap = _saved.updateAndGet(saved)
        val metaMap = _savedMetadata.updateAndGet(meta)
        _fileChecks.value = metaMap.mapValues { (_, metadata) ->
            DownloadFileCheckStatus(
                state = DownloadFileCheckState.fromPersisted(metadata.fileCheckState),
                checkedAtMillis = metadata.fileCheckAtMillis,
            )
        }
        if (!::prefs.isInitialized) return
        synchronized(recordLock) {
            prefs.edit {
                    putString(KEY_SAVED, json.encodeToString(serializer, savedMap))
                    .putString(KEY_SAVED_METADATA, json.encodeToString(metadataSerializer, metaMap))
                }
        }
    }

    private val recordLock = Any()

    /** A bounded, URI-deduplicated batch for background file checks. */
    internal fun fileCheckCandidates(limit: Int): List<DownloadFileCheckCandidate> =
        selectDownloadFileCheckBatch(
            _savedMetadata.value.values
                .groupBy { it.uri }
                .map { (uri, aliases) ->
                    val oldest = aliases.minByOrNull { it.fileCheckAtMillis ?: 0L } ?: aliases.first()
                    DownloadFileCheckCandidate(
                        uri = uri,
                        state = if (aliases.any { it.fileCheckState == DownloadFileCheckState.UNCHECKED.persistedValue }) {
                            DownloadFileCheckState.UNCHECKED
                        } else {
                            DownloadFileCheckState.fromPersisted(oldest.fileCheckState)
                        },
                        checkedAtMillis = aliases.mapNotNull { it.fileCheckAtMillis }.minOrNull(),
                    )
                },
            limit,
        )

    /** Persist one result against every id alias for the same physical file. */
    internal fun recordFileCheck(uri: String, available: Boolean, checkedAtMillis: Long) {
        val state = if (available) DownloadFileCheckState.AVAILABLE else DownloadFileCheckState.UNAVAILABLE
        record(
            saved = { it },
            meta = { current ->
                current.mapValues { (_, metadata) ->
                    if (metadata.uri == uri) metadata.copy(
                        fileCheckState = state.persistedValue,
                        fileCheckAtMillis = checkedAtMillis,
                    ) else metadata
                }
            },
        )
    }

    /** Returns all downloaded songs whose files still exist on disk. */
    suspend fun getDownloadedSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val metaMap = _savedMetadata.value
        val result = mutableListOf<Song>()
        val seenUris = mutableSetOf<String>()
        val migratedAddedByUri = mutableMapOf<String, Long>()
        val migratedFormatByUri = mutableMapOf<String, String>()

        for ((_, meta) in metaMap) {
            // A downloaded music video and the catalogue track it resolved to
            // are deliberately recorded as two ids for one file. Verify and
            // materialise that file once, not once per alias.
            if (!seenUris.add(meta.uri)) continue
            val uri = meta.uri.toUri()
            if (DownloadStore.exists(context, uri)) {
                val fileModifiedMillis = uri.takeIf { it.scheme == "file" }
                    ?.path
                    ?.let(::File)
                    ?.lastModified()
                    ?.takeIf { it > 0 }
                val (dateAddedSeconds, dateModifiedSeconds) = resolvedDownloadDates(
                    persistedAddedSeconds = meta.dateAddedSeconds,
                    fileModifiedMillis = fileModifiedMillis,
                )
                if (meta.dateAddedSeconds == null && dateAddedSeconds != null) {
                    migratedAddedByUri[meta.uri] = dateAddedSeconds
                }
                val downloadFormat = meta.downloadFormat
                    ?: legacyYoutubeDownloadBadge(context, uri)?.also {
                        migratedFormatByUri[meta.uri] = it
                    }
                result.add(
                    Song(
                        videoId = meta.videoId,
                        title = meta.title,
                        artist = meta.artist,
                        thumbnailUrl = meta.thumbnailUrl,
                        durationText = meta.durationText,
                        albumName = meta.albumName,
                        localUri = meta.uri,
                        downloadFormat = downloadFormat,
                        isExplicit = meta.isExplicit,
                        localDateAddedSeconds = dateAddedSeconds,
                        localDateModifiedSeconds = dateModifiedSeconds,
                    )
                )
            } else {
                // Clear every id for the shared file. Leaving its second alias
                // behind would make a later menu claim the missing download
                // still exists.
                metaMap.filterValues { it.uri == meta.uri }.keys.forEach(::forget)
            }
        }
        if (migratedAddedByUri.isNotEmpty() || migratedFormatByUri.isNotEmpty()) {
            record(
                saved = { it },
                meta = { current ->
                    current.mapValues { (_, saved) ->
                        saved.copy(
                            dateAddedSeconds = saved.dateAddedSeconds
                                ?: migratedAddedByUri[saved.uri],
                            downloadFormat = saved.downloadFormat
                                ?: migratedFormatByUri[saved.uri],
                        )
                    }
                },
            )
        }
        result
    }

    /**
     * Returns the available files for one downloaded collection, in its saved
     * running order.
     *
     * Opening one downloaded playlist used to call [getDownloadedSongs] and
     * then build every saved collection merely to discard all but this one.
     * That makes the cost of opening a ten-track playlist proportional to the
     * listener's entire Downloads folder, including a ContentResolver open for
     * every recorded id.  The collection record already says exactly which
     * files can belong here, so only those files need to be verified.
     */
    suspend fun getCollectionSongs(context: Context, collectionId: String): List<Song> =
        withContext(Dispatchers.IO) {
            val record = _collections.value[collectionId] ?: return@withContext emptyList()
            val metadata = _savedMetadata.value
            val saved = _saved.value
            val metadataByUri = metadata.values.associateBy { it.uri }
            val seenUris = HashSet<String>()

            record.videoIds.mapNotNull { videoId ->
                val uriString = saved[videoId] ?: metadata[videoId]?.uri ?: return@mapNotNull null
                if (!seenUris.add(uriString)) return@mapNotNull null
                val uri = uriString.toUri()
                if (!DownloadStore.exists(context, uri)) {
                    // Both a video id and its catalogue replacement can name
                    // this file. Prune every alias now that the one disk check
                    // has established that the shared file is gone.
                    saved.filterValues { it == uriString }.keys.forEach(::forget)
                    return@mapNotNull null
                }

                val meta = metadata[videoId] ?: metadataByUri[uriString] ?: return@mapNotNull null
                val fileModifiedMillis = uri.takeIf { it.scheme == "file" }
                    ?.path
                    ?.let(::File)
                    ?.lastModified()
                    ?.takeIf { it > 0 }
                val (dateAddedSeconds, dateModifiedSeconds) = resolvedDownloadDates(
                    persistedAddedSeconds = meta.dateAddedSeconds,
                    fileModifiedMillis = fileModifiedMillis,
                )
                Song(
                    videoId = videoId,
                    title = meta.title,
                    artist = meta.artist,
                    thumbnailUrl = meta.thumbnailUrl,
                    durationText = meta.durationText,
                    albumName = meta.albumName,
                    localUri = uriString,
                    downloadFormat = meta.downloadFormat ?: legacyYoutubeDownloadBadge(context, uri),
                    isExplicit = meta.isExplicit,
                    localDateAddedSeconds = dateAddedSeconds,
                    localDateModifiedSeconds = dateModifiedSeconds,
                )
            }
        }

    private fun String.toUri(): Uri = Uri.parse(this)

    // ---- Driven by DownloadService -----------------------------------------

    /**
     * The next track to fetch, or null when the queue is empty.
     *
     * Claims it as running under the same lock that removed it, so there is no
     * instant where a track is in neither the queue nor the running slot and a
     * [cancel] for it would quietly do nothing.
     */
    internal fun takeNext(): DownloadClaim? = synchronized(lock) {
        val entry = pending.entries.firstOrNull { canStartDownloadClaim(it.key, retiring) } ?: return null
        pending.remove(entry.key)
        val token = ++nextClaimToken
        running[entry.key] = RunningDownloadClaim(token)
        DownloadClaim(entry.value, token)
    }

    /** Attach the job only to the exact queue claim that created it. */
    internal fun onRunning(videoId: String, claimToken: Long, job: Job) {
        val attached = synchronized(lock) {
            attachRunningJobIfClaimMatches(running, videoId, claimToken, job)
        }
        if (!attached) job.cancel()
    }

    /** [videoId] is finished, one way or another, and no longer holds a worker. */
    internal fun onIdle(videoId: String, claimToken: Long) {
        synchronized(lock) {
            removeRunningClaimIfMatches(running, videoId, claimToken)
            removeRetiringDownloadClaim(retiring, videoId, claimToken)
        }
    }

    /** Whether anything is still queued or in flight — see [DownloadService]'s workers. */
    internal fun busy(): Boolean = synchronized(lock) {
        pending.isNotEmpty() || running.isNotEmpty() || retiring.isNotEmpty()
    }

    /**
     * The service is gone, so nothing is running any more.
     *
     * Distinct from [onIdle], which is one worker reporting one finished track.
     * This is the whole drain going away at once — active claims are void, and
     * leaving one behind would have [enqueue] refuse that track forever as
     * already in flight. Pending requests are retained for a replacement
     * service instance to drain.
     */
    internal fun onStopped() {
        synchronized(lock) {
            val abandoned = (running.keys + retiring.keys).distinct()
            running.clear()
            retiring.clear()
            abandoned.forEach { id ->
                _active.update { it - id }
                DownloadSession.forget(id)
            }
        }
    }

    /**
     * Fetch one track, start to finish.
     *
     * Two halves, and the split is what lets a queue go at any speed: [prepare]
     * decides where the bytes come from and [transfer] moves them. Everything
     * that can go wrong past the point of reserving a destination has to
     * unreserve it — a cancelled or failed download must not leave a partial
     * file behind pretending to be a whole one, which is what
     * [DownloadStore.Pending] exists to make hard to get wrong.
     *
     * Pinned to [Dispatchers.IO] here rather than trusted to arrive on it.
     * Resolving a stream blocks on HTTP and runs YouTube's player JavaScript
     * through Rhino, and [DownloadService] drives this from a main-thread scope
     * so its notification work stays where it belongs — inheriting that would
     * put every network call in the resolve on the main thread, where they
     * don't fail loudly so much as fail *uniformly*: `NetworkOnMainThreadException`
     * is caught by the same per-client `runCatching` that exists to tolerate a
     * client being turned away, so every client appears to be refused and the
     * whole thing reads as a network outage.
     *
     * Several of these run at once — see [DownloadService]. Nothing in here is
     * shared between them but the two state flows, and both are written through
     * atomic updates for exactly that reason.
     */
    internal suspend fun run(context: Context, claim: DownloadClaim) = withContext(Dispatchers.IO) {
        val song = claim.song
        val id = song.videoId
        val claimToken = claim.token
        ensureCurrentClaim(id, claimToken)
        // Set before the lookup, not after it. Resolving where a lossless track
        // comes from is the long part of a download, and leaving the row on
        // "Queued" for all of it reads as a queue that has stopped rather than
        // one that is working.
        if (!updateRunningIfCurrent(id, claimToken, 0f)) ensureCurrentClaim(id, claimToken)

        try {
            val plan = prepare(context, song)
            ensureCurrentClaim(id, claimToken)
            // The manager is showing the row that was tapped, which for a music
            // video is the wrong title and the wrong cover for the file actually
            // being written. Corrected here rather than left to disagree with
            // the notification and with the Downloads page afterwards.
            if (!retitleIfCurrent(id, claimToken, plan.track)) ensureCurrentClaim(id, claimToken)
            transfer(context, song, plan, claimToken)
        } catch (e: CancellationException) {
            clearIfCurrent(id, claimToken)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed for $id: ${e.message}", e)
            failIfCurrent(id, claimToken, e.friendly())
        }
    }

    /**
     * Everything that has to be known before a byte can be asked for, and
     * nothing that touches the destination.
     *
     * Split out of [run] so it *can* be done ahead of time. Nothing does yet —
     * [DownloadService] still calls it inline, and the overlap that used to be
     * the point of the split is bought by running four workers instead. Kept
     * apart anyway, because the split is what makes the expensive half
     * separately measurable and separately cancellable.
     *
     * On a lossless queue this is the expensive half by a wide margin: a module
     * search fans out across a whole index and then a stream endpoint is
     * opened, tens of seconds against the few the transfer itself takes on a
     * fast connection. Run between transfers, as it used to be, that time was
     * simply the connection standing idle once per track, which is what a long
     * queue spent most of its life doing.
     *
     * Nothing here writes to [_active] or to [DownloadSession]. It may be
     * running for a track that is still queued — or for one that gets cancelled
     * before its turn — and a preparation is not a download.
     */
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    internal suspend fun prepare(context: Context, song: Song): Prepared = withContext(Dispatchers.IO) {
        // Downloads preserve the exact item the listener picked. Catalogue
        // matching is a manual playback action and must not silently change a
        // download or its filename.
        val track = song
        // Read once, here, for the whole of this track. Both routes below
        // and the re-resolve inside [Downloader.fetch] have to agree on
        // which rung they are fetching, and re-reading the setting per call
        // would let a change made mid-download splice two renditions into
        // one file.
        val quality = AppSettings.downloadQuality.value

        // Asked before the lookup rather than after it, unlike the check on the
        // route's own filename below. A file already sitting in Music under a
        // lossless extension is the answer to the whole question, and spending
        // a twenty-second module search to arrive at a name we could have
        // guessed is the difference between re-running a 300-track queue in
        // seconds and re-running it in hours. Only the extensions that can only
        // be lossless are worth guessing at: an `.m4a` may be this app's ALAC
        // or its AAC, and adopting the wrong one would quietly answer a request
        // for lossless with a transcode.
        if (quality.keepsLossless) {
            LOSSLESS_EXTENSIONS.firstNotNullOfOrNull { extension ->
                DownloadStore.existing(context, DownloadStore.fileNameFor(track, extension))
            }?.let { uri ->
                return@withContext Prepared(song.videoId, track, route = null, alreadyAt = uri)
            }
        }

        // Asked for from the Cached songs folder: the copy already on disk is
        // the answer, if it is whole. See [fromCache].
        if (song.videoId in fromCache) {
            AudioCache.completeCopyOf(song.videoId)?.let(::cachedRoute)?.let { route ->
                Log.d(TAG, "downloading ${song.videoId} from the song cache as .${route.extension}")
                return@withContext Prepared(song.videoId, track, route = route, alreadyAt = null)
            }
        }
        val route = try {
            routeFor(context, track, quality)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No connection, or nothing would resolve. A complete copy in the
            // song cache is still the song, and better saved than refused.
            AudioCache.completeCopyOf(song.videoId)?.let(::cachedRoute)
                ?.also { Log.w(TAG, "${song.videoId} could not be fetched (${e.message}); saving the cached copy") }
                ?: throw e
        }
        Log.d(TAG, "downloading ${song.videoId} as .${route.extension} (${route.describe}, ${quality.label})")
        Prepared(song.videoId, track, route = route, alreadyAt = null)
    }

    /**
     * What [prepare] worked out, ready for a transfer to be run against it.
     *
     * [videoId] rides along so a look-ahead can be checked against the track
     * actually taken off the queue: the two diverge whenever something is
     * cancelled while its route is being resolved, and a plan applied to the
     * wrong track would write one song's bytes under another's name.
     */
    internal class Prepared(
        val videoId: String,
        /** The catalogue track behind the row, which may not be the row. */
        val track: Song,
        /** Null when [alreadyAt] answered the question instead. */
        val route: Route?,
        /** A file already in Music that is this download, if there is one. */
        val alreadyAt: Uri?,
    )

    /**
     * Fetch the bytes [plan] points at and publish them.
     *
     * Owns the destination from end to end: every exit out of here either
     * commits or aborts, so a caller is free to call it a second time with a
     * freshly resolved plan without the first attempt leaving anything behind.
     */
    private suspend fun transfer(context: Context, song: Song, plan: Prepared, claimToken: Long) {
        val id = song.videoId
        val track = plan.track
        ensureCurrentClaim(id, claimToken)

        // Already there from a previous run the record lost track of — adopt it
        // rather than writing a second copy beside it.
        plan.alreadyAt?.let { uri ->
            completeIfCurrent(id, claimToken) { remember(song, track, uri) }
            return
        }
        val route = plan.route ?: error("Nothing to download")

        val name = DownloadStore.fileNameFor(track, route.extension)
        DownloadStore.existing(context, name)?.let { alreadyThere ->
            completeIfCurrent(id, claimToken) { remember(song, track, alreadyThere) }
            return
        }

        if (!reserveStorageBudget(context, song, route.expectedSizeBytes, claimToken)) {
            val reason = _storagePrompt.value?.reason
                ?: context.getString(R.string.download_storage_budget_reached)
            error(reason)
        }
        var pending: DownloadStore.Pending? = null
        var lyrics: Deferred<LyricsTag.Embeddable?>? = null
        var artwork: Deferred<MediaTagger.Artwork?>? = null
        try {
            coroutineScope {
                // Started before the transfer rather than after it, so four lyric
                // services are being raced while the bytes are already moving. Done
                // after the commit instead, every download would pay the slowest of
                // them in dead time — and it is a *suspending* wait, so it would sit
                // in the one stretch of this function that has no way back: past the
                // commit, [pending] is null and a cancellation there would abandon a
                // finished file that nothing has recorded yet. Awaited below while
                // there is still a pending destination to abort.
                //
                // [LyricsTag.forTrack] is contracted not to throw for anything but
                // cancellation, and that contract is load-bearing here: this is a
                // plain child of the scope, so a failure inside it would cancel the
                // download it was only meant to decorate.
                // HLS has no tag container, but its private offline package has
                // sidecars for exactly the same lyrics and full-resolution cover.
                if (route.taggable && (route.offlineHls != null || MediaTagger.carriesTags(route.extension))) {
                    lyrics = async { LyricsTag.forTrack(track) }
                    artwork = async { MediaTagger.artworkFor(context, track) }
                }

                val alreadyThere = DownloadStore.existing(context, name)
                if (alreadyThere != null) {
                    Log.d(TAG, "$name is already in Music; adopting it")
                    synchronized(storageBudgetLock) {
                        completeIfCurrent(id, claimToken) { remember(song, track, alreadyThere) }
                        storageReservations.remove(claimToken)
                    }
                    return@coroutineScope
                }

                val manifest = route.offlineHls
                if (manifest != null) {
                    val onSegment: (Long, Long) -> Unit = { written, total ->
                        val fraction = written.toFloat() / total
                        updateRunningIfCurrent(id, claimToken, fraction)
                    }
                    val savedUri = if (manifest.dash) {
                        OfflineDash.save(
                            context = context,
                            id = id,
                            url = manifest.url,
                            headers = manifest.headers,
                            onProgress = onSegment,
                            lyrics = lyrics?.await(),
                            artwork = artwork?.await(),
                        )
                    } else {
                        OfflineHls.save(
                            context = context,
                            id = id,
                            url = manifest.url,
                            headers = manifest.headers,
                            onProgress = onSegment,
                            lyrics = lyrics?.await(),
                            artwork = artwork?.await(),
                        )
                    }
                    val recorded = completeIfCurrent(id, claimToken) {
                        remember(song, track, savedUri, route.downloadFormat)
                    }
                    if (!recorded) DownloadStore.delete(context, savedUri)
                    synchronized(storageBudgetLock) {
                        storageReservations.remove(claimToken)
                    }
                    Log.d(TAG, "saved offline ${if (manifest.dash) "DASH" else "HLS"} package for $name")
                    return@coroutineScope
                }

                val destination = DownloadStore.begin(context, name, route.mimeType)
                pending = destination
                destination.openStream().use { sink ->
                    route.write(sink) { written, total ->
                        val fraction = written.toFloat() / total
                        updateRunningIfCurrent(id, claimToken, fraction)
                    }
                }
                val words = lyrics?.await()
                val cover = artwork?.await()
                // Publish only after metadata is part of the file. This keeps
                // concurrent album workers from exposing untagged tracks.
                MediaTagger.embed(context, destination.tagUri, track, route.extension, words, cover)
                val artworkUri = MediaTagger.persistArtwork(context, track.thumbnailUrl, cover)
                val savedUri = destination.commit()
                pending = null
                val recorded = completeIfCurrent(id, claimToken) {
                    remember(song, track, savedUri, route.downloadFormat, artworkUri)
                }
                if (!recorded) DownloadStore.delete(context, savedUri)
                synchronized(storageBudgetLock) {
                    storageReservations.remove(claimToken)
                }
                Log.d(TAG, "saved $name")
            }
        } catch (e: Throwable) {
            pending?.abort()
            throw e
        } finally {
            // Every exit needs this, not just the failing ones: the adopt-it
            // path above returns with the lookup still in flight, and
            // [coroutineScope] does not return while a child of it is running —
            // so an unwaited job would hold the whole queue up for the length of
            // a lyrics search per already-downloaded track.
            lyrics?.cancel()
            artwork?.cancel()
            releaseStorageReservation(claimToken)
        }
    }

    /**
     * One resolved download: what to call the file, what to tell the store it
     * is, and how to fill it.
     *
     * Exists so [run] has one linear body rather than two nearly-identical
     * ones. Everything after the bytes are chosen — the duplicate check, the
     * pending row, the commit, the tagging, the abort on failure — is the same
     * work whichever server the audio came from, and the two routes differ only
     * in these four answers.
     */
    internal class Route(
        val extension: String,
        val mimeType: String,
        /** For the log line, so a download's provenance is on the record. */
        val describe: String,
        /** Short premium-rendition badge shown only in Audify's Downloads list. */
        val downloadFormat: String? = null,
        /** Exact projected bytes when the resolved source exposes a reliable length. */
        val expectedSizeBytes: Long? = null,
        val taggable: Boolean = true,
        val offlineHls: Manifest? = null,
        val write: suspend (OutputStream, (written: Long, total: Long) -> Unit) -> Unit,
    )

    /**
     * A stream that arrived as an index rather than as audio, and which of the
     * two index formats it is. Both are saved into the same offline package —
     * see [OfflineDash] — so [dash] only decides who does the parsing.
     */
    internal class Manifest(
        val url: String,
        val headers: Map<String, String>,
        val dash: Boolean = false,
    )

    /**
     * Where this download's bytes are coming from.
     *
     * A configured source gets asked first, and YouTube is what happens when
     * none of them can serve it — see [SourceResolver.forDownload] for what
     * "can" means, which is narrower here than it is for playback.
     *
     * @param quality read once by the caller and passed down, so that a setting
     *   changed while this track is in the queue applies to the next one rather
     *   than to the middle of this one. [Downloader.fetch] resolves again after
     *   a mid-download refusal and has to ask for the same rung it started on.
     */
    private suspend fun routeFor(context: Context, track: Song, quality: DownloadQuality): Route {
        fromSources(track, quality)?.let { (stream, storable) ->
            // A manifest is an index, not audio. Whichever kind it is, fetching
            // it as a file writes the index into something named `.flac` —
            // which is exactly what a `.mpd` did until [OfflineDash] existed:
            // a 2.7 KB download that reported success and could never play.
            // Read off the URL rather than off `stream.format`, which describes
            // the audio inside and says nothing about the envelope.
            val dash = OfflineDash.handles(stream.url)
            val hls = stream.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            val packaged = hls || dash
            // A manifest's segments are joined back into one ordinary file —
            // see [ManifestFile] — exported or not, so a lossless download is
            // a `.flac` wherever it lands. Exports used to decline manifests
            // instead, which meant YouTube: Tidal serves every lossless tier
            // as a manifest, so an exporting user heard FLAC and saved 131kbps
            // AAC. Only a codec the assembler can't file falls through to the
            // package (in-app) or YouTube (export) below.
            if (packaged && storable.extension in ManifestFile.EXTENSIONS) {
                return Route(
                    extension = storable.extension,
                    mimeType = storable.mimeType,
                    describe = "${stream.format.summary} (assembled from ${if (dash) "DASH" else "HLS"})",
                    downloadFormat = stream.format.downloadBadge(),
                    write = { sink, onProgress ->
                        ManifestFile.write(
                            context.cacheDir, stream.url, stream.headers, dash,
                            storable.extension, sink, onProgress,
                        )
                    },
                )
            }
            if (packaged && AppSettings.exportDownloads.value) {
                Log.d(TAG, "can't export a .${storable.extension} manifest; taking YouTube for ${track.videoId}")
                return@let
            }
            return Route(
                // DASH is saved *as* HLS — see [OfflineDash] for why — so both
                // kinds land as the same package and are named for what was
                // written rather than for what was fetched.
                extension = if (packaged) "m3u8" else storable.extension,
                mimeType = if (packaged) "application/vnd.apple.mpegurl" else storable.mimeType,
                describe = stream.format.summary,
                downloadFormat = stream.format.downloadBadge(),
                // SourceStream has no content-length contract. Do not infer a
                // byte count from duration, bitrate or codec; the budget gate
                // will block this route as size-unknown when enabled.
                taggable = true,
                offlineHls = Manifest(stream.url, stream.headers, dash = dash).takeIf { packaged },
                write = { sink, onProgress ->
                    Downloader.fetchDirect(stream.url, stream.headers, sink, onProgress)
                },
            )
        }
        // MediaStore.Audio refuses audio/webm on affected Android versions.
        // Keep Opus for app-private downloads, but pin exported downloads to
        // AAC-in-MP4 for both the initial resolve and any mid-transfer retry.
        val requireM4a = AppSettings.exportDownloads.value
        val stream = StreamResolver.resolveForDownload(track.videoId, quality.maxKbps, requireM4a)
        return Route(
            extension = stream.downloadExtension,
            mimeType = stream.downloadMimeType,
            describe = "${stream.kbps}kbps ${stream.mimeType}",
            downloadFormat = youtubeDownloadBadge(stream.downloadExtension, stream.kbps),
            expectedSizeBytes = Downloader.measureContentLength(stream.url),
            write = { sink, onProgress ->
                Downloader.fetch(track.videoId, stream, quality.maxKbps, requireM4a, sink, onProgress)
            },
        )
    }

    /**
     * A route that copies [copy] out of the song cache instead of fetching
     * anything, or null when it cannot be filed where downloads go: exported
     * downloads are pinned away from WebM for the same MediaStore reason
     * [routeFor] gives.
     */
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun cachedRoute(copy: AudioCache.CompleteCopy): Route? {
        if (copy.extension == "webm" && AppSettings.exportDownloads.value) return null
        return Route(
            extension = copy.extension,
            mimeType = copy.mimeType,
            describe = "song cache, ${copy.length} bytes",
            downloadFormat = if (copy.extension == "flac") "FLAC" else null,
            expectedSizeBytes = copy.length,
            write = { sink, onProgress -> AudioCache.copyOut(copy, sink, onProgress) },
        )
    }

    /**
     * The stream to keep for [track] from a configured source, with how to file
     * it — or null, which is not a failure, just YouTube's turn.
     *
     * Usually a bit-exact one; not always. [SourceResolver.forDownload] falls
     * back to the best lossy copy any enabled source holds when nothing has the
     * recording losslessly, and only gives up on the sources entirely when what
     * they offer would not beat YouTube's own AAC. Which of those happened is
     * the resolver's business — from here it is a URL and a codec either way.
     *
     * Bounded, because a module search waits on every backend it has (see
     * `ModuleSource.SEARCH_PATIENT_MS`) and does that once per query the matcher
     * offers. For a track no module holds, that is the whole queue stopped for
     * the better part of a minute on the way to a download that was always
     * going to be YouTube's. `PlaybackService.SUBSTITUTE_TIMEOUT_MS` bounds the
     * same search for the same reason.
     *
     * The [DownloadStore.storable] check belongs here rather than inside the
     * resolver: the resolver's job is finding the best audio, and whether this
     * device will keep a file of that codec is a question about Android.
     *
     * @param quality pinned by [run] for the whole of this track. Passed on to
     *   the resolver rather than left to it, so that the twenty seconds this may
     *   spend searching cannot be a window in which the setting changes and the
     *   two halves of one decision disagree.
     */
    private suspend fun fromSources(
        track: Song,
        quality: DownloadQuality,
    ): Pair<SourceStream, DownloadStore.Storable>? {
        // Wrapped so the two ways this can come back empty stay apart in the
        // log. `withTimeoutOrNull` collapses them into one null, and the
        // difference is the whole diagnosis: "no enabled source holds this
        // recording" is a fact about the track, and "the clock ran out" is a
        // fact about [SOURCE_LOOKUP_MS] — which is what silently sent a whole
        // Lossless queue to YouTube's AAC while the log said nothing at all.
        var timedOut = true
        val stream = withTimeoutOrNull(SOURCE_LOOKUP_MS) {
            try {
                SourceResolver.forDownload(
                    TrackMatcher.targetOf(track),
                    SourceResolver.requestForDownload(quality),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "source lookup failed for ${track.videoId}: ${e.message}")
                null
            }.also { timedOut = false }
        }
        if (stream == null) {
            if (timedOut) {
                Log.w(
                    TAG,
                    "source lookup for ${track.videoId} ran past ${SOURCE_LOOKUP_MS}ms; " +
                        "downloading it from YouTube instead of ${quality.label}",
                )
            }
            return null
        }

        val storable = DownloadStore.storable(stream.format.codec)
        if (storable == null) {
            Log.d(TAG, "nothing to file a '${stream.format.codec}' as; taking YouTube for ${track.videoId}")
            return null
        }
        return stream to storable
    }

    /** Back to "not downloaded" — used for success, where [saved] takes over, and for cancellation. */
    private fun clear(videoId: String) {
        _active.update { it - videoId }
    }

    private fun ensureCurrentClaim(videoId: String, claimToken: Long) {
        if (!synchronized(lock) { running[videoId]?.token == claimToken }) {
            throw CancellationException("Download claim for $videoId is no longer active")
        }
    }

    private fun updateRunningIfCurrent(videoId: String, claimToken: Long, fraction: Float): Boolean =
        synchronized(lock) {
            if (running[videoId]?.token != claimToken) return@synchronized false
            _active.update { it + (videoId to DownloadState.Running(fraction)) }
            DownloadSession.running(videoId, fraction)
            true
        }

    private fun retitleIfCurrent(videoId: String, claimToken: Long, song: Song): Boolean =
        synchronized(lock) {
            if (running[videoId]?.token != claimToken) return@synchronized false
            DownloadSession.retitle(videoId, song)
            true
        }

    /** The saved file and its session state commit together against the live claim. */
    private fun completeIfCurrent(videoId: String, claimToken: Long, save: () -> Unit): Boolean =
        synchronized(lock) {
            if (running[videoId]?.token != claimToken) return@synchronized false
            save()
            fromCache.remove(videoId)
            DownloadSession.done(videoId)
            _active.update { it - videoId }
            true
        }

    private fun clearIfCurrent(videoId: String, claimToken: Long): Boolean =
        synchronized(lock) {
            if (running[videoId]?.token != claimToken) return@synchronized false
            _active.update { it - videoId }
            true
        }

    private fun failIfCurrent(videoId: String, claimToken: Long, reason: String): Boolean =
        synchronized(lock) {
            if (running[videoId]?.token != claimToken) return@synchronized false
            _active.update { it + (videoId to DownloadState.Failed(reason)) }
            DownloadSession.failed(videoId, reason)
            true
        }

    private fun fail(videoId: String, reason: String) {
        _active.update { it + (videoId to DownloadState.Failed(reason)) }
        DownloadSession.failed(videoId, reason)
    }

    /**
     * A failure a user can read. The message on an [error] raised in this
     * package is already written for them; anything else is a network fault
     * with a class name for a message.
     *
     * [IllegalArgumentException] is in here because of one that wasn't: the
     * media store throws it for a MIME type it won't accept, and for a while
     * every download on this device failed that way and reported itself as a
     * connection problem. The message it carries is not written for a user, but
     * `Unsupported MIME type audio/webm` at least sends someone looking in the
     * right direction.
     */
    private fun Exception.friendly(): String = when {
        (this is IllegalStateException || this is IllegalArgumentException) &&
            !message.isNullOrBlank() -> message!!
        else -> "Download failed — check your connection"
    }

    /**
     * How long the source lookup may hold a download up before it goes to
     * YouTube regardless.
     *
     * **This has to clear `ModuleSource.SEARCH_PATIENT_MS`, and for a long time
     * it didn't.** It was 20s against a patient window of 25s, which made the
     * arithmetic decide the outcome: [SourceResolver.forDownload] asks every
     * source with `waitForAll = true`, that flag buys each module the full 25s
     * patient window, and this timeout fired five seconds before the window it
     * was waiting on could even close. Every track whose modules were not
     * unusually quick came back null — not "no source has it", but "nobody was
     * asked for long enough" — and null here means YouTube. So a Lossless
     * setting reliably produced YouTube's AAC, and it looked like a bulk-only
     * fault because it *was* one: a single download runs with the engines to
     * itself and lands inside 20s, while four workers sharing three interpreter
     * engines per module (`QuickJsExecutor.ENGINES_PER_MODULE`) do not.
     *
     * Sized off what it actually bounds, therefore, rather than off the playback
     * timeout it used to be matched to. One patient search is 25s, the matcher
     * offers up to three queries (`TrackMatcher.queries` — the third only for a
     * kana title, in the other script), and [streamBest] may then open up to
     * `STREAM_ATTEMPTS` stream endpoints on the winner. Eighty-five seconds
     * covers a slow-but-working index; it is still finite, because the
     * alternative is the queue stalled per track on modules that simply do not
     * have the recording.
     *
     * The comparison with playback is no longer the right one and is worth
     * stating plainly: `PlaybackService.SUBSTITUTE_TIMEOUT_MS` is short because
     * a listener is staring at a paused player. Nothing is waiting here, the
     * answer becomes a permanent file, and re-fetching it later costs the whole
     * download again.
     *
     * It bounds the lossy half of that lookup too, which is why
     * [SourceResolver.forDownload] runs both halves at once rather than in
     * turn: a fast source queued behind a slow one would spend this budget
     * waiting for a module and never be asked.
     */
    private const val SOURCE_LOOKUP_MS = 85_000L

    /**
     * The extensions a file in Music can carry that say, on their own, that a
     * lossless request has already been answered.
     *
     * `m4a` is deliberately absent even though [DownloadStore.storable] files
     * ALAC as one: an `.m4a` in this folder is just as likely to be the AAC a
     * download at the High rung wrote, and there is nothing in the name to
     * separate them. Guessing wrong there would answer a request for lossless
     * with a transcode and never fetch the real thing.
     */
    private val LOSSLESS_EXTENSIONS = listOf("flac", "wav")

    /**
     * Why a download didn't start, when the reason is a setting rather than a
     * fault.
     *
     * Names the switch, because a refusal that only says no leaves the user
     * looking for a network problem that isn't there. Shared with the callers
     * that show it as a toast so the two cannot drift apart.
     */
    internal const val WIFI_ONLY_REFUSAL = "Downloads are set to Wi-Fi only"

    /** Dropped when the sheet is reopened; a failure is worth showing once. */
    fun dismissFailure(videoId: String) {
        synchronized(lock) {
            if (_active.value[videoId] is DownloadState.Failed) clear(videoId)
        }
    }

    private const val KEY_SAVED = "downloaded_tracks"
}

internal data class DownloadClaim(val song: Song, val token: Long)

internal data class RunningDownloadClaim(val token: Long, var job: Job? = null)

/** A cancelled worker must neither attach to nor clear a newer retry's claim. */
internal fun attachRunningJobIfClaimMatches(
    running: MutableMap<String, RunningDownloadClaim>,
    videoId: String,
    claimToken: Long,
    job: Job,
): Boolean {
    val claim = running[videoId] ?: return false
    if (claim.token != claimToken) return false
    claim.job = job
    return true
}

internal fun removeRunningClaimIfMatches(
    running: MutableMap<String, RunningDownloadClaim>,
    videoId: String,
    claimToken: Long,
): Boolean {
    if (running[videoId]?.token != claimToken) return false
    running.remove(videoId)
    return true
}

internal fun canStartDownloadClaim(
    videoId: String,
    retiring: Map<String, Set<Long>>,
): Boolean = retiring[videoId].isNullOrEmpty()

/** A drain must be repeated if a start raced its final empty-queue check. */
internal fun shouldRestartDownloadDrain(restartRequested: Boolean, queueBusy: Boolean): Boolean =
    restartRequested || queueBusy

internal fun removeRetiringDownloadClaim(
    retiring: MutableMap<String, MutableSet<Long>>,
    videoId: String,
    claimToken: Long,
): Boolean {
    val claims = retiring[videoId] ?: return false
    if (!claims.remove(claimToken)) return false
    if (claims.isEmpty()) retiring.remove(videoId)
    return true
}

@kotlinx.serialization.Serializable
internal data class SavedSongMetadata(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    /**
     * What release this track is off, when the row it was downloaded from knew.
     *
     * Added after the fact and defaulted, so a record written before it existed
     * still decodes — those entries come back with a null album and are filled
     * in from the file's own tags instead, see LocalMediaRepository.
     */
    val albumName: String? = null,
    val uri: String,
    val downloadFormat: String? = null,
    /** Stable creation time for downloads that MediaStore does not index. */
    val dateAddedSeconds: Long? = null,
    /** File accessibility only; this does not establish that the audio is playable. */
    val fileCheckState: String = DownloadFileCheckState.UNCHECKED.persistedValue,
    /** Explicit-content badge from the source catalogue; null for legacy/unknown records. */
    val isExplicit: Boolean? = null,
    /** Time of the most recent file accessibility check, including failed checks. */
    val fileCheckAtMillis: Long? = null,
)

/**
 * Dates for a downloaded file-backed row.
 *
 * Old metadata has no persisted creation time, so the file timestamp is the
 * only honest migration value. From then on the persisted value stays fixed,
 * while modified time continues to follow the file on disk.
 */
internal fun resolvedDownloadDates(
    persistedAddedSeconds: Long?,
    fileModifiedMillis: Long?,
): Pair<Long?, Long?> {
    val modifiedSeconds = fileModifiedMillis?.takeIf { it > 0 }?.div(1_000)
    return (persistedAddedSeconds ?: modifiedSeconds) to modifiedSeconds
}

/** Premium source labels used in Audify's Downloads list. */
private fun StreamFormat.downloadBadge(): String? = when {
    isDolbyAtmos -> "DOLBY"
    codec.equals("flac", ignoreCase = true) || codec.equals("x-flac", ignoreCase = true) -> "FLAC"
    else -> null
}

/**
 * YouTube does not expose a named quality tier once the resolved file has been
 * saved, so retain the concrete codec/container choice and bitrate alongside
 * the download. This is display metadata only: the bytes are still the exact
 * WebM/Opus or MP4/AAC rendition returned by YouTube.
 */
internal fun youtubeDownloadBadge(extension: String, kbps: Int): String? {
    if (kbps <= 0) return null
    val codec = if (extension.equals("m4a", ignoreCase = true)) "AAC" else "OPUS"
    return "$codec · $kbps kbps"
}

/** Recover display metadata for private YouTube downloads made before it was persisted. */
private fun legacyYoutubeDownloadBadge(context: Context, uri: Uri): String? {
    if (!uri.lastPathSegment.orEmpty().substringBefore('?').endsWith(".webm", ignoreCase = true)) {
        return null
    }
    val bitsPerSecond = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull() ?: return null
    val kbps = ((bitsPerSecond + 500L) / 1_000L).toInt()
    return youtubeDownloadBadge("webm", kbps)
}

/**
 * What a batch download was asked for as a whole.
 *
 * Built by whichever surface the tap came from — a release page's own download
 * button, a shelf card's menu — because that surface is the only thing that
 * knows the answer, and by the time the tracks reach the queue they are forty
 * unrelated rows. Null everywhere a single track is downloaded on its own,
 * which is the honest answer there: one song off an album is not the album.
 */
data class DownloadTarget(
    /**
     * What this release is filed under: its browse id where it has one, so the
     * same album downloaded twice is one entry rather than two.
     */
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    /** Playlists and albums are grouped alike but not billed alike. */
    val playlist: Boolean = false,
)

/**
 * A release the record says was downloaded whole, as it is written down.
 *
 * Only the tracks' ids, not the tracks: a [Song] is a wide row full of things
 * that go stale — like state, autoplay provenance, a resolved local path — and
 * a second copy of one per release is a second copy to keep in step. The songs
 * are looked up out of what the Downloads page already read off disk instead;
 * see [Downloads.collectionsAmong].
 */
@kotlinx.serialization.Serializable
data class SavedCollection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val playlist: Boolean = false,
    /** In the order the page listed them, which is the order to play them in. */
    val videoIds: List<String> = emptyList(),
)

/** A [SavedCollection] with its surviving tracks attached, ready to draw. */
data class DownloadedCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val playlist: Boolean,
    val songs: List<Song>,
)

internal data class PersistedDownloadMap<T>(
    val values: Map<String, T>,
    val invalidEntries: Int,
    val invalidRoot: Boolean,
)

/** Salvage valid records from a partially malformed persisted download map. */
internal fun <T> decodePersistedDownloadMap(
    raw: String,
    valueSerializer: KSerializer<T>,
    json: Json,
): PersistedDownloadMap<T> {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        ?: return PersistedDownloadMap(emptyMap(), invalidEntries = 0, invalidRoot = true)
    var invalidEntries = 0
    val values = buildMap {
        root.forEach { (key, value) ->
            val decoded = runCatching { json.decodeFromJsonElement(valueSerializer, value) }.getOrNull()
            if (decoded == null) {
                invalidEntries++
            } else {
                put(key, decoded)
            }
        }
    }
    return PersistedDownloadMap(values, invalidEntries, invalidRoot = false)
}
