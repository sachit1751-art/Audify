package com.music.audify

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.ui.onboarding.OnboardingScreen
import com.music.audify.data.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signInIsOptionalAndSkipFinishesOnboardingFromTheWelcomePage() {
        var finished = false
        var signInRequested = false

        composeRule.setContent {
            MaterialTheme {
                OnboardingScreen(
                    signedIn = false,
                    spotifyConnected = false,
                    initialName = "",
                    onNameChosen = {},
                    onSignIn = { signInRequested = true },
                    onSpotifyConnect = {},
                    onListenTogether = {},
                    onFinish = { finished = true },
                )
            }
        }

        composeRule.onNodeWithText("Find it.\nFeel it.\nShare it.").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").performClick()

        composeRule.runOnIdle {
            assertTrue(finished)
            assertFalse(signInRequested)
        }
    }

    @Test
    fun progressAndSkipExposeAccessibleSemanticsAndReducedMotionChangesPagesImmediately() {
        AppSettings.reduceAnimation.value = true
        composeRule.mainClock.autoAdvance = false

        try {
            composeRule.setContent {
                MaterialTheme {
                    OnboardingScreen(
                        signedIn = false,
                        spotifyConnected = false,
                        initialName = "",
                        onNameChosen = {},
                        onSignIn = {},
                        onSpotifyConnect = {},
                        onListenTogether = {},
                        onFinish = {},
                    )
                }
            }

            composeRule.onNodeWithContentDescription("Step 1 of 4")
                .assertIsDisplayed()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ProgressBarRangeInfo,
                        ProgressBarRangeInfo(1f, 1f..4f),
                    ),
                )
            composeRule.onNodeWithText("Skip")
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

            composeRule.onNodeWithText("Get started").performClick()
            composeRule.onNodeWithText("Your music.\nYour way.").assertIsDisplayed()
        } finally {
            AppSettings.reduceAnimation.value = false
            composeRule.mainClock.autoAdvance = true
        }
    }
}
