package com.music.audify.data.stats

import com.music.audify.data.model.HomeShelf
import java.time.LocalDate
import java.time.LocalTime

/** The four local-time windows used to shape the Home discovery shelf. */
enum class ListeningHourBlock(val labelKey: String, private val hours: IntRange) {
    MORNING("morning", 6..10),
    AFTERNOON("afternoon", 11..16),
    EVENING("evening", 17..21),
    LATE_NIGHT("late_night", 22..23),
    ;

    fun contains(hour: Int): Boolean = hour in hours ||
        (this == LATE_NIGHT && hour in 0..5)

    fun plays(hourlyPlays: List<Int>): Int =
        (0..23).filter(::contains).sumOf { hourlyPlays.getOrElse(it) { 0 }.coerceAtLeast(0) }

    companion object {
        fun at(hour: Int): ListeningHourBlock? = entries.firstOrNull { it.contains(hour) }
    }
}

/**
 * Adds daily 24-hour play counters inside an inclusive local-date window.
 * Invalid dates and malformed/short legacy arrays are ignored safely.
 */
internal fun aggregateHourOfDayPlays(
    dailyCounts: Map<String, List<Int>>,
    firstDay: LocalDate,
    lastDay: LocalDate,
): List<Int> {
    if (lastDay.isBefore(firstDay)) return List(24) { 0 }
    val totals = MutableList(24) { 0 }
    dailyCounts.forEach { (dateKey, counts) ->
        val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return@forEach
        if (date < firstDay || date > lastDay) return@forEach
        repeat(24) { hour ->
            totals[hour] += counts.getOrElse(hour) { 0 }.coerceAtLeast(0)
        }
    }
    return totals
}

/**
 * Prefer the block matching the current local time. If it has no history,
 * choose the most-listened block; return null when the window has no plays.
 */
fun selectTimeAwareHourBlock(
    now: LocalTime,
    hourlyPlays: List<Int>,
): ListeningHourBlock? {
    val current = ListeningHourBlock.at(now.hour) ?: return null
    if (current.plays(hourlyPlays) > 0) return current
    return ListeningHourBlock.entries
        .map { block -> block to block.plays(hourlyPlays) }
        .filter { (_, plays) -> plays > 0 }
        .maxByOrNull { (_, plays) -> plays }
        ?.first
}

/**
 * Reuse an existing discovery shelf whose title/subtitle best fits the selected
 * time block. If the feed has no explicitly themed shelf, fall back to its
 * first non-Recents shelf rather than inventing or fetching new content.
 */
internal fun selectTimeAwareDiscoveryShelf(
    shelves: List<HomeShelf>,
    block: ListeningHourBlock,
): HomeShelf? {
    val eligible = shelves.filter { shelf ->
        !shelf.title.equals("Recents", ignoreCase = true) &&
            shelf.items.any { it.videoId != null || it.browseId != null }
    }
    val preferredTerms = when (block) {
        ListeningHourBlock.MORNING -> listOf("morning", "commute", "daily discover", "quick picks", "energy")
        ListeningHourBlock.AFTERNOON -> listOf("afternoon", "hits", "charts", "new releases", "for you")
        ListeningHourBlock.EVENING -> listOf("evening", "relax", "chill", "mix", "radio")
        ListeningHourBlock.LATE_NIGHT -> listOf("late night", "night", "relax", "chill", "sleep", "ambient")
    }
    return eligible.firstOrNull { shelf ->
        val searchable = "${shelf.title} ${shelf.subtitle}".lowercase()
        preferredTerms.any(searchable::contains)
    } ?: eligible.firstOrNull()
}
