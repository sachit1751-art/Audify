// Adapted from Zenith (https://github.com/rezapranata089-bit/Zenith), GPL-3.0; Audify is also GPL-3.0.
package com.motion.lyrics

import com.music.audify.data.lyrics.LyricLine

/** Ubah kata-kata LyricLine menjadi MotionWord (indeks karakter di teks baris, end eksklusif). */
fun LyricLine.toMotionWords(): List<MotionWord> {
    val spans = wordSpans
    val out = ArrayList<MotionWord>(spans.size)
    for (i in spans.indices) {
        val span = spans[i]
        val word = words[i]
        val end = (span.last + 1).coerceAtMost(text.length)
        if (span.first >= end) continue
        out.add(MotionWord(span.first, end, word.startMs, maxOf(word.startMs + 1, word.endMs)))
    }
    return out
}