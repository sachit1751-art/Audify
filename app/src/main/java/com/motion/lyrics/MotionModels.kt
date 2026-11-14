package com.motion.lyrics

/**
 * Adapter ringan dari model lirik yang sudah ada.
 * charStart/charEnd = indeks karakter di teks BARIS (end eksklusif).
 */
data class MotionWord(
    val charStart: Int,
    val charEnd: Int,
    val startMs: Long,
    val endMs: Long
)