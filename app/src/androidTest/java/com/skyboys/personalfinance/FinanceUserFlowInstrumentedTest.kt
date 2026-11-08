package com.skyboys.personalfinance

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceUserFlowInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun userCanCreateAnExpenseAndSeeItInActivity() {
        val description = "UI test expense ${UUID.randomUUID().toString().take(8)}"

        composeRule.onNodeWithContentDescription("Add transaction").performClick()
        composeRule.onNodeWithText("New transaction").assertExists()

        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields.get(0).performTextInput(description)
        fields.get(1).performTextInput("123.45")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Activity").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(description).assertExists()
    }

    @Test
    fun accountEditorAndSettingsScreenOpen() {
        composeRule.onNodeWithText("Accounts").performClick()
        composeRule.onNodeWithText("Your accounts").assertExists()
        composeRule.onNodeWithText("Add account").performClick()
        composeRule.onNodeWithText("Account name").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Upload statements").assertExists()
        composeRule.onNodeWithText("Create encrypted backup").assertExists()
        composeRule.onNodeWithText("Google Sheets snapshot").assertExists()
    }
}
