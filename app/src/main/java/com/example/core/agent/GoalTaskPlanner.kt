package com.example.core.agent

import com.example.core.tools.ToolCallRequest
import java.util.Locale

data class PlannedGoalBreakdown(
    val isMultiStepAppGoal: Boolean,
    val targetAppQuery: String?,
    val searchQuery: String?,
    val wantsSummary: Boolean,
    val wantsMemorySave: Boolean,
    val wantsBrowserWorkflow: Boolean,
    val targetUrl: String?,
    val steps: List<AgentPlanStep>,
    val plannedToolRequests: Map<Int, ToolCallRequest> = emptyMap()
)

data class ReplanOutcome(
    val shouldReplan: Boolean,
    val strategyDescription: String,
    val recoveryStep: AgentPlanStep? = null,
    val recoveryToolRequest: ToolCallRequest? = null,
    val modelGuidanceNote: String
)

/**
 * Goal-oriented multi-step task planner and dynamic replanner.
 *
 * Understands composite goals (e.g., "Find a football video on YouTube, summarize it,
 * and save the summary") and structures them into verifiable Observation -> Reasoning ->
 * Action -> Verification pipelines rather than isolated one-shot commands.
 */
class GoalTaskPlanner(
    private val intentRouter: IntentRouter = IntentRouter()
) {

    fun buildGoalPlan(
        prompt: String,
        decision: RoutingDecision
    ): PlannedGoalBreakdown {
        val clean = prompt.trim()
        val lower = clean.lowercase(Locale.ROOT)

        val targetUrl = extractUrl(clean)
        val wantsBrowser = targetUrl != null ||
            lower.contains("open website") ||
            lower.contains("browse ") ||
            lower.contains("fill form") ||
            lower.contains("click link")

        val appWorkflow = if (targetUrl != null) null else detectMultiStepAppGoal(clean, lower)
        val wantsSummary = lower.contains("summarize") ||
            lower.contains("summary") ||
            lower.contains("لخص") ||
            lower.contains("ملخص") ||
            lower.contains("résume")
        val wantsMemorySave = lower.contains("save the summary") ||
            lower.contains("save summary") ||
            lower.contains("save it") ||
            lower.contains("remember this") ||
            lower.contains("save to memory") ||
            lower.contains("احفظ الملخص") ||
            lower.contains("احفظه")

        if (appWorkflow != null && decision.matchedSkillName.isNullOrBlank()) {
            val steps = mutableListOf<AgentPlanStep>()
            val toolMap = linkedMapOf<Int, ToolCallRequest>()
            var idx = 0

            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.MEMORY_LOOKUP,
                    title = "Query Memory & App Knowledge",
                    detail = "Load user preferences and learned navigation hints for ${appWorkflow.first}"
                )
            )

            val launchReq = ToolCallRequest(
                toolName = "launch_installed_app",
                arguments = mapOf("app_query" to appWorkflow.first),
                source = "GoalTaskPlanner"
            )
            toolMap[idx] = launchReq
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.ANDROID_TOOL,
                    title = "Execute launch_installed_app",
                    detail = "app_query=${appWorkflow.first}"
                )
            )

            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.CONTEXT_OBSERVE,
                    title = "Observe ${appWorkflow.first} Screen",
                    detail = "Inspect UI hierarchy, OCR, dialogs, and loading state after launch"
                )
            )

            val engineLabel = if (decision.useCloudModel) "Cloud AI" else "Local LiteRT-LM"
            val objectiveDetail = buildString {
                append("Navigate ${appWorkflow.first}")
                if (!appWorkflow.second.isNullOrBlank()) {
                    append(", search '${appWorkflow.second}', select relevant result, observe screen")
                }
                if (wantsSummary) append(", extract & summarize findings")
                if (wantsMemorySave) append(", persist verified summary via save_memory")
            }
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.AI_SYNTHESIS,
                    title = "Execute Goal Loop ($engineLabel)",
                    detail = objectiveDetail
                )
            )

            steps.add(
                AgentPlanStep(
                    index = idx,
                    type = PlanStepType.RESULT_VERIFY,
                    title = "Verify Goal Completion",
                    detail = "Confirm verified actions, extracted content, and saved artifacts before reporting success"
                )
            )

            return PlannedGoalBreakdown(
                isMultiStepAppGoal = true,
                targetAppQuery = appWorkflow.first,
                searchQuery = appWorkflow.second,
                wantsSummary = wantsSummary,
                wantsMemorySave = wantsMemorySave,
                wantsBrowserWorkflow = false,
                targetUrl = null,
                steps = steps,
                plannedToolRequests = toolMap
            )
        }

        if (wantsBrowser && targetUrl != null && decision.matchedSkillName.isNullOrBlank()) {
            val steps = mutableListOf<AgentPlanStep>()
            val toolMap = linkedMapOf<Int, ToolCallRequest>()
            var idx = 0

            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.MEMORY_LOOKUP,
                    title = "Query Local Memory",
                    detail = "Load relevant context and preferences"
                )
            )
            val openReq = ToolCallRequest(
                toolName = "browser_open",
                arguments = mapOf("url" to targetUrl),
                source = "GoalTaskPlanner"
            )
            toolMap[idx] = openReq
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.ANDROID_TOOL,
                    title = "Execute browser_open",
                    detail = "url=$targetUrl"
                )
            )
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.AI_SYNTHESIS,
                    title = "Analyze Web Page & Complete Goal",
                    detail = "Read page links/forms, navigate if needed, and synthesize verified results"
                )
            )
            steps.add(
                AgentPlanStep(
                    index = idx,
                    type = PlanStepType.RESULT_VERIFY,
                    title = "Verify Browser Outcome",
                    detail = "Verify extracted web facts and completed actions"
                )
            )
            return PlannedGoalBreakdown(
                isMultiStepAppGoal = false,
                targetAppQuery = null,
                searchQuery = null,
                wantsSummary = wantsSummary,
                wantsMemorySave = wantsMemorySave,
                wantsBrowserWorkflow = true,
                targetUrl = targetUrl,
                steps = steps,
                plannedToolRequests = toolMap
            )
        }

        val defaultSteps = intentRouter.decomposeTasks(clean, decision)
        val defaultMap = linkedMapOf<Int, ToolCallRequest>()
        for (step in defaultSteps) {
            if (step.type == PlanStepType.ANDROID_TOOL) {
                val match = decision.detectedTools.firstOrNull { "Execute ${it.toolName}" == step.title }
                if (match != null) {
                    defaultMap[step.index] = match
                }
            }
        }
        return PlannedGoalBreakdown(
            isMultiStepAppGoal = false,
            targetAppQuery = null,
            searchQuery = null,
            wantsSummary = wantsSummary,
            wantsMemorySave = wantsMemorySave,
            wantsBrowserWorkflow = wantsBrowser,
            targetUrl = targetUrl,
            steps = defaultSteps,
            plannedToolRequests = defaultMap
        )
    }

    /**
     * Produces a bounded dynamic replan strategy when a tool or UI interaction fails or stalls.
     */
    fun planRecoveryAfterFailure(
        failedToolName: String,
        failureReason: String,
        activePackage: String?,
        attemptCount: Int
    ): ReplanOutcome {
        if (attemptCount >= MAX_DYNAMIC_REPLANS) {
            return ReplanOutcome(
                shouldReplan = false,
                strategyDescription = "Max dynamic replan attempts ($MAX_DYNAMIC_REPLANS) reached; escalate clearly to user.",
                modelGuidanceNote = "[REPLAN_EXHAUSTED] '$failedToolName' failed ($failureReason). Stop retrying blindly and report the exact blocker honestly."
            )
        }

        val lowerReason = failureReason.lowercase(Locale.ROOT)
        return when {
            failedToolName == "tap_ui_element" &&
                (lowerReason.contains("not found") || lowerReason.contains("no element") || lowerReason.contains("no matching")) -> {
                ReplanOutcome(
                    shouldReplan = true,
                    strategyDescription = "Element not found in Accessibility tree -> scroll/re-observe screen with OCR fallback.",
                    recoveryToolRequest = ToolCallRequest(
                        toolName = "capture_screen_ocr",
                        arguments = emptyMap(),
                        source = "DynamicReplanner"
                    ),
                    modelGuidanceNote = "[DYNAMIC_REPLAN] tap_ui_element failed ($failureReason). Use capture_screen_ocr + tap_coordinates or scroll_screen to locate the target visually."
                )
            }

            failedToolName == "launch_installed_app" -> {
                ReplanOutcome(
                    shouldReplan = true,
                    strategyDescription = "App launch failed -> check web/browser alternative or inspect current screen.",
                    modelGuidanceNote = "[DYNAMIC_REPLAN] launch_installed_app failed ($failureReason). Verify app name or use browser_open / web_search if the app is not installed."
                )
            }

            lowerReason.contains("loading") || lowerReason.contains("timeout") || lowerReason.contains("busy") -> {
                ReplanOutcome(
                    shouldReplan = true,
                    strategyDescription = "Screen still loading or timed out -> wait for UI stabilization.",
                    recoveryToolRequest = ToolCallRequest(
                        toolName = "wait_for_screen",
                        arguments = mapOf("timeout_ms" to "4000", "condition" to "not_loading"),
                        source = "DynamicReplanner"
                    ),
                    modelGuidanceNote = "[DYNAMIC_REPLAN] Previous action hit a loading/timeout state. Waited for stabilization; re-observe before acting."
                )
            }

            failedToolName in setOf("tap_coordinates", "swipe_screen", "scroll_screen", "input_ui_text") -> {
                ReplanOutcome(
                    shouldReplan = true,
                    strategyDescription = "UI gesture failed on ${activePackage ?: "current screen"} -> re-inspect visible elements and check for blocking dialogs.",
                    recoveryToolRequest = ToolCallRequest(
                        toolName = "inspect_screen_elements",
                        arguments = mapOf("max_elements" to "25"),
                        source = "DynamicReplanner"
                    ),
                    modelGuidanceNote = "[DYNAMIC_REPLAN] Gesture '$failedToolName' failed ($failureReason). Check the refreshed screen tree for dialogs, permission prompts, or shifted element coordinates."
                )
            }

            else -> {
                ReplanOutcome(
                    shouldReplan = true,
                    strategyDescription = "Tool '$failedToolName' failed -> choose an alternative tool or gracefully explain limitations.",
                    modelGuidanceNote = "[DYNAMIC_REPLAN] Tool '$failedToolName' failed ($failureReason). Adapt your plan using an alternative verified tool or report accurately without fabricating success."
                )
            }
        }
    }

    private fun detectMultiStepAppGoal(cleanPrompt: String, lower: String): Pair<String, String?>? {
        val knownApps = listOf(
            "youtube" to "YouTube",
            "يوتيوب" to "YouTube",
            "spotify" to "Spotify",
            "whatsapp" to "WhatsApp",
            "واتساب" to "WhatsApp",
            "telegram" to "Telegram",
            "gmail" to "Gmail",
            "maps" to "Maps",
            "google maps" to "Maps",
            "chrome" to "Chrome",
            "instagram" to "Instagram",
            "settings" to "Settings",
            "calendar" to "Calendar",
            "clock" to "Clock",
            "notes" to "Notes",
            "play store" to "Play Store"
        )

        val matchedApp = knownApps.firstOrNull { (token, _) ->
            Regex("""(?:^|\W)${Regex.escape(token)}(?:$|\W)""").containsMatchIn(lower)
        } ?: return null

        val actionVerbs = listOf(
            "find", "search", "open", "play", "watch", "look up", "check", "summarize",
            "send", "navigate", "abhat", "ابحث", "افتح", "شغل", "لخص", "cherche", "ouvre"
        )
        val hasActionVerb = actionVerbs.any { lower.contains(it) }
        if (!hasActionVerb) return null

        // Only treat as multi-step app workflow if user mentions app + a goal (not pure conceptual Q&A)
        val isConceptualQuestion = (lower.startsWith("what is ") || lower.startsWith("who founded ") || lower.startsWith("explain how ")) &&
            !lower.contains("open ") && !lower.contains("find ") && !lower.contains("search ")
        if (isConceptualQuestion) return null

        val query = extractSearchSubject(cleanPrompt, matchedApp.first)
        return matchedApp.second to query
    }

    private fun extractSearchSubject(prompt: String, appToken: String): String? {
        val patterns = listOf(
            Regex("""(?:find|search(?:\s+for)?|look\s+for|watch|play)\s+(?:a\s+|an\s+|the\s+)?(.+?)\s+(?:on|in|using)\s+${Regex.escape(appToken)}""", RegexOption.IGNORE_CASE),
            Regex("""(?:on|in)\s+${Regex.escape(appToken)}[\s,]+(?:find|search(?:\s+for)?|watch|play)\s+(?:a\s+|an\s+|the\s+)?(.+?)(?:,|and\s+summarize|and\s+save|$)""", RegexOption.IGNORE_CASE),
            Regex("""ابحث\s+عن\s+(.+?)\s+(?:في|على)\s+${Regex.escape(appToken)}""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val found = p.find(prompt)?.groupValues?.getOrNull(1)?.trim()
            if (!found.isNullOrBlank() && found.length in 2..90) {
                return found.trimEnd('.', ',', ';')
            }
        }
        return null
    }

    private fun extractUrl(prompt: String): String? =
        Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE).find(prompt)?.value?.trimEnd('.', ',', ')', ';')

    companion object {
        const val MAX_DYNAMIC_REPLANS = 3
    }
}
