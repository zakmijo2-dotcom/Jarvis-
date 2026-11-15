package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.voice.JarvisVoiceService
import com.example.core.voice.VoiceServiceStatus
import com.example.data.repository.AppThemeMode
import com.example.data.repository.JarvisSettings
import com.example.ui.navigation.JarvisAppShell
import com.example.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWakeIntent(intent)
        val app = application as JarvisApplication
        val container = app.container
        setContent {
            val settings by container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = JarvisSettings())
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemDark
            }

            SideEffect {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            LaunchedEffect(Unit) {
                app.markFirstFrameDrawn()
            }

            JarvisTheme(darkTheme = darkTheme) {
                JarvisAppShell(
                    container = container,
                    app = app
                )
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
