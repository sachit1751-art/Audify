package com.music.audify.data.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocialPagingTest {

    @Test
    fun limitsInsideTheRangeAreUnchanged() {
        assertEquals(1, SocialPaging.clamp(1))
        assertEquals(SocialPaging.DEFAULT_PAGE_SIZE, SocialPaging.clamp(SocialPaging.DEFAULT_PAGE_SIZE))
        assertEquals(SocialPaging.MAX_PAGE_SIZE, SocialPaging.clamp(SocialPaging.MAX_PAGE_SIZE))
    }

    @Test
    fun limitsBelowOneBecomeOne() {
        assertEquals(1, SocialPaging.clamp(0))
        assertEquals(1, SocialPaging.clamp(-5))
        assertEquals(1, SocialPaging.clamp(Int.MIN_VALUE))
    }

    @Test
    fun limitsAboveTheCapBecomeTheCap() {
        assertEquals(SocialPaging.MAX_PAGE_SIZE, SocialPaging.clamp(SocialPaging.MAX_PAGE_SIZE + 1))
        assertEquals(SocialPaging.MAX_PAGE_SIZE, SocialPaging.clamp(Int.MAX_VALUE))
    }

    @Test
    fun theDefaultIsInsideTheAllowedRange() {
        assertTrue(SocialPaging.DEFAULT_PAGE_SIZE in 1..SocialPaging.MAX_PAGE_SIZE)
    }
}
