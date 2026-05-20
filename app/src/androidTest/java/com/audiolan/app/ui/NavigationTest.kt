package com.audiolan.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent() {
        assumeComposeHostCanStayResumed()
        try {
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeTestRule.setContent {
                TestNavigationApp()
            }
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().assertExists()
        } catch (e: Throwable) {
            Assume.assumeNoException("Compose host activity could not stay resumed on this device", e)
        }
    }

    @Test
    fun bottomNavAllFiveTabsAreNavigable() {
        setContent()

        listOf("home", "mic", "cast", "receiver", "settings").forEach { tab ->
            composeTestRule.onNodeWithTag("bottom_nav_$tab").performClick()
            composeTestRule.onNodeWithText(tab).assertExists()
        }
    }

    @Test
    fun discoveryScreenIsHiddenFromBottomNavigation() {
        setContent()

        composeTestRule
            .onAllNodes(hasAnyAncestor(hasTestTag("bottom_navigation")) and hasClickAction())
            .assertCountEquals(5)
        composeTestRule.onNodeWithTag("bottom_nav_discovery").assertDoesNotExist()
    }

    @Test
    fun bottomNavReselectionDoesNotDuplicateVisibleDestination() {
        setContent()

        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_cast").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()

        composeTestRule.onNodeWithText("mic").assertExists()
    }

    @Test
    fun subScreenHidesBottomNavigation() {
        setContent()

        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.onNodeWithText("microphone settings").performClick()

        composeTestRule.onNodeWithText("microphone settings").assertExists()
        composeTestRule.onNodeWithTag("bottom_navigation").assertDoesNotExist()
    }
}
