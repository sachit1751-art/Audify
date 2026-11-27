// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.audify.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Persistent background release checks and APK downloads; installation is always user-initiated. */
object AppUpdateScheduler {
    private const val UNIQUE_WORK = "audify-background-update-download"

    fun sync(context: Context, enabled: Boolean = AppSettings.automaticUpdateChecks.value) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AppUpdateDownloadWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class AppUpdateDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!AppSettings.automaticUpdateChecks.value) return Result.success()
        return try {
            when (AppUpdateChecker.check(AppSettings.includePreReleaseUpdates.value || AppSettings.betaUpdatesEnabled.value)) {
                null -> Result.retry()
                false -> Result.success()
                true -> {
                    AppUpdateChecker.downloadApk(applicationContext)
                    when (AppUpdateChecker.download.value) {
                        is AppUpdateChecker.DownloadState.Ready -> Result.success()
                        is AppUpdateChecker.DownloadState.Failed -> Result.retry()
                        else -> Result.success()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
