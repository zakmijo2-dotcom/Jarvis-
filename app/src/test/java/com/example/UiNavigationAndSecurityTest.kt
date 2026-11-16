package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
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

    @Before
    fun resetSettingsBefore() {
        val app = ApplicationProvider.getApplicationContext<JarvisApplication>()
        runBlocking { app.container.settingsRepository.setUiAdvancedModeEnabled(false) }
        composeTestRule.waitForIdle()
    }

    @After
    fun resetSettingsAfter() {
        val app = ApplicationProvider.getApplicationContext<JarvisApplication>()
        runBlocking { app.container.settingsRepository.setUiAdvancedModeEnabled(false) }
    }

    @Test
    fun `navigate through new sub hubs bridge setup permissions security and all activity history tabs`() {
        composeTestRule.waitForIdle()

        // Enable Advanced Operator Mode so the 4-tab bar + More hub are accessible
        composeTestRule.onNodeWithTag("topbar_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_advanced_operator_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
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
        composeTestRule.onNodeWithTag("adb_pairing_port_input").performScrollTo().performTextInput("37123")
        composeTestRule.onNodeWithTag("adb_pairing_code_input").performScrollTo().performTextInput("654321")
        composeTestRule.onNodeWithTag("adb_pair_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithTag("bridge_feedback_banner")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("bridge_feedback_banner").assertIsDisplayed()
        // Ephemeral 6-digit pairing code must be wiped immediately after pairing attempt
        composeTestRule.onNodeWithTag("adb_pairing_code_input").assertTextEquals("6-digit code", "")

        // Back to More -> Permissions & Security (including MediaProjection card)
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
            .performScrollToNode(hasTestTag("sec_grant_projection_btn"))
        composeTestRule.onNodeWithTag("sec_grant_projection_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("permissions_security_list")
            .performScrollToNode(hasTestTag("permissions_back_button"))
        composeTestRule.onNodeWithTag("permissions_back_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Activity & Benchmarks History and cycle all 5 tabs
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("nav_tab_activity_history"))
        composeTestRule.onNodeWithTag("nav_tab_activity_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("history_tab_executions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_tab_checkpoints").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_tab_web").performClick()
        composeTestRule.waitForIdle()
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

        // Chat -> Settings -> Appearance -> Toggle Light & Dark Graphite theme cleanly
        composeTestRule.onNodeWithTag("topbar_settings_button").performClick()
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

        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Open Models via Clean Chat top bar model selector chip and verify hardware fit card
        composeTestRule.onNodeWithTag("chat_model_selector_chip").performClick()
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
    }
}
