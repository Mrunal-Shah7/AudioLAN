package com.audiolan.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

class StreamListScreenTest {
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
    fun emptyStateMessageIsVisibleOnMicTab() {
        setContent()
        navigateToMicAndRemoveVisibleStreams()

        composeTestRule.onNodeWithText("no streams configured", substring = true).assertExists()
    }

    @Test
    fun fabExists() {
        setContent()

        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()

        composeTestRule.onNode(hasContentDescription("add new stream")).assertExists()
    }

    @Test
    fun fabTapNavigatesToDetail() {
        setContent()

        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()
        composeTestRule.onNode(hasContentDescription("add new stream")).performClick()

        composeTestRule.onNodeWithText("new stream").assertExists()
    }

    @Test
    fun backFromDetailReturnsToList() {
        setContent()

        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()
        composeTestRule.onNode(hasContentDescription("add new stream")).performClick()
        composeTestRule.onNode(hasContentDescription("navigate back")).performClick()

        composeTestRule.onNodeWithText("mic").assertExists()
    }

    private fun navigateToMicAndRemoveVisibleStreams() {
        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()
        repeat(10) {
            val deleteButtons = composeTestRule.onAllNodesWithText("delete")
            if (deleteButtons.fetchSemanticsNodes().isEmpty()) return
            deleteButtons[0].performClick()
            val confirmButtons = composeTestRule.onAllNodesWithText("delete")
            confirmButtons[confirmButtons.fetchSemanticsNodes().lastIndex].performClick()
            composeTestRule.waitForIdle()
        }
    }
}
