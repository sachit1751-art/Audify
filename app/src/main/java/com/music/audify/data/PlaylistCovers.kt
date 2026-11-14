package com.music.audify.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cover playlist yang dipilih dari galeri. YouTube Music tidak menerima cover
 * kustom dari app ini, jadi gambarnya disimpan di perangkat dan dipasang di
 * atas thumbnail playlist tempat ia digambar.
 */
object PlaylistCovers {
    private const val DIR = "playlist_covers"
    private const val SIZE = 800
    private const val SEP = "__"

    private fun key(id: String) = id.removePrefix("VL").replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** Satu kali baca folder; hasilnya dipakai untuk banyak playlist sekaligus. */
    fun lookup(context: Context): (String) -> String? {
        val latest = HashMap<String, File>()
        dir(context).listFiles()?.forEach { f ->
            val k = f.name.substringBefore(SEP)
            val cur = latest[k]
            if (cur == null || f.lastModified() > cur.lastModified()) latest[k] = f
        }
        return { id -> latest[key(id)]?.let { Uri.fromFile(it).toString() } }
    }

    fun get(context: Context, id: String): String? = lookup(context)(id)

    /** Menghapus cover kustom; kartu kembali ke thumbnail bawaan setelah Library dimuat ulang. */
    fun remove(context: Context, id: String) {
        val k = key(id)
        dir(context).listFiles { f -> f.name.startsWith(k + SEP) }?.forEach { it.delete() }
    }

    /** Memotong jadi persegi, mengecilkan, menyimpan. Mengembalikan alamat file, atau null jika gagal. */
    suspend fun save(context: Context, id: String, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > SIZE * 2 || bounds.outHeight / sample > SIZE * 2) sample *= 2
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
            val k = key(id)
            val folder = dir(context).apply { mkdirs() }
            folder.listFiles { f -> f.name.startsWith(k + SEP) }?.forEach { it.delete() }
            val out = File(folder, "$k$SEP${System.currentTimeMillis()}.jpg")
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            Uri.fromFile(out).toString()
        }.getOrNull()
    }
}