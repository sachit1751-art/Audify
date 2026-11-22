package com.music.audify

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.ui.onboarding.OnboardingScreen
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
    fun discoveryOffersAllThreeExperiencesAndNameCanBeSkipped() {
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

        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("Your music.\nYour way.").assertIsDisplayed()
        composeRule.onNodeWithText("YouTube Music").assertIsDisplayed()
        composeRule.onNodeWithText("Spotify").assertIsDisplayed()
        composeRule.onNodeWithText("Listen Together").assertIsDisplayed()

        composeRule.onNodeWithText("Continue without connecting").performClick()
        composeRule.onNodeWithText("Make it yours.").assertIsDisplayed()
        composeRule.onNodeWithText("Skip for now").performClick()
        composeRule.onNodeWithText("Ready to explore.").assertIsDisplayed()
    }
}
