package com.sachit.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan 013: the mini player lyric line relies on two behaviors of [LyricsUtils]:
 * plain (unsynced) lyrics must parse to no lines so they stay hidden in the mini player,
 * and [LyricsUtils.findCurrentLineIndex] must resolve the active line without ever
 * indexing out of bounds.
 */
class MiniPlayerLyricLineTest {

    private val syncedLyrics = """
        [00:01.00]First line
        [00:05.00]Second line
        [00:10.00]Third line
    """.trimIndent()

    private val plainLyrics = """
        First line
        Second line
        Third line
    """.trimIndent()

    @Test
    fun `synced lyrics parse to timestamped entries`() {
        val entries = LyricsUtils.parseLyrics(syncedLyrics)
        assertEquals(3, entries.size)
        assertEquals(1000L, entries[0].time)
        assertEquals("Second line", entries[1].text)
        assertEquals(10000L, entries[2].time)
    }

    @Test
    fun `plain lyrics parse to no entries so they stay hidden in the mini player`() {
        assertTrue(LyricsUtils.parseLyrics(plainLyrics).isEmpty())
    }

    @Test
    fun `blank lyrics parse to no entries`() {
        assertTrue(LyricsUtils.parseLyrics("").isEmpty())
        assertTrue(LyricsUtils.parseLyrics("   ").isEmpty())
    }

    @Test
    fun `position before the first line resolves to index -1`() {
        val entries = LyricsUtils.parseLyrics(syncedLyrics)
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(entries, 0L))
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(entries, 500L))
    }

    @Test
    fun `position at a line's exact timestamp activates that line`() {
        val entries = LyricsUtils.parseLyrics(syncedLyrics)
        // The 100ms threshold only looks forward: a line is active from its own timestamp on
        val index = LyricsUtils.findCurrentLineIndex(entries, 5000L)
        assertEquals(1, index)
        assertEquals("Second line", entries[index].text)
    }

    @Test
    fun `position inside a line resolves to that line`() {
        val entries = LyricsUtils.parseLyrics(syncedLyrics)
        val index = LyricsUtils.findCurrentLineIndex(entries, 7000L)
        assertEquals(1, index)
        assertEquals("Second line", entries[index].text)
    }

    @Test
    fun `position past the last line clamps to the last index`() {
        val entries = LyricsUtils.parseLyrics(syncedLyrics)
        val index = LyricsUtils.findCurrentLineIndex(entries, 60_000L)
        assertEquals(entries.lastIndex, index)
        assertEquals("Third line", entries[index].text)
    }

    @Test
    fun `empty entry list resolves to -1 without throwing`() {
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(emptyList(), 0L))
    }
}
