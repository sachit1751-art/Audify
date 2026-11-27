package com.music.audify

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.audify.ui.screens.SettingsRow
import com.music.audify.ui.screens.SettingsSubRow
import com.music.audify.ui.components.IosSwitch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickableSettingsRowsExposeButtonSemantics() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                SettingsRow(title = "Audio quality", onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Audio quality")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun settingsSubRowIsOneSwitchRatherThanNestedClickTargets() {
        var checked = true
        composeRule.setContent {
            MaterialTheme {
                SettingsSubRow(
                    title = "Reduce motion",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }

        composeRule.onNodeWithText("Reduce motion")
            .assertIsDisplayed()
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
        composeRule.runOnIdle { assertTrue(!checked) }
    }

    @Test
    fun settingsRowWithTrailingSwitchExposesOnlyOneSwitchAction() {
        var checked = true
        composeRule.setContent {
            MaterialTheme {
                SettingsRow(
                    title = "Pause on mute",
                    toggleableValue = checked,
                    onClick = { checked = !checked },
                    trailing = { IosSwitch(checked = checked, onCheckedChange = null) },
                )
            }
        }

        composeRule.onNodeWithText("Pause on mute")
            .assertIsDisplayed()
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
        composeRule.runOnIdle { assertTrue(!checked) }
    }
}
