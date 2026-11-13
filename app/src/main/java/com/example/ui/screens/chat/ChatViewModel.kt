package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.agent.AgentCore
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.ai.GenerationBenchmark
import com.example.core.model.ArtifactState
import com.example.core.model.ModelManager
import com.example.core.model.ModelSlot
import com.example.core.model.RuntimeFormatId
import com.example.core.tools.NotificationBridge
import com.example.core.tools.PendingToolConfirmation
import com.example.core.tools.SpecialAccessType
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.data.local.db.ConversationWithPreview
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val activeConversationId: String? = null,
    val activeConversationTitle: String = "Primary Core Session",
    val messages: List<MessageEntity> = emptyList(),
    val conversations: List<ConversationWithPreview> = emptyList(),
    val drawerSearchQuery: String = "",
    val activeModel: ModelSlot? = null,
    val settings: JarvisSettings = JarvisSettings(),
    val engineLoadState: EngineLoadState = EngineLoadState.Unloaded,
    val isGenerating: Boolean = false,
    val streamingReply: String? = null,
    val streamingBackend: String? = null,
    val streamingEstimatedTokens: Int = 0,
    val lastBenchmark: GenerationBenchmark? = null,
    val nativeMemoryMb: Float = 0f,
    val pendingToolConfirmation: PendingToolConfirmation? = null,
    val activeToolName: String? = null,
    val pendingRuntimePermission: String? = null,
    val pendingSpecialAccess: SpecialAccessType? = null,
    val permissionRationale: String? = null
)

private data class ChatToolOverlayState(
    val pendingConfirmation: PendingToolConfirmation? = null,
    val activeToolName: String? = null,
    val pendingRuntimePermission: String? = null,
    val pendingSpecialAccess: SpecialAccessType? = null,
    val permissionRationale: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val modelManager: ModelManager,
    private val agentCore: AgentCore,
    private val aiEngine: AiEngine,
    private val settingsRepository: SettingsRepository,
    private val modelBenchmarkDao: ModelBenchmarkDao? = null
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val drawerSearchQuery = MutableStateFlow("")
    private val streamingReply = MutableStateFlow<String?>(null)
    private val streamingBackend = MutableStateFlow<String?>(null)
    private val streamingTokenEstimate = MutableStateFlow(0)
    private val toolOverlayState = MutableStateFlow(ChatToolOverlayState())
    private var generationJob: Job? = null
    private var toolJob: Job? = null
    private var lastPermissionDeniedRequest: Pair<String, ToolCallRequest>? = null

    private val activeMessagesFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
    }

    private val baseDataFlow = combine(
        activeConversationId,
        conversationRepository.observeConversations(),
        activeMessagesFlow,
        drawerSearchQuery,
        modelManager.observeActiveModel(),
        settingsRepository.settingsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allConversations = values[1] as List<ConversationWithPreview>
        @Suppress("UNCHECKED_CAST")
        val messages = values[2] as List<MessageEntity>
        val settings = values[5] as JarvisSettings
        NotificationBridge.syncPrivacyControls(
            enabled = settings.notificationReaderEnabled,
            maskEnabled = settings.notificationPrivacyMaskEnabled
        )
        BaseDataSnapshot(
            activeId = values[0] as String?,
            allConversations = allConversations,
            messages = messages,
            query = values[3] as String,
            activeModel = values[4] as ModelSlot?,
            settings = settings
        )
    }

    val uiState: StateFlow<ChatUiState> = combine(
        baseDataFlow,
        aiEngine.loadState,
        aiEngine.isGenerating,
        streamingReply,
        streamingBackend,
        streamingTokenEstimate,
        aiEngine.lastBenchmark,
        aiEngine.nativeMemoryMb,
        toolOverlayState
    ) { values ->
        val base = values[0] as BaseDataSnapshot
        val loadState = values[1] as EngineLoadState
        val isGen = values[2] as Boolean
        val streamText = values[3] as String?
        val streamBackend = values[4] as String?
        val streamTok = values[5] as Int
        val lastBench = values[6] as GenerationBenchmark?
        val nativeMem = values[7] as Float
        val toolOverlay = values[8] as ChatToolOverlayState

        val resolvedId = base.activeId ?: base.allConversations.firstOrNull()?.id
        val filteredConversations = if (base.query.isBlank()) {
            base.allConversations
        } else {
            val needle = base.query.trim().lowercase()
            base.allConversations.filter {
                it.title.lowercase().contains(needle) ||
                    (it.lastMessage?.lowercase()?.contains(needle) == true)
            }
        }
        val activeTitle = base.allConversations.firstOrNull { it.id == resolvedId }?.title
            ?: "Primary Core Session"

        ChatUiState(
            activeConversationId = resolvedId,
            activeConversationTitle = activeTitle,
            messages = base.messages,
            conversations = filteredConversations,
            drawerSearchQuery = base.query,
            activeModel = base.activeModel,
            settings = base.settings,
            engineLoadState = loadState,
            isGenerating = isGen || streamText != null,
            streamingReply = streamText,
            streamingBackend = streamBackend,
            streamingEstimatedTokens = streamTok,
            lastBenchmark = lastBench,
            nativeMemoryMb = nativeMem,
            pendingToolConfirmation = toolOverlay.pendingConfirmation,
            activeToolName = toolOverlay.activeToolName,
            pendingRuntimePermission = toolOverlay.pendingRuntimePermission,
            pendingSpecialAccess = toolOverlay.pendingSpecialAccess,
            permissionRationale = toolOverlay.permissionRationale
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    init {
        viewModelScope.launch {
            modelManager.ensureSeeded()
            activeConversationId.value = conversationRepository.ensureDefaultConversation()
        }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || uiState.value.isGenerating) return

        generationJob = viewModelScope.launch {
            val convId = activeConversationId.value
                ?: conversationRepository.ensureDefaultConversation().also {
                    activeConversationId.value = it
                }
            val currentSettings = uiState.value.settings
            val activeModel = modelManager.getActiveModelOnce()
            val historyBeforeSend = conversationRepository.getMessagesOnce(convId)

            conversationRepository.appendMessage(
                conversationId = convId,
                role = MessageRole.USER,
                content = text,
                modelLabel = "Operator"
            )

            // Direct /tool pipeline: AgentCore -> ToolRegistry -> PermissionValidator -> Android API
            val directToolRequest = agentCore.parseDirectToolCommand(text)
            if (directToolRequest != null) {
                dispatchToolInternal(convId, directToolRequest, currentSettings, userConfirmed = false)
                return@launch
            }

            // Auto-load or hot-swap local verified LiteRT-LM model if active slot changed or isn't loaded
            val readyState = aiEngine.loadState.value as? EngineLoadState.Ready
            if (readyState == null || (activeModel != null && readyState.modelId != activeModel.id)) {
                if (activeModel != null && activeModel.runtimeFormat != RuntimeFormatId.LITERT_LM) {
                    conversationRepository.appendMessage(
                        conversationId = convId,
                        role = MessageRole.SYSTEM_NOTICE,
                        content = "\"${activeModel.displayName}\" uses ${activeModel.runtimeFormat.label}, whose runtime adapter is prepared for a later phase. Select a LiteRT-LM (.litertlm) model in Models to run inference.",
                        modelLabel = "Adapter Boundary"
                    )
                    return@launch
                } else if (activeModel != null && activeModel.artifactState == ArtifactState.READY) {
                    val loadRes = aiEngine.loadModel(activeModel, currentSettings)
                    if (loadRes.isFailure) {
                        val reason = loadRes.exceptionOrNull()?.localizedMessage
                            ?: "Unable to initialize local LiteRT-LM runtime."
                        conversationRepository.appendMessage(
                            conversationId = convId,
                            role = MessageRole.SYSTEM_NOTICE,
                            content = reason,
                            modelLabel = "LiteRT-LM // Load Error"
                        )
                        return@launch
                    }
                } else {
                    val label = activeModel?.displayName ?: "No Model Selected"
                    conversationRepository.appendMessage(
                        conversationId = convId,
                        role = MessageRole.SYSTEM_NOTICE,
                        content = "Local model \"$label\" is not installed or loaded yet. Open Models to download or import a .litertlm model artifact and load the engine.",
                        modelLabel = "Engine Unloaded"
                    )
                    return@launch
                }
            }

            val modelDisplayName = activeModel?.displayName ?: "Gemma 3 1B"
            streamingReply.value = ""
            streamingTokenEstimate.value = 0

            try {
                agentCore.streamPrompt(
                    conversationId = convId,
                    prompt = text,
                    history = historyBeforeSend,
                    settings = currentSettings
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Started -> {
                            streamingBackend.value = event.backendUsed
                        }

                        is AiStreamEvent.Delta -> {
                            streamingReply.value = event.accumulatedText
                            streamingTokenEstimate.value = event.estimatedTokens
                        }

                        is AiStreamEvent.Completed -> {
                            val finalContent = event.fullText.trim()
                            clearStreamingState()
                            if (finalContent.isNotEmpty()) {
                                conversationRepository.appendMessage(
                                    conversationId = convId,
                                    role = MessageRole.ASSISTANT,
                                    content = finalContent,
                                    modelLabel = modelDisplayName,
                                    ttftMs = event.benchmark.ttftMs,
                                    tokensPerSecond = event.benchmark.tokensPerSecond,
                                    outputTokens = event.benchmark.outputTokens,
                                    backendUsed = event.benchmark.backendUsed
                                )
                                modelBenchmarkDao?.insertBenchmark(
                                    ModelBenchmarkEntity(
                                        id = UUID.randomUUID().toString(),
                                        modelId = activeModel?.id ?: "unknown",
                                        modelName = modelDisplayName,
                                        backendUsed = event.benchmark.backendUsed,
                                        ttftMs = event.benchmark.ttftMs,
                                        tokensPerSecond = event.benchmark.tokensPerSecond,
                                        outputTokens = event.benchmark.outputTokens,
                                        prefillTokens = event.benchmark.prefillTokens,
                                        nativeHeapMb = event.benchmark.nativeHeapMb,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                val modelToolCall = agentCore.extractModelToolCall(finalContent)
                                if (modelToolCall != null) {
                                    dispatchToolInternal(
                                        convId = convId,
                                        request = modelToolCall,
                                        settings = currentSettings,
                                        userConfirmed = false
                                    )
                                }
                            } else {
                                conversationRepository.appendMessage(
                                    conversationId = convId,
                                    role = MessageRole.SYSTEM_NOTICE,
                                    content = "Model returned an empty completion.",
                                    modelLabel = modelDisplayName
                                )
                            }
                        }

                        is AiStreamEvent.Cancelled -> {
                            val partial = event.partialText.trim()
                            clearStreamingState()
                            if (partial.isNotEmpty()) {
                                conversationRepository.appendMessage(
                                    conversationId = convId,
                                    role = MessageRole.ASSISTANT,
                                    content = "$partial\n\n[Generation stopped]",
                                    modelLabel = modelDisplayName,
                                    ttftMs = event.benchmark?.ttftMs,
                                    tokensPerSecond = event.benchmark?.tokensPerSecond,
                                    outputTokens = event.benchmark?.outputTokens,
                                    backendUsed = event.benchmark?.backendUsed
                                )
                            } else {
                                conversationRepository.appendMessage(
                                    conversationId = convId,
                                    role = MessageRole.SYSTEM_NOTICE,
                                    content = "Inference cancelled before first token.",
                                    modelLabel = modelDisplayName
                                )
                            }
                        }

                        is AiStreamEvent.Error -> {
                            clearStreamingState()
                            conversationRepository.appendMessage(
                                conversationId = convId,
                                role = MessageRole.SYSTEM_NOTICE,
                                content = event.message,
                                modelLabel = "LiteRT-LM // Error"
                            )
                        }
                    }
                }
            } finally {
                clearStreamingState()
            }
        }
    }

    fun confirmPendingTool() {
        val pending = toolOverlayState.value.pendingConfirmation ?: return
        val convId = activeConversationId.value ?: return
        val settings = uiState.value.settings
        toolOverlayState.value = toolOverlayState.value.copy(pendingConfirmation = null)
        toolJob = viewModelScope.launch {
            dispatchToolInternal(convId, pending.request, settings, userConfirmed = true)
        }
    }

    fun cancelPendingTool() {
        val pending = toolOverlayState.value.pendingConfirmation ?: return
        val convId = activeConversationId.value
        toolOverlayState.value = toolOverlayState.value.copy(pendingConfirmation = null)
        if (convId != null) {
            viewModelScope.launch {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.TOOL_SNAPSHOT,
                    content = "Action '${pending.request.toolName}' cancelled by user before execution.",
                    modelLabel = "Tool // Cancelled"
                )
            }
        }
    }

    fun cancelActiveTool() {
        val activeName = toolOverlayState.value.activeToolName
        agentCore.cancelActiveTool()
        toolJob?.cancel()
        toolOverlayState.value = toolOverlayState.value.copy(activeToolName = null)
        val convId = activeConversationId.value
        if (convId != null && activeName != null) {
            viewModelScope.launch {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.TOOL_SNAPSHOT,
                    content = "Tool execution '$activeName' cancelled.",
                    modelLabel = "Tool // Cancelled"
                )
            }
        }
    }

    fun dismissPermissionPrompt() {
        lastPermissionDeniedRequest = null
        toolOverlayState.value = toolOverlayState.value.copy(
            pendingRuntimePermission = null,
            pendingSpecialAccess = null,
            permissionRationale = null
        )
    }

    fun onRuntimePermissionResult(permission: String, granted: Boolean) {
        val deferred = lastPermissionDeniedRequest
        lastPermissionDeniedRequest = null
        toolOverlayState.value = toolOverlayState.value.copy(
            pendingRuntimePermission = null,
            pendingSpecialAccess = null,
            permissionRationale = null
        )
        if (deferred != null) {
            val (convId, req) = deferred
            viewModelScope.launch {
                if (granted) {
                    dispatchToolInternal(convId, req, uiState.value.settings, userConfirmed = false)
                } else {
                    conversationRepository.appendMessage(
                        conversationId = convId,
                        role = MessageRole.SYSTEM_NOTICE,
                        content = "Permission $permission was denied; tool '${req.toolName}' did not run.",
                        modelLabel = "Permission Validator"
                    )
                }
            }
        }
    }

    private suspend fun dispatchToolInternal(
        convId: String,
        request: ToolCallRequest,
        settings: JarvisSettings,
        userConfirmed: Boolean
    ) {
        toolOverlayState.value = toolOverlayState.value.copy(activeToolName = request.toolName)
        val result: ToolExecutionResult = try {
            agentCore.executeTool(
                request = request,
                settings = settings,
                userConfirmed = userConfirmed
            )
        } catch (_: CancellationException) {
            ToolExecutionResult(
                toolName = request.toolName,
                status = ToolExecutionStatus.CANCELLED,
                summary = "Tool execution '${request.toolName}' was cancelled."
            )
        } finally {
            toolOverlayState.value = toolOverlayState.value.copy(activeToolName = null)
        }

        when (result.status) {
            ToolExecutionStatus.CONFIRMATION_REQUIRED -> {
                val pending = agentCore.describeConfirmation(request)
                toolOverlayState.value = toolOverlayState.value.copy(pendingConfirmation = pending)
            }

            ToolExecutionStatus.PERMISSION_DENIED -> {
                if (result.missingRuntimePermission != null || result.missingSpecialAccess != null) {
                    lastPermissionDeniedRequest = convId to request
                    toolOverlayState.value = toolOverlayState.value.copy(
                        pendingRuntimePermission = result.missingRuntimePermission,
                        pendingSpecialAccess = result.missingSpecialAccess,
                        permissionRationale = result.summary
                    )
                }
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.TOOL_SNAPSHOT,
                    content = formatToolResultForChat(result),
                    modelLabel = "Permission Validator // ${result.toolName}"
                )
            }

            else -> {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.TOOL_SNAPSHOT,
                    content = formatToolResultForChat(result),
                    modelLabel = "Tool // ${result.toolName} (${result.status.name})"
                )
            }
        }
    }

    private fun formatToolResultForChat(result: ToolExecutionResult): String = buildString {
        appendLine("STATUS: ${result.status.name}")
        append(result.summary)
        if (result.verifiedFacts.isNotEmpty()) {
            appendLine()
            append("Verified: ")
            append(result.verifiedFacts.entries.joinToString(" • ") { "${it.key}=${it.value}" })
        }
    }.trim()

    fun cancelGeneration() {
        viewModelScope.launch {
            aiEngine.cancelGeneration()
        }
    }

    fun loadActiveModelNow() {
        viewModelScope.launch {
            val activeModel = modelManager.getActiveModelOnce() ?: return@launch
            aiEngine.loadModel(activeModel, uiState.value.settings)
        }
    }

    private fun clearStreamingState() {
        streamingReply.value = null
        streamingBackend.value = null
        streamingTokenEstimate.value = 0
    }

    fun runSystemSnapshot() {
        if (uiState.value.isGenerating) return
        viewModelScope.launch {
            val convId = activeConversationId.value
                ?: conversationRepository.ensureDefaultConversation().also {
                    activeConversationId.value = it
                }
            val snapshot = agentCore.runSystemTelemetrySnapshot()
            conversationRepository.appendMessage(
                conversationId = convId,
                role = MessageRole.TOOL_SNAPSHOT,
                content = snapshot.content,
                modelLabel = snapshot.label
            )
        }
    }

    fun runEngineReadinessAudit() {
        if (uiState.value.isGenerating) return
        viewModelScope.launch {
            val convId = activeConversationId.value
                ?: conversationRepository.ensureDefaultConversation().also {
                    activeConversationId.value = it
                }
            val audit = agentCore.inspectEngineReadiness()
            conversationRepository.appendMessage(
                conversationId = convId,
                role = MessageRole.TOOL_SNAPSHOT,
                content = audit.content,
                modelLabel = audit.label
            )
        }
    }

    fun selectConversation(id: String) {
        activeConversationId.value = id
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val newId = conversationRepository.createConversation(
                title = "Core Session ${uiState.value.conversations.size + 1}"
            )
            activeConversationId.value = newId
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            conversationRepository.renameConversation(id, newTitle)
        }
    }

    fun togglePinned(id: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            conversationRepository.togglePinned(id, currentlyPinned)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val nextId = conversationRepository.deleteConversation(id)
            if (activeConversationId.value == id) {
                activeConversationId.value = nextId
            }
        }
    }

    fun updateSearchQuery(query: String) {
        drawerSearchQuery.value = query
    }

    private data class BaseDataSnapshot(
        val activeId: String?,
        val allConversations: List<ConversationWithPreview>,
        val messages: List<MessageEntity>,
        val query: String,
        val activeModel: ModelSlot?,
        val settings: JarvisSettings
    )

    companion object {
        fun factory(
            conversationRepository: ConversationRepository,
            modelManager: ModelManager,
            agentCore: AgentCore,
            aiEngine: AiEngine,
            settingsRepository: SettingsRepository,
            modelBenchmarkDao: ModelBenchmarkDao? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    conversationRepository = conversationRepository,
                    modelManager = modelManager,
                    agentCore = agentCore,
                    aiEngine = aiEngine,
                    settingsRepository = settingsRepository,
                    modelBenchmarkDao = modelBenchmarkDao
                ) as T
            }
        }
    }
}
