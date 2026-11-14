package com.music.audify.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitContentFilterTest {
    @Test
    fun hidesOnlyItemsExplicitlyMarkedAsExplicit() {
        assertFalse(isVisibleWithExplicitFilter(true, hideExplicitContent = true))
        assertTrue(isVisibleWithExplicitFilter(false, hideExplicitContent = true))
        assertTrue(isVisibleWithExplicitFilter(null, hideExplicitContent = true))
    }

    @Test
    fun disabledFilterKeepsEveryItemVisible() {
        assertTrue(isVisibleWithExplicitFilter(true, hideExplicitContent = false))
        assertTrue(isVisibleWithExplicitFilter(false, hideExplicitContent = false))
        assertTrue(isVisibleWithExplicitFilter(null, hideExplicitContent = false))
    }
}
