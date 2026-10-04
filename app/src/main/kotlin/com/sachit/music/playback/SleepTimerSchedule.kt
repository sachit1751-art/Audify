/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The automatic sleep timer's configuration, in the shape settings store it.
 *
 * Times stay as "HH:mm" strings and the per-day map as "0=09:00-23:00;1=22:00-06:00" because that
 * is what the settings screen writes; parsing them is the policy's job, not the caller's.
 */
data class SleepTimerSchedule(
    val repeat: String = REPEAT_DAILY,
    val startTime: String = DEFAULT_START,
    val endTime: String = DEFAULT_END,
    val defaultMinutes: Int = DEFAULT_MINUTES,
    val customDays: String = "0,1,2,3,4",
    val dayTimes: String = "",
) {
    companion object {
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKDAYS = "weekdays"
        const val REPEAT_WEEKENDS = "weekends"
        const val REPEAT_WEEKDAYS_WEEKENDS = "weekdays_weekends"
        const val REPEAT_CUSTOM = "custom"
        const val DEFAULT_START = "09:00"
        const val DEFAULT_END = "23:00"
        const val DEFAULT_MINUTES = 30
    }
}

/** Why the automatic sleep timer did or did not start. */
sealed interface SleepTimerDecision {
    /** The setting is off. */
    data object NotEnabled : SleepTimerDecision

    /** A timer is already counting down; starting another would double it up. */
    data object AlreadyActive : SleepTimerDecision

    /** Today's repeat mode excludes this day. */
    data object DayNotAllowed : SleepTimerDecision

    /** Today is allowed, but now is outside the configured window. */
    data object OutsideWindow : SleepTimerDecision

    /** A stored time could not be parsed. Treated as "do not start" rather than throwing. */
    data object InvalidSchedule : SleepTimerDecision

    /** Start a timer for [minutes]. */
    data class Start(val minutes: Int) : SleepTimerDecision
}

/**
 * Decides whether the automatic sleep timer should start for a given schedule and moment.
 *
 * Pure: the caller supplies the date and time, so the rules can be tested at any moment of any day
 * without a clock, a service or a DataStore. The window rules are unchanged from the implementation
 * this replaced — in particular the boundary comparisons stay strict and an overnight range is
 * still expressed as "after end OR before start".
 */
object SleepTimerPolicy {
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    fun evaluate(
        schedule: SleepTimerSchedule,
        date: LocalDate,
        time: LocalTime,
    ): SleepTimerDecision {
        if (!isDayAllowed(schedule, dayIndex(date))) return SleepTimerDecision.DayNotAllowed

        // Both resolving the window and parsing it are guarded: a stored time the user typed by hand
        // can be unparseable, and that must mean "do not start" rather than an exception.
        val window =
            try {
                val (startText, endText) = windowFor(schedule, dayIndex(date))
                LocalTime.parse(startText, TIME_FORMAT) to LocalTime.parse(endText, TIME_FORMAT)
            } catch (_: DateTimeParseException) {
                return SleepTimerDecision.InvalidSchedule
            }

        val (start, end) = window

        val inRange =
            if (end.isAfter(start)) {
                time.isAfter(start) && time.isBefore(end)
            } else {
                // Overnight window, e.g. 22:00-06:00.
                time.isAfter(start) || time.isBefore(end)
            }

        return if (inRange) SleepTimerDecision.Start(schedule.defaultMinutes) else SleepTimerDecision.OutsideWindow
    }

    /**
     * Day index used by the stored configuration: Monday is 0 through Sunday 6.
     *
     * [LocalDate.getDayOfWeek] is 1-based and Sunday is 7, so it is shifted rather than
     * modulo-mapped — the stored indices are what the settings screen has always written.
     */
    internal fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value - 1

    private fun isDayAllowed(
        schedule: SleepTimerSchedule,
        dayIndex: Int,
    ): Boolean =
        when (schedule.repeat) {
            SleepTimerSchedule.REPEAT_DAILY,
            SleepTimerSchedule.REPEAT_WEEKDAYS_WEEKENDS,
            -> true

            SleepTimerSchedule.REPEAT_WEEKDAYS -> dayIndex in 0..4
            SleepTimerSchedule.REPEAT_WEEKENDS -> dayIndex in 5..6

            SleepTimerSchedule.REPEAT_CUSTOM ->
                schedule.customDays.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(dayIndex)

            // An unrecognised repeat mode means the stored config is not understood; do not start.
            else -> false
        }

    private fun windowFor(
        schedule: SleepTimerSchedule,
        dayIndex: Int,
    ): Pair<String, String> {
        // "daily" is the one mode with a single global window. Every other mode may override it per
        // day, and falls back to the global window for a day it has no entry for.
        if (schedule.repeat == SleepTimerSchedule.REPEAT_DAILY) {
            return schedule.startTime to schedule.endTime
        }
        return parseDayTimes(schedule.dayTimes)[dayIndex] ?: (schedule.startTime to schedule.endTime)
    }

    /** Parses "0=09:00-23:00;1=22:00-06:00" into day index to start/end pair. */
    internal fun parseDayTimes(raw: String): Map<Int, Pair<String, String>> {
        if (raw.isBlank()) return emptyMap()
        return raw
            .split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size != 2) return@mapNotNull null
                val dayIndex = parts[0].toIntOrNull() ?: return@mapNotNull null
                val times = parts[1].split("-")
                if (times.size != 2) return@mapNotNull null
                dayIndex to (times[0] to times[1])
            }.toMap()
    }
}
