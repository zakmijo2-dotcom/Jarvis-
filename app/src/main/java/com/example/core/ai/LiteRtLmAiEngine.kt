package com.example.core.ai

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Debug
import com.example.core.model.ArtifactState
import com.example.core.model.ModelSlot
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LiteRtLmAiEngine(
    context: Context
) : AiEngine {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jarvis-litertlm-engine").apply { isDaemon = true }
    }
    private val engineDispatcher = executor.asCoroutineDispatcher()
    private val engineScope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelId: String? = null
    private var activeModelPath: String? = null
    private var activeBackendLabel: String = "CPU"
    private var activeContextCeiling: Int = 4096
    private var activeConversationId: String? = null
    private var activeSamplerSignature: String? = null
    private var activeInitTimeMs: Long = 0L
    private var activeModelSizeBytes: Long = 0L

    private val _loadState = MutableStateFlow<EngineLoadState>(EngineLoadState.Unloaded)
    override val loadState: StateFlow<EngineLoadState> = _loadState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastBenchmark = MutableStateFlow<GenerationBenchmark?>(null)
    override val lastBenchmark: StateFlow<GenerationBenchmark?> = _lastBenchmark.asStateFlow()

    private val _nativeMemoryMb = MutableStateFlow(readNativeHeapMb())
    override val nativeMemoryMb: StateFlow<Float> = _nativeMemoryMb.asStateFlow()

    private val cancelRequested = AtomicBoolean(false)

    override suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
        withContext(engineDispatcher) {
            mutex.withLock {
                loadInternalLocked(slot, settings, forceReload = false)
            }
        }

    override suspend fun reloadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
        withContext(engineDispatcher) {
            mutex.withLock {
                loadInternalLocked(slot, settings, forceReload = true)
            }
        }

    override suspend fun unloadModel() {
        withContext(engineDispatcher) {
            mutex.withLock {
                releaseInternalLocked()
            }
        }
    }

    private fun loadInternalLocked(
        slot: ModelSlot,
        settings: JarvisSettings,
        forceReload: Boolean
    ): Result<Unit> {
        val modelPath = slot.localFilePath
        if (modelPath.isNullOrBlank() || slot.artifactState != ArtifactState.READY) {
            val msg = "${slot.displayName} is not installed locally. Download or import a .litertlm artifact in Models first."
            _loadState.value = EngineLoadState.Error(msg)
            return Result.failure(IllegalStateException(msg))
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile || modelFile.length() <= 1024L) {
            val msg = "Model artifact missing or corrupted at $modelPath."
            _loadState.value = EngineLoadState.Error(msg, isCorruptedFile = true)
            return Result.failure(IllegalStateException(msg))
        }

        if (!forceReload &&
            engine != null &&
            activeModelId == slot.id &&
            activeModelPath == modelPath &&
            activeContextCeiling == settings.contextLengthTokens
        ) {
            return Result.success(Unit)
        }

        val headroom = ThermalAndMemoryPolicy.assessMemoryHeadroom(appContext, modelFile.length())
        if (!headroom.canLoadModel) {
            val msg = headroom.reason ?: "Insufficient free system RAM to initialize ${slot.displayName}."
            _loadState.value = EngineLoadState.Error(msg)
            return Result.failure(IllegalStateException(msg))
        }

        releaseInternalLocked()

        val (thermalCode, _) = ThermalAndMemoryPolicy.readThermalStatus(appContext)
        val candidates: List<Pair<Backend, String>> = when (settings.inferenceBackend) {
            InferenceBackendPreference.AUTO_GPU_FALLBACK -> {
                if (thermalCode >= android.os.PowerManager.THERMAL_STATUS_CRITICAL) {
                    listOf(
                        Backend.CPU() to "CPU (XNNPACK Thermal)",
                        Backend.GPU() to "GPU (OpenCL)"
                    )
                } else {
                    listOf(
                        Backend.GPU() to "GPU (OpenCL)",
                        Backend.CPU() to "CPU (XNNPACK)"
                    )
                }
            }
            InferenceBackendPreference.GPU_ONLY -> listOf(Backend.GPU() to "GPU (OpenCL)")
            InferenceBackendPreference.CPU_ONLY -> listOf(Backend.CPU() to "CPU (XNNPACK)")
        }

        val cacheDirPath = File(appContext.cacheDir, "litertlm_cache").apply { mkdirs() }.absolutePath
        var lastError: Throwable? = null

        for ((backend, backendLabel) in candidates) {
            _loadState.value = EngineLoadState.Loading(slot.displayName, backendLabel)
            val startNs = System.nanoTime()
            var candidateEngine: Engine? = null
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    maxNumTokens = settings.contextLengthTokens,
                    cacheDir = cacheDirPath
                )
                candidateEngine = Engine(config)
                candidateEngine.initialize()

                engine = candidateEngine
                activeModelId = slot.id
                activeModelPath = modelFile.absolutePath
                activeBackendLabel = backendLabel
                activeContextCeiling = settings.contextLengthTokens
                activeConversationId = null
                conversation = null

                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                activeInitTimeMs = elapsedMs
                activeModelSizeBytes = modelFile.length()
                _loadState.value = EngineLoadState.Ready(
                    modelId = slot.id,
                    modelName = slot.displayName,
                    backendUsed = backendLabel,
                    loadTimeMs = elapsedMs
                )
                refreshMemoryUsage()
                return Result.success(Unit)
            } catch (t: Throwable) {
                lastError = t
                // Never call close() on an Engine whose initialize() threw
                if (candidateEngine != null && runCatching { candidateEngine.isInitialized() }.getOrDefault(false)) {
                    runCatching { candidateEngine.close() }
                }
            }
        }

        val errorMsg = buildString {
            append("Failed to initialize LiteRT-LM for ${slot.displayName}: ")
            append(lastError?.localizedMessage ?: lastError?.javaClass?.simpleName ?: "Unknown runtime error")
        }
        val corruptedHint = errorMsg.contains("corrupt", ignoreCase = true) ||
            errorMsg.contains("invalid", ignoreCase = true) ||
            errorMsg.contains("magic", ignoreCase = true) ||
            errorMsg.contains("flatbuffer", ignoreCase = true)
        _loadState.value = EngineLoadState.Error(errorMsg, isCorruptedFile = corruptedHint)
        refreshMemoryUsage()
        return Result.failure(lastError ?: IllegalStateException(errorMsg))
    }

    override fun streamReply(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent> = callbackFlow {
        val job = engineScope.launch {
            mutex.withLock {
                val currentEngine = engine
                if (currentEngine == null || !currentEngine.isInitialized()) {
                    trySend(
                        AiStreamEvent.Error(
                            "Local LiteRT-LM engine is not loaded. Open Models or tap Load Gemma 3 1B first."
                        )
                    )
                    close()
                    return@withLock
                }

                cancelRequested.set(false)
                _isGenerating.value = true
                trySend(AiStreamEvent.Started(activeBackendLabel))

                try {
                    val samplerSignature = "${settings.samplerTopK}_${settings.samplerTopP}_${settings.samplerTemperature}"
                    val activeConv = if (conversation != null &&
                        activeConversationId == conversationId &&
                        activeSamplerSignature == samplerSignature
                    ) {
                        checkNotNull(conversation)
                    } else {
                        conversation?.let { runCatching { it.close() } }
                        conversation = null

                        val seededHistory = buildSeededMessages(
                            history = history,
                            contextBudgetTokens = settings.contextLengthTokens
                        )
                        val convConfig = ConversationConfig(
                            systemInstruction = Contents.of(JARVIS_SYSTEM_INSTRUCTION),
                            initialMessages = seededHistory,
                            samplerConfig = SamplerConfig(
                                topK = settings.samplerTopK,
                                topP = settings.samplerTopP.toDouble(),
                                temperature = settings.samplerTemperature.toDouble(),
                                seed = 0
                            )
                        )
                        currentEngine.createConversation(convConfig).also {
                            conversation = it
                            activeConversationId = conversationId
                            activeSamplerSignature = samplerSignature
                        }
                    }

                    val (thermalCode, thermalLabel) = ThermalAndMemoryPolicy.readThermalStatus(appContext)
                    val adaptedSettings = ThermalAndMemoryPolicy.applyThermalAdaptation(
                        settings = settings,
                        thermalCode = thermalCode,
                        thermalLabel = thermalLabel
                    ).effectiveSettings

                    val startNs = System.nanoTime()
                    var firstTokenNs: Long? = null
                    val buffer = StringBuilder()
                    var chunkCount = 0
                    var hitTokenCeiling = false
                    var peakHeapMb = readNativeHeapMb()

                    activeConv.sendMessageAsync(prompt).collect { chunkMessage ->
                        val delta = chunkMessage.toString()
                        if (firstTokenNs == null && delta.isNotEmpty()) {
                            firstTokenNs = System.nanoTime()
                        }
                        buffer.append(delta)
                        chunkCount++
                        if (chunkCount % 6 == 0) {
                            peakHeapMb = maxOf(peakHeapMb, readNativeHeapMb())
                        }

                        val estimatedTokens = estimateTokenCount(buffer.toString()).coerceAtLeast(chunkCount)
                        trySend(
                            AiStreamEvent.Delta(
                                accumulatedText = buffer.toString(),
                                deltaText = delta,
                                estimatedTokens = estimatedTokens
                            )
                        )

                        if (estimatedTokens >= adaptedSettings.maxOutputTokens && !hitTokenCeiling) {
                            hitTokenCeiling = true
                            cancelRequested.set(true)
                            runCatching { activeConv.cancelProcess() }
                        }
                    }

                    val endNs = System.nanoTime()
                    peakHeapMb = maxOf(peakHeapMb, readNativeHeapMb())
                    val benchmark = extractBenchmark(
                        conversation = activeConv,
                        startNs = startNs,
                        firstTokenNs = firstTokenNs,
                        endNs = endNs,
                        outputText = buffer.toString(),
                        fallbackTokenCount = chunkCount,
                        peakHeapMb = peakHeapMb,
                        thermalLabel = thermalLabel
                    )
                    _lastBenchmark.value = benchmark
                    refreshMemoryUsage()

                    if (cancelRequested.get() && !hitTokenCeiling) {
                        trySend(AiStreamEvent.Cancelled(buffer.toString(), benchmark))
                    } else {
                        trySend(AiStreamEvent.Completed(buffer.toString(), benchmark))
                    }
                } catch (e: CancellationException) {
                    runCatching { conversation?.cancelProcess() }
                    trySend(AiStreamEvent.Cancelled("", null))
                } catch (t: Throwable) {
                    val message = t.localizedMessage ?: t.javaClass.simpleName
                    trySend(AiStreamEvent.Error("Local inference error ($activeBackendLabel): $message"))
                } finally {
                    _isGenerating.value = false
                    refreshMemoryUsage()
                    close()
                }
            }
        }

        awaitClose {
            if (job.isActive) {
                cancelRequested.set(true)
                runCatching { conversation?.cancelProcess() }
                job.cancel()
            }
        }
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
        runCatching { conversation?.cancelProcess() }
    }

    override fun refreshMemoryUsage() {
        _nativeMemoryMb.value = readNativeHeapMb()
    }

    @Suppress("DEPRECATION")
    override suspend fun trimMemory(level: Int) {
        if (_isGenerating.value) return
        withContext(engineDispatcher) {
            mutex.withLock {
                if (_isGenerating.value) return@withLock
                when {
                    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        releaseInternalLocked()
                    }
                    level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                        conversation?.let { runCatching { it.close() } }
                        conversation = null
                        activeConversationId = null
                        refreshMemoryUsage()
                    }
                }
            }
        }
    }

    override fun release() {
        engineScope.launch {
            mutex.withLock {
                releaseInternalLocked()
            }
            executor.shutdown()
        }.invokeOnCompletion {
            engineScope.cancel()
        }
    }

    private fun releaseInternalLocked() {
        if (_isGenerating.getAndResetToFalse()) {
            cancelRequested.set(true)
            runCatching { conversation?.cancelProcess() }
        }
        conversation?.let { runCatching { it.close() } }
        conversation = null
        activeConversationId = null
        activeSamplerSignature = null

        engine?.let { eng ->
            if (runCatching { eng.isInitialized() }.getOrDefault(false)) {
                runCatching { eng.close() }
            }
        }
        engine = null
        activeModelId = null
        activeModelPath = null
        _loadState.value = EngineLoadState.Unloaded
        refreshMemoryUsage()
    }

    private fun MutableStateFlow<Boolean>.getAndResetToFalse(): Boolean {
        val prev = value
        value = false
        return prev
    }

    private fun buildSeededMessages(
        history: List<MessageEntity>,
        contextBudgetTokens: Int
    ): List<Message> {
        val conversational = history.filter {
            it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT
        }
        if (conversational.isEmpty()) return emptyList()

        // Reserve ~40% of context budget for system prompt, current turn, and reply generation
        val maxHistoryCharBudget = (contextBudgetTokens * 0.6f * 3.5f).toInt().coerceAtLeast(1024)
        var runningChars = 0
        val selected = mutableListOf<Message>()

        for (item in conversational.asReversed()) {
            val text = item.content.trim()
            if (text.isEmpty()) continue
            if (runningChars + text.length > maxHistoryCharBudget && selected.isNotEmpty()) {
                break
            }
            runningChars += text.length
            val mapped = when (item.role) {
                MessageRole.USER -> Message.user(text)
                MessageRole.ASSISTANT -> Message.model(text)
                else -> null
            }
            if (mapped != null) {
                selected.add(mapped)
            }
        }
        return selected.asReversed()
    }

    @OptIn(ExperimentalApi::class)
    private fun extractBenchmark(
        conversation: Conversation,
        startNs: Long,
        firstTokenNs: Long?,
        endNs: Long,
        outputText: String,
        fallbackTokenCount: Int,
        peakHeapMb: Float,
        thermalLabel: String
    ): GenerationBenchmark {
        val nativeBench = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
        val fallbackTtftMs = ((firstTokenNs ?: endNs) - startNs) / 1_000_000L
        val decodeDurationSec = ((endNs - (firstTokenNs ?: startNs)).coerceAtLeast(1_000_000L)) / 1_000_000_000.0
        val estimatedTokens = estimateTokenCount(outputText).coerceAtLeast(fallbackTokenCount)

        val ttftMs = nativeBench?.timeToFirstTokenInSecond
            ?.takeIf { it > 0.0 }
            ?.let { (it * 1000.0).toLong() }
            ?: fallbackTtftMs.coerceAtLeast(1L)

        val outTokens = nativeBench?.lastDecodeTokenCount
            ?.takeIf { it > 0 }
            ?: estimatedTokens

        val tps = nativeBench?.lastDecodeTokensPerSecond
            ?.takeIf { it > 0.0 }
            ?.toFloat()
            ?: (outTokens.toFloat() / decodeDurationSec.toFloat()).coerceAtLeast(0.1f)

        val prefillTokens = nativeBench?.lastPrefillTokenCount ?: 0
        val currentHeapMb = readNativeHeapMb()

        return GenerationBenchmark(
            ttftMs = ttftMs,
            tokensPerSecond = tps,
            outputTokens = outTokens,
            prefillTokens = prefillTokens,
            backendUsed = activeBackendLabel,
            nativeHeapMb = currentHeapMb,
            initTimeMs = activeInitTimeMs,
            peakMemoryMb = maxOf(peakHeapMb, currentHeapMb),
            modelSizeBytes = activeModelSizeBytes,
            thermalStatus = thermalLabel
        )
    }

    private fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }

    private fun readNativeHeapMb(): Float {
        val bytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L)
        return bytes / (1024f * 1024f)
    }

    companion object {
        private const val JARVIS_SYSTEM_INSTRUCTION =
            "You are JARVIS, a concise, accurate, helpful personal AI assistant running locally on Android."
    }
}
