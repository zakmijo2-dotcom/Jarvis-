package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.agent.AgentCore
import com.example.core.agent.AgentPlanStep
import com.example.core.agent.AgentPlanState
import com.example.core.agent.AgentRoutingMode
import com.example.core.agent.HybridAgentEvent
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.ai.GenerationBenchmark
import com.example.core.memory.LocalMemory
import com.example.core.model.ModelManager
import com.example.core.model.ModelSlot
import com.example.core.tools.AndroidTools
import com.example.core.tools.NotificationBridge
import com.example.core.tools.PendingToolConfirmation
import com.example.core.tools.SpecialAccessType
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import java.io.File
import com.example.data.local.db.ConversationWithPreview
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
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
import java.util.UUID

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
    val streamingIsCloud: Boolean = false,
    val lastBenchmark: GenerationBenchmark? = null,
    val nativeMemoryMb: Float = 0f,
    val pendingToolConfirmation: PendingToolConfirmation? = null,
    val activeToolName: String? = null,
    val activePlan: AgentPlanState? = null,
    val pendingRuntimePermission: String? = null,
    val pendingSpecialAccess: SpecialAccessType? = null,
    val permissionRationale: String? = null
)

private data class ChatToolOverlayState(
    val pendingConfirmation: PendingToolConfirmation? = null,
    val activeToolName: String? = null,
    val pendingRuntimePermission: String? = null,
    val pendingSpecialAccess: SpecialAccessType? = null,
    val permissionRationale: String? = null,
    val activePlan: AgentPlanState? = null,
    val streamingIsCloud: Boolean = false
)

private data class PausedPlanResumeContext(
    val conversationId: String,
    val prompt: String,
    val history: List<MessageEntity>,
    val stepIndex: Int,
    val stepsSnapshot: List<AgentPlanStep>
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val modelManager: ModelManager,
    private val agentCore: AgentCore,
    private val aiEngine: AiEngine,
    private val settingsRepository: SettingsRepository,
    private val modelBenchmarkDao: ModelBenchmarkDao? = null,
    private val localMemory: LocalMemory? = null,
    private val androidTools: AndroidTools? = null
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val drawerSearchQuery = MutableStateFlow("")
    private val streamingReply = MutableStateFlow<String?>(null)
    private val streamingBackend = MutableStateFlow<String?>(null)
    private val streamingTokenEstimate = MutableStateFlow(0)
    private val toolOverlayState = MutableStateFlow(ChatToolOverlayState())
    private var generationJob: Job? = null
    private var toolJob: Job? = null
    private var pausedPlanContext: PausedPlanResumeContext? = null
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
            streamingIsCloud = toolOverlay.streamingIsCloud,
            lastBenchmark = lastBench,
            nativeMemoryMb = nativeMem,
            pendingToolConfirmation = toolOverlay.pendingConfirmation,
            activeToolName = toolOverlay.activeToolName,
            activePlan = toolOverlay.activePlan,
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

            runHybridExecution(
                convId = convId,
                prompt = text,
                history = historyBeforeSend,
                settings = currentSettings,
                activeModel = activeModel,
                confirmedStepIndex = null,
                existingSteps = null
            )
        }
    }

    fun regenerateLastResponse() {
        if (uiState.value.isGenerating) return
        generationJob = viewModelScope.launch {
            val convId = activeConversationId.value ?: return@launch
            val allMessages = conversationRepository.getMessagesOnce(convId)
            val lastUserIdx = allMessages.indexOfLast { it.role == MessageRole.USER }
            if (lastUserIdx < 0) return@launch
            val lastUserMessage = allMessages[lastUserIdx]
            val historyBeforeLastUser = allMessages.subList(0, lastUserIdx)
            val currentSettings = uiState.value.settings
            val activeModel = modelManager.getActiveModelOnce()

            runHybridExecution(
                convId = convId,
                prompt = lastUserMessage.content,
                history = historyBeforeLastUser,
                settings = currentSettings,
                activeModel = activeModel,
                confirmedStepIndex = null,
                existingSteps = null
            )
        }
    }

    private suspend fun runHybridExecution(
        convId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings,
        activeModel: ModelSlot?,
        confirmedStepIndex: Int?,
        existingSteps: List<AgentPlanStep>?
    ) {
        try {
            agentCore.executeHybridTask(
                conversationId = convId,
                prompt = prompt,
                history = history,
                settings = settings,
                confirmedStepIndex = confirmedStepIndex,
                existingSteps = existingSteps
            ).collect { hybridEvent ->
                when (hybridEvent) {
                    is HybridAgentEvent.PlanUpdated -> {
                        toolOverlayState.value = toolOverlayState.value.copy(
                            activePlan = hybridEvent.plan
                        )
                    }

                    is HybridAgentEvent.ToolSnapshotEmitted -> {
                        conversationRepository.appendMessage(
                            conversationId = convId,
                            role = MessageRole.TOOL_SNAPSHOT,
                            content = hybridEvent.content,
                            modelLabel = hybridEvent.label
                        )
                    }

                    is HybridAgentEvent.Notice -> {
                        conversationRepository.appendMessage(
                            conversationId = convId,
                            role = MessageRole.SYSTEM_NOTICE,
                            content = hybridEvent.message,
                            modelLabel = hybridEvent.label
                        )
                    }

                    is HybridAgentEvent.ConfirmationRequired -> {
                        val currentSteps = toolOverlayState.value.activePlan?.steps.orEmpty()
                        pausedPlanContext = PausedPlanResumeContext(
                            conversationId = convId,
                            prompt = prompt,
                            history = history,
                            stepIndex = hybridEvent.stepIndex,
                            stepsSnapshot = currentSteps
                        )
                        val pending = agentCore.describeConfirmation(hybridEvent.request)
                        toolOverlayState.value = toolOverlayState.value.copy(
                            pendingConfirmation = pending
                        )
                    }

                    is HybridAgentEvent.PermissionRequired -> {
                        lastPermissionDeniedRequest = convId to hybridEvent.request
                        toolOverlayState.value = toolOverlayState.value.copy(
                            pendingRuntimePermission = hybridEvent.missingRuntimePermission,
                            pendingSpecialAccess = hybridEvent.missingSpecialAccess,
                            permissionRationale = hybridEvent.rationale
                        )
                    }

                    is HybridAgentEvent.AiStream -> {
                        val event = hybridEvent.event
                        val modelLabel = if (hybridEvent.usedCloud) {
                            val chosenModel = settings.cloudModelId.ifBlank { settings.cloudProviderKind.defaultModel }
                            "Cloud // $chosenModel"
                        } else {
                            "${activeModel?.displayName ?: "Gemma 3 1B"} (Local)"
                        }

                        when (event) {
                            is AiStreamEvent.Started -> {
                                streamingReply.value = ""
                                streamingTokenEstimate.value = 0
                                streamingBackend.value = event.backendUsed
                                toolOverlayState.value = toolOverlayState.value.copy(
                                    streamingIsCloud = hybridEvent.usedCloud
                                )
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
                                        modelLabel = modelLabel,
                                        ttftMs = event.benchmark.ttftMs,
                                        tokensPerSecond = event.benchmark.tokensPerSecond,
                                        outputTokens = event.benchmark.outputTokens,
                                        backendUsed = event.benchmark.backendUsed
                                    )
                                    val telemetry = androidTools?.let { tools ->
                                        runCatching { tools.readDeviceTelemetry() }.getOrNull()
                                    }
                                    val resolvedModelSize = event.benchmark.modelSizeBytes.takeIf { it > 0L }
                                        ?: activeModel?.localFilePath?.let { path ->
                                            runCatching { File(path).length() }.getOrNull()?.takeIf { it > 0L }
                                        }
                                        ?: activeModel?.expectedSizeBytes
                                        ?: 0L
                                    val resolvedInitTime = event.benchmark.initTimeMs.takeIf { it > 0L }
                                        ?: (aiEngine.loadState.value as? EngineLoadState.Ready)?.loadTimeMs
                                        ?: 0L
                                    val resolvedThermal = telemetry?.thermalStatusLabel?.takeIf { it != "Unavailable" }
                                        ?: event.benchmark.thermalStatus
                                    modelBenchmarkDao?.insertBenchmark(
                                        ModelBenchmarkEntity(
                                            id = UUID.randomUUID().toString(),
                                            modelId = if (hybridEvent.usedCloud) settings.cloudProviderKind.id else (activeModel?.id ?: "unknown"),
                                            modelName = modelLabel,
                                            backendUsed = event.benchmark.backendUsed,
                                            ttftMs = event.benchmark.ttftMs,
                                            tokensPerSecond = event.benchmark.tokensPerSecond,
                                            outputTokens = event.benchmark.outputTokens,
                                            prefillTokens = event.benchmark.prefillTokens,
                                            nativeHeapMb = event.benchmark.nativeHeapMb,
                                            timestamp = System.currentTimeMillis(),
                                            initTimeMs = resolvedInitTime,
                                            peakMemoryMb = maxOf(event.benchmark.peakMemoryMb, event.benchmark.nativeHeapMb),
                                            modelSizeBytes = resolvedModelSize,
                                            thermalStatus = resolvedThermal,
                                            batteryPercent = telemetry?.batteryPercent ?: event.benchmark.batteryPercent,
                                            batteryTempCelsius = telemetry?.batteryTempCelsius ?: event.benchmark.batteryTempCelsius
                                        )
                                    )
                                    val modelToolCall = agentCore.extractModelToolCall(finalContent)
                                    if (modelToolCall != null) {
                                        dispatchStandaloneTool(
                                            convId = convId,
                                            request = modelToolCall,
                                            settings = settings,
                                            userConfirmed = false
                                        )
                                    }
                                } else {
                                    conversationRepository.appendMessage(
                                        conversationId = convId,
                                        role = MessageRole.SYSTEM_NOTICE,
                                        content = "Model returned an empty completion.",
                                        modelLabel = modelLabel
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
                                        modelLabel = modelLabel,
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
                                        modelLabel = modelLabel
                                    )
                                }
                            }

                            is AiStreamEvent.Error -> {
                                clearStreamingState()
                                conversationRepository.appendMessage(
                                    conversationId = convId,
                                    role = MessageRole.SYSTEM_NOTICE,
                                    content = event.message,
                                    modelLabel = if (hybridEvent.usedCloud) "Cloud API // Error" else "LiteRT-LM // Error"
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            clearStreamingState()
            if (pausedPlanContext == null &&
                toolOverlayState.value.pendingConfirmation == null &&
                toolOverlayState.value.pendingRuntimePermission == null &&
                toolOverlayState.value.pendingSpecialAccess == null
            ) {
                toolOverlayState.value = toolOverlayState.value.copy(activePlan = null)
            }
        }
    }

    fun confirmPendingTool() {
        val pending = toolOverlayState.value.pendingConfirmation ?: return
        val paused = pausedPlanContext
        val convId = paused?.conversationId ?: activeConversationId.value ?: return
        val settings = uiState.value.settings
        toolOverlayState.value = toolOverlayState.value.copy(pendingConfirmation = null)
        pausedPlanContext = null

        toolJob = viewModelScope.launch {
            if (paused != null) {
                runHybridExecution(
                    convId = convId,
                    prompt = paused.prompt,
                    history = paused.history,
                    settings = settings,
                    activeModel = modelManager.getActiveModelOnce(),
                    confirmedStepIndex = paused.stepIndex,
                    existingSteps = paused.stepsSnapshot
                )
            } else {
                dispatchStandaloneTool(convId, pending.request, settings, userConfirmed = true)
            }
        }
    }

    fun cancelPendingTool() {
        val pending = toolOverlayState.value.pendingConfirmation ?: return
        val convId = pausedPlanContext?.conversationId ?: activeConversationId.value
        pausedPlanContext = null
        toolOverlayState.value = toolOverlayState.value.copy(pendingConfirmation = null, activePlan = null)
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
        agentCore.cancelAutonomousTask()
        toolJob?.cancel()
        generationJob?.cancel()
        toolOverlayState.value = toolOverlayState.value.copy(activeToolName = null, activePlan = null)
        val convId = activeConversationId.value
        if (convId != null) {
            viewModelScope.launch {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.TOOL_SNAPSHOT,
                    content = "Execution '${activeName ?: "Autonomous Plan"}' cancelled.",
                    modelLabel = "Agent // Cancelled"
                )
            }
        }
    }

    fun setRoutingMode(mode: AgentRoutingMode) {
        viewModelScope.launch {
            settingsRepository.setRoutingMode(mode)
        }
    }

    fun summarizeCurrentSessionToMemory() {
        val convId = activeConversationId.value ?: return
        val memory = localMemory ?: return
        viewModelScope.launch {
            val msgs = conversationRepository.getMessagesOnce(convId)
            val saved = memory.summarizeAndStoreConversation(
                conversationId = convId,
                title = uiState.value.activeConversationTitle,
                messages = msgs,
                userApproved = true
            )
            val notice = if (saved != null) {
                "Saved approved session summary to local Room memory: ${saved.content}"
            } else {
                "Session summary skipped (empty conversation or contains sensitive credentials)."
            }
            conversationRepository.appendMessage(
                conversationId = convId,
                role = MessageRole.SYSTEM_NOTICE,
                content = notice,
                modelLabel = "Local Memory"
            )
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
                    dispatchStandaloneTool(convId, req, uiState.value.settings, userConfirmed = false)
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

    private suspend fun dispatchStandaloneTool(
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
            agentCore.cancelAutonomousTask()
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
        toolOverlayState.value = toolOverlayState.value.copy(streamingIsCloud = false)
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
            modelBenchmarkDao: ModelBenchmarkDao? = null,
            localMemory: LocalMemory? = null,
            androidTools: AndroidTools? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    conversationRepository = conversationRepository,
                    modelManager = modelManager,
                    agentCore = agentCore,
                    aiEngine = aiEngine,
                    settingsRepository = settingsRepository,
                    modelBenchmarkDao = modelBenchmarkDao,
                    localMemory = localMemory,
                    androidTools = androidTools
                ) as T
            }
        }
    }
}
