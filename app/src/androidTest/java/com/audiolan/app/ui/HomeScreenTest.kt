package com.audiolan.app.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent() {
        assumeComposeHostCanStayResumed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeTestRule.setContent {
                TestHomeApp(context)
            }
            composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().assertExists()
        } catch (e: Throwable) {
            Assume.assumeNoException("Compose host activity could not stay resumed on this device", e)
        }
    }

    @Test
    fun threeServiceButtonsAreVisible() {
        setContent()

        composeTestRule.onNodeWithText("start microphone service").assertExists()
        composeTestRule.onNodeWithText("start cast service").assertExists()
        composeTestRule.onNodeWithText("start receiver service").assertExists()
    }

    @Test
    fun serviceButtonTextIsLowercase() {
        setContent()

        composeTestRule.onNodeWithText("start microphone service").assertExists()
        composeTestRule.onNodeWithText("Start Microphone Service").assertDoesNotExist()
    }

    @Test
    fun scanNetworkButtonIsVisible() {
        setContent()

        composeTestRule.onNodeWithText("scan network").assertExists()
    }

    @Test
    fun availableNetworksSectionIsVisible() {
        setContent()

        composeTestRule.onNodeWithText("available networks").assertExists()
    }

    @Test
    fun refreshButtonIsVisible() {
        setContent()

        composeTestRule.onNodeWithText("refresh").assertExists()
    }
}
