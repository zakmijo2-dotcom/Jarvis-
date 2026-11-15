package com.example.core.agent

import com.example.core.ai.ModelCapabilityProfile
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolRegistry
import com.example.data.repository.JarvisSettings
import java.util.Locale

class IntentRouter {

    fun routePrompt(
        prompt: String,
        settings: JarvisSettings,
        isLocalModelReady: Boolean,
        isNetworkConnected: Boolean = true,
        activeCapabilities: ModelCapabilityProfile? = null,
        matchedSkillName: String? = null
    ): RoutingDecision {
        val clean = prompt.trim()
        val lower = clean.lowercase(Locale.getDefault())
        val localCapLabel = activeCapabilities?.toSummaryLabel() ?: "ctx=${settings.contextLengthTokens} • tools=yes • offline"
        val cloudCapLabel = "ctx=cloud • tools=yes • streaming"

        // Direct /tool command always maps to deterministic local tool execution
        val directTool = ToolRegistry.parseDirectToolCommand(clean)
        if (directTool != null) {
            return RoutingDecision(
                route = ExecutionRoute.ON_DEVICE_TOOLS,
                reason = "Direct operator tool command (${directTool.toolName}).",
                complexityScore = 1,
                needsWebResearch = false,
                detectedTools = listOf(directTool),
                useCloudModel = false,
                capabilitySummary = "deterministic_tool"
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
                route = if ((detectedTools.isNotEmpty() || matchedSkillName != null) && complexity <= 3) {
                    ExecutionRoute.ON_DEVICE_TOOLS
                } else {
                    ExecutionRoute.LOCAL_LITERT_LM
                },
                reason = if (explicitOverride == "local") {
                    "Explicit @local override selected by user."
                } else if (matchedSkillName != null) {
                    "Matched saved local skill '$matchedSkillName' (Local Only)."
                } else {
                    "Routing mode locked to Local Only (offline privacy preserved)."
                },
                complexityScore = complexity,
                needsWebResearch = false, // Strictly disabled in LOCAL_ONLY
                detectedTools = detectedTools,
                explicitModelOverride = explicitOverride,
                useCloudModel = false,
                privacyBlockedReason = privacyNotice,
                capabilitySummary = localCapLabel,
                matchedSkillName = matchedSkillName
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
                    privacyBlockedReason = "Cloud upload consent is disabled in Settings. Enable \"Allow Cloud API Transmission\" before sending data externally.",
                    capabilitySummary = cloudCapLabel,
                    matchedSkillName = matchedSkillName
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
                    privacyBlockedReason = "No API key configured for ${settings.cloudProviderKind.label}. Add your API key in Settings.",
                    capabilitySummary = cloudCapLabel,
                    matchedSkillName = matchedSkillName
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
                useCloudModel = true,
                capabilitySummary = cloudCapLabel,
                matchedSkillName = matchedSkillName
            )
        }

        // 3. AUTO_HYBRID intelligence
        if ((matchedSkillName != null || detectedTools.isNotEmpty()) && !needsWeb && complexity <= 4) {
            return RoutingDecision(
                route = ExecutionRoute.ON_DEVICE_TOOLS,
                reason = if (matchedSkillName != null) {
                    "Matched saved workflow skill '$matchedSkillName'; executing privately on-device."
                } else {
                    "Detected ${detectedTools.size} local device action(s); handling privately on-device."
                },
                complexityScore = complexity,
                needsWebResearch = false,
                detectedTools = detectedTools,
                useCloudModel = false,
                capabilitySummary = localCapLabel,
                matchedSkillName = matchedSkillName
            )
        }

        val exceedsLocalContext = activeCapabilities != null &&
            clean.length / 4 > (activeCapabilities.maxContextTokens * 0.7f)
        val localSupportsTools = activeCapabilities?.supportsToolCalling ?: true
        val prefersCloud = needsWeb || complexity >= 6 || !isLocalModelReady || exceedsLocalContext ||
            (detectedTools.isNotEmpty() && !localSupportsTools)

        if (prefersCloud && canUseCloud) {
            return RoutingDecision(
                route = if (needsWeb && settings.webResearchEnabled) {
                    ExecutionRoute.HYBRID_WEB_AND_AI
                } else {
                    ExecutionRoute.CLOUD_API
                },
                reason = when {
                    needsWeb -> "Web-dependent query routed to ${settings.cloudProviderKind.label}."
                    exceedsLocalContext -> "Prompt exceeds local context budget (${activeCapabilities?.maxContextTokens}); routed to Cloud AI."
                    complexity >= 6 -> "High reasoning complexity ($complexity/10) routed to Cloud AI."
                    else -> "Local model unloaded; falling back to consented Cloud AI."
                },
                complexityScore = complexity,
                needsWebResearch = needsWeb && settings.webResearchEnabled,
                detectedTools = detectedTools,
                useCloudModel = true,
                capabilitySummary = cloudCapLabel,
                matchedSkillName = matchedSkillName
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
            privacyBlockedReason = fallbackNote,
            capabilitySummary = localCapLabel,
            matchedSkillName = matchedSkillName
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
                detail = "Retrieve ranked user preferences, project context & facts"
            )
        )

        if (decision.needsWebResearch) {
            val cleanQuery = prompt
                .replace(Regex("""@(?:cloud|local|[a-zA-Z0-9._-]+)"""), "")
                .replace(
                    Regex(
                        """^(?:search web for|search the web for|look up|research|google|ابحث في الويب عن|ابحث في الانترنت عن|ابحث في الإنترنت عن|ابحث عن|فتش على|حوس على|cherche sur le web|rechercher sur le web|recherche)\s+""",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
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

        if (!decision.matchedSkillName.isNullOrBlank()) {
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.SKILL_EXECUTION,
                    title = "Run Skill ${decision.matchedSkillName}",
                    detail = "Execute verified multi-step workflow '${decision.matchedSkillName}'"
                )
            )
        } else {
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
        }

        val lower = prompt.lowercase(Locale.getDefault())
        val shouldObserveContext = lower.contains("screen") ||
            lower.contains("current app") ||
            lower.contains("notification") ||
            lower.contains("الشاشة") ||
            lower.contains("الإشعارات") ||
            lower.contains("الاشعارات") ||
            lower.contains("écran") ||
            decision.matchedSkillName != null ||
            decision.detectedTools.any {
                it.toolName in setOf("tap_ui_element", "scroll_screen", "input_ui_text", "inspect_screen_elements")
            }

        if (shouldObserveContext) {
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.CONTEXT_OBSERVE,
                    title = "Observe Device Context",
                    detail = "Capture telemetry, battery, network, and authorized UI context"
                )
            )
        }

        val shouldRunSynthesis = (decision.detectedTools.isEmpty() && decision.matchedSkillName == null) ||
            prompt.split(" ").size > 7 ||
            decision.needsWebResearch ||
            prompt.contains("summarize", ignoreCase = true) ||
            prompt.contains("explain", ignoreCase = true) ||
            prompt.contains("plan", ignoreCase = true) ||
            prompt.contains("لخص") ||
            prompt.contains("اشرح") ||
            prompt.contains("خطة") ||
            prompt.contains("résume", ignoreCase = true) ||
            prompt.contains("explique", ignoreCase = true)

        if (shouldRunSynthesis) {
            val engineLabel = if (decision.useCloudModel) "Cloud AI Streaming" else "Local LiteRT-LM"
            steps.add(
                AgentPlanStep(
                    index = idx++,
                    type = PlanStepType.AI_SYNTHESIS,
                    title = "Synthesize Response ($engineLabel)",
                    detail = "Combine memory, verified step outputs, and prompt"
                )
            )
        }

        if (decision.detectedTools.isNotEmpty() || !decision.matchedSkillName.isNullOrBlank() || decision.needsWebResearch) {
            steps.add(
                AgentPlanStep(
                    index = idx,
                    type = PlanStepType.RESULT_VERIFY,
                    title = "Verify & Distill Outcome",
                    detail = "Audit step verification evidence and safe skill eligibility"
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
            // English
            "search the web", "search web", "look up online", "latest news",
            "current price", "today's weather", "weather in", "who won",
            "release date", "what is the latest", "research online", "live info",
            "wikipedia",
            // Arabic (MSA & Algerian Darija)
            "ابحث في الويب", "ابحث في الانترنت", "ابحث في الإنترنت", "بحث في الويب",
            "ابحث عن", "فتش في الانترنت", "حوس في الانترنت", "أخبار اليوم", "اخبار اليوم",
            "آخر الأخبار", "اخر الاخبار", "سعر اليوم", "حالة الطقس", "الطقس في",
            "ويكيبيديا", "واش صاري اليوم",
            // French
            "cherche sur le web", "rechercher sur le web", "recherche web",
            "dernières actualités", "actualités du jour", "météo à", "météo aujourd'hui",
            "quel est le prix", "wikipédia"
        )
        return webPhrases.any { lowerPrompt.contains(it) }
    }

    internal fun detectNaturalLanguageTools(prompt: String): List<ToolCallRequest> {
        val clauses = prompt
            .split(
                Regex(
                    """\s+(?:and then|then|and|ثم|وبعدين|ومن بعد|و(?=\s*(?:افتح|شغل|اقرأ|اقرا|افحص|ذكرني|فكرني|اتصل|ارفع|اخفض|شوف|حل))|puis|et ensuite|et)\s+""",
                    RegexOption.IGNORE_CASE
                )
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val detected = mutableListOf<ToolCallRequest>()
        for (clause in clauses) {
            val lower = clause.lowercase(Locale.getDefault())
            when {
                // 1. Battery status (EN / AR / DZ / FR)
                (lower.contains("battery") && (lower.contains("check") || lower.contains("read") || lower.contains("level") || lower.contains("status") || lower.contains("percent"))) ||
                    lower.contains("البطارية") || lower.contains("حالة البطارية") || lower.contains("مستوى البطارية") ||
                    lower.contains("نسبة الشحن") || lower.contains("شحال الشارج") || lower.contains("شحال البطارية") ||
                    lower.contains("état de la batterie") || lower.contains("niveau de batterie") ||
                    (lower.contains("batterie") && (lower.contains("vérifie") || lower.contains("quel") || lower.contains("combien") || lower.contains("état") || lower.contains("niveau"))) ->
                    detected.add(ToolCallRequest("read_battery_status", source = "Agent Planner"))

                // 2. Device info & RAM specs (EN / AR / DZ / FR)
                ((lower.contains("device info") || lower.contains("ram usage") || lower.contains("system specs")) &&
                    (lower.contains("check") || lower.contains("read") || lower.contains("show"))) ||
                    lower.contains("معلومات الجهاز") || lower.contains("مواصفات الجهاز") || lower.contains("حالة الجهاز") ||
                    lower.contains("حالة الرام") || lower.contains("الذاكرة العشوائية") || lower.contains("معلومات الهاتف") ||
                    lower.contains("infos de l'appareil") || lower.contains("informations de l'appareil") || lower.contains("état de la ram") ->
                    detected.add(ToolCallRequest("read_device_info", source = "Agent Planner"))

                // 3. Wi-Fi settings (EN / AR / DZ / FR)
                lower.contains("wifi settings") || lower.contains("wi-fi settings") ||
                    lower.contains("إعدادات الواي فاي") || lower.contains("اعدادات الواي فاي") ||
                    lower.contains("إعدادات الشبكة") || lower.contains("اعدادات الويفي") ||
                    lower.contains("paramètres wifi") || lower.contains("paramètres wi-fi") ->
                    detected.add(ToolCallRequest("open_android_settings", mapOf("page" to "wifi"), "Agent Planner"))

                // 4. Bluetooth settings (EN / AR / DZ / FR)
                lower.contains("bluetooth settings") ||
                    lower.contains("إعدادات البلوتوث") || lower.contains("اعدادات البلوتوث") ||
                    lower.contains("paramètres bluetooth") ->
                    detected.add(ToolCallRequest("open_android_settings", mapOf("page" to "bluetooth"), "Agent Planner"))

                // 5. Home screen (EN / AR / DZ / FR)
                lower.contains("go home") || lower.contains("home screen") ||
                    lower.contains("الشاشة الرئيسية") || lower.contains("ارجع للرئيسية") || lower.contains("اذهب للشاشة الرئيسية") ||
                    lower.contains("écran d'accueil") || lower.contains("retour à l'accueil") ->
                    detected.add(ToolCallRequest("go_home_screen", source = "Agent Planner"))

                // 6. Read notifications (EN / AR / DZ / FR)
                lower.contains("read notifications") || lower.contains("check notifications") ||
                    lower.contains("اقرأ الإشعارات") || lower.contains("اقرا الاشعارات") ||
                    lower.contains("فحص الإشعارات") || lower.contains("اعرض الإشعارات") || lower.contains("شوف الاشعارات") ||
                    lower.contains("lire les notifications") || lower.contains("vérifier les notifications") || lower.contains("afficher les notifications") ->
                    detected.add(ToolCallRequest("read_notifications", mapOf("limit" to "5"), "Agent Planner"))

                // 7. Volume Up / Down (EN / AR / DZ / FR)
                lower.contains("volume up") || lower.contains("increase volume") ||
                    lower.contains("ارفع الصوت") || lower.contains("زيد للصوت") || lower.contains("علي الصوت") ||
                    lower.contains("monte le son") || lower.contains("augmenter le volume") ->
                    detected.add(ToolCallRequest("adjust_media_volume", mapOf("action" to "up"), "Agent Planner"))

                lower.contains("volume down") || lower.contains("decrease volume") ||
                    lower.contains("اخفض الصوت") || lower.contains("نقص الصوت") || lower.contains("وطي الصوت") ||
                    lower.contains("baisse le son") || lower.contains("diminuer le volume") ->
                    detected.add(ToolCallRequest("adjust_media_volume", mapOf("action" to "down"), "Agent Planner"))

                // 8. Media playback (EN / AR / DZ / FR)
                lower.contains("pause music") || lower.contains("play music") || lower.contains("play/pause") ||
                    lower.contains("أوقف الموسيقى") || lower.contains("شغل الموسيقى") || lower.contains("حبس الموسيقى") ||
                    lower.contains("pause musique") || lower.contains("jouer la musique") ->
                    detected.add(ToolCallRequest("control_media_playback", mapOf("command" to "play_pause"), "Agent Planner"))

                // 9. Reminders & timers (EN / AR / DZ / FR)
                lower.contains("remind me") || lower.contains("set a timer") || lower.contains("set timer") || lower.contains("create reminder") ||
                    lower.contains("ذكرني") || lower.contains("فكرني") || lower.contains("اضبط مؤقت") || lower.contains("انشئ تذكير") || lower.contains("أنشئ تذكير") || lower.contains("ديرلي تذكير") ||
                    lower.contains("rappelle-moi") || lower.contains("rappelle moi") || lower.contains("créer un rappel") || lower.contains("mets un minuteur") -> {
                    val minutes = extractReminderMinutes(lower)
                    val label = extractReminderLabel(clause)
                    detected.add(ToolCallRequest("create_reminder", mapOf("label" to label, "minutes" to minutes), "Agent Planner"))
                }

                // 10. Launch app by name (EN / AR / DZ / FR)
                lower.startsWith("launch ") || lower.startsWith("open app ") ||
                    lower.startsWith("افتح تطبيق ") || lower.startsWith("شغل تطبيق ") || lower.startsWith("حل تطبيق ") || lower.startsWith("افتح برنامج ") ||
                    lower.startsWith("ouvre l'application ") || lower.startsWith("lance l'application ") || lower.startsWith("ouvrir l'application ") -> {
                    val target = clause
                        .replace(
                            Regex(
                                """^(?:launch|open app|افتح تطبيق|شغل تطبيق|حل تطبيق|افتح برنامج|ouvre l'application|lance l'application|ouvrir l'application)\s+""",
                                RegexOption.IGNORE_CASE
                            ),
                            ""
                        )
                        .trim()
                    if (target.length >= 2) {
                        detected.add(ToolCallRequest("launch_installed_app", mapOf("app_query" to target), "Agent Planner"))
                    }
                }
            }
        }
        return detected.distinctBy { "${it.toolName}:${it.arguments}" }
    }

    private fun extractReminderMinutes(lowerClause: String): String {
        val normalizedDigits = normalizeArabicDigits(lowerClause)
        val explicitMin = Regex("""(\d{1,4})\s*(?:min|minute|minutes|دقيقة|دقائق|دقايق)""")
            .find(normalizedDigits)
            ?.groupValues
            ?.getOrNull(1)
        if (explicitMin != null) return explicitMin

        val explicitHours = Regex("""(\d{1,2})\s*(?:hour|hours|hr|ساعة|ساعات|heure|heures)""")
            .find(normalizedDigits)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (explicitHours != null) return (explicitHours * 60).coerceIn(1, 1440).toString()

        return when {
            normalizedDigits.contains("نصف ساعة") || normalizedDigits.contains("نص ساعة") || normalizedDigits.contains("demi-heure") -> "30"
            normalizedDigits.contains("ربع ساعة") -> "15"
            normalizedDigits.contains("ساعتين") -> "120"
            normalizedDigits.contains("ساعة") -> "60"
            normalizedDigits.contains("عشر دقائق") || normalizedDigits.contains("عشرة دقايق") -> "10"
            normalizedDigits.contains("خمس دقائق") || normalizedDigits.contains("خمسة دقايق") -> "5"
            else -> "15"
        }
    }

    private fun extractReminderLabel(rawClause: String): String {
        val cleaned = normalizeArabicDigits(rawClause)
            .replace(
                Regex(
                    """^(?:please\s+|من فضلك\s+|لو سمحت\s+|svp\s+)?(?:remind me to|remind me|set a timer for|set timer for|create reminder for|ذكرني\s+بـ|ذكرني\s+أن|ذكرني\s+ان|ذكرني|فكرني\s+بـ|فكرني|اضبط مؤقت\s+لـ|اضبط مؤقت|أنشئ تذكير\s+لـ|انشئ تذكير|ديرلي تذكير|rappelle-moi de|rappelle moi de|rappelle-moi|rappelle moi|créer un rappel pour|mets un minuteur pour)\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    """\s+(?:in|بعد|خلال|dans)\s+(?:\d+\s*(?:min|minute|minutes|hour|hours|دقيقة|دقائق|دقايق|ساعة|ساعات|heure|heures)|نصف ساعة|نص ساعة|ربع ساعة|ساعتين|ساعة|عشر دقائق|خمس دقائق).*$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
        return cleaned.ifBlank { "JARVIS Reminder" }.take(80)
    }

    private fun normalizeArabicDigits(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> out.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> out.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    internal fun computeComplexityScore(rawPrompt: String, lowerPrompt: String): Int {
        var score = 1
        val words = rawPrompt.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (words >= 15) score += 2
        if (words >= 35) score += 2

        val complexKeywords = listOf(
            "architect", "compare", "tradeoff", "analyze", "algorithm",
            "optimize", "debug", "refactor", "research", "comprehensive",
            "step-by-step", "detailed", "strategy", "benchmark", "math", "prove",
            "قارن", "حلل", "خوارزمية", "معمارية", "بالتفصيل", "خطوة بخطوة", "استراتيجية",
            "comparer", "analyser", "algorithme", "architecture", "détaillé", "stratégie"
        )
        val keywordHits = complexKeywords.count { lowerPrompt.contains(it) }
        score += (keywordHits * 2).coerceAtMost(5)
        if (detectWebResearchIntent(lowerPrompt)) score += 3
        return score.coerceIn(1, 10)
    }
}
