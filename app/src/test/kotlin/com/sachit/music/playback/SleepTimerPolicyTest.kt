/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Rules for the automatic sleep timer, pinned on the JVM.
 *
 * These decisions used to be unreachable without an ExoPlayer, a bound service and a real clock,
 * because they were interleaved with seven blocking preference reads.
 */
class SleepTimerPolicyTest {
    // 2026-10-05 is a Monday, so dayOfWeek is 0 and the day indices run 0 (Mon) to 6 (Sun).
    private val monday: LocalDate = LocalDate.of(2026, 10, 5)
    private val saturday: LocalDate = LocalDate.of(2026, 10, 10)
    private val sunday: LocalDate = LocalDate.of(2026, 10, 11)

    private fun evaluate(
        schedule: SleepTimerSchedule,
        date: LocalDate = monday,
        time: LocalTime = LocalTime.of(12, 0),
    ) = SleepTimerPolicy.evaluate(schedule, date, time)

    @Test
    fun `starts inside the daily window`() {
        val decision = evaluate(SleepTimerSchedule(repeat = "daily", startTime = "09:00", endTime = "23:00"))

        assertEquals(SleepTimerDecision.Start(30), decision)
    }

    @Test
    fun `does not start outside the daily window`() {
        val decision =
            evaluate(
                SleepTimerSchedule(startTime = "09:00", endTime = "23:00"),
                time = LocalTime.of(8, 59),
            )

        assertEquals(SleepTimerDecision.OutsideWindow, decision)
    }

    @Test
    fun `window bounds are exclusive`() {
        val schedule = SleepTimerSchedule(startTime = "09:00", endTime = "23:00")

        assertEquals(SleepTimerDecision.OutsideWindow, evaluate(schedule, time = LocalTime.of(9, 0)))
        assertEquals(SleepTimerDecision.OutsideWindow, evaluate(schedule, time = LocalTime.of(23, 0)))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, time = LocalTime.of(9, 1)))
    }

    @Test
    fun `carries the configured duration`() {
        val decision =
            evaluate(
                SleepTimerSchedule(startTime = "09:00", endTime = "23:00", defaultMinutes = 45),
                time = LocalTime.of(10, 0),
            )

        assertEquals(SleepTimerDecision.Start(45), decision)
    }

    @Test
    fun `overnight window covers both sides of midnight`() {
        val schedule = SleepTimerSchedule(repeat = "daily", startTime = "22:00", endTime = "06:00")

        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = sunday, time = LocalTime.of(23, 30)))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = sunday, time = LocalTime.of(2, 0)))
        // Midday is outside an overnight window, unlike a window that spans it.
        assertEquals(SleepTimerDecision.OutsideWindow, evaluate(schedule, date = sunday, time = LocalTime.of(12, 0)))
    }

    @Test
    fun `weekdays mode excludes the weekend`() {
        val schedule = SleepTimerSchedule(repeat = "weekdays", startTime = "09:00", endTime = "23:00")

        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = monday))
        assertEquals(SleepTimerDecision.DayNotAllowed, evaluate(schedule, date = saturday))
        assertEquals(SleepTimerDecision.DayNotAllowed, evaluate(schedule, date = sunday))
    }

    @Test
    fun `weekends mode excludes weekdays`() {
        val schedule = SleepTimerSchedule(repeat = "weekends", startTime = "09:00", endTime = "23:00")

        assertEquals(SleepTimerDecision.DayNotAllowed, evaluate(schedule, date = monday))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = saturday))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = sunday))
    }

    @Test
    fun `sunday is the last day index`() {
        // Guards the Monday-is-zero indexing the stored day indices rely on.
        assertEquals(0, SleepTimerPolicy.dayIndex(monday))
        assertEquals(5, SleepTimerPolicy.dayIndex(saturday))
        assertEquals(6, SleepTimerPolicy.dayIndex(sunday))
    }

    @Test
    fun `custom days mode honours the stored day list`() {
        val schedule =
            SleepTimerSchedule(
                repeat = "custom",
                customDays = "5, 6",
                startTime = "09:00",
                endTime = "23:00",
            )

        assertEquals(SleepTimerDecision.DayNotAllowed, evaluate(schedule, date = monday))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = saturday))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = sunday))
    }

    @Test
    fun `custom days mode tolerates blank entries`() {
        val schedule =
            SleepTimerSchedule(
                repeat = "custom",
                customDays = ",5,,6,",
                startTime = "09:00",
                endTime = "23:00",
            )

        assertEquals(SleepTimerDecision.DayNotAllowed, evaluate(schedule, date = monday))
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = saturday))
    }

    @Test
    fun `unrecognised repeat mode never starts`() {
        val decision = evaluate(SleepTimerSchedule(repeat = "hourly", startTime = "00:00", endTime = "23:59"))

        assertEquals(SleepTimerDecision.DayNotAllowed, decision)
    }

    @Test
    fun `per-day times override the global window`() {
        val schedule =
            SleepTimerSchedule(
                repeat = "weekdays",
                startTime = "09:00",
                endTime = "12:00",
                dayTimes = "0=22:00-23:00",
            )

        // Monday has an override, so the global window must not apply.
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = monday, time = LocalTime.of(22, 30)))
        assertEquals(SleepTimerDecision.OutsideWindow, evaluate(schedule, date = monday, time = LocalTime.of(10, 0)))
    }

    @Test
    fun `per-day times fall back to the global window when a day has no entry`() {
        val schedule =
            SleepTimerSchedule(
                repeat = "weekdays",
                startTime = "09:00",
                endTime = "12:00",
                dayTimes = "0=22:00-23:00",
            )

        // Tuesday has no override, so the global window applies.
        assertEquals(SleepTimerDecision.Start(30), evaluate(schedule, date = monday.plusDays(1), time = LocalTime.of(10, 0)))
    }

    @Test
    fun `daily mode ignores the per-day map`() {
        val schedule =
            SleepTimerSchedule(
                repeat = "daily",
                startTime = "09:00",
                endTime = "12:00",
                dayTimes = "0=22:00-23:00",
            )

        assertEquals(SleepTimerDecision.OutsideWindow, evaluate(schedule, date = monday, time = LocalTime.of(22, 30)))
    }

    @Test
    fun `malformed per-day entries are skipped rather than fatal`() {
        val parsed = SleepTimerPolicy.parseDayTimes("0=22:00-23:00;bad;1;2=09:00;3=10:00-11:00")

        assertEquals(mapOf(0 to ("22:00" to "23:00"), 3 to ("10:00" to "11:00")), parsed)
    }

    @Test
    fun `unparseable time reports an invalid schedule instead of throwing`() {
        val decision = evaluate(SleepTimerSchedule(startTime = "not-a-time", endTime = "23:00"))

        assertEquals(SleepTimerDecision.InvalidSchedule, decision)
    }

    @Test
    fun `empty defaults describe a daytime daily window`() {
        val decision = evaluate(SleepTimerSchedule(), time = LocalTime.of(10, 0))

        assertEquals(SleepTimerDecision.Start(SleepTimerSchedule.DEFAULT_MINUTES), decision)
    }
}
