package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis", appName)
    }

    @Test
    fun `navigate across all five jarvis destinations and inspect phase 3 model hub tabs`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_voice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("VOICE CONSOLE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_status_indicator").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_listen_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_fallback_input_field").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_ptt").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_continuous").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_lang_ar-dz").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_lang_fr-fr").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wake_word_evaluation_card").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_models").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("MODEL REGISTRY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_runtime_hud_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("LITERT-LM RUNTIME HUD").assertIsDisplayed()

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
        composeTestRule.onNodeWithText("HUGGING FACE MODEL HUB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hf_search_query_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hf_manual_repo_input").assertIsDisplayed()

        // Switch to Queue tab
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("hub_tab_queue"))
        composeTestRule.onNodeWithTag("hub_tab_queue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasText("BACKGROUND DOWNLOAD QUEUE"))
        composeTestRule.onNodeWithText("BACKGROUND DOWNLOAD QUEUE").assertIsDisplayed()

        // Switch to Benchmarks tab
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("hub_tab_benchmarks"))
        composeTestRule.onNodeWithTag("hub_tab_benchmarks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasText("MODEL BENCHMARK HISTORY"))
        composeTestRule.onNodeWithText("MODEL BENCHMARK HISTORY").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_device").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SYSTEM TELEMETRY").assertIsDisplayed()

        composeTestRule.onNodeWithTag("device_tab_controls").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_controls_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("device_controls_list")
            .performScrollToNode(hasTestTag("switch_notification_reader"))
        composeTestRule.onNodeWithTag("switch_notification_reader").assertIsDisplayed()
        composeTestRule.onNodeWithTag("device_controls_list")
            .performScrollToNode(hasTestTag("accessibility_disclosure_card"))
        composeTestRule.onNodeWithTag("accessibility_disclosure_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CORE CONFIGURATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("LOCAL AI INFERENCE (LITERT-LM)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("context_chip_4096").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("J.A.R.V.I.S.").assertIsDisplayed()
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
                .onAllNodesWithText("ENGINE UNLOADED")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("ENGINE UNLOADED").assertIsDisplayed()
    }

    @Test
    fun `open conversation history drawer from chat top bar`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("open_history_drawer_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("history_drawer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_session_button").assertIsDisplayed()
    }
}
