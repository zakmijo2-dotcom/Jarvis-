package com.example.core.ai

import android.content.Context
import android.os.Debug
import com.example.core.model.ModelSlot
import com.example.core.model.RuntimeFormatId
import com.example.data.local.db.MessageEntity
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ModelCapabilityProfile(
    val supportsToolCalling: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val maxContextTokens: Int = 4096,
    val estimatedLatencyTier: String = "FAST_LOCAL",
    val offlineCapable: Boolean = true
) {
    fun toSummaryLabel(): String =
        "ctx=$maxContextTokens • tools=${if (supportsToolCalling) "yes" else "no"} • tier=$estimatedLatencyTier • ${if (offlineCapable) "offline" else "cloud"}"
}

data class RuntimePerformanceSnapshot(
    val runtimeName: String,
    val formatId: RuntimeFormatId,
    val isExecutionSupported: Boolean,
    val isLoaded: Boolean,
    val activeModelName: String?,
    val backendUsed: String?,
    val nativeHeapMb: Float,
    val lastBenchmark: GenerationBenchmark?,
    val capabilities: ModelCapabilityProfile = ModelCapabilityProfile()
)

/**
 * Modular local runtime engine boundary so LiteRT-LM, future GGUF/llama.cpp,
 * LiteRT/TFLite, and MediaPipe Task runtimes share an identical lifecycle and telemetry contract.
 */
interface ModelRuntimeEngine {
    val formatId: RuntimeFormatId
    val runtimeName: String
    val isExecutionSupported: Boolean
    val capabilityProfile: ModelCapabilityProfile
    val loadState: StateFlow<EngineLoadState>
    val isGenerating: StateFlow<Boolean>
    val lastBenchmark: StateFlow<GenerationBenchmark?>
    val nativeMemoryMb: StateFlow<Float>

    suspend fun initialize(): Result<Unit>
    suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit>
    fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent>
    suspend fun cancel()
    suspend fun unload()
    suspend fun trimMemory(level: Int) {}
    fun release()
    fun reportPerformance(): RuntimePerformanceSnapshot
}

class LiteRtLmRuntimeAdapter(
    private val delegate: LiteRtLmAiEngine
) : ModelRuntimeEngine {
    override val formatId: RuntimeFormatId = RuntimeFormatId.LITERT_LM
    override val runtimeName: String = "LiteRT-LM"
    override val isExecutionSupported: Boolean = true
    override val capabilityProfile: ModelCapabilityProfile = ModelCapabilityProfile(
        supportsToolCalling = true,
        supportsVision = false,
        supportsStreaming = true,
        maxContextTokens = 4096,
        estimatedLatencyTier = "FAST_LOCAL",
        offlineCapable = true
    )
    override val loadState: StateFlow<EngineLoadState> = delegate.loadState
    override val isGenerating: StateFlow<Boolean> = delegate.isGenerating
    override val lastBenchmark: StateFlow<GenerationBenchmark?> = delegate.lastBenchmark
    override val nativeMemoryMb: StateFlow<Float> = delegate.nativeMemoryMb

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
        delegate.loadModel(slot, settings)

    suspend fun reloadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
        delegate.reloadModel(slot, settings)

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent> = delegate.streamReply(conversationId, prompt, history, settings)

    override suspend fun cancel() = delegate.cancelGeneration()

    override suspend fun unload() = delegate.unloadModel()

    override suspend fun trimMemory(level: Int) = delegate.trimMemory(level)

    override fun release() = delegate.release()

    override fun reportPerformance(): RuntimePerformanceSnapshot {
        val ready = loadState.value as? EngineLoadState.Ready
        return RuntimePerformanceSnapshot(
            runtimeName = runtimeName,
            formatId = formatId,
            isExecutionSupported = isExecutionSupported,
            isLoaded = ready != null,
            activeModelName = ready?.modelName,
            backendUsed = ready?.backendUsed,
            nativeHeapMb = nativeMemoryMb.value,
            lastBenchmark = lastBenchmark.value
        )
    }
}

/**
 * Honest boundary for runtimes whose artifact formats are inspected and stored by the Model Hub,
 * while native execution libraries (e.g., llama.cpp JNI, TFLite interpreter, MediaPipe GenAI)
 * are not bundled in the APK yet.
 */
class PreparedFormatRuntimeStub(
    override val formatId: RuntimeFormatId,
    override val runtimeName: String,
    private val unavailableReason: String
) : ModelRuntimeEngine {
    override val isExecutionSupported: Boolean = false
    override val capabilityProfile: ModelCapabilityProfile = ModelCapabilityProfile(
        supportsToolCalling = false,
        supportsVision = false,
        supportsStreaming = false,
        maxContextTokens = 2048,
        estimatedLatencyTier = "UNSUPPORTED",
        offlineCapable = false
    )
    private val _loadState = MutableStateFlow<EngineLoadState>(EngineLoadState.Unloaded)
    override val loadState: StateFlow<EngineLoadState> = _loadState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastBenchmark = MutableStateFlow<GenerationBenchmark?>(null)
    override val lastBenchmark: StateFlow<GenerationBenchmark?> = _lastBenchmark.asStateFlow()

    private val _nativeMemoryMb = MutableStateFlow(0f)
    override val nativeMemoryMb: StateFlow<Float> = _nativeMemoryMb.asStateFlow()

    override suspend fun initialize(): Result<Unit> =
        Result.failure(UnsupportedOperationException(unavailableReason))

    override suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> {
        _loadState.value = EngineLoadState.Error(unavailableReason)
        return Result.failure(UnsupportedOperationException(unavailableReason))
    }

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent> = flow {
        emit(AiStreamEvent.Error(unavailableReason))
    }

    override suspend fun cancel() = Unit

    override suspend fun unload() {
        _loadState.value = EngineLoadState.Unloaded
    }

    override fun release() {
        _loadState.value = EngineLoadState.Unloaded
    }

    override fun reportPerformance(): RuntimePerformanceSnapshot = RuntimePerformanceSnapshot(
        runtimeName = runtimeName,
        formatId = formatId,
        isExecutionSupported = false,
        isLoaded = false,
        activeModelName = null,
        backendUsed = null,
        nativeHeapMb = nativeMemoryMb.value,
        lastBenchmark = null
    )
}

/**
 * Coordinates registered local runtimes under a unified [AiEngine] contract and prevents
 * simultaneous heavy-model memory allocation unless verified free RAM headroom allows it.
 */
class RuntimeEngineCoordinator(
    context: Context,
    private val runtimes: Map<RuntimeFormatId, ModelRuntimeEngine>
) : AiEngine {

    constructor(
        context: Context,
        liteRtLmAiEngine: LiteRtLmAiEngine
    ) : this(
        context = context,
        runtimes = mapOf(
            RuntimeFormatId.LITERT_LM to LiteRtLmRuntimeAdapter(liteRtLmAiEngine),
            RuntimeFormatId.GGUF_LLAMA_CPP to PreparedFormatRuntimeStub(
                formatId = RuntimeFormatId.GGUF_LLAMA_CPP,
                runtimeName = "GGUF / llama.cpp",
                unavailableReason = "GGUF / llama.cpp native JNI backend is not bundled yet. Select a .litertlm model for offline execution."
            ),
            RuntimeFormatId.LITERT_TFLITE to PreparedFormatRuntimeStub(
                formatId = RuntimeFormatId.LITERT_TFLITE,
                runtimeName = "LiteRT / TFLite",
                unavailableReason = "Standalone .tflite LLM execution runtime is not bundled yet. Use a .litertlm bundle instead."
            ),
            RuntimeFormatId.MEDIAPIPE_TASK to PreparedFormatRuntimeStub(
                formatId = RuntimeFormatId.MEDIAPIPE_TASK,
                runtimeName = "MediaPipe LLM Task",
                unavailableReason = "MediaPipe .task LLM runtime is prepared as an adapter boundary; use .litertlm bundles for active LiteRT-LM execution."
            )
        )
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleMutex = Mutex()
    private val loadedModelSizes = mutableMapOf<RuntimeFormatId, Long>()

    private var activeFormat: RuntimeFormatId = RuntimeFormatId.LITERT_LM

    private val _loadState = MutableStateFlow<EngineLoadState>(EngineLoadState.Unloaded)
    override val loadState: StateFlow<EngineLoadState> = _loadState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastBenchmark = MutableStateFlow<GenerationBenchmark?>(null)
    override val lastBenchmark: StateFlow<GenerationBenchmark?> = _lastBenchmark.asStateFlow()

    private val _nativeMemoryMb = MutableStateFlow(readNativeHeapMb())
    override val nativeMemoryMb: StateFlow<Float> = _nativeMemoryMb.asStateFlow()

    init {
        // Mirror the default active runtime state streams seamlessly
        runtimes.forEach { (format, runtime) ->
            scope.launch {
                runtime.loadState.collect { st ->
                    if (format == activeFormat) {
                        _loadState.value = st
                    }
                }
            }
            scope.launch {
                runtime.isGenerating.collect { gen ->
                    if (format == activeFormat) {
                        _isGenerating.value = gen
                    }
                }
            }
            scope.launch {
                runtime.lastBenchmark.collect { bench ->
                    if (format == activeFormat && bench != null) {
                        _lastBenchmark.value = bench
                    }
                }
            }
            scope.launch {
                runtime.nativeMemoryMb.collect { mb ->
                    if (format == activeFormat) {
                        _nativeMemoryMb.value = mb
                    }
                }
            }
        }
    }

    fun getRuntime(formatId: RuntimeFormatId): ModelRuntimeEngine? = runtimes[formatId]

    fun activeCapabilities(settings: JarvisSettings? = null): ModelCapabilityProfile {
        val base = runtimes[activeFormat]?.capabilityProfile ?: ModelCapabilityProfile()
        if (settings == null) return base
        val ready = _loadState.value as? EngineLoadState.Ready
        val tier = when {
            ready?.backendUsed?.contains("GPU", ignoreCase = true) == true -> "GPU_ACCELERATED"
            ready?.backendUsed?.contains("NPU", ignoreCase = true) == true -> "NPU_ACCELERATED"
            ready != null -> "CPU_LOCAL"
            else -> base.estimatedLatencyTier
        }
        return base.copy(
            maxContextTokens = settings.contextLengthTokens.coerceAtLeast(512),
            estimatedLatencyTier = tier
        )
    }

    fun reportAllRuntimes(): List<RuntimePerformanceSnapshot> =
        runtimes.values.map { it.reportPerformance() }

    override suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
        lifecycleMutex.withLock {
            loadForRuntimeLocked(slot, settings, forceReload = false)
        }

    override suspend fun reloadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
        lifecycleMutex.withLock {
            loadForRuntimeLocked(slot, settings, forceReload = true)
        }

    private suspend fun loadForRuntimeLocked(
        slot: ModelSlot,
        settings: JarvisSettings,
        forceReload: Boolean
    ): Result<Unit> {
        val targetRuntime = runtimes[slot.runtimeFormat]
        if (targetRuntime == null) {
            val errMsg = "No local runtime adapter registered for ${slot.runtimeFormat.label}."
            _loadState.value = EngineLoadState.Error(errMsg)
            return Result.failure(UnsupportedOperationException(errMsg))
        }

        if (!targetRuntime.isExecutionSupported) {
            val res = targetRuntime.loadModel(slot, settings)
            activeFormat = slot.runtimeFormat
            _loadState.value = targetRuntime.loadState.value
            return res
        }

        // Prevent simultaneous heavy-model loads unless RAM headroom cleanly permits concurrency
        unloadConflictingRuntimesLocked(targetFormat = slot.runtimeFormat, incomingSlot = slot)

        activeFormat = slot.runtimeFormat
        _loadState.value = EngineLoadState.Loading(slot.displayName, settings.inferenceBackend.label)

        val result = if (forceReload && targetRuntime is LiteRtLmRuntimeAdapter) {
            targetRuntime.reloadModel(slot, settings)
        } else {
            targetRuntime.initialize()
            targetRuntime.loadModel(slot, settings)
        }

        if (result.isSuccess) {
            val fileSize = slot.localFilePath?.let { java.io.File(it).length() } ?: slot.expectedSizeBytes
            loadedModelSizes[slot.runtimeFormat] = fileSize
        } else {
            loadedModelSizes.remove(slot.runtimeFormat)
        }
        _loadState.value = targetRuntime.loadState.value
        _nativeMemoryMb.value = targetRuntime.nativeMemoryMb.value
        return result
    }

    private suspend fun unloadConflictingRuntimesLocked(
        targetFormat: RuntimeFormatId,
        incomingSlot: ModelSlot
    ) {
        val incomingBytes = incomingSlot.localFilePath?.let { java.io.File(it).length() }
            ?.takeIf { it > 0L } ?: incomingSlot.expectedSizeBytes
        val headroom = ThermalAndMemoryPolicy.assessMemoryHeadroom(appContext, incomingBytes)

        for ((otherFormat, otherRuntime) in runtimes) {
            if (otherFormat == targetFormat) continue
            val isOtherLoaded = otherRuntime.loadState.value is EngineLoadState.Ready
            if (!isOtherLoaded) continue

            val existingBytes = loadedModelSizes[otherFormat] ?: 0L
            val combinedNeededBytes = existingBytes + incomingBytes + CONCURRENT_SAFETY_RESERVE_BYTES
            val allowConcurrent = !headroom.isLowMemory &&
                headroom.availableRamBytes > 0L &&
                headroom.availableRamBytes >= combinedNeededBytes

            if (!allowConcurrent) {
                otherRuntime.unload()
                loadedModelSizes.remove(otherFormat)
            }
        }
    }

    override suspend fun unloadModel() {
        lifecycleMutex.withLock {
            runtimes.values.forEach { runtime ->
                if (runtime.loadState.value !is EngineLoadState.Unloaded) {
                    runtime.unload()
                }
            }
            loadedModelSizes.clear()
            _loadState.value = EngineLoadState.Unloaded
            refreshMemoryUsage()
        }
    }

    override suspend fun trimMemory(level: Int) {
        runtimes.values.forEach { it.trimMemory(level) }
        val activeRuntime = runtimes[activeFormat]
        if (activeRuntime != null) {
            _loadState.value = activeRuntime.loadState.value
            if (_loadState.value is EngineLoadState.Unloaded) {
                loadedModelSizes.remove(activeFormat)
            }
        }
        refreshMemoryUsage()
    }

    override fun streamReply(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent> {
        val runtime = runtimes[activeFormat]
            ?: return flow {
                emit(AiStreamEvent.Error("No active local runtime configured."))
            }
        return runtime.generateStream(conversationId, prompt, history, settings)
    }

    override suspend fun cancelGeneration() {
        runtimes[activeFormat]?.cancel()
    }

    override fun refreshMemoryUsage() {
        runtimes[activeFormat]?.let { rt ->
            _nativeMemoryMb.value = rt.nativeMemoryMb.value
        } ?: run {
            _nativeMemoryMb.value = readNativeHeapMb()
        }
    }

    override fun release() {
        runtimes.values.forEach { it.release() }
        loadedModelSizes.clear()
        scope.cancel()
    }

    private fun readNativeHeapMb(): Float {
        val bytes = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L)
        return bytes / (1024f * 1024f)
    }

    companion object {
        private const val CONCURRENT_SAFETY_RESERVE_BYTES = 2048L * 1024L * 1024L
    }
}
