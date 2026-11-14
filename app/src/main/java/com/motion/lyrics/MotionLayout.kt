package com.motion.lyrics

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection

/** Geometri per huruf, di-cache sekali per hasil layout teks. */
internal class MotionLayout(
    val result: TextLayoutResult,
    val count: Int,
    val charIndex: IntArray,
    val wordIndex: IntArray,
    val lineIndex: IntArray,
    val left: FloatArray,
    val top: FloatArray,
    val right: FloatArray,
    val bottom: FloatArray,
    val glyphY: FloatArray,
    val rtl: BooleanArray,
    val glyphs: Array<TextLayoutResult>
)

internal fun buildMotionLayout(
    result: TextLayoutResult,
    measurer: TextMeasurer,
    style: TextStyle,
    words: List<MotionWord>
): MotionLayout {
    val text = result.layoutInput.text.text
    val wordOf = IntArray(text.length) { -1 }
    words.forEachIndexed { wi, w ->
        for (c in w.charStart.coerceAtLeast(0) until w.charEnd.coerceAtMost(text.length)) wordOf[c] = wi
    }
    val idx = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (!ch.isWhitespace() && !Character.isLowSurrogate(ch)) idx.add(i)
        i++
    }
    val n = idx.size
    val charIndex = IntArray(n); val wordIndex = IntArray(n); val lineIndex = IntArray(n)
    val l = FloatArray(n); val t = FloatArray(n); val r = FloatArray(n); val b = FloatArray(n)
    val gy = FloatArray(n); val rtl = BooleanArray(n)
    val glyphs = ArrayList<TextLayoutResult>(n)
    for (k in 0 until n) {
        val c = idx[k]
        val box = result.getBoundingBox(c)
        val line = result.getLineForOffset(c)
        val len = if (Character.isHighSurrogate(text[c]) && c + 1 < text.length) 2 else 1
        val g = measurer.measure(
            AnnotatedString(text.substring(c, c + len)),
            style = style, softWrap = false, maxLines = 1
        )
        charIndex[k] = c; wordIndex[k] = wordOf[c]; lineIndex[k] = line
        l[k] = box.left; t[k] = box.top; r[k] = box.right; b[k] = box.bottom
        gy[k] = result.getLineBaseline(line) - g.firstBaseline
        rtl[k] = result.getBidiRunDirection(c) == ResolvedTextDirection.Rtl
        glyphs.add(g)
    }
    return MotionLayout(
        result, n, charIndex, wordIndex, lineIndex, l, t, r, b, gy, rtl, glyphs.toTypedArray()
    )
}