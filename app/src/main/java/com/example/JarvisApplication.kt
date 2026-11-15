package com.example

import android.Manifest
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.di.JarvisContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class JarvisApplication : Application(), ComponentCallbacks2 {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val processStartedAtMs: Long = System.currentTimeMillis()
    @Volatile
    var firstFrameDrawnAtMs: Long = 0L
        private set

    val container: JarvisContainer by lazy { JarvisContainer(this) }

    fun markFirstFrameDrawn() {
        if (firstFrameDrawnAtMs == 0L) {
            firstFrameDrawnAtMs = System.currentTimeMillis()
        }
    }

    fun measuredStartupDurationMs(): Long? {
        val drawn = firstFrameDrawnAtMs
        return if (drawn >= processStartedAtMs && drawn > 0L) (drawn - processStartedAtMs) else null
    }

    override fun onCreate() {
        super.onCreate()
        container.shizukuController.startLifecycleTracking()
        applicationScope.launch {
            runCatching {
                container.settingsRepository.migrateLegacyTokensIfNeeded()
                val initial = container.settingsRepository.settingsFlow.first()
                container.wirelessAdbController.restoreSavedEndpoint(
                    host = initial.wirelessAdbHost,
                    connectPort = initial.wirelessAdbConnectPort.takeIf { it > 0 },
                    paired = initial.wirelessAdbPaired
                )
                container.modelManager.ensureSeeded()
                container.conversationRepository.ensureDefaultConversation()
                container.settingsRepository.settingsFlow
                    .onEach { settings ->
                        val hasMic = runCatching {
                            ContextCompat.checkSelfPermission(
                                this@JarvisApplication,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        }.getOrDefault(false)
                        container.wakeWordEngine.updateAccessKey(settings.porcupineAccessKey)
                        container.wakeWordEngine.syncStandbyPreference(
                            enabled = settings.wakeWordStandbyEnabled,
                            hasMicPermission = hasMic,
                            isAppInForeground = true,
                            porcupineAccessKeyPresent = settings.porcupineAccessKey.isNotBlank()
                        )
                    }
                    .launchIn(this)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        applicationScope.launch {
            container.aiEngine.trimMemory(level)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        applicationScope.launch {
            container.aiEngine.unloadModel()
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        container.shizukuController.stopLifecycleTracking()
        container.wirelessAdbController.release()
        container.deviceCapabilityManager.release()
        container.aiEngine.release()
        container.voiceEngine.release()
        super.onTerminate()
    }
}
