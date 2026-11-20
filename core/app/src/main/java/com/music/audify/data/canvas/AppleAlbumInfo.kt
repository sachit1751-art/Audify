package com.music.audify.data.canvas

import com.music.audify.data.AppleCatalogue
import com.music.audify.data.DebugLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Apa yang diketahui Apple tentang sebuah album: tanggal rilis lengkap dan
 * baris hak cipta (℗ tahun label). Keduanya tidak ada di halaman album YouTube
 * Music, yang hanya memberi tahun.
 */
data class AppleAlbumInfo(
    val releaseDate: LocalDate?,
    val copyright: String?,
    val genre: String?,
)

/** Tanggal rilis dalam format lokal pengguna, mis. "March 3, 1988". */
fun AppleAlbumInfo.releaseDateText(): String? =
    releaseDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))

/**
 * Mencari album di iTunes Search dan mengambil tanggal rilis + copyright.
 *
 * Hanya dipakai sebagai pelengkap: halaman album sudah tampil dengan tahun dari
 * subtitle, dan jawaban Apple menggantinya kalau ada. Cocok bila judul album
 * sama persis (setelah dinormalkan) dan artisnya saling mengandung. Tidak
 * pernah melempar exception.
 */
object AppleAlbumInfoRepository {

    private const val TAG = "AppleAlbumInfo"
    private const val CACHE_SIZE = 64

    /** Hasil yang sudah selesai; [info] null berarti Apple tidak punya album ini. */
    private class Entry(val info: AppleAlbumInfo?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    /** Yang sudah diketahui untuk album [browseId], tanpa request. */
    fun cached(browseId: String): AppleAlbumInfo? =
        synchronized(cache) { cache[browseId] }?.info

    /**
     * Info Apple untuk album [browseId] berjudul [albumTitle] oleh [artist],
     * atau null bila tidak ada yang terbukti sama. Gagal jaringan tidak
     * disimpan, jadi dicoba lagi saat halaman dibuka ulang.
     */
    suspend fun infoFor(
        browseId: String,
        albumTitle: String,
        artist: String,
        trackCount: Int,
    ): AppleAlbumInfo? {
        val titleKey = albumTitle.titleKey()
        val artistKey = artist.primaryArtist().let { AppleCatalogue.matchKey(it) }
        if (titleKey.isEmpty() || artistKey.isEmpty()) return null
        synchronized(cache) { cache[browseId] }?.let { return it.info }

        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(albumTitle, artist, titleKey, artistKey, trackCount) }
                .onFailure { Log.d(TAG, "lookup failed for $albumTitle: $it") }
                .getOrNull()
        } ?: return null
        synchronized(cache) { cache[browseId] = entry }
        return entry.info
    }

    private fun lookup(
        albumTitle: String,
        artist: String,
        titleKey: String,
        artistKey: String,
        trackCount: Int,
    ): Entry? {
        val url = "https://itunes.apple.com/search?term=" +
            URLEncoder.encode("${artist.primaryArtist()} $albumTitle", "UTF-8") +
            "&entity=album&limit=10"
        val body = canvasGet(url, mapOf("User-Agent" to CANVAS_UA)) ?: return null
        val results = JSONObject(body).optJSONArray("results") ?: return Entry(null)

        val matches = ArrayList<JSONObject>()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            if (r.optString("collectionName").titleKey() != titleKey) continue
            val name = AppleCatalogue.matchKey(r.optString("artistName"))
            if (name.isEmpty() || !(name.contains(artistKey) || artistKey.contains(name))) continue
            matches += r
        }
        // Dua rilisan bisa sama judul dan artis (standar vs. edisi ulang):
        // yang jumlah lagunya sama dengan halaman didahulukan.
        val best = matches.firstOrNull { it.optInt("trackCount") == trackCount }
            ?: matches.firstOrNull()
            ?: return Entry(null)

        val date = runCatching { LocalDate.parse(best.optString("releaseDate").take(10)) }.getOrNull()
        val copyright = best.optString("copyright").trim().takeIf { it.isNotEmpty() }
        val genre = best.optString("primaryGenreName").trim().takeIf { it.isNotEmpty() }
        if (date == null && copyright == null && genre == null) return Entry(null)
        return Entry(AppleAlbumInfo(date, copyright, genre))
    }

    /** Judul tanpa akhiran " - Single" / " - EP" milik Apple, dinormalkan. */
    private fun String.titleKey(): String =
        AppleCatalogue.matchKey(removeSuffix(" - Single").removeSuffix(" - EP"))

    /** Kredit "A, B & C" dibaca sebagai artis pertamanya, "A". */
    private fun String.primaryArtist(): String =
        split(',', '&').first().trim()
}