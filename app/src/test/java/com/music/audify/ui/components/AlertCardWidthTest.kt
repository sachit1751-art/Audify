package com.music.audify.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertCardWidthTest {
    @Test
    fun phoneWidthsKeepSixteenDpSideMargins() {
        assertEquals(288.dp, alertCardWidth(320.dp))
        assertEquals(328.dp, alertCardWidth(360.dp))
        assertEquals(368.dp, alertCardWidth(400.dp))
    }

    @Test
    fun wideWindowsCapTheCardAtFourHundredTwentyDp() {
        assertEquals(416.dp, alertCardWidth(448.dp))
        assertEquals(420.dp, alertCardWidth(480.dp))
        assertEquals(420.dp, alertCardWidth(800.dp))
    }

    @Test
    fun translationPickerKeepsPhoneMarginsAndExpandsForLandscape() {
        assertEquals(288.dp, translationDialogCardWidth(320.dp))
        assertEquals(328.dp, translationDialogCardWidth(360.dp))
        assertEquals(368.dp, translationDialogCardWidth(400.dp))
        assertEquals(448.dp, translationDialogCardWidth(480.dp))
        assertEquals(568.dp, translationDialogCardWidth(600.dp))
        assertEquals(640.dp, translationDialogCardWidth(672.dp))
        assertEquals(640.dp, translationDialogCardWidth(800.dp))
        assertEquals(640.dp, translationDialogCardWidth(1000.dp))
    }

    @Test
    fun narrowAvailableWidthNeverProducesANegativeCardWidth() {
        assertEquals(0.dp, alertCardWidth(16.dp))
        assertEquals(0.dp, alertCardWidth((-8).dp))
        assertEquals(0.dp, translationDialogCardWidth(16.dp))
        assertEquals(0.dp, translationDialogCardWidth((-8).dp))
    }

    @Test
    fun translationPickerScrollsItsChromeOnlyWhenTheViewportCannotFitIt() {
        assertEquals(true, translationDialogNeedsOuterScroll(200.dp))
        assertEquals(true, translationDialogNeedsOuterScroll(239.dp))
        assertEquals(false, translationDialogNeedsOuterScroll(240.dp))
        assertEquals(false, translationDialogNeedsOuterScroll(320.dp))
    }
}
