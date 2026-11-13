package com.example.core.agent

import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolRegistry
import com.example.data.repository.JarvisSettings
import java.util.Locale

class IntentRouter {

    fun routePrompt(
        prompt: String,
        settings: JarvisSettings,
        isLocalModelReady: Boolean,
        isNetworkConnected: Boolean = true
    ): RoutingDecision {
        val clean = prompt.trim()
        val lower = clean.lowercase(Locale.getDefault())

        // Direct /tool command always maps to deterministic local tool execution
        val directTool = ToolRegistry.parseDirectToolCommand(clean)
        if (directTool != null) {
            return RoutingDecision(
                route = ExecutionRoute.ON_DEVICE_TOOLS,
                reason = "Direct operator tool command (${directTool.toolName}).",
                complexityScore = 1,
                needsWebResearch = false,
                detectedTools = listOf(directTool),
                useCloudModel = false
            )
        }

        val explicitOverride = parseExplicitOverride(clean)
        val detectedTools = detectNaturalLanguageTools(clean)
        val needsWeb = detectWebResearchIntent(lower)
        val complexity = computeComplexityScore(clean, lower)

        val hasCloudKey = settings.cloudApiKey.isNotBlank()
        val canUseCloud = settings.cloudUploadConsentGranted && hasCloudKey && isNetworkConnected

        // 1. User forced LOCAL_ONLY
        if (settings.routingMode == AgentRoutingMode.LOCAL_ONLY || explicitOverride == "local") {
            val privacyNotice = if (needsWeb && !settings.webResearchEnabled) {
                "Local-Only mode active: local models cannot access the internet."
            } else null
            return RoutingDecision(
                route = if (detectedTools.isNotEmpty() && complexity <= 3) {
                    ExecutionRoute.ON_DEVICE_TOOLS
                } else {
                    ExecutionRoute.LOCAL_LITERT_LM
                },
                reason = if (explicitOverride == "local") {
                    "Explicit @local override selected by user."
                } else {
                    "Routing mode locked to Local Only (offline privacy preserved)."
                },
                complexityScore = complexity,
                needsWebResearch = false, // Strictly disabled in LOCAL_ONLY
                detectedTools = detectedTools,
                explicitModelOverride = explicitOverride,
                useCloudModel = false,
                privacyBlockedReason = privacyNotice
            )
        }

        // 2. User forced CLOUD_ONLY or explicit @cloud / model override
        val cloudRequested = settings.routingMode == AgentRoutingMode.CLOUD_ONLY ||
            (explicitOverride != null && explicitOverride != "local")
        if (cloudRequested) {
            if (!settings.cloudUploadConsentGranted) {
                return RoutingDecision(
                    route = ExecutionRoute.CLOUD_API,
                    reason = "Cloud routing requested, but Cloud Data Consent is OFF.",
                    complexityScore = complexity,
                    needsWebResearch = needsWeb && settings.webResearchEnabled,
                    detectedTools = detectedTools,
                    explicitModelOverride = explicitOverride,
                    useCloudModel = true,
                    privacyBlockedReason = "Cloud upload consent is disabled in Settings. Enable \"Allow Cloud API Transmission\" before sending data externally."
                )
            }
            if (!hasCloudKey) {
                return RoutingDecision(
                    route = ExecutionRoute.CLOUD_API,
                    reason = "Cloud routing requested, but no personal API key is configured.",
                    complexityScore = complexity,
                    needsWebResearch = needsWeb && settings.webResearchEnabled,
                    detectedTools = detectedTools,
                    explicitModelOverride = explicitOverride,
                    useCloudModel = true,
                    privacyBlockedReason = "No API key configured for ${settings.cloudProviderKind.label}. Add your API key in Settings."
                )
            }
            return RoutingDecision(
                route = if (needsWeb && settings.webResearchEnabled) {
                    ExecutionRoute.HYBRID_WEB_AND_AI
                } else {
                    ExecutionRoute.CLOUD_API
                },
                reason = if (explicitOverride != null) {
                    "Explicit user model selection (@$explicitOverride)."
                } else {
                    "Routing mode set to Cloud Only (${settings.cloudProviderKind.label})."
                },
                complexityScore = complexity,
                needsWebResearch = needsWeb && settings.webResearchEnabled,
                detectedTools = detectedTools,
                explicitModelOverride = explicitOverride,
                useCloudModel = true
            )
        }

        // 3. AUTO_HYBRID intelligence
        if (detectedTools.isNotEmpty() && !needsWeb && complexity <= 4) {
            return RoutingDecision(
                route = ExecutionRoute.ON_DEVICE_TOOLS,
                reason = "Detected ${detectedTools.size} local device action(s); handling privately on-device.",
                complexityScore = complexity,
                needsWebResearch = false,
                detectedTools = detectedTools,
                useCloudModel = false
            )
        }

        val prefersCloud = needsWeb || complexity >= 6 || !isLocalModelReady
        if (prefersCloud && canUseCloud) {
            return RoutingDecision(
                route = if (needsWeb && settings.webResearchEnabled) {
                    ExecutionRoute.HYBRID_WEB_AND_AI
                } else {
                    ExecutionRoute.CLOUD_API
                },
                reason = when {
                    needsWeb -> "Web-dependent query routed to ${settings.cloudProviderKind.label}."
                    complexity >= 6 -> "High reasoning complexity ($complexity/10) routed to Cloud AI."
                    else -> "Local model unloaded; falling back to consented Cloud AI."
                },
                complexityScore = complexity,
                needsWebResearch = needsWeb && settings.webResearchEnabled,
                detectedTools = detectedTools,
                useCloudModel = true
            )
        }

        val fallbackNote = if (needsWeb && !settings.webResearchEnabled) {
            "Web Research is disabled in Settings; local models do not have internet access."
        } else null

        return RoutingDecision(
            route = if (needsWeb && settings.webResearchEnabled && isNetworkConnected) {
                ExecutionRoute.HYBRID_WEB_AND_AI
            } else {
                ExecutionRoute.LOCAL_LITERT_LM
            },
            reason = when {
                needsWeb && settings.webResearchEnabled -> "Live Web Research grounded into Local LiteRT-LM synthesis."
                !isNetworkConnected -> "Offline detected: using local LiteRT-LM inference."
                prefersCloud && !settings.cloudUploadConsentGranted -> "High-complexity prompt kept local because Cloud upload consent is OFF."
                prefersCloud && !hasCloudKey -> "Complex prompt kept local because no Cloud API key is configured."
                else -> "Everyday conversation/planning routed locally on LiteRT-LM."
            },
            complexityScore = complexity,
            needsWebResearch = needsWeb && settings.webResearchEnabled,
            detectedTools = detectedTools,
            useCloudModel = false,
            privacyBlockedReason = fallbackNote
        )
    }

    fun decomposeTasks(
        prompt: String,
        decision: RoutingDecision
    ): List<AgentPlanStep> {
        val steps = mutableListOf<AgentPlanStep>()
        var idx = 0

        steps.add(
            AgentPlanStep(
                index = idx++,
                type = PlanStepType.MEMORY_LOOKUP,
                title = "Query Local Memory",
                detail = "Retrieve user-approved preferences & historical context"
            )
        )

        if (decision.needsWebResearch) {
            val cleanQuery = prompt
                .replace(Regex("""@(?:cloud|local|[a-zA-Z0-9._-]+)"""), "")
                .replace(Regex("""^(?:search web for|search the web for|look up|research|google)\s+""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { prompt.trim() }
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.WEB_RESEARCH,
                    title = "Live Web Research",
                    detail = cleanQuery.take(80)
                )
            )
        }

        decision.detectedTools.forEach { toolReq ->
            val argsSummary = if (toolReq.arguments.isEmpty()) "no args"
            else toolReq.arguments.entries.joinToString(", ") { "${it.key}=${it.value}" }
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.ANDROID_TOOL,
                    title = "Execute ${toolReq.toolName}",
                    detail = argsSummary
                )
            )
        }

        val shouldRunSynthesis = decision.detectedTools.isEmpty() ||
            prompt.split(" ").size > 7 ||
            decision.needsWebResearch ||
            prompt.contains("summarize", ignoreCase = true) ||
            prompt.contains("explain", ignoreCase = true) ||
            prompt.contains("plan", ignoreCase = true)

        if (shouldRunSynthesis) {
            val engineLabel = if (decision.useCloudModel) "Cloud AI Streaming" else "Local LiteRT-LM"
            steps.add(
                AgentPlanStep(
                    index = idx,
                    type = PlanStepType.AI_SYNTHESIS,
                    title = "Synthesize Response ($engineLabel)",
                    detail = "Combine memory, verified step outputs, and prompt"
                )
            )
        }

        return steps
    }

    internal fun parseExplicitOverride(prompt: String): String? {
        val match = Regex("""^@(local|cloud|[a-zA-Z0-9._:/-]{3,48})\b""", RegexOption.IGNORE_CASE)
            .find(prompt.trim()) ?: return null
        return match.groupValues[1].lowercase(Locale.getDefault())
    }

    internal fun detectWebResearchIntent(lowerPrompt: String): Boolean {
        val webPhrases = listOf(
            "search the web", "search web", "look up online", "latest news",
            "current price", "today's weather", "weather in", "who won",
            "release date", "what is the latest", "research online", "live info",
            "wikipedia"
        )
        return webPhrases.any { lowerPrompt.contains(it) }
    }

    internal fun detectNaturalLanguageTools(prompt: String): List<ToolCallRequest> {
        val clauses = prompt
            .split(Regex("""\s+(?:and then|then|and)\s+""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val detected = mutableListOf<ToolCallRequest>()
        for (clause in clauses) {
            val lower = clause.lowercase(Locale.getDefault())
            when {
                lower.contains("battery") && (lower.contains("check") || lower.contains("read") || lower.contains("level") || lower.contains("status")) ->
                    detected.add(ToolCallRequest("read_battery_status", source = "Agent Planner"))

                (lower.contains("device info") || lower.contains("ram usage") || lower.contains("system specs")) &&
                    (lower.contains("check") || lower.contains("read") || lower.contains("show")) ->
                    detected.add(ToolCallRequest("read_device_info", source = "Agent Planner"))

                lower.contains("wifi settings") || lower.contains("wi-fi settings") ->
                    detected.add(ToolCallRequest("open_android_settings", mapOf("page" to "wifi"), "Agent Planner"))

                lower.contains("bluetooth settings") ->
                    detected.add(ToolCallRequest("open_android_settings", mapOf("page" to "bluetooth"), "Agent Planner"))

                lower.contains("go home") || lower.contains("home screen") ->
                    detected.add(ToolCallRequest("go_home_screen", source = "Agent Planner"))

                lower.contains("read notifications") || lower.contains("check notifications") ->
                    detected.add(ToolCallRequest("read_notifications", mapOf("limit" to "5"), "Agent Planner"))

                lower.contains("volume up") || lower.contains("increase volume") ->
                    detected.add(ToolCallRequest("adjust_media_volume", mapOf("action" to "up"), "Agent Planner"))

                lower.contains("volume down") || lower.contains("decrease volume") ->
                    detected.add(ToolCallRequest("adjust_media_volume", mapOf("action" to "down"), "Agent Planner"))

                lower.contains("pause music") || lower.contains("play music") || lower.contains("play/pause") ->
                    detected.add(ToolCallRequest("control_media_playback", mapOf("command" to "play_pause"), "Agent Planner"))

                lower.startsWith("launch ") || lower.startsWith("open app ") -> {
                    val target = clause
                        .removePrefix("launch ").removePrefix("Launch ")
                        .removePrefix("open app ").removePrefix("Open app ")
                        .trim()
                    if (target.length >= 2) {
                        detected.add(ToolCallRequest("launch_installed_app", mapOf("app_query" to target), "Agent Planner"))
                    }
                }
            }
        }
        return detected.distinctBy { "${it.toolName}:${it.arguments}" }
    }

    internal fun computeComplexityScore(rawPrompt: String, lowerPrompt: String): Int {
        var score = 1
        val words = rawPrompt.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (words >= 15) score += 2
        if (words >= 35) score += 2

        val complexKeywords = listOf(
            "architect", "compare", "tradeoff", "analyze", "algorithm",
            "optimize", "debug", "refactor", "research", "comprehensive",
            "step-by-step", "detailed", "strategy", "benchmark", "math", "prove"
        )
        val keywordHits = complexKeywords.count { lowerPrompt.contains(it) }
        score += (keywordHits * 2).coerceAtMost(5)
        if (detectWebResearchIntent(lowerPrompt)) score += 3
        return score.coerceIn(1, 10)
    }
}
