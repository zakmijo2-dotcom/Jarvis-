package com.example.core.agent

import com.example.core.ai.AiStreamEvent
import com.example.core.tools.SpecialAccessType
import com.example.core.tools.ToolCallRequest

enum class AgentRoutingMode(val label: String) {
    AUTO_HYBRID("Auto Hybrid"),
    LOCAL_ONLY("Local Only"),
    CLOUD_ONLY("Cloud Only");

    companion object {
        fun fromName(raw: String?): AgentRoutingMode {
            if (raw.isNullOrBlank()) return AUTO_HYBRID
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO_HYBRID
        }
    }
}

enum class ExecutionRoute(val label: String, val isExternalCloud: Boolean) {
    LOCAL_LITERT_LM("Local (LiteRT-LM)", false),
    CLOUD_API("Cloud API", true),
    ON_DEVICE_TOOLS("On-Device Tools", false),
    HYBRID_WEB_AND_AI("Web + AI Synthesis", true)
}

/**
 * Explicit runtime execution phase of the autonomous agent.
 * Driven strictly by real agent state transitions — never fake timers.
 */
enum class AgentExecutionPhase(val label: String, val isTerminal: Boolean = false) {
    IDLE("Idle", false),
    THINKING("Thinking", false),
    OBSERVING("Observing", false),
    EXECUTING("Executing", false),
    WAITING_PERMISSION("Waiting for permission", false),
    WAITING_CONFIRMATION("Waiting for approval", false),
    VERIFYING("Verifying", false),
    PAUSED("Paused", false),
    RECOVERING("Recovering", false),
    COMPLETED("Completed", true),
    FAILED("Failed", true),
    CANCELLED("Cancelled", true)
}

data class AgentTimelineEntry(
    val id: String,
    val timestampMs: Long,
    val phase: AgentExecutionPhase,
    val title: String,
    val detail: String,
    val toolName: String? = null,
    val succeeded: Boolean? = null
)

data class ObservationPreviewSnapshot(
    val packageName: String?,
    val screenTypeLabel: String,
    val elementCount: Int,
    val clickableCount: Int,
    val isLoading: Boolean,
    val hasDialog: Boolean,
    val summary: String,
    val screenshotBase64Preview: String? = null,
    val capturedAtMs: Long = System.currentTimeMillis()
)

data class DynamicReplanRecord(
    val failedStepTitle: String,
    val failureReason: String,
    val recoveryStrategy: String,
    val addedStepsCount: Int
)

data class StopSummary(
    val planId: String,
    val goal: String,
    val reason: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val lastObservedPackage: String?,
    val canResumeFromCheckpoint: Boolean
)

data class RoutingDecision(
    val route: ExecutionRoute,
    val reason: String,
    val complexityScore: Int,
    val needsWebResearch: Boolean,
    val detectedTools: List<ToolCallRequest>,
    val explicitModelOverride: String? = null,
    val useCloudModel: Boolean = false,
    val privacyBlockedReason: String? = null,
    val capabilitySummary: String = "",
    val matchedSkillName: String? = null,
    val recommendedModelTier: String = "BALANCED"
)

enum class PlanStepType(val label: String) {
    MEMORY_LOOKUP("Local Memory"),
    CONTEXT_OBSERVE("Observe Context"),
    WEB_RESEARCH("Web Research"),
    SKILL_EXECUTION("Skill Workflow"),
    ANDROID_TOOL("Device Tool"),
    AI_SYNTHESIS("AI Synthesis"),
    RESULT_VERIFY("Verify Outcome")
}

enum class PlanStepStatus(val label: String) {
    PENDING("Pending"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    WAITING_CONFIRMATION("Awaiting Confirmation"),
    WAITING_PERMISSION("Permission Required"),
    RECOVERED("Recovered"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class AgentPlanStep(
    val index: Int,
    val type: PlanStepType,
    val title: String,
    val detail: String,
    val status: PlanStepStatus = PlanStepStatus.PENDING,
    val resultSummary: String? = null,
    val verificationNote: String? = null
)

data class AgentPlanState(
    val planId: String,
    val goal: String,
    val routeDecision: RoutingDecision,
    val steps: List<AgentPlanStep>,
    val isCancelled: Boolean = false,
    val isPaused: Boolean = false,
    val currentPhase: AgentExecutionPhase = AgentExecutionPhase.THINKING,
    val replanCount: Int = 0
)

data class AgentExecutionRecord(
    val id: String,
    val conversationId: String,
    val userPrompt: String,
    val routeTaken: String,
    val routeReason: String,
    val status: String,
    val stepsSummary: String,
    val resultSummary: String,
    val durationMs: Long,
    val timestamp: Long
)

sealed interface HybridAgentEvent {
    data class PhaseChanged(
        val phase: AgentExecutionPhase,
        val detail: String
    ) : HybridAgentEvent

    data class TimelineRecorded(
        val entry: AgentTimelineEntry
    ) : HybridAgentEvent

    data class ObservationCaptured(
        val preview: ObservationPreviewSnapshot
    ) : HybridAgentEvent

    data class ReplanTriggered(
        val record: DynamicReplanRecord
    ) : HybridAgentEvent

    data class TaskStopped(
        val summary: StopSummary
    ) : HybridAgentEvent

    data class PlanUpdated(val plan: AgentPlanState) : HybridAgentEvent

    data class ToolSnapshotEmitted(
        val label: String,
        val content: String
    ) : HybridAgentEvent

    data class ConfirmationRequired(
        val request: ToolCallRequest,
        val stepIndex: Int
    ) : HybridAgentEvent

    data class PermissionRequired(
        val request: ToolCallRequest,
        val missingRuntimePermission: String?,
        val missingSpecialAccess: SpecialAccessType?,
        val rationale: String
    ) : HybridAgentEvent

    data class AiStream(
        val event: AiStreamEvent,
        val usedCloud: Boolean
    ) : HybridAgentEvent

    data class Notice(
        val label: String,
        val message: String
    ) : HybridAgentEvent
}
