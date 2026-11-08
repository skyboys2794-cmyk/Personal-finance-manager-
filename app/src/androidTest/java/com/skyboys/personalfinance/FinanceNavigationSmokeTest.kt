package com.skyboys.personalfinance

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

@RunWith(AndroidJUnit4::class)
class FinanceNavigationSmokeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    ).around(composeRule)

    @Test fun mainTabsRenderAndNavigate() {
        composeRule.onNodeWithText("Your finances").assertIsDisplayed()
        composeRule.onNodeWithText("Activity").performClick()
        composeRule.onNodeWithText("All transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Accounts").performClick()
        composeRule.onNodeWithText("Your accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Plans").performClick()
    }
}
