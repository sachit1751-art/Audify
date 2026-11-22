package com.music.audify.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingNavigationTest {
    @Test
    fun backWalksFromDiscoveryToWelcomeAndStopsAtRoot() {
        assertEquals(PAGE_WELCOME, previousOnboardingPage(PAGE_DISCOVERY))
        assertNull(previousOnboardingPage(PAGE_WELCOME))
    }

    @Test
    fun optionalNameReturnsToDiscovery() {
        assertEquals(PAGE_DISCOVERY, previousOnboardingPage(PAGE_NAME))
    }

    @Test
    fun readyPageReturnsToOptionalName() {
        assertEquals(PAGE_NAME, previousOnboardingPage(PAGE_MUSIC))
    }
}
