package com.music.audify.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.music.audify.R
import com.music.audify.playback.smart.AutomixModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service yang mengunduh dua model Automix, supaya unduhan tetap
 * berjalan saat layar pengaturan ditutup atau app ke latar belakang.
 * Status dan progres ada di [AutomixModels] dan [DownloadSession]; service ini
 * hanya menjalankan pekerjaannya dan menampilkan notifikasi.
 */
class AutomixModelService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var job: Job? = null
    private var notifier: Job? = null

    @Volatile
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Wajib dipromosikan dalam beberapa detik, apa pun isi intent-nya.
        promote(0)

        if (intent?.action == ACTION_CANCEL) {
            AutomixModels.cancel()
            shutdown()
            return START_NOT_STICKY
        }

        if (job == null) {
            job = scope.launch {
                // Diulang bila ada unduhan baru yang dimulai tepat saat yang lama selesai.
                do {
                    AutomixModels.runPending(applicationContext)
                } while (AutomixModels.downloading.value)
                shutdown()
            }
            notifier = scope.launch {
                AutomixModels.progress.collect { percent ->
                    postNotification(percent)
                    delay(PROGRESS_REFRESH_MS)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun shutdown() {
        notifier?.cancel()
        notifier = null
        job = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promote(percent: Int) {
        val notification = buildNotification(percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification(percent: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(percent)) }
    }

    private fun buildNotification(percent: Int): Notification {
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomixModelService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Downloading Automix models")
            .setContentText(if (percent > 0) "$percent%" else "Starting")
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Songs being saved to your Music folder"
                setShowBadge(false)
            },
        )
    }

    private companion object {
        /** Sama dengan channel DownloadService supaya tampil di grup yang sama. */
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 0x8176
        const val ACTION_CANCEL = "com.music.audify.download.CANCEL_AUTOMIX_MODELS"
        const val PROGRESS_REFRESH_MS = 250L
    }
}