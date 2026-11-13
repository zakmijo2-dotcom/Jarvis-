package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.di.JarvisContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class JarvisApplication : Application(), ComponentCallbacks2 {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: JarvisContainer by lazy { JarvisContainer(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            container.modelManager.ensureSeeded()
            container.conversationRepository.ensureDefaultConversation()
            container.settingsRepository.settingsFlow
                .onEach { settings ->
                    container.wakeWordEngine.syncStandbyPreference(settings.wakeWordStandbyEnabled)
                }
                .launchIn(this)
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
        container.aiEngine.release()
        container.voiceEngine.release()
        super.onTerminate()
    }
}
