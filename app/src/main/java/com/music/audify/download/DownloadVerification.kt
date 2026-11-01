package com.music.audify.download

import androidx.core.net.toUri

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** What a cheap file-access check established. Neither result claims playability. */
internal enum class DownloadFileCheckState(val persistedValue: String) {
    UNCHECKED("unchecked"),
    AVAILABLE("available"),
    UNAVAILABLE("unavailable");

    companion object {
        fun fromPersisted(value: String): DownloadFileCheckState =
            entries.firstOrNull { it.persistedValue == value } ?: UNCHECKED
    }
}

internal data class DownloadFileCheckStatus(
    val state: DownloadFileCheckState,
    val checkedAtMillis: Long?,
)

internal data class DownloadFileCheckCandidate(
    val uri: String,
    val state: DownloadFileCheckState,
    val checkedAtMillis: Long?,
)

/** Unchecked files first, then the oldest checked files; a URI is checked once. */
internal fun selectDownloadFileCheckBatch(
    candidates: List<DownloadFileCheckCandidate>,
    limit: Int,
): List<DownloadFileCheckCandidate> {
    if (limit <= 0) return emptyList()
    return candidates
        .distinctBy { it.uri }
        .sortedWith(
            compareBy<DownloadFileCheckCandidate> { it.state != DownloadFileCheckState.UNCHECKED }
                .thenBy { it.checkedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.uri },
        )
        .take(limit)
}

/** Reads a small prefix to establish that the URI yields bytes, not that audio can decode. */
internal fun hasReadableFileHeader(input: InputStream): Boolean =
    input.use { stream -> stream.read(ByteArray(16)) > 0 }

/** Owns the periodic schedule and the entry point for a user-requested check. */
object DownloadVerificationScheduler {
    private const val PERIODIC_WORK = "download-file-check-periodic"
    private const val MANUAL_WORK = "download-file-check-manual"

    private val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DownloadFileCheckWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Enqueue a bounded pass immediately; callers can expose this as a manual Verify action. */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DownloadFileCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

class DownloadFileCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val candidates = Downloads.fileCheckCandidates(BATCH_SIZE)
        for (candidate in candidates) {
            if (isStopped) return@withContext Result.retry()
            val available = try {
                val uri = candidate.uri.toUri()
                DownloadStore.exists(applicationContext, uri) &&
                    applicationContext.contentResolver.openInputStream(uri)?.let(::hasReadableFileHeader) == true
            } catch (cancelled: CancellationException) {
                // A stopped worker must not turn cancellation into a failed
                // file check and continue mutating persisted status.
                throw cancelled
            } catch (_: Exception) {
                false
            }
            Downloads.recordFileCheck(candidate.uri, available, System.currentTimeMillis())
        }
        Result.success()
    }

    private companion object {
        /** Keep each scheduled pass short even for libraries with thousands of files. */
        const val BATCH_SIZE = 25
    }
}
