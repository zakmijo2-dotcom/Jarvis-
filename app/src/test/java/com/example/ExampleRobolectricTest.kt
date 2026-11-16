package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis", appName)
    }

    @Test
    fun `clean default chat mode hides bottom tabs and offers drawer and settings entry points`() {
        composeTestRule.waitForIdle()

        // Default startup opens Clean Chat mode without bottom navigation bar clutter
        composeTestRule.onNodeWithTag("chat_model_selector_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("topbar_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("topbar_new_session_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_attach_image_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_input_field").assertIsDisplayed()

        // Bottom 4-tab operator bar must be absent in Clean Default Mode
        composeTestRule.onAllNodesWithTag("nav_tab_home").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("nav_tab_more").assertCountEquals(0)

        // Drawer quick navigation footer exposes 1-tap Models, Voice, Activity, Settings
        composeTestRule.onNodeWithTag("open_history_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_drawer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_session_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_nav_models").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_nav_voice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_nav_activity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_nav_settings").assertIsDisplayed()
    }

    @Test
    fun `toggling advanced operator mode reveals 4-tab workspace home bento and all sub hubs`() {
        composeTestRule.waitForIdle()

        // Enable Advanced Operator Mode from Settings
        composeTestRule.onNodeWithTag("topbar_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_advanced_operator_mode").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Home tab now available in 4-tab bar
        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_greeting_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_headline_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_bento_voice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("home_bento_chat"))
        composeTestRule.onNodeWithTag("home_bento_chat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_bento_models").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("home_bento_device"))
        composeTestRule.onNodeWithTag("home_bento_device").assertIsDisplayed()

        // More -> Voice screen
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_voice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_screen_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_listen_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_close_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_toggle_type_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_fallback_input_field").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_status_indicator").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_ptt").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_continuous").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_lang_ar-dz").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_lang_fr-fr").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wake_word_evaluation_card").performScrollTo().assertIsDisplayed()

        // Models tab
        composeTestRule.onNodeWithTag("nav_tab_models").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("models_screen_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_runtime_hud_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("LiteRT-LM runtime").assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("active_model_banner")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("active_model_banner"))
        composeTestRule.onNodeWithTag("active_model_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("model_card_slot_gemma3_1b_int4"))
        composeTestRule.onNodeWithTag("model_card_slot_gemma3_1b_int4").assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Gemma 3 1B Instruct (Int4)") and hasAnyAncestor(hasTestTag("model_card_slot_gemma3_1b_int4"))
        ).assertIsDisplayed()

        // Switch to Discover tab
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("hub_tab_discover"))
        composeTestRule.onNodeWithTag("hub_tab_discover").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("hf_discover_header"))
        composeTestRule.onNodeWithText("Hugging Face").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hf_search_query_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hf_manual_repo_input").assertIsDisplayed()

        // Switch to Queue tab
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("hub_tab_queue"))
        composeTestRule.onNodeWithTag("hub_tab_queue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasText("Download queue"))
        composeTestRule.onNodeWithText("Download queue").assertIsDisplayed()

        // Switch to Benchmarks tab
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("hub_tab_benchmarks"))
        composeTestRule.onNodeWithTag("hub_tab_benchmarks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasText("Benchmark history"))
        composeTestRule.onNodeWithText("Benchmark history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("benchmark_summary_panel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avg TTFT").assertIsDisplayed()

        // More -> Device
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_device").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Device") and !hasAnyAncestor(hasTestTag("device_telemetry_list"))
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag("device_tab_controls")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_controls_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("device_controls_list")
            .performScrollToNode(hasTestTag("switch_notification_reader"))
        composeTestRule.onNodeWithTag("switch_notification_reader").assertIsDisplayed()
        composeTestRule.onNodeWithTag("device_controls_list")
            .performScrollToNode(hasTestTag("accessibility_disclosure_card"))
        composeTestRule.onNodeWithTag("accessibility_disclosure_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("device_tab_permissions")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_permissions_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("device_permissions_list")
            .performScrollToNode(hasTestTag("permissions_privacy_policy_card"))
        composeTestRule.onNodeWithTag("permissions_privacy_policy_card").assertIsDisplayed()

        // Back to More -> Settings
        composeTestRule.onNodeWithTag("device_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local inference").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("context_chip_4096"))
        composeTestRule.onNodeWithTag("context_chip_4096").assertIsDisplayed()

        // Expand collapsible settings sections and verify Phone-Use & MCP & Cloud & Web & Memory & Skills & History
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_cloud"))
        composeTestRule.onNodeWithTag("settings_section_cloud").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_hybrid_routing_card"))
        composeTestRule.onNodeWithTag("settings_hybrid_routing_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_web"))
        composeTestRule.onNodeWithTag("settings_section_web").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_web_research_card"))
        composeTestRule.onNodeWithTag("settings_web_research_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_phone_use"))
        composeTestRule.onNodeWithTag("settings_section_phone_use").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("switch_screenshot_vision"))
        composeTestRule.onNodeWithTag("switch_screenshot_vision").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_mcp"))
        composeTestRule.onNodeWithTag("settings_section_mcp").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("mcp_add_button"))
        composeTestRule.onNodeWithTag("mcp_add_button")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_memory"))
        composeTestRule.onNodeWithTag("settings_section_memory").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_memory_manager_card"))
        composeTestRule.onNodeWithTag("settings_memory_manager_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_skills"))
        composeTestRule.onNodeWithTag("settings_section_skills").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_skills_library_card"))
        composeTestRule.onNodeWithTag("settings_skills_library_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_section_history"))
        composeTestRule.onNodeWithTag("settings_section_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_execution_history_card"))
        composeTestRule.onNodeWithTag("settings_execution_history_card").assertIsDisplayed()

        // Return to Chat tab
        composeTestRule.onNodeWithTag("nav_tab_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Jarvis").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_privacy_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_routing_mode").assertIsDisplayed()
    }

    @Test
    fun `home bento voice tile and history see all open expected destinations in operator mode`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("topbar_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_advanced_operator_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_bento_voice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_listen_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_close_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_headline_text").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("home_history_see_all"))
        composeTestRule.onNodeWithTag("home_history_see_all").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_drawer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_session_button").assertIsDisplayed()
    }

    @Test
    fun `sending chat message persists user prompt and reports unloaded local engine honestly`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chat_input_field").performTextInput("Initialize diagnostic check")
        composeTestRule.onNodeWithTag("chat_send_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Initialize diagnostic check")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Initialize diagnostic check").assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Unloaded", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Unloaded", substring = true, ignoreCase = true).onFirst().assertIsDisplayed()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("copy_message_button")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `arabic message renders cleanly in chat with rtl auto direction`() {
        composeTestRule.waitForIdle()

        val arabicPrompt = "مرحبا جارفيس ما حالة البطارية"
        composeTestRule.onNodeWithTag("chat_input_field").performTextInput(arabicPrompt)
        composeTestRule.onNodeWithTag("chat_send_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText(arabicPrompt)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(arabicPrompt).assertIsDisplayed()
    }
}
