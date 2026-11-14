package com.music.audify.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.music.audify.data.TrackLog
import com.music.audify.data.listentogether.ListenTogether
import com.music.audify.data.model.Song
import com.music.audify.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Prefer music-only version": sebuah music video didengar sebagai lagu katalognya
 * dan tidak pernah mulai diputar sebagai video dulu.
 *
 * [PlaybackService] mengonversi baris yang sedang diputar dan baris berikutnya,
 * sehingga transisi ke lagu berikutnya sudah direncanakan terhadap lagunya, bukan
 * baru ketahuan video saat tiba. Baris hasil konversi adalah baris audio biasa
 * (tanpa `&m=1`, bukan video-origin), sehingga Automix, crossfade dan jalur upgrade
 * kualitas memperlakukannya seperti lagu lain. Lihat [asMusicOnlyOf].
 *
 * Sebelum sebuah baris punya jawabannya, byte-nya ditahan di [holdingFactory].
 * Player buffering selama itu, dan mengganti baris menginterupsi thread loader
 * sehingga penantian berakhir.
 *
 * Baris tanpa versi audio dilepas ([settle]) dan memutar audio video itu sendiri,
 * tanpa gambar.
 */
object MusicOnlyPlayback {

    private const val TAG = "Audify"

    /** Berapa lama sebuah baris menunggu versi audionya sebelum memutar audio videonya sendiri. */
    const val ANSWER_TIMEOUT_MS = 15_000L

    /** Batas penahanan itu sendiri, di atas [ANSWER_TIMEOUT_MS]: tidak ada yang boleh menunggu selamanya. */
    private const val HOLD_LIMIT_MS = ANSWER_TIMEOUT_MS + 5_000L

    /** Video yang dipilih manual dari pemutar dan tetap menjadi video. */
    private val kept = MutableStateFlow<Set<String>>(emptySet())

    /** Video yang sementara diputar apa adanya karena versi audionya tidak ditemukan. */
    private val settled = MutableStateFlow<Set<String>>(emptySet())

    /** Tidak pernah di party, tempat semua orang mendengar satu versi yang sama. */
    fun isActive(): Boolean =
        AppSettings.autoMusicOnly.value && !ListenTogether.state.value.inParty

    /** Apakah [videoId], sebuah music video, menampilkan gambarnya dan bukan sampul. */
    fun showsPicture(videoId: String): Boolean = !isActive() || videoId in kept.value

    /** Apakah URI pemutaran ini music video yang masih menunggu jawabannya. */
    fun holds(uri: Uri): Boolean {
        if (uri.scheme != "audify" || uri.authority != "watch") return false
        // `m` menandai baris video; baris yang dikembalikan manual adalah upload persis itu.
        if (uri.getQueryParameter("m") != "1") return false
        if (uri.getQueryParameter(DIRECT_YOUTUBE_PARAMETER) == "1") return false
        val videoId = mediaIdIn(uri) ?: return false
        return isActive() && videoId !in kept.value && videoId !in settled.value
    }

    /** Unduhan tidak punya URI `audify`, jadi tidak pernah dikonversi: itu yang ada di perangkat. */
    fun wants(item: MediaItem): Boolean = item.localConfiguration?.uri?.let(::holds) == true

    /** [videoId] diputar apa adanya: tidak ada versi audio, atau tidak tepat waktu. */
    fun settle(videoId: String) = settled.update { it + videoId }

    /** Putar berikutnya bertanya lagi, karena lookup yang gagal mungkin hanya jaringan. */
    fun forgetExcept(videoIds: Set<String>) = settled.update { it intersect videoIds }

    /** Pendengar memilih [videoId] secara manual. */
    fun keep(videoId: String) = kept.update { it + videoId }

    /** Pendengar berpindah dari [videoId] secara manual. */
    fun letGo(videoId: String) = kept.update { it - videoId }

    /** Pengaturan berubah, jadi keputusan lama tidak berlaku lagi. */
    fun reset() {
        kept.value = emptySet()
        settled.value = emptySet()
    }

    private suspend fun awaitAnswer(videoId: String) {
        withTimeoutOrNull(HOLD_LIMIT_MS) {
            combine(
                AppSettings.autoMusicOnly,
                ListenTogether.state,
                kept,
                settled,
            ) { on, party, keptNow, settledNow ->
                !on || party.inParty || videoId in keptNow || videoId in settledNow
            }.first { it }
        }
    }

    /** [upstream], kecuali music video yang menunggu jawaban tidak dibuka sampai ada jawabannya. */
    @OptIn(UnstableApi::class)
    fun holdingFactory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val inner = upstream.createDataSource()
        object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) =
                inner.addTransferListener(transferListener)

            override fun open(dataSpec: DataSpec): Long {
                if (holds(dataSpec.uri)) {
                    mediaIdIn(dataSpec.uri)?.let { videoId ->
                        TrackLog.d(TAG, "holding music video for its audio version", about = videoId)
                        runBlocking { awaitAnswer(videoId) }
                    }
                }
                return inner.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                inner.read(buffer, offset, length)

            override fun getUri(): Uri? = inner.uri

            override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

            override fun close() = inner.close()
        }
    }
}

/**
 * Versi audio ini, menggantikan [video] di antrean.
 *
 * Bukan video-origin, tidak seperti tombol di menu pemutar: flag itu yang menjauhkan
 * Automix dari sebuah baris, padahal inti konversi di awal adalah agar Automix aktif.
 */
fun Song.asMusicOnlyOf(video: Song): Song = copy(
    isVideoOrigin = false,
    queueTier = video.queueTier,
    queueEntryId = video.queueEntryId,
    radioName = video.radioName,
    playbackSource = video.playbackSource,
    playbackSourceType = video.playbackSourceType,
    playbackSourceId = video.playbackSourceId,
)