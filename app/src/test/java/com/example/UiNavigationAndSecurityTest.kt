package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiNavigationAndSecurityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `navigate through new sub hubs bridge setup permissions security and activity history`() {
        composeTestRule.waitForIdle()

        // 1. Open More -> Shizuku & Wireless ADB Setup
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("nav_tab_bridge_setup"))
        composeTestRule.onNodeWithTag("nav_tab_bridge_setup").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("shizuku_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bridge_setup_list")
            .performScrollToNode(hasTestTag("wireless_adb_card"))
        composeTestRule.onNodeWithTag("wireless_adb_card").assertIsDisplayed()

        // Test Wireless ADB pairing failure handling and ephemeral code scrubbing
        composeTestRule.onNodeWithTag("bridge_setup_list")
            .performScrollToNode(hasTestTag("adb_pairing_port_input"))
        composeTestRule.onNodeWithTag("adb_pairing_port_input").performTextInput("37123")
        composeTestRule.onNodeWithTag("adb_pairing_code_input").performTextInput("654321")
        composeTestRule.onNodeWithTag("bridge_setup_list")
            .performScrollToNode(hasTestTag("adb_pair_button"))
        composeTestRule.onNodeWithTag("adb_pair_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithTag("bridge_feedback_banner")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("bridge_feedback_banner").assertIsDisplayed()
        // Ephemeral 6-digit pairing code must be wiped immediately after pairing attempt
        composeTestRule.onNodeWithTag("adb_pairing_code_input").assertTextEquals("6-digit code", "")

        // Back to More -> Permissions & Security (pinned top bar button is always visible)
        composeTestRule.onNodeWithTag("bridge_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("nav_tab_permissions_security"))
        composeTestRule.onNodeWithTag("nav_tab_permissions_security").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("permissions_security_list")
            .performScrollToNode(hasTestTag("keystore_health_card"))
        composeTestRule.onNodeWithTag("keystore_health_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("keystore_recheck_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("keystore_health_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("permissions_security_list")
            .performScrollToNode(hasTestTag("permissions_back_button"))
        composeTestRule.onNodeWithTag("permissions_back_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Activity & Benchmarks History and cycle all 3 tabs
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("nav_tab_activity_history"))
        composeTestRule.onNodeWithTag("nav_tab_activity_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("history_tab_executions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_tab_commands").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_tab_benchmarks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list").assertIsDisplayed()
    }

    @Test
    fun `settings supports dark light system theme switching and model hub renders hardware fit card`() {
        composeTestRule.waitForIdle()

        // Models tab shows storage & hardware fit card
        composeTestRule.onNodeWithTag("nav_tab_models").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("active_model_banner")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("model_storage_and_fit_card"))
        composeTestRule.onNodeWithTag("model_storage_and_fit_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage & hardware fit").assertIsDisplayed()

        // More -> Settings -> Appearance -> Toggle Light & Dark Graphite theme cleanly
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("nav_tab_settings"))
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_appearance"))
        composeTestRule.onNodeWithTag("settings_section_appearance").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("theme_chip_light"))
        composeTestRule.onNodeWithTag("theme_chip_light").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("theme_chip_dark").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_chip_dark").assertIsDisplayed()
    }
}
