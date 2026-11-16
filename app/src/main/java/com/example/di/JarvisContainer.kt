package com.example.di

import android.content.Context
import com.example.core.agent.AgentCore
import com.example.core.agent.IntentRouter
import com.example.core.agent.Phase2AgentCore
import com.example.core.agent.ToolRegistryFactory
import com.example.core.ai.AiEngine
import com.example.core.ai.LiteRtLmAiEngine
import com.example.core.ai.ModelRouter
import com.example.core.ai.RuntimeEngineCoordinator
import com.example.core.cloud.CloudAiClient
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.cloud.Phase1CloudProviderRegistry
import com.example.core.device.AndroidNativeController
import com.example.core.device.DeviceCapabilityManager
import com.example.core.device.DeviceCommandRouter
import com.example.core.device.PermissionManager
import com.example.core.device.ShizukuController
import com.example.core.device.WirelessAdbController
import com.example.core.hf.HuggingFaceApiClient
import com.example.core.mcp.McpClient
import com.example.core.mcp.McpManager
import com.example.core.memory.LocalMemory
import com.example.core.memory.RoomLocalMemory
import com.example.core.model.DownloadQueueManager
import com.example.core.model.ModelArtifactManager
import com.example.core.model.ModelCompatibilityEngine
import com.example.core.model.ModelFormatRegistry
import com.example.core.model.ModelManager
import com.example.core.model.RoomModelManager
import com.example.core.overlay.ExecutionOverlayManager
import com.example.core.security.HttpSecuritySupport
import com.example.core.security.SecureCredentialStore
import com.example.core.skills.RoomSkillEngine
import com.example.core.skills.SkillEngine
import com.example.core.tools.AndroidSystemTools
import com.example.core.tools.AndroidTools
import com.example.core.tools.ToolRegistry
import com.example.core.vision.OnDeviceOcrEngine
import com.example.core.vision.ScreenPerceptionEngine
import com.example.core.voice.AndroidVoiceEngine
import com.example.core.voice.VoiceEngine
import com.example.core.wakeword.ModularWakeWordEngine
import com.example.core.wakeword.WakeWordEngine
import com.example.core.web.OkHttpWebResearchService
import com.example.core.web.WebPageExtractor
import com.example.core.web.WebResearchService
import com.example.data.local.db.AgentCheckpointDao
import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.local.db.ResearchHistoryDao
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

    open val sharedOkHttpClient: okhttp3.OkHttpClient by lazy {
        val cacheDir = java.io.File(appContext.cacheDir, "http_api_cache")
        okhttp3.OkHttpClient.Builder()
            .cache(okhttp3.Cache(cacheDir, 16L * 1024L * 1024L))
            .connectionPool(okhttp3.ConnectionPool(4, 3, java.util.concurrent.TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor(HttpSecuritySupport.hfRedirectAuthSanitizer)
            .build()
    }

    open val downloadOkHttpClient: okhttp3.OkHttpClient by lazy {
        ModelArtifactManager.defaultHttpClient()
    }

    open val huggingFaceApiClient: HuggingFaceApiClient by lazy {
        HuggingFaceApiClient(httpClient = sharedOkHttpClient)
    }

    open val modelArtifactManager: ModelArtifactManager by lazy {
        ModelArtifactManager(
            context = appContext,
            modelManager = modelManager,
            httpClient = downloadOkHttpClient
        )
    }

    open val downloadQueueManager: DownloadQueueManager by lazy {
        DownloadQueueManager(
            context = appContext,
            queueDao = database.downloadQueueDao(),
            modelManager = modelManager,
            httpClient = downloadOkHttpClient
        )
    }

    open val liteRtLmAiEngine: LiteRtLmAiEngine by lazy {
        LiteRtLmAiEngine(
            context = appContext,
            systemInstructionProvider = {
                "You are JARVIS, a concise, accurate, helpful personal AI assistant running locally on Android.\n\n" +
                    toolRegistry.buildSystemToolCatalogPrompt()
            }
        )
    }

    open val runtimeEngineCoordinator: RuntimeEngineCoordinator by lazy {
        RuntimeEngineCoordinator(
            context = appContext,
            liteRtLmAiEngine = liteRtLmAiEngine
        )
    }

    open val aiEngine: AiEngine by lazy { runtimeEngineCoordinator }

    open val cloudAiClient: CloudAiClient by lazy { OkHttpCloudAiClient(baseClient = sharedOkHttpClient) }

    open val agentCheckpointDao: AgentCheckpointDao by lazy {
        database.agentCheckpointDao()
    }

    open val researchHistoryDao: ResearchHistoryDao by lazy {
        database.researchHistoryDao()
    }

    open val webResearchService: WebResearchService by lazy {
        OkHttpWebResearchService(
            baseClient = sharedOkHttpClient,
            researchHistoryDao = researchHistoryDao
        )
    }

    open val webPageExtractor: WebPageExtractor by lazy {
        WebPageExtractor(baseClient = sharedOkHttpClient)
    }

    open val mcpClient: McpClient by lazy {
        McpClient(baseClient = sharedOkHttpClient)
    }

    open val mcpManager: McpManager by lazy {
        McpManager(
            context = appContext,
            mcpServerDao = database.mcpServerDao(),
            mcpClient = mcpClient,
            credentialStore = credentialStore
        )
    }

    open val onDeviceOcrEngine: OnDeviceOcrEngine by lazy {
        OnDeviceOcrEngine()
    }

    open val executionOverlayManager: ExecutionOverlayManager by lazy {
        ExecutionOverlayManager(appContext)
    }

    open val screenPerceptionEngine: ScreenPerceptionEngine by lazy {
        ScreenPerceptionEngine(
            context = appContext,
            ocrEngine = onDeviceOcrEngine,
            overlayManager = executionOverlayManager
        )
    }

    open val voiceEngine: VoiceEngine by lazy {
        AndroidVoiceEngine(appContext)
    }

    open val wakeWordEngine: WakeWordEngine by lazy {
        ModularWakeWordEngine(appContext)
    }

    open val androidTools: AndroidTools by lazy {
        AndroidSystemTools(appContext)
    }

    open val shizukuController: ShizukuController by lazy {
        ShizukuController(appContext)
    }

    open val wirelessAdbController: WirelessAdbController by lazy {
        WirelessAdbController(appContext)
    }

    open val androidNativeController: AndroidNativeController by lazy {
        AndroidNativeController(appContext, androidTools)
    }

    open val permissionManager: PermissionManager by lazy {
        PermissionManager(appContext, shizukuController)
    }

    open val deviceCapabilityManager: DeviceCapabilityManager by lazy {
        DeviceCapabilityManager(
            shizukuController = shizukuController,
            wirelessAdbController = wirelessAdbController,
            permissionManager = permissionManager,
            settingsRepository = settingsRepository
        )
    }

    open val deviceCommandRouter: DeviceCommandRouter by lazy {
        DeviceCommandRouter(
            shizukuController = shizukuController,
            wirelessAdbController = wirelessAdbController,
            nativeController = androidNativeController,
            capabilityManager = deviceCapabilityManager,
            permissionManager = permissionManager,
            settingsRepository = settingsRepository
        )
    }

    open val localMemory: LocalMemory by lazy {
        RoomLocalMemory(database.memoryDao())
    }

    open val skillEngine: SkillEngine by lazy {
        RoomSkillEngine(database.skillDao())
    }

    open val toolRegistry: ToolRegistry by lazy {
        ToolRegistryFactory.createDefault(
            androidTools = androidTools,
            localMemoryProvider = { localMemory },
            skillEngineProvider = { skillEngine },
            deviceCommandRouterProvider = { deviceCommandRouter },
            perceptionEngineProvider = { screenPerceptionEngine },
            webResearchProvider = { webResearchService },
            webPageExtractorProvider = { webPageExtractor },
            mcpManagerProvider = { mcpManager }
        )
    }

    open val cloudProviderRegistry: CloudProviderRegistry by lazy {
        Phase1CloudProviderRegistry()
    }

    open val modelRouter: ModelRouter by lazy {
        ModelRouter(formatRegistry = formatRegistry)
    }

    open val agentCore: AgentCore by lazy {
        Phase2AgentCore(
            aiEngine = aiEngine,
            modelManager = modelManager,
            androidTools = androidTools,
            cloudProviderRegistry = cloudProviderRegistry,
            wakeWordEngine = wakeWordEngine,
            localMemory = localMemory,
            appContext = appContext,
            toolRegistry = toolRegistry,
            cloudAiClient = cloudAiClient,
            webResearchService = webResearchService,
            intentRouter = IntentRouter(),
            agentExecutionDao = database.agentExecutionDao(),
            skillEngine = skillEngine,
            deviceCommandRouter = deviceCommandRouter,
            modelRouter = modelRouter,
            modelBenchmarkDao = modelBenchmarkDao,
            screenPerceptionEngine = screenPerceptionEngine,
            executionOverlayManager = executionOverlayManager,
            checkpointDao = agentCheckpointDao,
            mcpManager = mcpManager
        )
    }
}
