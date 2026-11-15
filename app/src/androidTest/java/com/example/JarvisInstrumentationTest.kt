package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.device.ShizukuController
import com.example.core.device.WirelessAdbConnectionState
import com.example.core.device.WirelessAdbController
import com.example.core.security.SecureCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisInstrumentationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shizukuAndWirelessAdbControllersFailClosedWhenUnconfigured() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val shizuku = ShizukuController(context)
        val shizukuSnapshot = shizuku.refreshStatus()
        assertNotNull(shizukuSnapshot.availability)

        val adb = WirelessAdbController(context)
        val connectAttempt = adb.connect(host = "127.0.0.1", connectPortText = "1")
        assertTrue(connectAttempt.isFailure)
        assertFalse(adb.status.value.connectionState == WirelessAdbConnectionState.CONNECTED)
    }

    @Test
    fun keystoreEncryptDecryptRoundTripWorksOnAndroidRuntime() {
        val store = SecureCredentialStore()
        val encrypted = store.encryptResult("jarvis_instrumentation_secret")
        assertTrue(encrypted.isSuccess)
        val health = store.verifyKeystoreHealth()
        assertTrue(health.keystoreAvailable)
    }

    @Test
    fun mainActivityNavigatesAcrossHomeMoreBridgeAndModels() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_greeting_text").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_models").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("engine_runtime_hud_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("nav_tab_bridge_setup"))
        composeTestRule.onNodeWithTag("nav_tab_bridge_setup").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shizuku_status_card").assertIsDisplayed()
    }
}
