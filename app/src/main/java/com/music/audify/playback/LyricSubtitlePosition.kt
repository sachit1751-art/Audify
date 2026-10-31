package com.music.audify.playback

/** Maps the raw Media3 position onto the user-adjusted lyric timeline. */
internal fun adjustedLyricSubtitlePosition(positionMs: Long, offsetMs: Int): Long =
    (positionMs - offsetMs.toLong()).coerceAtLeast(0L)
