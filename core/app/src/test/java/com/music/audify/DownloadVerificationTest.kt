// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.download.DownloadFileCheckCandidate
import com.music.audify.download.DownloadFileCheckState
import com.music.audify.download.hasReadableFileHeader
import com.music.audify.download.selectDownloadFileCheckBatch
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadVerificationTest {

    @Test
    fun `file check requires a readable nonempty header`() {
        assertTrue(hasReadableFileHeader(ByteArrayInputStream(byteArrayOf(0x49, 0x44, 0x33))))
        assertFalse(hasReadableFileHeader(ByteArrayInputStream(byteArrayOf())))
    }

    @Test
    fun `batch prioritises unchecked files and deduplicates uri aliases`() {
        val candidates = listOf(
            DownloadFileCheckCandidate("checked-late", DownloadFileCheckState.AVAILABLE, 300L),
            DownloadFileCheckCandidate("unchecked", DownloadFileCheckState.UNCHECKED, null),
            DownloadFileCheckCandidate("checked-old", DownloadFileCheckState.UNAVAILABLE, 100L),
            DownloadFileCheckCandidate("unchecked", DownloadFileCheckState.UNCHECKED, null),
        )

        val batch = selectDownloadFileCheckBatch(candidates, limit = 2)

        assertEquals(listOf("unchecked", "checked-old"), batch.map { it.uri })
    }

    @Test
    fun `batch limit is respected and nonpositive limits do no work`() {
        val candidates = (1..4).map {
            DownloadFileCheckCandidate("uri-$it", DownloadFileCheckState.UNCHECKED, null)
        }

        assertEquals(3, selectDownloadFileCheckBatch(candidates, limit = 3).size)
        assertTrue(selectDownloadFileCheckBatch(candidates, limit = 0).isEmpty())
    }
}
