package com.music.audify.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A half-open playback range: start inclusive, end exclusive. */
data class SkipSegment(val startMs: Long, val endMs: Long)

object SkipSegmentStore {
    const val MAX_SEGMENTS_PER_TRACK = 25
    const val MAX_TRACKS = 500
    private const val MAX_STORE_CHARS = 512 * 1024
    private const val MAX_DURATION_MS = 24L * 60 * 60 * 1_000
    private val json = Json { ignoreUnknownKeys = true }

    fun normalize(segments: List<SkipSegment>): List<SkipSegment> {
        val sorted = segments.asSequence()
            .filter { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= MAX_DURATION_MS }
            .sortedBy { it.startMs }
            .toList()
        if (sorted.isEmpty()) return emptyList()
        val merged = ArrayList<SkipSegment>(minOf(sorted.size, MAX_SEGMENTS_PER_TRACK))
        for (segment in sorted) {
            val last = merged.lastOrNull()
            if (last != null && segment.startMs <= last.endMs) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, segment.endMs))
            } else if (merged.size < MAX_SEGMENTS_PER_TRACK) {
                merged += segment
            }
        }
        return merged
    }

    fun encode(byTrack: Map<String, List<SkipSegment>>): String = buildJsonObject {
        byTrack.entries.asSequence()
            .filter { it.key.isNotBlank() && it.key.length <= 128 }
            .take(MAX_TRACKS)
            .forEach { (trackId, segments) ->
                val valid = normalize(segments)
                if (valid.isNotEmpty()) put(trackId, buildJsonArray {
                    valid.forEach { segment -> add(buildJsonObject {
                        put("startMs", segment.startMs)
                        put("endMs", segment.endMs)
                    }) }
                })
            }
    }.toString()

    fun decode(raw: String?): Map<String, List<SkipSegment>> {
        if (raw.isNullOrBlank() || raw.length > MAX_STORE_CHARS) return emptyMap()
        return runCatching {
            json.parseToJsonElement(raw).jsonObject.entries.asSequence()
                .filter { it.key.isNotBlank() && it.key.length <= 128 }
                .take(MAX_TRACKS)
                .mapNotNull { (trackId, element) ->
                    val parsed = runCatching {
                        element.jsonArray.mapNotNull { item ->
                            val fields = item.jsonObject
                            val start = fields["startMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                            val end = fields["endMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                            SkipSegment(start, end)
                        }
                    }.getOrDefault(emptyList())
                    trackId to normalize(parsed)
                }
                .filter { it.second.isNotEmpty() }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    /** Accepts m:ss, mm:ss.mmm or h:mm:ss.mmm. */
    fun parseTimestamp(value: String): Long? {
        val match = Regex("^(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?$")
            .matchEntire(value.trim()) ?: return null
        val hours = match.groupValues[1].takeIf(String::isNotEmpty)?.toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        if (seconds >= 60 || (match.groupValues[1].isNotEmpty() && minutes >= 60)) return null
        val millis = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: 0L
        val total = ((hours * 60 + minutes) * 60 + seconds) * 1_000 + millis
        return total.takeIf { it <= MAX_DURATION_MS }
    }

    fun formatTimestamp(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0)
        val totalSeconds = safe / 1_000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3_600
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(totalSeconds / 60, seconds)
    }

    fun matchingSegment(videoId: String, positionMs: Long, byTrack: Map<String, List<SkipSegment>>): SkipSegment? =
        byTrack[videoId]?.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
}
