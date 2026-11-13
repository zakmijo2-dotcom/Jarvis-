package com.example

import android.app.Application
import com.example.di.JarvisContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class JarvisApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: JarvisContainer

    override fun onCreate() {
        super.onCreate()
        container = JarvisContainer(this)
        applicationScope.launch {
            container.modelManager.ensureSeeded()
            container.conversationRepository.ensureDefaultConversation()
        }
        container.settingsRepository.settingsFlow
            .onEach { settings ->
                container.wakeWordEngine.syncStandbyPreference(settings.wakeWordStandbyEnabled)
            }
            .launchIn(applicationScope)
    }

    override fun onTerminate() {
        container.aiEngine.release()
        container.voiceEngine.release()
        super.onTerminate()
    }
}
