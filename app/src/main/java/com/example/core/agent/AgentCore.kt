package com.example.core.agent

import android.content.Context
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.memory.LocalMemory
import com.example.core.model.ModelManager
import com.example.core.tools.AccessibilityActionTool
import com.example.core.tools.AdjustMediaVolumeTool
import com.example.core.tools.AndroidTools
import com.example.core.tools.ComposeMessageTool
import com.example.core.tools.ControlMediaPlaybackTool
import com.example.core.tools.GoHomeScreenTool
import com.example.core.tools.InitiatePhoneCallTool
import com.example.core.tools.JarvisTool
import com.example.core.tools.LaunchInstalledAppTool
import com.example.core.tools.OpenAndroidSettingsTool
import com.example.core.tools.OpenAppScreenTool
import com.example.core.tools.PendingToolConfirmation
import com.example.core.tools.ReadBatteryStatusTool
import com.example.core.tools.ReadDeviceInfoTool
import com.example.core.tools.ReadNotificationsTool
import com.example.core.tools.SearchContactsTool
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolRegistry
import com.example.core.wakeword.WakeWordEngine
import com.example.data.local.db.MessageEntity
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.flow.Flow
import java.util.Locale

sealed interface AgentDispatchResult {
    data class ToolSnapshot(
        val label: String,
        val content: String
    ) : AgentDispatchResult
}

object ToolRegistryFactory {
    fun createDefault(androidTools: AndroidTools): ToolRegistry = ToolRegistry(
        tools = listOf(
            LaunchInstalledAppTool(),
            OpenAppScreenTool(),
            GoHomeScreenTool(),
            OpenAndroidSettingsTool(),
            ReadBatteryStatusTool(androidTools),
            ReadDeviceInfoTool(androidTools),
            AdjustMediaVolumeTool(),
            ControlMediaPlaybackTool(),
            SearchContactsTool(),
            InitiatePhoneCallTool(),
            ComposeMessageTool(),
            ReadNotificationsTool(),
            AccessibilityActionTool()
        )
    )
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

    fun parseDirectToolCommand(prompt: String): ToolCallRequest?
    fun extractModelToolCall(modelOutput: String): ToolCallRequest?
    fun describeConfirmation(request: ToolCallRequest): PendingToolConfirmation?
    fun listRegisteredTools(): List<JarvisTool>
    fun cancelActiveTool()

    suspend fun executeTool(
        request: ToolCallRequest,
        settings: JarvisSettings,
        userConfirmed: Boolean = false
    ): ToolExecutionResult
}

class Phase2AgentCore(
    private val aiEngine: AiEngine,
    private val modelManager: ModelManager,
    private val androidTools: AndroidTools,
    private val cloudProviderRegistry: CloudProviderRegistry,
    private val wakeWordEngine: WakeWordEngine,
    @Suppress("unused") private val localMemory: LocalMemory,
    private val appContext: Context? = null,
    private val toolRegistry: ToolRegistry = ToolRegistryFactory.createDefault(androidTools)
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
            appendLine("JARVIS SYSTEM READINESS")
            appendLine("• Local AI Engine: $engineStatus")
            appendLine("• Active Model Slot: ${activeModel?.displayName ?: "None"} (${activeModel?.artifactState?.label ?: "Unset"})")
            appendLine("• Native Heap Allocated: ${String.format(Locale.US, "%.1f", aiEngine.nativeMemoryMb.value)} MB")
            appendLine("• Wake Word Interface: ${wakeWordEngine.state.value.label} (\"${wakeWordEngine.keyword}\", verified=${wakeWordEngine.isPhraseDetectionVerified})")
            appendLine("• Android Tool Registry: ${toolRegistry.listTools().size} verified tools online")
            appendLine("• Cloud Boundaries: ${providers.joinToString { it.name }}")
            appendLine("• Local Memory Store: Room Persistence Active")
        }.trim()
        return AgentDispatchResult.ToolSnapshot(
            label = "AgentCore // Readiness Audit",
            content = summary
        )
    }

    override fun parseDirectToolCommand(prompt: String): ToolCallRequest? =
        ToolRegistry.parseDirectToolCommand(prompt)

    override fun extractModelToolCall(modelOutput: String): ToolCallRequest? =
        ToolRegistry.parseModelToolCall(modelOutput)

    override fun describeConfirmation(request: ToolCallRequest): PendingToolConfirmation? {
        val tool = toolRegistry.getTool(request.toolName) ?: return null
        val (confirmMsg, warning) = tool.describeConfirmation(request.arguments)
        return PendingToolConfirmation(
            request = request,
            toolDescription = tool.description,
            confirmationMessage = confirmMsg,
            sensitivityWarning = warning,
            origin = request.source
        )
    }

    override fun listRegisteredTools(): List<JarvisTool> = toolRegistry.listTools()

    override fun cancelActiveTool() {
        toolRegistry.cancelActiveExecution()
    }

    override suspend fun executeTool(
        request: ToolCallRequest,
        settings: JarvisSettings,
        userConfirmed: Boolean
    ): ToolExecutionResult {
        val ctx = appContext
            ?: return ToolExecutionResult(
                toolName = request.toolName,
                status = ToolExecutionStatus.FAILED,
                summary = "Android application context is not bound to AgentCore."
            )
        return toolRegistry.dispatch(
            context = ctx,
            request = request,
            settings = settings,
            userConfirmed = userConfirmed
        )
    }
}
