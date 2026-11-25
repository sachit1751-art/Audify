package com.music.audify.data.settings

import kotlin.math.roundToLong

/** A half-open playback interval: playback jumps when it enters [startMs, endMs). */
data class SkipSegment(
    val startMs: Long,
    val endMs: Long,
) {
    val isValid: Boolean get() = startMs >= 0L && endMs > startMs
}

/** Optional in/out points: only this portion of a track is played. */
data class TrackPlaybackWindow(
    val startMs: Long,
    val endMs: Long,
) {
    val isValid: Boolean get() = startMs >= 0L && endMs > startMs
}

/** Parses m:ss or h:mm:ss without accepting ambiguous or negative values. */
fun parseSkipTimestamp(value: String): Long? {
    val parts = value.trim().split(':')
    if (parts.size !in 2..3 || parts.any { it.isBlank() || it.length > 9 }) return null
    val last = parts.last().toDoubleOrNull() ?: return null
    if (!last.isFinite() || last < 0.0 || last >= 60.0) return null
    val leading = parts.dropLast(1).map {
        if (it.any { char -> !char.isDigit() }) return null
        it.toLongOrNull() ?: return null
    }
    if (leading.first() < 0L || leading.drop(1).any { it !in 0..59 }) return null
    val seconds = if (leading.size == 1) leading[0] * 60.0 + last
        else leading[0] * 3600.0 + leading[1] * 60.0 + last
    return seconds.takeIf { it <= Long.MAX_VALUE / 1000.0 }?.times(1000.0)?.roundToLong()
}

fun formatSkipTimestamp(positionMs: Long): String {
    val seconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    return if (minutes >= 60L) {
        "%d:%02d:%02d".format(minutes / 60L, minutes % 60L, remainder)
    } else {
        "%02d:%02d".format(minutes, remainder)
    }
}

/** Sorts, drops invalid ranges and merges overlaps so one seek handles each region. */
fun normalizeSkipSegments(segments: List<SkipSegment>): List<SkipSegment> {
    val sorted = segments.filter { it.isValid }.sortedBy { it.startMs }
    if (sorted.isEmpty()) return emptyList()
    val result = ArrayList<SkipSegment>(sorted.size)
    for (segment in sorted) {
        val previous = result.lastOrNull()
        if (previous != null && segment.startMs <= previous.endMs) {
            result[result.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, segment.endMs))
        } else {
            result += segment
        }
    }
    return result
}

/** Returns the range containing the playhead; both boundaries are deterministic. */
fun List<SkipSegment>.activeAt(positionMs: Long, durationMs: Long): SkipSegment? =
    firstOrNull { positionMs >= it.startMs && positionMs < it.endMs && it.endMs <= durationMs }

/** Small, fail-closed codec and lookup helpers for the per-track skip-range store. */
object SkipSegmentStore {
    fun normalize(segments: List<SkipSegment>): List<SkipSegment> = normalizeSkipSegments(segments)

    fun encode(ranges: Map<String, List<SkipSegment>>): String {
        return ranges.mapNotNull { (id, segments) ->
            val normalized = normalize(segments)
            if (normalized.isEmpty()) null else
                "${quoteJson(id)}:[${normalized.joinToString(",") { "[${it.startMs},${it.endMs}]" }}]"
        }.joinToString(prefix = "{", postfix = "}", separator = ",")
    }

    fun decode(raw: String): Map<String, List<SkipSegment>> {
        if (raw.isBlank() || raw.length > MAX_ENCODED_LENGTH) return emptyMap()
        return runCatching {
            var index = 0
            fun whitespace() { while (index < raw.length && raw[index].isWhitespace()) index++ }
            fun expect(char: Char) { whitespace(); require(index < raw.length && raw[index] == char); index++ }
            fun string(): String {
                expect('"')
                return buildString {
                    while (index < raw.length) {
                        val char = raw[index++]
                        when (char) {
                            '"' -> return@buildString
                            '\\' -> {
                                require(index < raw.length)
                                when (val escaped = raw[index++]) {
                                    '"', '\\', '/' -> append(escaped)
                                    'b' -> append('\b')
                                    'f' -> append('\u000C')
                                    'n' -> append('\n')
                                    'r' -> append('\r')
                                    't' -> append('\t')
                                    'u' -> {
                                        require(index + 4 <= raw.length)
                                        append(raw.substring(index, index + 4).toInt(16).toChar())
                                        index += 4
                                    }
                                    else -> error("invalid JSON escape")
                                }
                            }
                            else -> { require(char.code >= 0x20); append(char) }
                        }
                    }
                    error("unterminated JSON string")
                }
            }
            fun number(): Long {
                whitespace()
                val start = index
                if (index < raw.length && raw[index] == '-') index++
                while (index < raw.length && raw[index].isDigit()) index++
                require(index > start)
                return raw.substring(start, index).toLong()
            }
            val decoded = LinkedHashMap<String, List<SkipSegment>>()
            expect('{')
            whitespace()
            if (index < raw.length && raw[index] == '}') { index++; return decoded }
            while (index < raw.length) {
                val id = string()
                expect(':')
                expect('[')
                val ranges = ArrayList<SkipSegment>()
                whitespace()
                if (index < raw.length && raw[index] != ']') {
                    while (true) {
                        expect('[')
                        val start = number()
                        expect(',')
                        val end = number()
                        expect(']')
                        ranges += SkipSegment(start, end)
                        whitespace()
                        if (index < raw.length && raw[index] == ',') { index++; continue }
                        break
                    }
                }
                expect(']')
                normalize(ranges).takeIf { it.isNotEmpty() }?.let { decoded[id] = it }
                whitespace()
                if (index < raw.length && raw[index] == ',') { index++; continue }
                break
            }
            expect('}')
            whitespace()
            require(index == raw.length)
            decoded
        }.getOrDefault(emptyMap())
    }

    fun parseTimestamp(value: String): Long? = parseSkipTimestamp(value)

    fun matchingSegment(trackId: String, positionMs: Long, ranges: Map<String, List<SkipSegment>>): SkipSegment? =
        ranges[trackId]?.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }

    private const val MAX_ENCODED_LENGTH = 512 * 1024

    private fun quoteJson(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
