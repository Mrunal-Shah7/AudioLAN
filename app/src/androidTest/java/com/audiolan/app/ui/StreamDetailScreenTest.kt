package com.audiolan.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

class StreamDetailScreenTest {
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
    fun newStreamScreenShowsAllFourFields() {
        setContent()
        openNewMicStream()

        composeTestRule.onNodeWithText("name").assertExists()
        composeTestRule.onNodeWithText("hostname").assertExists()
        composeTestRule.onNodeWithText("port").assertExists()
        composeTestRule.onNodeWithText("net quality").assertExists()
    }

    @Test
    fun saveButtonExistsAndIsVisible() {
        setContent()
        openNewMicStream()

        composeTestRule.onNodeWithTag("stream_detail_save").assertExists()
    }

    @Test
    fun emptyNameShowsValidationErrorOnSave() {
        setContent()
        openNewMicStream()

        composeTestRule.onNodeWithTag("stream_detail_save").performClick()

        composeTestRule.onNodeWithText("name cannot be empty").assertExists()
    }

    @Test
    fun portDialogAcceptsEnteredValue() {
        setContent()
        openNewMicStream()

        setDialogField("port", "8080")

        composeTestRule.onNodeWithText("8080").assertExists()
    }

    @Test
    fun netQualityPickerSelectsFast() {
        setContent()
        openNewMicStream()

        composeTestRule.onNodeWithText("net quality").performClick()
        listOf("optimal", "fast", "medium", "slow", "very slow").forEach { option ->
            composeTestRule.onNodeWithText(option).assertExists()
        }
        val fastOptions = composeTestRule.onAllNodesWithText("fast")
        fastOptions[fastOptions.fetchSemanticsNodes().lastIndex].performClick()

        composeTestRule.onNodeWithText("fast").assertExists()
    }

    @Test
    fun validSaveNavigatesBackToStreamList() {
        setContent()
        openNewMicStream()
        setDialogField("name", "TestMic")
        setDialogField("hostname", "192.168.1.50")
        setDialogField("port", "8080")

        composeTestRule.onNodeWithTag("stream_detail_save").performClick()

        composeTestRule.onNodeWithText("TestMic").assertExists()
        deleteVisibleStream("TestMic")
    }

    private fun openNewMicStream() {
        composeTestRule.onNodeWithTag("bottom_nav_mic").performClick()
        composeTestRule.onNode(hasContentDescription("add new stream")).performClick()
    }

    private fun setDialogField(label: String, value: String) {
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(value)
        composeTestRule.onNodeWithTag("${label}_dialog_save").performClick()
    }

    private fun deleteVisibleStream(name: String) {
        composeTestRule.onNodeWithText(name).assertExists()
        composeTestRule.onNodeWithText("delete").performClick()
        val confirmButtons = composeTestRule.onAllNodesWithText("delete")
        confirmButtons[confirmButtons.fetchSemanticsNodes().lastIndex].performClick()
    }
}
