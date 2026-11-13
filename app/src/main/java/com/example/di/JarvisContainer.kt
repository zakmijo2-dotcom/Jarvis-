package com.example.di

import android.content.Context
import com.example.core.agent.AgentCore
import com.example.core.agent.Phase2AgentCore
import com.example.core.ai.AiEngine
import com.example.core.ai.LiteRtLmAiEngine
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.cloud.Phase1CloudProviderRegistry
import com.example.core.hf.HuggingFaceApiClient
import com.example.core.memory.LocalMemory
import com.example.core.memory.RoomLocalMemory
import com.example.core.model.DownloadQueueManager
import com.example.core.model.ModelArtifactManager
import com.example.core.model.ModelCompatibilityEngine
import com.example.core.model.ModelFormatRegistry
import com.example.core.model.ModelManager
import com.example.core.model.RoomModelManager
import com.example.core.security.SecureCredentialStore
import com.example.core.tools.AndroidSystemTools
import com.example.core.tools.AndroidTools
import com.example.core.voice.AndroidVoiceEngine
import com.example.core.voice.VoiceEngine
import com.example.core.wakeword.Phase1WakeWordEngine
import com.example.core.wakeword.WakeWordEngine
import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.repository.ConversationRepository
import com.example.data.repository.SettingsRepository

open class JarvisContainer(context: Context) {
    private val appContext = context.applicationContext

    open val database: JarvisDatabase by lazy { JarvisDatabase.create(appContext) }

    open val credentialStore: SecureCredentialStore by lazy { SecureCredentialStore() }

    open val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(database.conversationDao())
    }

    open val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    open val modelManager: ModelManager by lazy {
        RoomModelManager(database.modelDao())
    }

    open val modelBenchmarkDao: ModelBenchmarkDao by lazy {
        database.modelBenchmarkDao()
    }

    open val formatRegistry: ModelFormatRegistry by lazy {
        ModelFormatRegistry()
    }

    open val compatibilityEngine: ModelCompatibilityEngine by lazy {
        ModelCompatibilityEngine(formatRegistry)
    }

    open val huggingFaceApiClient: HuggingFaceApiClient by lazy {
        HuggingFaceApiClient()
    }

    open val modelArtifactManager: ModelArtifactManager by lazy {
        ModelArtifactManager(appContext, modelManager)
    }

    open val downloadQueueManager: DownloadQueueManager by lazy {
        DownloadQueueManager(
            context = appContext,
            queueDao = database.downloadQueueDao(),
            modelManager = modelManager
        )
    }

    open val aiEngine: AiEngine by lazy { LiteRtLmAiEngine(appContext) }

    open val voiceEngine: VoiceEngine by lazy {
        AndroidVoiceEngine(appContext)
    }

    open val wakeWordEngine: WakeWordEngine by lazy {
        Phase1WakeWordEngine()
    }

    open val androidTools: AndroidTools by lazy {
        AndroidSystemTools(appContext)
    }

    open val cloudProviderRegistry: CloudProviderRegistry by lazy {
        Phase1CloudProviderRegistry()
    }

    open val localMemory: LocalMemory by lazy {
        RoomLocalMemory(database.memoryDao())
    }

    open val agentCore: AgentCore by lazy {
        Phase2AgentCore(
            aiEngine = aiEngine,
            modelManager = modelManager,
            androidTools = androidTools,
            cloudProviderRegistry = cloudProviderRegistry,
            wakeWordEngine = wakeWordEngine,
            localMemory = localMemory
        )
    }
}
