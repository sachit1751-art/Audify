package com.music.audify

import com.music.audify.ui.replay.shouldShowWeeklyRecap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyRecapVisibilityTest {
    @Test
    fun hiddenWhenDisabledOrThereIsNoListeningData() {
        assertFalse(shouldShowWeeklyRecap(enabled = false, hasData = true, seenWeek = 0, currentWeek = 10))
        assertFalse(shouldShowWeeklyRecap(enabled = true, hasData = false, seenWeek = 0, currentWeek = 10))
    }

    @Test
    fun shownUntilOpenedOrDismissedDuringCurrentWeek() {
        assertTrue(shouldShowWeeklyRecap(enabled = true, hasData = true, seenWeek = 0, currentWeek = 10))
        assertFalse(shouldShowWeeklyRecap(enabled = true, hasData = true, seenWeek = 10, currentWeek = 10))
    }

    @Test
    fun becomesAvailableAgainWhenWeekRollsOver() {
        assertTrue(shouldShowWeeklyRecap(enabled = true, hasData = true, seenWeek = 10, currentWeek = 11))
    }
}
