package com.music.audify.data.stats

import java.math.BigDecimal
import java.math.RoundingMode

/** One track's listening totals inside one calendar-month bucket. */
data class ListeningSummaryCsvRow(
    val songId: String,
    val month: String,
    val song: String,
    val artist: String,
    val album: String,
    val minutes: String,
    val playCount: Int,
)

/**
 * Builds a CSV from the monthly aggregates retained by [ListeningStats].
 * Individual play events and their timestamps are not stored by this app.
 */
object ListeningSummaryCsv {
    private const val NOTE =
        "# Monthly listening summary. Individual play timestamps are not stored."
    private val HEADER = listOf("song_id", "month", "song", "artist", "album", "minutes", "play_count")

    fun rows(buckets: List<StoredBucket>): List<ListeningSummaryCsvRow> =
        buckets.flatMap { bucket ->
            bucket.tracks.mapNotNull { track ->
                if (track.ms <= 0L && track.plays <= 0) return@mapNotNull null
                ListeningSummaryCsvRow(
                    songId = track.id,
                    month = bucket.month,
                    song = track.title,
                    artist = track.artist,
                    album = track.album.orEmpty(),
                    minutes = BigDecimal.valueOf(track.ms)
                        .divide(BigDecimal.valueOf(60_000L), 2, RoundingMode.HALF_UP)
                        .toPlainString(),
                    playCount = track.plays,
                )
            }
        }.sortedWith(compareBy<ListeningSummaryCsvRow> { it.month }
            .thenBy { it.song.lowercase() }
            .thenBy { it.artist.lowercase() })

    fun write(buckets: List<StoredBucket>): String = buildString {
        append(NOTE).append("\r\n")
        append(HEADER.joinToString(",", transform = ::escape)).append("\r\n")
        rows(buckets).forEach { row ->
            append(
                listOf(row.songId, row.month, row.song, row.artist, row.album, row.minutes, row.playCount.toString())
                    .joinToString(",", transform = ::escape),
            )
            append("\r\n")
        }
    }

    /** RFC 4180 field escaping, including commas, quotes and embedded newlines. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
