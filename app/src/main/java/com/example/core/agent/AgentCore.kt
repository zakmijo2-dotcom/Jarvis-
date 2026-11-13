package com.example.core.agent

import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.memory.LocalMemory
import com.example.core.model.ModelManager
import com.example.core.tools.AndroidTools
import com.example.core.wakeword.WakeWordEngine
import com.example.data.local.db.MessageEntity
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.flow.Flow

sealed interface AgentDispatchResult {
    data class ToolSnapshot(
        val label: String,
        val content: String
    ) : AgentDispatchResult
}

interface AgentCore {
    fun streamPrompt(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent>

    suspend fun runSystemTelemetrySnapshot(): AgentDispatchResult.ToolSnapshot
    suspend fun inspectEngineReadiness(): AgentDispatchResult.ToolSnapshot
}

class Phase2AgentCore(
    private val aiEngine: AiEngine,
    private val modelManager: ModelManager,
    private val androidTools: AndroidTools,
    private val cloudProviderRegistry: CloudProviderRegistry,
    private val wakeWordEngine: WakeWordEngine,
    @Suppress("unused") private val localMemory: LocalMemory
) : AgentCore {

    override fun streamPrompt(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings
    ): Flow<AiStreamEvent> = aiEngine.streamReply(
        conversationId = conversationId,
        prompt = prompt,
        history = history,
        settings = settings
    )

    override suspend fun runSystemTelemetrySnapshot(): AgentDispatchResult.ToolSnapshot {
        val telemetry = androidTools.readDeviceTelemetry()
        return AgentDispatchResult.ToolSnapshot(
            label = "AndroidTools // System Snapshot",
            content = androidTools.formatTelemetrySummary(telemetry)
        )
    }

    override suspend fun inspectEngineReadiness(): AgentDispatchResult.ToolSnapshot {
        val activeModel = modelManager.getActiveModelOnce()
        val providers = cloudProviderRegistry.listProviders()
        val engineStatus = when (val state = aiEngine.loadState.value) {
            is EngineLoadState.Unloaded -> "Unloaded"
            is EngineLoadState.Loading -> "Loading ${state.modelName} (${state.targetBackend})"
            is EngineLoadState.Ready -> "Loaded: ${state.modelName} on ${state.backendUsed} (${state.loadTimeMs} ms)"
            is EngineLoadState.Error -> "Error: ${state.message}"
        }
        val summary = buildString {
            appendLine("JARVIS PHASE 2 ENGINE READINESS")
            appendLine("• Local AI Engine: $engineStatus")
            appendLine("• Active Model Slot: ${activeModel?.displayName ?: "None"} (${activeModel?.artifactState?.label ?: "Unset"})")
            appendLine("• Native Heap Allocated: ${String.format("%.1f", aiEngine.nativeMemoryMb.value)} MB")
            appendLine("• Wake Word Boundary: ${wakeWordEngine.state.value.name} (\"${wakeWordEngine.keyword}\")")
            appendLine("• Cloud Boundaries: ${providers.joinToString { it.name }} (Phase 3)")
            appendLine("• Local Memory Store: Room Persistence Active")
        }.trim()
        return AgentDispatchResult.ToolSnapshot(
            label = "AgentCore // Readiness Audit",
            content = summary
        )
    }
}
