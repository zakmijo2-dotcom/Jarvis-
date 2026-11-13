package com.example.core.ai

import com.example.core.model.ModelSlot
import com.example.data.local.db.MessageEntity
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface EngineLoadState {
    data object Unloaded : EngineLoadState
    data class Loading(val modelName: String, val targetBackend: String) : EngineLoadState
    data class Ready(
        val modelId: String,
        val modelName: String,
        val backendUsed: String,
        val loadTimeMs: Long
    ) : EngineLoadState

    data class Error(val message: String, val isCorruptedFile: Boolean = false) : EngineLoadState
}

data class GenerationBenchmark(
    val ttftMs: Long,
    val tokensPerSecond: Float,
    val outputTokens: Int,
    val prefillTokens: Int,
    val backendUsed: String,
    val nativeHeapMb: Float
)

sealed interface AiStreamEvent {
    data class Started(val backendUsed: String) : AiStreamEvent
    data class Delta(
        val accumulatedText: String,
        val deltaText: String,
        val estimatedTokens: Int
    ) : AiStreamEvent

    data class Completed(
        val fullText: String,
        val benchmark: GenerationBenchmark
    ) : AiStreamEvent

    data class Cancelled(
        val partialText: String,
        val benchmark: GenerationBenchmark?
    ) : AiStreamEvent

    data class Error(val message: String) : AiStreamEvent
}

interface AiEngine {
    val loadState: StateFlow<EngineLoadState>
    val isGenerating: StateFlow<Boolean>
    val lastBenchmark: StateFlow<GenerationBenchmark?>
    val nativeMemoryMb: StateFlow<Float>

    suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit>
    suspend fun unloadModel()
    suspend fun reloadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit>

    fun streamReply(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent>

    suspend fun cancelGeneration()
    fun refreshMemoryUsage()
    fun release()
}
