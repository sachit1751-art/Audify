package com.music.audify.playback.smart

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import androidx.core.content.ContextCompat
import com.music.audify.data.model.Song
import com.music.audify.data.settings.AppSettings
import com.music.audify.download.AutomixModelService
import com.music.audify.download.DownloadSession
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Dua model ONNX Automix: tidak dibundel di APK, diunduh sekali setelah pengguna setuju. */
object AutomixModels {
    /** Id baris di manager unduhan (DownloadSession). */
    const val SESSION_ID = "audify.automix.models"

    private val lock = Any()

    /** Naik tiap start/cancel; unduhan lama yang dibatalkan berhenti sendiri saat ketahuan berbeda. */
    private val generation = AtomicLong(0L)

    private val _downloading = MutableStateFlow(false)

    /** True selama unduhan berjalan. Saklar Automix tampil ON selama ini true. */
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val sessionSong = Song(
        videoId = SESSION_ID,
        title = "Automix models",
        artist = "Automix",
        thumbnailUrl = null,
    )

    /**
     * Memulai unduhan lewat [AutomixModelService]. Bila model sudah ada, langsung
     * menyalakan Automix. Aman dipanggil berulang: unduhan yang sedang jalan tidak digandakan.
     */
    fun start(context: Context) {
        val app = context.applicationContext
        if (isReady(app)) {
            AppSettings.setSmartFadeEnabled(true)
            return
        }
        synchronized(lock) {
            if (_downloading.value) return
            generation.incrementAndGet()
            _progress.value = 0
            _downloading.value = true
        }
        DownloadSession.queued(sessionSong)
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, AutomixModelService::class.java))
        }.onFailure {
            synchronized(lock) {
                generation.incrementAndGet()
                _downloading.value = false
            }
            DownloadSession.failed(SESSION_ID, "Download can't start right now")
        }
    }

    /** Membatalkan unduhan: saklar kembali OFF dan barisnya hilang dari manager. */
    fun cancel() {
        synchronized(lock) {
            generation.incrementAndGet()
            _downloading.value = false
            _progress.value = 0
        }
        DownloadSession.forget(SESSION_ID)
    }

    /** Dipanggil [AutomixModelService]; memblokir sampai selesai, gagal, atau dibatalkan. */
    internal suspend fun runPending(context: Context) {
        withContext(Dispatchers.IO) {
            val gen = synchronized(lock) {
                if (!_downloading.value) return@withContext
                generation.get()
            }
            DownloadSession.running(SESSION_ID, 0f)
            val ok = download(
                context = context,
                onProgress = { percent ->
                    if (generation.get() == gen) {
                        _progress.value = percent
                        DownloadSession.running(SESSION_ID, percent / 100f)
                    }
                },
                isCancelled = { generation.get() != gen || !isActive },
            )
            val stillWanted = synchronized(lock) {
                if (generation.get() != gen) {
                    false
                } else {
                    _downloading.value = false
                    true
                }
            }
            // Dibatalkan atau digantikan unduhan baru: yang membatalkan sudah melapor.
            if (!stillWanted) return@withContext
            if (ok) {
                _progress.value = 100
                AppSettings.setSmartFadeEnabled(true)
                DownloadSession.done(SESSION_ID)
            } else {
                DownloadSession.failed(SESSION_ID, "Download failed. Check your connection and try again.")
            }
        }
    }
    private const val BASE_URL =
        "https://raw.githubusercontent.com/sachit1751-art/Audify/main/app/src/main/assets/"

    private val names get() = listOf(BeatTracker.MODEL_ASSET, VocalTracker.MODEL_ASSET)

    /** Apakah kedua model sudah ada (hasil unduhan atau masih dibundel di build lama). */
    fun isReady(context: Context): Boolean = names.all { present(context, it) }

    private fun present(context: Context, name: String): Boolean {
        val file = File(context.filesDir, name)
        if (file.exists() && file.length() > 0L) return true
        return runCatching { context.assets.open(name).close() }.isSuccess
    }

    /**
     * Mengunduh model yang belum ada. Memblokir, jadi panggil dari thread latar.
     * [onProgress] 0..100 untuk semua model; [isCancelled] dicek terus selama mengunduh.
     */
    fun download(
        context: Context,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val missing = names.filterNot { present(context, it) }
        if (missing.isEmpty()) return true
        missing.forEachIndexed { index, name ->
            val ok = fetch(context, name, isCancelled) { fraction ->
                onProgress((((index + fraction) / missing.size) * 100).toInt())
            }
            if (!ok) return false
        }
        onProgress(100)
        return true
    }

    /** Ditulis ke file sementara dan di-rename setelah lengkap, supaya tidak ada model terpotong. */
    private fun fetch(
        context: Context,
        name: String,
        isCancelled: () -> Boolean,
        onFraction: (Float) -> Unit,
    ): Boolean {
        val target = File(context.filesDir, name)
        val temp = File(context.filesDir, "$name.${System.nanoTime()}.part")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(BASE_URL + name).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) return false
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (isCancelled()) return false
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onFraction((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            // Ukuran harus sama dengan yang dijanjikan server, supaya model terpotong tidak dipakai.
            return temp.length() > 0L && (total <= 0L || temp.length() == total) && temp.renameTo(target)
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
            if (temp.exists()) temp.delete()
        }
    }
}
