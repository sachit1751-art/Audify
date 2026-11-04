// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.music.audify.data.local.LocalDatabase.PlaylistAccountSyncRow
import com.music.audify.data.local.LocalDatabase.SyncOperationRow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SyncWorkScheduler {
    private const val UNIQUE_WORK = "audify-youtube-library-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<YouTubeSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request,
        )
    }
}

/** Processes only the currently selected account/profile; credentials never enter the queue. */
class YouTubeSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        LocalFirstRepository.initialize(applicationContext)
        // Innertube is a process-wide client. Defer background writes while the
        // UI is active so an account/profile switch cannot race its session.
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return Result.retry()
        }
        val auth = com.music.audify.auth.AuthStore(applicationContext)
        val session = auth.activeSession ?: return Result.success()
        val profileId = auth.activeProfileId ?: session.activeProfileId ?: return Result.success()
        val profile = session.profiles.firstOrNull { it.profileId == profileId } ?: return Result.success()

        return try {
            com.music.audify.data.innertube.Innertube.cookie = session.cookie
            com.music.audify.data.innertube.Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
            com.music.audify.data.innertube.Innertube.ensureSessionScope()
            LocalFirstRepository.resetDueRetries()
            while (true) {
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return Result.retry()
                }
                val active = auth.activeSession
                val activeProfile = auth.activeProfileId ?: active?.activeProfileId
                if (active?.accountId != session.accountId || activeProfile != profileId) return Result.retry()
                val operations = LocalFirstRepository.dueOperations(session.accountId, profileId)
                if (operations.isEmpty()) break
                for (operation in operations) {
                    try {
                        ensureSessionCurrent(auth, session, profileId)
                        when (operation.type) {
                            "favorite" -> {
                                val status = com.music.audify.data.model.LikeStatus.valueOf(
                                    JSONObject(operation.payloadJson).getString("status"),
                                )
                                val previous = LocalFirstRepository.favoriteSync(
                                    operation.entityId, session.accountId, profileId,
                                )?.status?.let { runCatching { com.music.audify.data.model.LikeStatus.valueOf(it) }.getOrNull() }
                                ensureSessionCurrent(auth, session, profileId)
                                val remote = com.music.audify.data.YtMusicRepository.songMenu(operation.entityId)
                                    .getOrThrow().likeStatus
                                    ?: throw SyncConflictException("YouTube did not provide a rating state; favorite was not overwritten")
                                if (previous != null && remote != previous) {
                                    throw SyncConflictException("Favorite changed in YouTube Music since the last sync")
                                }
                                if (previous == null && remote != status) {
                                    val safeFirstWrite = status == com.music.audify.data.model.LikeStatus.LIKE &&
                                        remote == com.music.audify.data.model.LikeStatus.INDIFFERENT
                                    if (!safeFirstWrite) {
                                        throw SyncConflictException("YouTube already has a different rating; review before syncing")
                                    }
                                    ensureSessionCurrent(auth, session, profileId)
                                    com.music.audify.data.YtMusicRepository.rate(operation.entityId, status).getOrThrow()
                                }
                                ensureSessionCurrent(auth, session, profileId)
                                LocalFirstRepository.saveFavoriteSync(
                                    operation.entityId, session.accountId, profileId, status,
                                )
                            }
                            "playlist" -> syncPlaylist(operation, session, profileId, auth)
                            else -> error("Unsupported sync operation: ${operation.type}")
                        }
                        ensureSessionCurrent(auth, session, profileId)
                        LocalFirstRepository.complete(operation)
                    } catch (_: SessionChangedException) {
                        return Result.retry()
                    } catch (conflict: SyncConflictException) {
                        LocalFirstRepository.conflict(operation, conflict.message ?: "Cloud changes need review")
                        if (operation.type == "playlist") {
                            LocalFirstRepository.markPlaylistConflict(
                                operation.entityId, session.accountId, profileId,
                                conflict.message ?: "Cloud changes need review",
                            )
                    } else if (operation.type == "favorite") {
                        LocalFirstRepository.markFavoriteConflict(
                            operation.entityId, session.accountId, profileId,
                            conflict.message ?: "Favorite sync conflict",
                        )
                        }
                    } catch (failure: Throwable) {
                        LocalFirstRepository.retry(operation, failure)
                        return Result.retry()
                    }
                }
            }
            if (LocalFirstRepository.hasRetryingOperations(session.accountId, profileId)) return Result.retry()
            Result.success()
        } catch (failure: Throwable) {
            if (runAttemptCount < 8) Result.retry() else Result.failure()
        }
    }

    private fun ensureSessionCurrent(
        auth: com.music.audify.auth.AuthStore,
        expected: com.music.audify.auth.GoogleAccountSession,
        profileId: String,
    ) {
        val active = auth.activeSession
        val selectedProfile = auth.activeProfileId ?: active?.activeProfileId
        val expectedProfile = expected.profiles.firstOrNull { it.profileId == profileId }
        val activeProfile = active?.profiles?.firstOrNull { it.profileId == profileId }
        if (auth.activeAccountId != expected.accountId || active?.accountId != expected.accountId ||
            selectedProfile != profileId || active.cookie != expected.cookie ||
            expectedProfile == null || activeProfile != expectedProfile
        ) {
            throw SessionChangedException()
        }
    }

    private suspend fun syncPlaylist(
        operation: SyncOperationRow,
        session: com.music.audify.auth.GoogleAccountSession,
        profileId: String,
        auth: com.music.audify.auth.AuthStore,
    ) {
        val accountId = session.accountId
        ensureSessionCurrent(auth, session, profileId)
        val payload = JSONObject(operation.payloadJson)
        val title = payload.getString("title")
        val localIds = (0 until payload.getJSONArray("videoIds").length())
            .map { payload.getJSONArray("videoIds").getString(it) }.filter(String::isNotBlank).distinct()
        val daoSync = LocalFirstRepository.playlistSync(operation.entityId, accountId, profileId)
        var sync = daoSync

        if (sync == null) {
            val firstBatch = localIds.take(50)
            ensureSessionCurrent(auth, session, profileId)
            val remoteId = com.music.audify.data.YtMusicRepository.createPlaylist(
                title, com.music.audify.data.model.PlaylistPrivacy.PRIVATE, firstBatch,
            ).getOrThrow()
            // Persist the remote ID immediately. If the process dies while
            // appending later chunks, retry resumes instead of creating a twin.
            sync = PlaylistAccountSyncRow(
                operation.entityId, accountId, profileId, remoteId, JSONArray(firstBatch).toString(),
                title, payload.optLong("revision", 1L), "partial", null, System.currentTimeMillis(),
            )
            LocalFirstRepository.savePlaylistSync(sync)
        }

        val remoteId = sync.youtubePlaylistId ?: throw SyncConflictException("Remote playlist identity is missing")
        val syncedOrder = (0 until JSONArray(sync.syncedVideoIdsJson).length())
            .map { JSONArray(sync.syncedVideoIdsJson).getString(it) }
        val syncedIds = syncedOrder.toSet()
        if (sync.syncedTitle != title) {
            throw SyncConflictException("Playlist title changed locally and remotely; review before syncing")
        }
        if (!localIds.containsAll(syncedIds)) {
            throw SyncConflictException("Playlist removals need review; cloud tracks were not deleted")
        }
        if (localIds.take(syncedOrder.size) != syncedOrder) {
            throw SyncConflictException("Playlist reordering needs review; cloud order was not overwritten")
        }
        ensureSessionCurrent(auth, session, profileId)
        val cloudIds = com.music.audify.data.YtMusicRepository.playlistEntries("VL$remoteId")
            .getOrThrow().map { it.videoId }
        if (cloudIds != syncedOrder) {
            throw SyncConflictException("Playlist changed in YouTube Music since the last sync; cloud changes were preserved")
        }
        var confirmed = syncedIds
        val additions = localIds.filterNot(confirmed::contains)
        additions.chunked(50).forEach { chunk ->
            ensureSessionCurrent(auth, session, profileId)
            com.music.audify.data.YtMusicRepository.addToPlaylist(remoteId, chunk).getOrThrow()
            confirmed = confirmed + chunk
            sync = PlaylistAccountSyncRow(
                sync.playlistId, sync.accountId, sync.profileId, sync.youtubePlaylistId,
                JSONArray(localIds.filter(confirmed::contains)).toString(), sync.syncedTitle,
                sync.syncedRevision, "partial", null, System.currentTimeMillis(),
            )
            LocalFirstRepository.savePlaylistSync(sync)
        }
        ensureSessionCurrent(auth, session, profileId)
        LocalFirstRepository.savePlaylistSync(PlaylistAccountSyncRow(
            sync.playlistId, sync.accountId, sync.profileId, sync.youtubePlaylistId,
            JSONArray(localIds).toString(), sync.syncedTitle,
            payload.optLong("revision", sync.syncedRevision), "synced", null, System.currentTimeMillis(),
        ))
    }

    private class SyncConflictException(message: String) : IllegalStateException(message)
    private class SessionChangedException : IllegalStateException("Selected YouTube account or profile changed")
}
