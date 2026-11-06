// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.stats

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class BackupIoTest {
    @Test
    fun boundedReaderAcceptsTheExactLimit() {
        val bytes = ByteArray(128) { it.toByte() }

        assertArrayEquals(bytes, Backup.readBoundedBackup(ByteArrayInputStream(bytes), 128).toByteArray())
    }

    @Test
    fun boundedReaderRejectsOversizedInputBeforeReturningIt() {
        val bytes = ByteArray(129) { it.toByte() }

        assertThrows(IllegalArgumentException::class.java) {
            Backup.readBoundedBackup(ByteArrayInputStream(bytes), 128)
        }
    }

    @Test
    fun exportSizeValidationMatchesTheRestoreLimit() {
        Backup.validateBackupSize(128, 128)

        assertThrows(IllegalArgumentException::class.java) {
            Backup.validateBackupSize(129, 128)
        }
    }

    @Test
    fun atomicSnapshotLeavesOnlyTheCompleteFinalFile() {
        val directory = Files.createTempDirectory("audify-backup-atomic-test").toFile()
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        try {
            val target = Backup.writeBackupAtomically(directory, "snapshot.json", bytes)

            assertTrue(target.isFile)
            assertArrayEquals(bytes, target.readBytes())
            assertFalse(directory.resolve("snapshot.json.tmp").exists())
            assertEquals(listOf("snapshot.json"), directory.list()?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }
}
