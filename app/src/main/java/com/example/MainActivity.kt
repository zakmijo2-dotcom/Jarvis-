package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.voice.JarvisVoiceService
import com.example.core.voice.VoiceServiceStatus
import com.example.ui.navigation.JarvisAppShell
import com.example.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWakeIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        val container = (application as JarvisApplication).container
        setContent {
            JarvisTheme {
                JarvisAppShell(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(JarvisVoiceService.EXTRA_ARM_VOICE_FROM_WAKE, false) == true) {
            VoiceServiceStatus.requestWakeArmFromNotification()
            JarvisVoiceService.cancelWakeAlertNotification(this)
        }
    }
}
