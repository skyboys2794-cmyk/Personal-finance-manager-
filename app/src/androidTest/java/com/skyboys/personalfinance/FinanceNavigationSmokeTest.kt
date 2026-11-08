package com.skyboys.personalfinance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun mainTabsRenderAndNavigate() {
        composeRule.onNodeWithText("Finance Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Activity").performClick()
        composeRule.onNodeWithText("All transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Accounts").performClick()
        composeRule.onNodeWithText("Your accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Plans").performClick()
    }
}
