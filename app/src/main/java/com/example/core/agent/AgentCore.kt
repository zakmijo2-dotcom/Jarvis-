package com.example.core.agent

import android.content.Context
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.BatteryThermalSnapshot
import com.example.core.ai.EngineLoadState
import com.example.core.ai.ModelRouter
import com.example.core.ai.RuntimeEngineCoordinator
import com.example.core.ai.ThermalAndMemoryPolicy
import com.example.core.cloud.CloudAiClient
import com.example.core.cloud.CloudInferenceConfig
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.CloudProviderRegistry
import com.example.core.cloud.CloudUsageStats
import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.device.DeviceCommandRouter
import com.example.core.memory.LocalMemory
import com.example.core.model.ArtifactState
import com.example.core.model.ModelManager
import com.example.core.model.RuntimeFormatId
import com.example.core.skills.SkillEngine
import com.example.core.tools.AccessibilityActionTool
import com.example.core.tools.AccessibilityController
import com.example.core.tools.AdjustMediaVolumeTool
import com.example.core.tools.AndroidTools
import com.example.core.tools.ComposeMessageTool
import com.example.core.tools.ControlMediaPlaybackTool
import com.example.core.tools.CreateReminderTool
import com.example.core.tools.ForceStopAppTool
import com.example.core.tools.GoHomeScreenTool
import com.example.core.tools.InitiatePhoneCallTool
import com.example.core.tools.InputUiTextTool
import com.example.core.tools.InspectScreenElementsTool
import com.example.core.tools.JarvisTool
import com.example.core.tools.LaunchInstalledAppTool
import com.example.core.tools.NotificationBridge
import com.example.core.tools.OpenAndroidSettingsTool
import com.example.core.tools.OpenAppScreenTool
import com.example.core.tools.PendingToolConfirmation
import com.example.core.tools.PrivilegedBridgeDiagnosticTool
import com.example.core.tools.ReadBatteryStatusTool
import com.example.core.tools.ReadDeviceInfoTool
import com.example.core.tools.ReadNotificationsTool
import com.example.core.tools.RetrieveMemoryTool
import com.example.core.tools.RunSkillTool
import com.example.core.tools.SaveMemoryTool
import com.example.core.tools.SaveSkillTool
import com.example.core.tools.ScrollScreenTool
import com.example.core.tools.SearchContactsTool
import com.example.core.tools.TapUiElementTool
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolRegistry
import com.example.core.tools.TrimAppCachesTool
import com.example.core.tools.UiObservationSnapshot
import com.example.core.wakeword.WakeWordEngine
import com.example.core.web.OkHttpWebResearchService
import com.example.core.web.WebResearchConfig
import com.example.core.web.WebResearchService
import com.example.data.local.db.AgentExecutionDao
import com.example.data.local.db.AgentExecutionEntity
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.ModelBenchmarkDao
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.flow.first
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
    fun createDefault(
        androidTools: AndroidTools,
        localMemoryProvider: () -> LocalMemory? = { null },
        skillEngineProvider: () -> SkillEngine? = { null },
        deviceCommandRouterProvider: () -> DeviceCommandRouter? = { null }
    ): ToolRegistry {
        var registryRef: ToolRegistry? = null
        val registry = ToolRegistry(
            tools = listOf(
                LaunchInstalledAppTool(),
                OpenAppScreenTool(),
                GoHomeScreenTool(routerProvider = deviceCommandRouterProvider),
                OpenAndroidSettingsTool(),
                ReadBatteryStatusTool(androidTools),
                ReadDeviceInfoTool(androidTools),
                AdjustMediaVolumeTool(),
                ControlMediaPlaybackTool(),
                PrivilegedBridgeDiagnosticTool(deviceCommandRouterProvider),
                ForceStopAppTool(deviceCommandRouterProvider),
                TrimAppCachesTool(deviceCommandRouterProvider),
                SearchContactsTool(),
                InitiatePhoneCallTool(),
                ComposeMessageTool(),
                ReadNotificationsTool(),
                AccessibilityActionTool(),
                InspectScreenElementsTool(),
                TapUiElementTool(),
                ScrollScreenTool(),
                InputUiTextTool(),
                CreateReminderTool(),
                RetrieveMemoryTool(localMemoryProvider),
                SaveMemoryTool(localMemoryProvider),
                SaveSkillTool(skillEngineProvider) { registryRef },
                RunSkillTool(skillEngineProvider) { registryRef }
            )
        )
        registryRef = registry
        return registry
    }
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
    private val toolRegistry: ToolRegistry = ToolRegistryFactory.createDefault(
        androidTools = androidTools,
        localMemoryProvider = { localMemory }
    ),
    private val cloudAiClient: CloudAiClient = OkHttpCloudAiClient(),
    private val webResearchService: WebResearchService = OkHttpWebResearchService(),
    private val intentRouter: IntentRouter = IntentRouter(),
    private val agentExecutionDao: AgentExecutionDao? = null,
    private val skillEngine: SkillEngine? = null,
    private val accessibilityController: AccessibilityController? = appContext?.let { AccessibilityController(it) },
    private val deviceCommandRouter: DeviceCommandRouter? = null,
    private val modelRouter: ModelRouter = ModelRouter(intentRouter = intentRouter),
    private val modelBenchmarkDao: ModelBenchmarkDao? = null
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
        val telemetry = runCatching { androidTools.readDeviceTelemetry() }.getOrNull()
        val isConnected = telemetry?.isNetworkConnected ?: true
        val matchedSkill = if (ToolRegistry.parseDirectToolCommand(prompt) == null) {
            runCatching { skillEngine?.findMatchingSkill(prompt) }.getOrNull()
        } else null

        val allSlots = runCatching { modelManager.observeModels().first() }.getOrDefault(listOfNotNull(activeModel))
        val batterySnap = appContext?.let { ThermalAndMemoryPolicy.readBatterySnapshot(it) } ?: BatteryThermalSnapshot()
        val recentBenchmarks = runCatching { modelBenchmarkDao?.observeBenchmarks()?.first() }.getOrNull().orEmpty()

        val routePlan = if (telemetry != null) {
            modelRouter.routeTask(
                prompt = prompt,
                historyChars = history.sumOf { it.content.length },
                settings = settings,
                models = allSlots,
                loadState = aiEngine.loadState.value,
                telemetry = telemetry,
                batterySnapshot = batterySnap,
                benchmarks = recentBenchmarks,
                matchedSkillName = matchedSkill?.name
            )
        } else {
            val activeCapabilities = (aiEngine as? RuntimeEngineCoordinator)?.activeCapabilities(settings)
            val fallbackDecision = intentRouter.routePrompt(
                prompt = prompt,
                settings = settings,
                isLocalModelReady = isLocalReady,
                isNetworkConnected = isConnected,
                activeCapabilities = activeCapabilities,
                matchedSkillName = matchedSkill?.name
            )
            com.example.core.ai.ModelRoutePlan(
                decision = fallbackDecision,
                preferredSlot = activeModel,
                recommendedContextTokens = settings.contextLengthTokens,
                memoryBudgetWarning = null,
                thermalBatteryNote = null,
                recommendations = emptyList()
            )
        }
        val decision = routePlan.decision
        val effectiveSettings = settings.copy(
            contextLengthTokens = routePlan.recommendedContextTokens.coerceIn(1024, 8192)
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
        routePlan.thermalBatteryNote?.let { note ->
            emit(HybridAgentEvent.Notice(label = "Thermal & Battery Adaptation", message = note))
        }

        val memorySnippets = mutableListOf<String>()
        val toolOutputs = mutableListOf<String>()
        val executedSafeRequests = mutableListOf<ToolCallRequest>()
        val seenModelToolCalls = mutableSetOf<String>()
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
                        if (step.type == PlanStepType.ANDROID_TOOL || step.type == PlanStepType.SKILL_EXECUTION || step.type == PlanStepType.CONTEXT_OBSERVE) {
                            toolOutputs.add(it)
                        }
                    }
                    continue
                }

                steps[i] = step.copy(status = PlanStepStatus.RUNNING)
                currentPlan = currentPlan.copy(steps = steps.toList())
                emit(HybridAgentEvent.PlanUpdated(currentPlan))

                when (step.type) {
                    PlanStepType.MEMORY_LOOKUP -> {
                        runCatching { localMemory.purgeExpiredMemories() }
                        val matches = localMemory.retrieveRelevantContext(prompt, maxItems = 4)
                        matches.forEach { memorySnippets.add("[${it.kind.label} / ${it.category}] ${it.content}") }
                        val summary = if (matches.isEmpty()) "No matching local memory items." else "Retrieved ${matches.size} ranked memory item(s)."
                        steps[i] = steps[i].copy(status = PlanStepStatus.COMPLETED, resultSummary = summary)
                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }

                    PlanStepType.CONTEXT_OBSERVE -> {
                        val telemetry = runCatching { androidTools.readDeviceTelemetry() }.getOrNull()
                        val observedParts = mutableListOf<String>()
                        telemetry?.let {
                            val availMb = it.availRamBytes / (1024L * 1024L)
                            val totalMb = it.totalRamBytes / (1024L * 1024L)
                            observedParts.add("Battery=${it.batteryPercent}% • RAM=${availMb}/${totalMb}MB • Thermal=${it.thermalStatusLabel} • Net=${if (it.isNetworkConnected) it.networkTransport else "Offline"}")
                        }
                        if (settings.notificationReaderEnabled) {
                            val notifCount = NotificationBridge.notifications.value.size
                            observedParts.add("CapturedNotifications=$notifCount")
                        }
                        var a11yNote = "UI tree not queried"
                        if (settings.accessibilityAutomationEnabled && accessibilityController?.isAuthorized() == true) {
                            val snap = accessibilityController.observeCurrentScreen(15)
                            a11yNote = "pkg=${snap.activePackageName ?: "none"}, nodes=${snap.elements.size}, hash=${snap.windowSignatureHash}"
                            observedParts.add(snap.toSanitizedPromptSummary(10))
                        }
                        val obsSummary = observedParts.joinToString("\n").ifBlank { "Minimal telemetry captured." }
                        toolOutputs.add("Context Observation: $obsSummary")
                        steps[i] = steps[i].copy(
                            status = PlanStepStatus.COMPLETED,
                            resultSummary = "Telemetry & context observed ($a11yNote)."
                        )
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
                            steps[i] = steps[i].copy(status = PlanStepStatus.RECOVERED, resultSummary = "Recovered offline after web failure: $err")
                            emit(HybridAgentEvent.Notice("Web Research // Recovered", "Web lookup failed ($err). Continuing with available offline knowledge without fabricating live facts."))
                        }
                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }

                    PlanStepType.SKILL_EXECUTION -> {
                        val skill = matchedSkill ?: decision.matchedSkillName?.let { skillEngine?.getSkillByName(it) }
                        val ctx = appContext
                        if (skill == null || skillEngine == null || ctx == null) {
                            steps[i] = steps[i].copy(status = PlanStepStatus.FAILED, resultSummary = "Saved skill could not be resolved.")
                            emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                            continue
                        }
                        val extractedParams = skillEngine.extractParameters(prompt, skill)
                        val report = skillEngine.executeSkill(
                            context = ctx,
                            skill = skill,
                            providedParams = extractedParams,
                            toolRegistry = toolRegistry,
                            settings = settings,
                            userConfirmed = confirmedStepIndex == i
                        )
                        val blockingResult = report.stepResults.firstOrNull {
                            it.status == ToolExecutionStatus.CONFIRMATION_REQUIRED ||
                                it.status == ToolExecutionStatus.PERMISSION_DENIED
                        }
                        if (blockingResult?.status == ToolExecutionStatus.CONFIRMATION_REQUIRED) {
                            val req = ToolCallRequest(blockingResult.toolName, blockingResult.requestArgs, "Skill:${skill.name}")
                            steps[i] = steps[i].copy(status = PlanStepStatus.WAITING_CONFIRMATION, resultSummary = blockingResult.summary)
                            emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                            emit(HybridAgentEvent.ConfirmationRequired(req, i))
                            overallStatus = "AWAITING_CONFIRMATION"
                            return@flow
                        }
                        if (blockingResult?.status == ToolExecutionStatus.PERMISSION_DENIED) {
                            val req = ToolCallRequest(blockingResult.toolName, blockingResult.requestArgs, "Skill:${skill.name}")
                            steps[i] = steps[i].copy(status = PlanStepStatus.WAITING_PERMISSION, resultSummary = blockingResult.summary)
                            emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                            emit(
                                HybridAgentEvent.PermissionRequired(
                                    request = req,
                                    missingRuntimePermission = blockingResult.missingRuntimePermission,
                                    missingSpecialAccess = blockingResult.missingSpecialAccess,
                                    rationale = blockingResult.summary
                                )
                            )
                            overallStatus = "PERMISSION_DENIED"
                            return@flow
                        }

                        toolOutputs.add(report.summary)
                        report.stepResults.forEach { r -> toolOutputs.add("${r.toolName}: ${r.summary}") }
                        steps[i] = steps[i].copy(
                            status = if (report.overallSuccess) PlanStepStatus.COMPLETED else PlanStepStatus.FAILED,
                            resultSummary = report.summary
                        )
                        emit(HybridAgentEvent.ToolSnapshotEmitted("Skill // ${skill.name} v${skill.version}", report.summary))
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

                        val preUiSnapshot: UiObservationSnapshot? =
                            if (toolRequest.toolName in UI_VERIFIED_TOOLS &&
                                settings.accessibilityAutomationEnabled &&
                                accessibilityController?.isAuthorized() == true
                            ) {
                                accessibilityController.observeCurrentScreen(20)
                            } else null

                        var toolRes = executeTool(toolRequest, settings, userConfirmed = confirmedStepIndex == i)
                        var recoveredByRetry = false

                        // Bounded 1x retry only for strictly read-only safe tools on transient failures
                        if (toolRes.status == ToolExecutionStatus.FAILED &&
                            toolRequest.toolName in SAFE_RETRYABLE_READ_TOOLS
                        ) {
                            val retryRes = executeTool(toolRequest, settings, userConfirmed = confirmedStepIndex == i)
                            if (retryRes.status == ToolExecutionStatus.SUCCESS) {
                                toolRes = retryRes
                                recoveredByRetry = true
                            }
                        }

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
                                var finalSummary = toolRes.summary
                                if (preUiSnapshot != null && accessibilityController != null) {
                                    val verification = accessibilityController.verifyExternalTransition(
                                        actionName = toolRes.toolName,
                                        dispatched = true,
                                        before = preUiSnapshot,
                                        maxElements = 20
                                    )
                                    finalSummary = "${toolRes.summary}\nUI Verification: ${verification.evidence}"
                                }
                                executedSafeRequests.add(toolRequest)
                                toolOutputs.add("${toolRes.toolName}: $finalSummary")
                                steps[i] = steps[i].copy(
                                    status = if (recoveredByRetry) PlanStepStatus.RECOVERED else PlanStepStatus.COMPLETED,
                                    resultSummary = finalSummary
                                )
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
                        var usedCloud = decision.useCloudModel
                        var synthesisFailed = false
                        var synthesisError = ""
                        var completedSuccessfully = false

                        for (loopIteration in 1..MAX_CLOSED_LOOP_TOOL_ITERATIONS) {
                            if (cancelPlanFlag.get()) {
                                overallStatus = "CANCELLED"
                                break
                            }

                            val augmentedPrompt = buildAugmentedPrompt(
                                rawPrompt = prompt,
                                memorySnippets = memorySnippets,
                                toolOutputs = toolOutputs,
                                webContextBlock = webContextBlock,
                                useCloud = usedCloud
                            )

                            var iterationText = ""
                            var iterationCompletedEvent: AiStreamEvent.Completed? = null
                            var iterationCancelledEvent: AiStreamEvent.Cancelled? = null
                            synthesisFailed = false
                            synthesisError = ""

                            val streamSource: Flow<AiStreamEvent> = if (usedCloud) {
                                val effectiveModel = decision.explicitModelOverride
                                    ?.takeIf { it != "cloud" && it != "local" }
                                    ?: settings.cloudModelId.ifBlank { settings.cloudProviderKind.defaultModel }
                                val toolsSchema = when (settings.cloudProviderKind) {
                                    CloudProviderKind.GEMINI_REST -> toolRegistry.buildGeminiToolsJsonString()
                                    CloudProviderKind.OPENAI_COMPATIBLE,
                                    CloudProviderKind.OPENROUTER,
                                    CloudProviderKind.CUSTOM_ENDPOINT -> toolRegistry.buildOpenAiToolsJsonString()
                                    CloudProviderKind.ANTHROPIC -> null
                                }
                                val cloudConfig = CloudInferenceConfig(
                                    providerKind = settings.cloudProviderKind,
                                    endpointUrl = settings.cloudEndpointUrl.ifBlank { settings.cloudProviderKind.defaultEndpoint },
                                    modelId = effectiveModel,
                                    apiKey = settings.cloudApiKey,
                                    timeoutSeconds = settings.cloudTimeoutSeconds,
                                    maxOutputTokens = settings.maxOutputTokens,
                                    temperature = settings.samplerTemperature,
                                    toolsSchemaJson = toolsSchema
                                )
                                val sysPrompt = buildString {
                                    appendLine("You are JARVIS, an accurate autonomous Android assistant. Never fabricate live data or tool results.")
                                    append(toolRegistry.buildSystemToolCatalogPrompt())
                                }
                                cloudAiClient.streamChat(cloudConfig, sysPrompt, augmentedPrompt, history)
                            } else {
                                val readyErr = ensureLocalModelLoaded(effectiveSettings)
                                if (readyErr != null) {
                                    steps[i] = steps[i].copy(status = PlanStepStatus.FAILED, resultSummary = readyErr)
                                    overallStatus = "LOCAL_UNLOADED"
                                    emit(HybridAgentEvent.Notice("Engine Unloaded", readyErr))
                                    break
                                }
                                aiEngine.streamReply(conversationId, augmentedPrompt, history, effectiveSettings)
                            }

                            streamSource.collect { ev ->
                                when (ev) {
                                    is AiStreamEvent.Started -> emit(HybridAgentEvent.AiStream(ev, usedCloud = usedCloud))
                                    is AiStreamEvent.Delta -> emit(HybridAgentEvent.AiStream(ev, usedCloud = usedCloud))
                                    is AiStreamEvent.Completed -> {
                                        iterationText = ev.fullText
                                        iterationCompletedEvent = ev
                                    }
                                    is AiStreamEvent.Cancelled -> {
                                        iterationText = ev.partialText
                                        iterationCancelledEvent = ev
                                        overallStatus = "CANCELLED"
                                    }
                                    is AiStreamEvent.Error -> {
                                        synthesisFailed = true
                                        synthesisError = ev.message
                                        if (usedCloud && settings.routingMode == AgentRoutingMode.AUTO_HYBRID && isLocalReady) {
                                            emit(
                                                HybridAgentEvent.Notice(
                                                    label = "Auto Hybrid // Local Fallback",
                                                    message = "Cloud provider unavailable (${ev.message}). Falling back to offline LiteRT-LM model."
                                                )
                                            )
                                        } else {
                                            emit(HybridAgentEvent.AiStream(ev, usedCloud = usedCloud))
                                        }
                                    }
                                }
                            }

                            // Automatic recovery onto local LiteRT-LM if AUTO_HYBRID cloud failed & local ready
                            if (synthesisFailed && usedCloud && settings.routingMode == AgentRoutingMode.AUTO_HYBRID && isLocalReady && !cancelPlanFlag.get()) {
                                usedCloud = false
                                synthesisFailed = false
                                val readyErr = ensureLocalModelLoaded(effectiveSettings)
                                if (readyErr == null) {
                                    runLocalModelStream(conversationId, augmentedPrompt, history, effectiveSettings) { ev ->
                                        when (ev) {
                                            is AiStreamEvent.Completed -> {
                                                iterationText = ev.fullText
                                                iterationCompletedEvent = ev
                                            }
                                            is AiStreamEvent.Cancelled -> {
                                                iterationText = ev.partialText
                                                iterationCancelledEvent = ev
                                                overallStatus = "CANCELLED"
                                            }
                                            is AiStreamEvent.Error -> {
                                                synthesisFailed = true
                                                synthesisError = ev.message
                                                emit(HybridAgentEvent.AiStream(ev, usedCloud = false))
                                            }
                                            else -> emit(HybridAgentEvent.AiStream(ev, usedCloud = false))
                                        }
                                    }
                                } else {
                                    synthesisFailed = true
                                    synthesisError = readyErr
                                }
                            }

                            val cancelledEv = iterationCancelledEvent
                            if (cancelledEv != null) {
                                finalAiText = cancelledEv.partialText
                                emit(HybridAgentEvent.AiStream(cancelledEv, usedCloud = usedCloud))
                                break
                            }

                            if (synthesisFailed) {
                                break
                            }

                            val completedEv = iterationCompletedEvent ?: break
                            val inlineToolCall = ToolRegistry.parseModelToolCall(iterationText)

                            if (inlineToolCall == null || loopIteration >= MAX_CLOSED_LOOP_TOOL_ITERATIONS) {
                                if (inlineToolCall != null && loopIteration >= MAX_CLOSED_LOOP_TOOL_ITERATIONS) {
                                    emit(
                                        HybridAgentEvent.Notice(
                                            label = "Agent Loop Guard",
                                            message = "Reached max tool verification depth ($MAX_CLOSED_LOOP_TOOL_ITERATIONS); concluding with verified facts gathered so far."
                                        )
                                    )
                                }
                                finalAiText = iterationText
                                emit(HybridAgentEvent.AiStream(completedEv, usedCloud = usedCloud))
                                completedSuccessfully = true
                                break
                            }

                            val callSignature = "${inlineToolCall.toolName}:${inlineToolCall.arguments}"
                            if (!seenModelToolCalls.add(callSignature)) {
                                emit(
                                    HybridAgentEvent.Notice(
                                        label = "Agent Loop Guard",
                                        message = "Prevented duplicate tool call '${inlineToolCall.toolName}'."
                                    )
                                )
                                finalAiText = iterationText
                                emit(HybridAgentEvent.AiStream(completedEv, usedCloud = usedCloud))
                                completedSuccessfully = true
                                break
                            }

                            val loopToolResult = executeTool(
                                request = inlineToolCall,
                                settings = settings,
                                userConfirmed = confirmedStepIndex == i
                            )

                            when (loopToolResult.status) {
                                ToolExecutionStatus.CONFIRMATION_REQUIRED -> {
                                    steps[i] = steps[i].copy(
                                        status = PlanStepStatus.WAITING_CONFIRMATION,
                                        resultSummary = loopToolResult.summary
                                    )
                                    emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                                    emit(HybridAgentEvent.ConfirmationRequired(inlineToolCall, i))
                                    overallStatus = "AWAITING_CONFIRMATION"
                                    return@flow
                                }

                                ToolExecutionStatus.PERMISSION_DENIED -> {
                                    steps[i] = steps[i].copy(
                                        status = PlanStepStatus.WAITING_PERMISSION,
                                        resultSummary = loopToolResult.summary
                                    )
                                    emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                                    emit(
                                        HybridAgentEvent.PermissionRequired(
                                            request = inlineToolCall,
                                            missingRuntimePermission = loopToolResult.missingRuntimePermission,
                                            missingSpecialAccess = loopToolResult.missingSpecialAccess,
                                            rationale = loopToolResult.summary
                                        )
                                    )
                                    emit(
                                        HybridAgentEvent.ToolSnapshotEmitted(
                                            label = "Permission Validator // ${loopToolResult.toolName}",
                                            content = formatToolResult(loopToolResult)
                                        )
                                    )
                                    overallStatus = "PERMISSION_DENIED"
                                    return@flow
                                }

                                else -> {
                                    if (loopToolResult.status == ToolExecutionStatus.SUCCESS) {
                                        executedSafeRequests.add(inlineToolCall)
                                    }
                                    emit(
                                        HybridAgentEvent.ToolSnapshotEmitted(
                                            label = "Closed-Loop Tool // ${loopToolResult.toolName} (${loopToolResult.status.name})",
                                            content = formatToolResult(loopToolResult)
                                        )
                                    )
                                    toolOutputs.add(
                                        "[TOOL_RESULT tool=${loopToolResult.toolName} status=${loopToolResult.status.name}] ${loopToolResult.summary}"
                                    )
                                }
                            }
                        }

                        if (overallStatus != "LOCAL_UNLOADED") {
                            steps[i] = steps[i].copy(
                                status = when {
                                    overallStatus == "CANCELLED" -> PlanStepStatus.CANCELLED
                                    synthesisFailed -> PlanStepStatus.FAILED
                                    !usedCloud && decision.useCloudModel && completedSuccessfully -> PlanStepStatus.RECOVERED
                                    else -> PlanStepStatus.COMPLETED
                                },
                                resultSummary = when {
                                    synthesisFailed -> synthesisError
                                    !usedCloud && decision.useCloudModel -> "Recovered via offline LiteRT-LM after Cloud failure."
                                    usedCloud -> "Completed via ${settings.cloudProviderKind.label}."
                                    else -> "Completed offline via LiteRT-LM."
                                }
                            )
                        }
                        emit(HybridAgentEvent.PlanUpdated(currentPlan.copy(steps = steps.toList())))
                    }

                    PlanStepType.RESULT_VERIFY -> {
                        val failedCount = steps.take(i).count { it.status == PlanStepStatus.FAILED }
                        val recoveredCount = steps.take(i).count { it.status == PlanStepStatus.RECOVERED }
                        var distilledNote = ""

                        if (failedCount == 0 &&
                            executedSafeRequests.size >= 2 &&
                            decision.matchedSkillName == null &&
                            skillEngine != null
                        ) {
                            val candidateName = "workflow_" + executedSafeRequests
                                .map { it.toolName.take(10) }
                                .distinct()
                                .joinToString("_")
                                .take(38)
                            val triggerHint = executedSafeRequests.joinToString(", ") {
                                it.toolName.replace('_', ' ')
                            }
                            val distilled = skillEngine.distillWorkflowIfSafe(
                                name = candidateName,
                                description = "Auto-distilled verified workflow for: ${prompt.take(90)}",
                                triggerPattern = triggerHint,
                                executedRequests = executedSafeRequests,
                                toolRegistry = toolRegistry
                            )
                            if (distilled.isSuccess) {
                                val saved = distilled.getOrThrow()
                                distilledNote = " • Auto-saved safe skill '${saved.name}' v${saved.version}"
                                emit(
                                    HybridAgentEvent.Notice(
                                        label = "Skills Engine",
                                        message = "Distilled reusable safe workflow '${saved.name}' v${saved.version}."
                                    )
                                )
                            }
                        }

                        val verifyStatus = when {
                            failedCount > 0 -> PlanStepStatus.FAILED
                            recoveredCount > 0 -> PlanStepStatus.RECOVERED
                            else -> PlanStepStatus.COMPLETED
                        }
                        val verifySummary = when {
                            failedCount > 0 -> "Verification flagged $failedCount failed step(s)."
                            recoveredCount > 0 -> "Verified completion with $recoveredCount recovered step(s)$distilledNote."
                            else -> "All executed steps verified cleanly$distilledNote."
                        }
                        steps[i] = steps[i].copy(status = verifyStatus, resultSummary = verifySummary)
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
            return "\"${activeModel.displayName}\" uses ${activeModel.runtimeFormat.label}, which is unsupported in this APK build (no native runtime bundled). Select a LiteRT-LM (.litertlm) model in Models."
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
        val skillsCount = runCatching { skillEngine?.listSkillsOnce()?.size ?: 0 }.getOrDefault(0)
        val a11yStatus = if (accessibilityController?.isAuthorized() == true) "Authorized" else "Inactive"
        val caps = deviceCommandRouter?.capabilityManager?.capabilities?.value
        val summary = buildString {
            appendLine("JARVIS SYSTEM READINESS")
            appendLine("• Local AI Engine: $engineStatus")
            appendLine("• Active Model Slot: ${activeModel?.displayName ?: "None"} (${activeModel?.artifactState?.label ?: "Unset"})")
            appendLine("• Native Heap Allocated: ${String.format(Locale.US, "%.1f", aiEngine.nativeMemoryMb.value)} MB")
            appendLine("• Privileged Bridge: ${caps?.privilegedBridgeSummary ?: "Native Android APIs"}")
            appendLine("• Shizuku Status: ${caps?.shizuku?.availability?.label ?: "Unavailable"}")
            appendLine("• Wireless ADB Status: ${caps?.wirelessAdb?.connectionState?.label ?: "Unconfigured"}")
            appendLine("• Wake Word Interface: ${wakeWordEngine.state.value.label} (\"${wakeWordEngine.keyword}\", verified=${wakeWordEngine.isPhraseDetectionVerified})")
            appendLine("• Android Tool Registry: ${toolRegistry.listTools().size} verified tools online")
            appendLine("• Cloud Providers: ${providers.joinToString { it.name }} (reqs=${usage.totalRequests})")
            appendLine("• Local Memory Store: Room v7 Persistence Active (WAL) • Skills=$skillsCount")
            appendLine("• Accessibility Stack: Limited UI Fallback ($a11yStatus)")
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

    companion object {
        private const val MAX_CLOSED_LOOP_TOOL_ITERATIONS = 3
        private val UI_VERIFIED_TOOLS = setOf("tap_ui_element", "scroll_screen", "input_ui_text")
        private val SAFE_RETRYABLE_READ_TOOLS = setOf(
            "read_battery_status",
            "read_device_info",
            "search_contacts",
            "retrieve_memory"
        )
    }
}

