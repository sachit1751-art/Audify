package com.music.audify.data.settings

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Stores bounded picker images in app-private, non-backed-up storage. */
internal object ThemeBackgroundStorage {
    const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
    const val MAX_IMAGE_PIXELS = 40_000_000L
    private const val BUFFER_SIZE = 32 * 1024

    /** Reject corrupt/oversized image headers before Coil decodes a wallpaper. */
    fun validateImageDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0) { "The selected file is not a readable image" }
        require(width.toLong() * height.toLong() <= MAX_IMAGE_PIXELS) {
            "Image dimensions are too large for a wallpaper"
        }
    }

    suspend fun copyToPrivateStorage(input: InputStream, directory: File): File = withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create wallpaper storage" }
        val staging = File.createTempFile("wallpaper-", ".tmp", directory)
        val destination = File(directory, staging.name.removeSuffix(".tmp") + ".img")
        try {
            input.use { source ->
                staging.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        totalBytes += count
                        check(totalBytes <= MAX_IMAGE_BYTES) { "Image exceeds the 20 MB wallpaper limit" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(staging.length() > 0L) { "The selected image is empty" }
            check(staging.renameTo(destination)) { "Unable to save the selected image" }
            destination
        } finally {
            staging.delete()
        }
    }

    fun removeIfOwned(file: File, directory: File): Boolean = runCatching {
        file.canonicalFile.parentFile == directory.canonicalFile &&
            file.name.startsWith("wallpaper-") && file.extension == "img" && file.delete()
    }.getOrDefault(false)

    /** Removes abandoned staging and replaced images while preserving the active wallpaper. */
    fun removeOrphans(directory: File, activeUri: String?) {
        val activeFile = runCatching { java.net.URI(activeUri).path?.let(::File)?.canonicalFile }.getOrNull()
        directory.listFiles().orEmpty().forEach { file ->
            val owned = file.name.startsWith("wallpaper-") &&
                (file.extension == "img" || file.extension == "tmp")
            if (owned && file.canonicalFile != activeFile) runCatching { file.delete() }
        }
    }
}
