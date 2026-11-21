package com.music.audify.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackWelcomeScheduleTest {
    @Test
    fun firstLaunchShowsWelcome() {
        assertTrue(isFeedbackWelcomeDue(nowMillis = 1_000L, lastDismissedAtMillis = 0L))
    }

    @Test
    fun welcomeWaitsUntilEightFullDaysHavePassed() {
        val dismissedAt = 10_000L
        val eightDays = 8L * 24L * 60L * 60L * 1000L

        assertFalse(isFeedbackWelcomeDue(dismissedAt + eightDays - 1L, dismissedAt))
        assertTrue(isFeedbackWelcomeDue(dismissedAt + eightDays, dismissedAt))
    }
}
