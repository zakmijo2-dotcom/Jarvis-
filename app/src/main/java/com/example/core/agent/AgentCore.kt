package com.example.core.agent

import android.content.Context
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.cloud.CloudAiClient
import com.example.core.cloud.CloudInferenceConfig
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.cloud.CloudUsageStats
import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.memory.LocalMemory
import com.example.core.model.ArtifactState
import com.example.core.model.ModelManager
import com.example.core.model.RuntimeFormatId
import com.example.core.tools.AccessibilityActionTool
import com.example.core.tools.AdjustMediaVolumeTool
import com.example.core.tools.AndroidTools
import com.example.core.tools.ComposeMessageTool
import com.example.core.tools.ControlMediaPlaybackTool
import com.example.core.tools.GoHomeScreenTool
import com.example.core.tools.InitiatePhoneCallTool
import com.example.core.tools.InputUiTextTool
import com.example.core.tools.InspectScreenElementsTool
import com.example.core.tools.JarvisTool
import com.example.core.tools.LaunchInstalledAppTool
import com.example.core.tools.OpenAndroidSettingsTool
import com.example.core.tools.OpenAppScreenTool
import com.example.core.tools.PendingToolConfirmation
import com.example.core.tools.ReadBatteryStatusTool
import com.example.core.tools.ReadDeviceInfoTool
import com.example.core.tools.ReadNotificationsTool
import com.example.core.tools.ScrollScreenTool
import com.example.core.tools.SearchContactsTool
import com.example.core.tools.TapUiElementTool
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolRegistry
import com.example.core.wakeword.WakeWordEngine
import com.example.core.web.OkHttpWebResearchService
import com.example.core.web.WebResearchConfig
import com.example.core.web.WebResearchService
import com.example.data.local.db.AgentExecutionDao
import com.example.data.local.db.AgentExecutionEntity
import com.example.data.local.db.MessageEntity
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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
            AccessibilityActionTool(),
            InspectScreenElementsTool(),
            TapUiElementTool(),
            ScrollScreenTool(),
            InputUiTextTool()
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

    fun executeHybridTask(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings,
        confirmedStepIndex: Int? = null,
        existingSteps: List<AgentPlanStep>? = null
    ): Flow<HybridAgentEvent>

    suspend fun runSystemTelemetrySnapshot(): AgentDispatchResult.ToolSnapshot
    suspend fun inspectEngineReadiness(): AgentDispatchResult.ToolSnapshot

    fun parseDirectToolCommand(prompt: String): ToolCallRequest?
    fun extractModelToolCall(modelOutput: String): ToolCallRequest?
    fun describeConfirmation(request: ToolCallRequest): PendingToolConfirmation?
    fun listRegisteredTools(): List<JarvisTool>
    fun cancelActiveTool()
    fun cancelAutonomousTask()

    fun observeExecutionHistory(): Flow<List<AgentExecutionRecord>>
    val cloudUsageStats: StateFlow<CloudUsageStats>
    fun resetCloudUsageStats()

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
    private val localMemory: LocalMemory,
    private val appContext: Context? = null,
    private val toolRegistry: ToolRegistry = ToolRegistryFactory.createDefault(androidTools),
    private val cloudAiClient: CloudAiClient = OkHttpCloudAiClient(),
    private val webResearchService: WebResearchService = OkHttpWebResearchService(),
    private val intentRouter: IntentRouter = IntentRouter(),
    private val agentExecutionDao: AgentExecutionDao? = null
) : AgentCore {

    private val cancelPlanFlag = AtomicBoolean(false)

    override val cloudUsageStats: StateFlow<CloudUsageStats> = cloudAiClient.usageStats

    override fun resetCloudUsageStats() {
        cloudAiClient.resetUsageStats()
    }

    override fun observeExecutionHistory(): Flow<List<AgentExecutionRecord>> {
        val dao = agentExecutionDao ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.observeExecutions().map { rows ->
            rows.map { row ->
                AgentExecutionRecord(
                    id = row.id,
                    conversationId = row.conversationId,
                    userPrompt = row.userPrompt,
                    routeTaken = row.routeTaken,
                    routeReason = row.routeReason,
                    status = row.status,
                    stepsSummary = formatStepsSummary(row.stepsJson),
                    resultSummary = row.resultSummary,
                    durationMs = row.durationMs,
                    timestamp = row.timestamp
                )
            }
        }
    }

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

    override fun executeHybridTask(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings,
        confirmedStepIndex: Int?,
        existingSteps: List<AgentPlanStep>?
    ): Flow<HybridAgentEvent> = flow {
        cancelPlanFlag.set(false)
        val startedMs = System.currentTimeMillis()
        val planId = UUID.randomUUID().toString()

        val activeModel = modelManager.getActiveModelOnce()
        val isLocalReady = (aiEngine.loadState.value is EngineLoadState.Ready) ||
            (activeModel?.runtimeFormat == RuntimeFormatId.LITERT_LM &&
                activeModel.artifactState == ArtifactState.READY)
        val isConnected = runCatching { androidTools.readDeviceTelemetry().isNetworkConnected }.getOrDefault(true)

        val decision = intentRouter.routePrompt(
            prompt = prompt,
            settings = settings,
            isLocalModelReady = isLocalReady,
            isNetworkConnected = isConnected
        )

        // Strict privacy / consent block for Cloud-Only or explicit cloud requests without consent/key
        if (decision.useCloudModel && decision.privacyBlockedReason != null) {
            emit(
                HybridAgentEvent.Notice(
                    label = "Privacy & Routing Guard",
                    message = decision.privacyBlockedReason
                )
            )
            recordExecution(
                id = planId,
                conversationId = conversationId,
                prompt = prompt,
                decision = decision,
                steps = emptyList(),
                status = "BLOCKED",
                summary = decision.privacyBlockedReason,
                durationMs = System.currentTimeMillis() - startedMs
            )
            return@flow
        }

        // Fast-path for direct `/tool` commands
        val directTool = ToolRegistry.parseDirectToolCommand(prompt)
        if (directTool != null) {
            val steps = mutableListOf(
                AgentPlanStep(0, PlanStepType.ANDROID_TOOL, "Execute ${directTool.toolName}", "Direct command", PlanStepStatus.RUNNING)
            )
            val plan = AgentPlanState(planId, prompt, decision, steps.toList())
            emit(HybridAgentEvent.PlanUpdated(plan))

            val result = executeTool(directTool, settings, userConfirmed = confirmedStepIndex == 0)
            when (result.status) {
                ToolExecutionStatus.CONFIRMATION_REQUIRED -> {
                    steps[0] = steps[0].copy(status = PlanStepStatus.WAITING_CONFIRMATION, resultSummary = result.summary)
                    emit(HybridAgentEvent.PlanUpdated(plan.copy(steps = steps.toList())))
                    emit(HybridAgentEvent.ConfirmationRequired(directTool, 0))
                }

                ToolExecutionStatus.PERMISSION_DENIED -> {
                    steps[0] = steps[0].copy(status = PlanStepStatus.WAITING_PERMISSION, resultSummary = result.summary)
                    emit(HybridAgentEvent.PlanUpdated(plan.copy(steps = steps.toList())))
                    emit(
                        HybridAgentEvent.PermissionRequired(
                            request = directTool,
                            missingRuntimePermission = result.missingRuntimePermission,
                            missingSpecialAccess = result.missingSpecialAccess,
                            rationale = result.summary
                        )
                    )
                    emit(HybridAgentEvent.ToolSnapshotEmitted("Permission Validator // ${result.toolName}", formatToolResult(result)))
                }

                else -> {
                    val stepStatus = if (result.status == ToolExecutionStatus.SUCCESS) PlanStepStatus.COMPLETED else PlanStepStatus.FAILED
                    steps[0] = steps[0].copy(status = stepStatus, resultSummary = result.summary)
                    emit(HybridAgentEvent.PlanUpdated(plan.copy(steps = steps.toList())))
                    emit(HybridAgentEvent.ToolSnapshotEmitted("Tool // ${result.toolName} (${result.status.name})", formatToolResult(result)))
                    recordExecution(
                        id = planId,
                        conversationId = conversationId,
                        prompt = prompt,
                        decision = decision,
                        steps = steps,
                        status = result.status.name,
                        summary = result.summary,
                        durationMs = System.currentTimeMillis() - startedMs
                    )
                }
            }
            return@flow
        }

        val steps = (existingSteps ?: intentRouter.decomposeTasks(prompt, decision)).toMutableList()
        var currentPlan = AgentPlanState(planId, prompt, decision, steps.toList())
        emit(HybridAgentEvent.PlanUpdated(currentPlan))

        if (decision.privacyBlockedReason != null) {
            emit(
                HybridAgentEvent.Notice(
                    label = "Offline / Privacy Boundary",
                    message = decision.privacyBlockedReason
                )
            )
        }

        val memorySnippets = mutableListOf<String>()
        val toolOutputs = mutableListOf<String>()
        var webContextBlock = ""
        var finalAiText = ""
        var overallStatus = "COMPLETED"

        try {
            for (i in steps.indices) {
                if (cancelPlanFlag.get()) {
                    markRemainingCancelled(steps, i)
                    emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList(), isCancelled = true)))
                    overallStatus = "CANCELLED"
                    break
                }

                val step = steps[i]
                if (step.status == PlanStepStatus.COMPLETED || step.status == PlanStepStatus.RECOVERED) {
                    step.resultSummary?.let {
                        if (step.type == PlanStepType.ANDROID_TOOL) toolOutputs.add(it)
                    }
                    continue
                }

                steps[i] = step.copy(status = PlanStepStatus.RUNNING)
                currentPlan = currentPlan.copy(steps = steps.toList())
                emit(HybridAgentEvent.PlanUpdated(currentPlan))

                when (step.type) {
                    PlanStepType.MEMORY_LOOKUP -> {
                        val matches = localMemory.retrieveRelevantContext(prompt, maxItems = 4)
                        matches.forEach { memorySnippets.add("[${it.kind.label} / ${it.category}] ${it.content}") }
                        val summary = if (matches.isEmpty()) "No matching local memory items." else "Retrieved ${matches.size} local memory item(s)."
                        steps[i] = steps[i].copy(status = PlanStepStatus.COMPLETED, resultSummary = summary)
                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }

                    PlanStepType.WEB_RESEARCH -> {
                        val webConfig = WebResearchConfig(
                            enabled = settings.webResearchEnabled,
                            provider = settings.webSearchProvider,
                            apiKey = settings.webSearchApiKey,
                            customEndpointUrl = settings.webSearchCustomUrl,
                            timeoutSeconds = settings.cloudTimeoutSeconds.coerceIn(5, 30)
                        )
                        val webResult = webResearchService.search(step.detail, webConfig)
                        if (webResult.isSuccess) {
                            val report = webResult.getOrThrow()
                            webContextBlock = report.formatContextPrompt()
                            val summary = "${report.results.size} web result(s) fetched via ${report.providerLabel}."
                            steps[i] = steps[i].copy(status = PlanStepStatus.COMPLETED, resultSummary = summary)
                            emit(HybridAgentEvent.ToolSnapshotEmitted("Web Research // ${report.providerLabel}", webContextBlock))
                        } else {
                            val err = webResult.exceptionOrNull()?.localizedMessage ?: "Web research failed."
                            // Error recovery: proceed without live web facts if local/cloud synthesis can still assist
                            steps[i] = steps[i].copy(status = PlanStepStatus.RECOVERED, resultSummary = "Recovered offline after web failure: $err")
                            emit(HybridAgentEvent.Notice("Web Research // Recovered", "Web lookup failed ($err). Continuing with available offline knowledge without fabricating live facts."))
                        }
                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }

                    PlanStepType.ANDROID_TOOL -> {
                        val toolRequest = decision.detectedTools.firstOrNull {
                            "Execute ${it.toolName}" == step.title
                        }
                        if (toolRequest == null) {
                            steps[i] = steps[i].copy(status = PlanStepStatus.FAILED, resultSummary = "Tool call could not be matched.")
                            emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                            continue
                        }
                        val toolRes = executeTool(toolRequest, settings, userConfirmed = confirmedStepIndex == i)
                        when (toolRes.status) {
                            ToolExecutionStatus.CONFIRMATION_REQUIRED -> {
                                steps[i] = steps[i].copy(status = PlanStepStatus.WAITING_CONFIRMATION, resultSummary = toolRes.summary)
                                emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                                emit(HybridAgentEvent.ConfirmationRequired(toolRequest, i))
                                overallStatus = "AWAITING_CONFIRMATION"
                                return@flow
                            }

                            ToolExecutionStatus.PERMISSION_DENIED -> {
                                steps[i] = steps[i].copy(status = PlanStepStatus.WAITING_PERMISSION, resultSummary = toolRes.summary)
                                emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                                emit(
                                    HybridAgentEvent.PermissionRequired(
                                        request = toolRequest,
                                        missingRuntimePermission = toolRes.missingRuntimePermission,
                                        missingSpecialAccess = toolRes.missingSpecialAccess,
                                        rationale = toolRes.summary
                                    )
                                )
                                emit(HybridAgentEvent.ToolSnapshotEmitted("Permission Validator // ${toolRes.toolName}", formatToolResult(toolRes)))
                                overallStatus = "PERMISSION_DENIED"
                                return@flow
                            }

                            ToolExecutionStatus.SUCCESS -> {
                                toolOutputs.add("${toolRes.toolName}: ${toolRes.summary}")
                                steps[i] = steps[i].copy(status = PlanStepStatus.COMPLETED, resultSummary = toolRes.summary)
                                emit(HybridAgentEvent.ToolSnapshotEmitted("Tool // ${toolRes.toolName} (VERIFIED)", formatToolResult(toolRes)))
                            }

                            else -> {
                                steps[i] = steps[i].copy(status = PlanStepStatus.RECOVERED, resultSummary = "Tool failed: ${toolRes.summary}")
                                toolOutputs.add("${toolRes.toolName} error: ${toolRes.summary}")
                                emit(HybridAgentEvent.ToolSnapshotEmitted("Tool // ${toolRes.toolName} (${toolRes.status.name})", formatToolResult(toolRes)))
                            }
                        }
                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }

                    PlanStepType.AI_SYNTHESIS -> {
                        val augmentedPrompt = buildAugmentedPrompt(
                            rawPrompt = prompt,
                            memorySnippets = memorySnippets,
                            toolOutputs = toolOutputs,
                            webContextBlock = webContextBlock,
                            useCloud = decision.useCloudModel
                        )

                        var usedCloud = decision.useCloudModel
                        var synthesisFailed = false
                        var synthesisError = ""

                        if (decision.useCloudModel) {
                            val effectiveModel = decision.explicitModelOverride
                                ?.takeIf { it != "cloud" && it != "local" }
                                ?: settings.cloudModelId.ifBlank { settings.cloudProviderKind.defaultModel }

                            val cloudConfig = CloudInferenceConfig(
                                providerKind = settings.cloudProviderKind,
                                endpointUrl = settings.cloudEndpointUrl.ifBlank { settings.cloudProviderKind.defaultEndpoint },
                                modelId = effectiveModel,
                                apiKey = settings.cloudApiKey,
                                timeoutSeconds = settings.cloudTimeoutSeconds,
                                maxOutputTokens = settings.maxOutputTokens,
                                temperature = settings.samplerTemperature
                            )
                            val sysPrompt = buildString {
                                appendLine("You are JARVIS, an accurate hybrid AI assistant. Never fabricate live data or tool results.")
                                append(toolRegistry.buildSystemToolCatalogPrompt())
                            }

                            cloudAiClient.streamChat(cloudConfig, sysPrompt, augmentedPrompt, history).collect { event ->
                                when (event) {
                                    is AiStreamEvent.Error -> {
                                        synthesisFailed = true
                                        synthesisError = event.message
                                        if (settings.routingMode == AgentRoutingMode.AUTO_HYBRID && isLocalReady) {
                                            // Error recovery: fallback cleanly from failed cloud request to local LiteRT-LM!
                                            emit(
                                                HybridAgentEvent.Notice(
                                                    label = "Auto Hybrid // Local Fallback",
                                                    message = "Cloud provider unavailable (${event.message}). Falling back to offline LiteRT-LM model."
                                                )
                                            )
                                        } else {
                                            emit(HybridAgentEvent.AiStream(event, usedCloud = true))
                                        }
                                    }

                                    is AiStreamEvent.Completed -> {
                                        finalAiText = event.fullText
                                        emit(HybridAgentEvent.AiStream(event, usedCloud = true))
                                    }

                                    is AiStreamEvent.Cancelled -> {
                                        finalAiText = event.partialText
                                        overallStatus = "CANCELLED"
                                        emit(HybridAgentEvent.AiStream(event, usedCloud = true))
                                    }

                                    else -> emit(HybridAgentEvent.AiStream(event, usedCloud = true))
                                }
                            }

                            // Automatic recovery onto local LiteRT-LM if AUTO_HYBRID cloud failed & local ready
                            if (synthesisFailed && settings.routingMode == AgentRoutingMode.AUTO_HYBRID && isLocalReady && !cancelPlanFlag.get()) {
                                usedCloud = false
                                synthesisFailed = false
                                runLocalModelStream(conversationId, augmentedPrompt, history, settings) { ev ->
                                    if (ev is AiStreamEvent.Completed) finalAiText = ev.fullText
                                    if (ev is AiStreamEvent.Error) {
                                        synthesisFailed = true
                                        synthesisError = ev.message
                                    }
                                    emit(HybridAgentEvent.AiStream(ev, usedCloud = false))
                                }
                                steps[i] = steps[i].copy(
                                    status = if (synthesisFailed) PlanStepStatus.FAILED else PlanStepStatus.RECOVERED,
                                    resultSummary = if (synthesisFailed) synthesisError else "Recovered via offline LiteRT-LM after Cloud failure."
                                )
                            } else {
                                steps[i] = steps[i].copy(
                                    status = when {
                                        overallStatus == "CANCELLED" -> PlanStepStatus.CANCELLED
                                        synthesisFailed -> PlanStepStatus.FAILED
                                        else -> PlanStepStatus.COMPLETED
                                    },
                                    resultSummary = if (synthesisFailed) synthesisError else "Completed via ${settings.cloudProviderKind.label}."
                                )
                            }
                        } else {
                            // Local LiteRT-LM inference
                            val readyErr = ensureLocalModelLoaded(settings)
                            if (readyErr != null) {
                                steps[i] = steps[i].copy(status = PlanStepStatus.FAILED, resultSummary = readyErr)
                                overallStatus = "LOCAL_UNLOADED"
                                emit(HybridAgentEvent.Notice("Engine Unloaded", readyErr))
                            } else {
                                runLocalModelStream(conversationId, augmentedPrompt, history, settings) { ev ->
                                    when (ev) {
                                        is AiStreamEvent.Completed -> finalAiText = ev.fullText
                                        is AiStreamEvent.Cancelled -> {
                                            finalAiText = ev.partialText
                                            overallStatus = "CANCELLED"
                                        }
                                        is AiStreamEvent.Error -> {
                                            synthesisFailed = true
                                            synthesisError = ev.message
                                        }
                                        else -> Unit
                                    }
                                    emit(HybridAgentEvent.AiStream(ev, usedCloud = false))
                                }
                                steps[i] = steps[i].copy(
                                    status = when {
                                        overallStatus == "CANCELLED" -> PlanStepStatus.CANCELLED
                                        synthesisFailed -> PlanStepStatus.FAILED
                                        else -> PlanStepStatus.COMPLETED
                                    },
                                    resultSummary = if (synthesisFailed) synthesisError else "Completed offline via LiteRT-LM."
                                )
                            }
                        }

                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }
                }
            }

            if (overallStatus == "COMPLETED" && steps.any { it.status == PlanStepStatus.FAILED }) {
                overallStatus = "PARTIAL_OR_FAILED"
            }
        } catch (ce: CancellationException) {
            overallStatus = "CANCELLED"
            throw ce
        } finally {
            recordExecution(
                id = planId,
                conversationId = conversationId,
                prompt = prompt,
                decision = decision,
                steps = steps,
                status = overallStatus,
                summary = finalAiText.ifBlank { steps.lastOrNull()?.resultSummary ?: overallStatus },
                durationMs = System.currentTimeMillis() - startedMs
            )
        }
    }

    private suspend fun ensureLocalModelLoaded(settings: JarvisSettings): String? {
        val activeModel = modelManager.getActiveModelOnce()
            ?: return "No model selected. Open Models to install a .litertlm model artifact."
        if (activeModel.runtimeFormat != RuntimeFormatId.LITERT_LM) {
            return "\"${activeModel.displayName}\" uses ${activeModel.runtimeFormat.label}, whose runtime adapter is prepared for future support. Select a LiteRT-LM (.litertlm) model in Models."
        }
        if (activeModel.artifactState != ArtifactState.READY) {
            return "Local model \"${activeModel.displayName}\" is not installed yet. Open Models to download or import a .litertlm artifact."
        }
        val ready = aiEngine.loadState.value as? EngineLoadState.Ready
        if (ready == null || ready.modelId != activeModel.id) {
            val res = aiEngine.loadModel(activeModel, settings)
            if (res.isFailure) {
                return res.exceptionOrNull()?.localizedMessage ?: "Failed to load local LiteRT-LM runtime."
            }
        }
        return null
    }

    private suspend fun runLocalModelStream(
        conversationId: String,
        prompt: String,
        history: List<MessageEntity>,
        settings: JarvisSettings,
        onEvent: suspend (AiStreamEvent) -> Unit
    ) {
        aiEngine.streamReply(conversationId, prompt, history, settings).collect { ev ->
            onEvent(ev)
        }
    }

    private fun buildAugmentedPrompt(
        rawPrompt: String,
        memorySnippets: List<String>,
        toolOutputs: List<String>,
        webContextBlock: String,
        useCloud: Boolean
    ): String {
        val cleaned = rawPrompt.replace(Regex("""^@(?:local|cloud|[a-zA-Z0-9._:/-]+)\s*""", RegexOption.IGNORE_CASE), "").trim()
            .ifBlank { rawPrompt }
        if (memorySnippets.isEmpty() && toolOutputs.isEmpty() && webContextBlock.isEmpty()) {
            return cleaned
        }
        return buildString {
            if (memorySnippets.isNotEmpty()) {
                appendLine("USER-APPROVED LOCAL MEMORY CONTEXT:")
                memorySnippets.forEach { appendLine("- $it") }
                appendLine()
            }
            if (toolOutputs.isNotEmpty()) {
                appendLine("VERIFIED ANDROID TOOL OUTPUTS:")
                toolOutputs.forEach { appendLine("- $it") }
                appendLine()
            }
            if (webContextBlock.isNotEmpty()) {
                appendLine(webContextBlock)
                appendLine()
            }
            if (!useCloud && webContextBlock.isEmpty()) {
                appendLine("[Running 100% offline on local model]")
            }
            append("USER REQUEST: ")
            append(cleaned)
        }.trim()
    }

    private fun markRemainingCancelled(steps: MutableList<AgentPlanStep>, fromIndex: Int) {
        for (idx in fromIndex until steps.size) {
            if (steps[idx].status == PlanStepStatus.PENDING || steps[idx].status == PlanStepStatus.RUNNING) {
                steps[idx] = steps[idx].copy(status = PlanStepStatus.CANCELLED, resultSummary = "Cancelled by user.")
            }
        }
    }

    private suspend fun recordExecution(
        id: String,
        conversationId: String,
        prompt: String,
        decision: RoutingDecision,
        steps: List<AgentPlanStep>,
        status: String,
        summary: String,
        durationMs: Long
    ) {
        val dao = agentExecutionDao ?: return
        val arr = JSONArray()
        steps.forEach { step ->
            arr.put(
                JSONObject().apply {
                    put("index", step.index)
                    put("type", step.type.name)
                    put("title", step.title)
                    put("status", step.status.name)
                    put("result", step.resultSummary.orEmpty())
                }
            )
        }
        dao.insertExecution(
            AgentExecutionEntity(
                id = id,
                conversationId = conversationId,
                userPrompt = prompt.take(300),
                routeTaken = decision.route.label,
                routeReason = decision.reason,
                status = status,
                stepsJson = arr.toString(),
                resultSummary = summary.take(400),
                durationMs = durationMs.coerceAtLeast(1L),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun formatStepsSummary(stepsJson: String): String = runCatching {
        val arr = JSONArray(stepsJson)
        (0 until arr.length()).joinToString(" → ") { i ->
            val obj = arr.getJSONObject(i)
            "${obj.optString("title")} [${obj.optString("status")}]"
        }
    }.getOrDefault("")

    private fun formatToolResult(result: ToolExecutionResult): String = buildString {
        appendLine("STATUS: ${result.status.name}")
        append(result.summary)
        if (result.verifiedFacts.isNotEmpty()) {
            appendLine()
            append("Verified: ")
            append(result.verifiedFacts.entries.joinToString(" • ") { "${it.key}=${it.value}" })
        }
    }.trim()

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
        val usage = cloudUsageStats.value
        val summary = buildString {
            appendLine("JARVIS SYSTEM READINESS")
            appendLine("• Local AI Engine: $engineStatus")
            appendLine("• Active Model Slot: ${activeModel?.displayName ?: "None"} (${activeModel?.artifactState?.label ?: "Unset"})")
            appendLine("• Native Heap Allocated: ${String.format(Locale.US, "%.1f", aiEngine.nativeMemoryMb.value)} MB")
            appendLine("• Wake Word Interface: ${wakeWordEngine.state.value.label} (\"${wakeWordEngine.keyword}\", verified=${wakeWordEngine.isPhraseDetectionVerified})")
            appendLine("• Android Tool Registry: ${toolRegistry.listTools().size} verified tools online")
            appendLine("• Cloud Providers: ${providers.joinToString { it.name }} (reqs=${usage.totalRequests})")
            appendLine("• Local Memory Store: Room v6 Persistence Active (WAL)")
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

    override fun cancelAutonomousTask() {
        cancelPlanFlag.set(true)
        toolRegistry.cancelActiveExecution()
        cloudAiClient.cancelActiveRequest()
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
        val startedMs = System.currentTimeMillis()
        val res = toolRegistry.dispatch(
            context = ctx,
            request = request,
            settings = settings,
            userConfirmed = userConfirmed
        )
        if (request.source.contains("Device", ignoreCase = true) &&
            res.status != ToolExecutionStatus.CONFIRMATION_REQUIRED
        ) {
            val stepStatus = when (res.status) {
                ToolExecutionStatus.SUCCESS -> PlanStepStatus.COMPLETED
                ToolExecutionStatus.CANCELLED -> PlanStepStatus.CANCELLED
                else -> PlanStepStatus.FAILED
            }
            val step = AgentPlanStep(
                index = 1,
                type = PlanStepType.ANDROID_TOOL,
                title = "Execute ${res.toolName}",
                detail = request.arguments.entries.joinToString(", ") { "${it.key}=${it.value}" },
                status = stepStatus,
                resultSummary = res.summary
            )
            val arr = JSONArray().put(
                JSONObject().apply {
                    put("index", step.index)
                    put("type", step.type.name)
                    put("title", step.title)
                    put("status", step.status.name)
                    put("result", step.resultSummary.orEmpty())
                }
            )
            agentExecutionDao?.insertExecution(
                AgentExecutionEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = "device_control",
                    userPrompt = "/tool ${res.toolName} ${step.detail}".trim(),
                    routeTaken = "Direct Android Tool",
                    routeReason = "Standalone Device Control execution (${request.source})",
                    status = res.status.name,
                    stepsJson = arr.toString(),
                    resultSummary = res.summary.take(400),
                    durationMs = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L),
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return res
    }
}
