package com.music.audify.data.settings

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeBackgroundStorageTest {
    @Test
    fun copiesImageBytesIntoPrivateStorageAndLeavesNoStagingFile() = runBlocking {
        val directory = Files.createTempDirectory("audify-wallpaper-test").toFile()
        val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x01, 0x02)

        try {
            val stored = ThemeBackgroundStorage.copyToPrivateStorage(
                ByteArrayInputStream(bytes),
                directory,
            )

            assertTrue(stored.isFile)
            assertArrayEquals(bytes, stored.readBytes())
            assertEquals(1, directory.listFiles()?.size)
            assertTrue(stored.name.endsWith(".img"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsEmptyImageAndCleansTemporaryFile() = runBlocking {
        val directory = Files.createTempDirectory("audify-wallpaper-empty-test").toFile()

        try {
            val result = runCatching {
                ThemeBackgroundStorage.copyToPrivateStorage(ByteArrayInputStream(byteArrayOf()), directory)
            }

            assertTrue(result.isFailure)
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsOversizedInputAndRemovesStagingFile() = runBlocking {
        val directory = Files.createTempDirectory("audify-wallpaper-size-test").toFile()

        try {
            val tooLarge = object : java.io.InputStream() {
                private var remaining = ThemeBackgroundStorage.MAX_IMAGE_BYTES + 1
                override fun read(): Int = if (remaining-- > 0) 1 else -1
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (remaining <= 0) return -1
                    val count = minOf(length.toLong(), remaining).toInt()
                    java.util.Arrays.fill(buffer, offset, offset + count, 1.toByte())
                    remaining -= count
                    return count
                }
            }

            val result = runCatching {
                ThemeBackgroundStorage.copyToPrivateStorage(tooLarge, directory)
            }

            assertTrue(result.isFailure)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupOnlyDeletesWallpaperFilesInsideItsDedicatedDirectory() {
        val directory = Files.createTempDirectory("audify-wallpaper-cleanup-test").toFile()
        val otherDirectory = Files.createTempDirectory("audify-wallpaper-other-test").toFile()
        val wallpaper = java.io.File(directory, "wallpaper-current.img").apply { writeBytes(byteArrayOf(1)) }
        val unrelated = java.io.File(directory, "important.db").apply { writeBytes(byteArrayOf(2)) }
        val outside = java.io.File(otherDirectory, "wallpaper-outside.img").apply { writeBytes(byteArrayOf(3)) }

        try {
            assertTrue(ThemeBackgroundStorage.removeIfOwned(wallpaper, directory))
            assertTrue(unrelated.exists())
            assertFalse(ThemeBackgroundStorage.removeIfOwned(unrelated, directory))
            assertTrue(unrelated.exists())
            assertFalse(ThemeBackgroundStorage.removeIfOwned(outside, directory))
            assertTrue(outside.exists())
        } finally {
            directory.deleteRecursively()
            otherDirectory.deleteRecursively()
        }
    }

    @Test
    fun orphanCleanupPreservesActiveWallpaperAndRemovesAbandonedFiles() {
        val directory = Files.createTempDirectory("audify-wallpaper-orphan-test").toFile()
        val active = java.io.File(directory, "wallpaper-active.img").apply { writeBytes(byteArrayOf(1)) }
        val orphan = java.io.File(directory, "wallpaper-orphan.img").apply { writeBytes(byteArrayOf(2)) }
        val staging = java.io.File(directory, "wallpaper-staging.tmp").apply { writeBytes(byteArrayOf(3)) }

        try {
            ThemeBackgroundStorage.removeOrphans(directory, active.toURI().toString())

            assertTrue(active.exists())
            assertFalse(orphan.exists())
            assertFalse(staging.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun imageDimensionsRejectInvalidAndExcessivePixelCounts() {
        ThemeBackgroundStorage.validateImageDimensions(4096, 4096)
        assertTrue(runCatching { ThemeBackgroundStorage.validateImageDimensions(0, 100) }.isFailure)
        assertTrue(runCatching { ThemeBackgroundStorage.validateImageDimensions(20_000, 20_000) }.isFailure)
    }
}
