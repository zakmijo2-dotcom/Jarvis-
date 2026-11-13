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
import com.example.data.local.db.ConversationWithPreview
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import java.util.UUID
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
    val nativeMemoryMb: Float = 0f
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
    private var generationJob: Job? = null

    private val activeMessagesFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        activeConversationId,
        conversationRepository.observeConversations(),
        activeMessagesFlow,
        drawerSearchQuery,
        modelManager.observeActiveModel(),
        settingsRepository.settingsFlow,
        aiEngine.loadState,
        aiEngine.isGenerating,
        streamingReply,
        streamingBackend,
        streamingTokenEstimate,
        aiEngine.lastBenchmark,
        aiEngine.nativeMemoryMb
    ) { values ->
        val activeId = values[0] as String?
        @Suppress("UNCHECKED_CAST")
        val allConversations = values[1] as List<ConversationWithPreview>
        @Suppress("UNCHECKED_CAST")
        val messages = values[2] as List<MessageEntity>
        val query = values[3] as String
        val activeModel = values[4] as ModelSlot?
        val settings = values[5] as JarvisSettings
        val loadState = values[6] as EngineLoadState
        val isGen = values[7] as Boolean
        val streamText = values[8] as String?
        val streamBackend = values[9] as String?
        val streamTok = values[10] as Int
        val lastBench = values[11] as GenerationBenchmark?
        val nativeMem = values[12] as Float

        val resolvedId = activeId ?: allConversations.firstOrNull()?.id
        val filteredConversations = if (query.isBlank()) {
            allConversations
        } else {
            val needle = query.trim().lowercase()
            allConversations.filter {
                it.title.lowercase().contains(needle) ||
                    (it.lastMessage?.lowercase()?.contains(needle) == true)
            }
        }
        val activeTitle = allConversations.firstOrNull { it.id == resolvedId }?.title
            ?: "Primary Core Session"

        ChatUiState(
            activeConversationId = resolvedId,
            activeConversationTitle = activeTitle,
            messages = messages,
            conversations = filteredConversations,
            drawerSearchQuery = query,
            activeModel = activeModel,
            settings = settings,
            engineLoadState = loadState,
            isGenerating = isGen || streamText != null,
            streamingReply = streamText,
            streamingBackend = streamBackend,
            streamingEstimatedTokens = streamTok,
            lastBenchmark = lastBench,
            nativeMemoryMb = nativeMem
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
