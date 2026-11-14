package com.motion.lyrics

import kotlin.math.max
import kotlin.math.min

internal class HeadOut {
    var head = 0f
    var sung = 0f
}

/** Karaoke head kontinu dalam ruang karakter, memakai posisi playback yang ada. */
internal fun computeHead(words: List<MotionWord>, pos: Long, out: HeadOut) {
    val first = words[0]
    if (pos < first.startMs) {
        out.head = first.charStart.toFloat(); out.sung = 0f; return
    }
    var lo = 0
    var hi = words.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (words[mid].startMs <= pos) lo = mid else hi = mid - 1
    }
    val w = words[lo]
    val nx = words.getOrNull(lo + 1)
    val dur = (w.endMs - w.startMs).coerceAtLeast(1L)
    val gap = if (nx != null) nx.startMs - w.endMs else Long.MAX_VALUE
    // Spasi mendapat sebagian durasi kata sebelumnya bila hampir tanpa jeda.
    val share = if (nx != null && gap < MotionTuning.NearGapMs)
        min(dur.toFloat() * MotionTuning.SpaceShare, MotionTuning.MaxSpaceShareMs) else 0f
    val effEnd = max(w.endMs - share.toLong(), w.startMs + 1)

    if (pos < effEnd) {
        // Head sampai ke huruf terakhir sedikit sebelum kata berakhir, jadi huruf
        // terakhir (mis. "e" di "sure") sudah terang penuh saat vokal masih ditahan.
        val frac = (pos - w.startMs).toFloat() /
            ((effEnd - w.startMs).toFloat() * MotionTuning.WordReachFraction)
        out.head = w.charStart + (w.charEnd - w.charStart) * frac.coerceIn(0f, 1f)
        out.sung = 1f
        return
    }
    if (nx == null) {
        out.head = w.charEnd.toFloat()
        out.sung = if (pos < effEnd + 400) 0.6f else 0f
        return
    }
    val travelStart = max(effEnd, nx.startMs - MotionTuning.MaxTravelMs)
    val through = ((pos - travelStart).toFloat() /
        (nx.startMs - travelStart).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    out.head = w.charEnd + (nx.charStart - w.charEnd) * smoothstep(through)
    out.sung = if (gap > 1500L) 0.2f else 0.5f
}