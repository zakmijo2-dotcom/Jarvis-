package com.example.core.skills

import android.content.Context
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolRegistry
import com.example.core.tools.ToolRiskLevel
import com.example.data.local.db.SkillDao
import com.example.data.local.db.SkillEntity
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class SkillStepSpec(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
    val expectedOutcomeHint: String = ""
)

data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val triggerPattern: String,
    val requiredPermissions: List<String> = emptyList(),
    val parameters: List<String> = emptyList(),
    val steps: List<SkillStepSpec>,
    val validationRules: String = "All step tool calls must return ToolExecutionStatus.SUCCESS.",
    val failureHandling: String = "Abort remaining steps cleanly without infinite retry and report exact tool error.",
    val version: Int = 1,
    val provenance: String = "User Created",
    val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class SkillExecutionReport(
    val skill: AgentSkill,
    val overallSuccess: Boolean,
    val completedSteps: Int,
    val totalSteps: Int,
    val stepResults: List<ToolExecutionResult>,
    val summary: String
)

interface SkillEngine {
    fun observeSkills(): Flow<List<AgentSkill>>
    suspend fun listSkillsOnce(): List<AgentSkill>
    suspend fun getSkillByName(name: String): AgentSkill?
    suspend fun findMatchingSkill(prompt: String): AgentSkill?
    fun extractParameters(prompt: String, skill: AgentSkill): Map<String, String> = emptyMap()
    suspend fun saveSkill(skill: AgentSkill, allowHighRiskManualSave: Boolean = false): Result<AgentSkill>
    suspend fun distillWorkflowIfSafe(
        name: String,
        description: String,
        triggerPattern: String,
        executedRequests: List<ToolCallRequest>,
        toolRegistry: ToolRegistry
    ): Result<AgentSkill>
    suspend fun deleteSkill(id: String)
    suspend fun executeSkill(
        context: Context,
        skill: AgentSkill,
        providedParams: Map<String, String>,
        toolRegistry: ToolRegistry,
        settings: JarvisSettings,
        userConfirmed: Boolean
    ): SkillExecutionReport
}

class RoomSkillEngine(
    private val skillDao: SkillDao
) : SkillEngine {

    override fun observeSkills(): Flow<List<AgentSkill>> =
        skillDao.observeSkills().map { rows -> rows.map(::toModel) }

    override suspend fun listSkillsOnce(): List<AgentSkill> =
        skillDao.getAllSkillsOnce().map(::toModel)

    override suspend fun getSkillByName(name: String): AgentSkill? =
        skillDao.getByName(name.trim())?.let(::toModel)

    override suspend fun findMatchingSkill(prompt: String): AgentSkill? {
        val clean = prompt.trim().lowercase(Locale.ROOT)
        if (clean.length < 4) return null
        val all = listSkillsOnce()
        if (all.isEmpty()) return null

        val explicitMatch = Regex(
            """(?:run skill|execute skill|نفذ مهارة|شغل مهارة|exécuter la compétence|lance la compétence)\s+([a-z0-9_-]{2,40})""",
            RegexOption.IGNORE_CASE
        )
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
        if (!explicitMatch.isNullOrBlank()) {
            all.firstOrNull { it.name.equals(explicitMatch, ignoreCase = true) }?.let { return it }
        }

        return all.firstOrNull { skill ->
            val nameHit = clean.contains(skill.name.lowercase(Locale.ROOT).replace('_', ' ')) ||
                clean.contains(skill.name.lowercase(Locale.ROOT))
            val triggerTokens = skill.triggerPattern
                .split(',', '|')
                .map { it.trim() }
                .filter { it.length >= 3 }
            val triggerHit = triggerTokens.any { token ->
                val lowerToken = token.lowercase(Locale.ROOT)
                if (lowerToken.contains("{{") || lowerToken.contains("{")) {
                    buildTemplateRegex(token)?.containsMatchIn(prompt.trim()) == true
                } else {
                    lowerToken.length >= 4 && clean.contains(lowerToken)
                }
            }
            nameHit || triggerHit
        }
    }

    override fun extractParameters(prompt: String, skill: AgentSkill): Map<String, String> {
        val requiredParams = (skill.parameters + extractTemplatePlaceholders(skill)).distinct()
        if (requiredParams.isEmpty()) return emptyMap()

        val extracted = linkedMapOf<String, String>()
        val trimmedPrompt = prompt.trim()

        // 1. Explicit key=value or key="value" or key: "value" in prompt
        for (param in requiredParams) {
            val kvRegex = Regex(
                """\b${Regex.escape(param)}\s*(?:=|:)\s*(?:"([^"]+)"|'([^']+)'|([^\s,;]+))""",
                RegexOption.IGNORE_CASE
            )
            val match = kvRegex.find(trimmedPrompt)
            val value = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()
            if (!value.isNullOrBlank()) {
                extracted[param] = value
            }
        }

        // 2. Match parameterized trigger templates (e.g. "open {{app_query}} and check" or "افتح {{app_query}}")
        val triggerCandidates = skill.triggerPattern
            .split(',', '|')
            .map { it.trim() }
            .filter { it.contains("{") }
        for (template in triggerCandidates) {
            val paramNamesInTemplate = PLACEHOLDER_REGEX.findAll(template)
                .map { it.groupValues[1] }
                .toList()
            if (paramNamesInTemplate.isEmpty()) continue
            val regex = buildTemplateRegex(template) ?: continue
            val match = regex.find(trimmedPrompt) ?: continue
            paramNamesInTemplate.forEachIndexed { idx, pName ->
                val captured = match.groupValues.getOrNull(idx + 1)?.trim()?.trim('"', '\'', '«', '»')
                if (!captured.isNullOrBlank() && extracted[pName].isNullOrBlank()) {
                    extracted[pName] = captured
                }
            }
        }

        // 3. Single-parameter positional / quoted fallback
        if (requiredParams.size == 1) {
            val singleParam = requiredParams.first()
            if (extracted[singleParam].isNullOrBlank()) {
                val quoted = QUOTED_PARAM_REGEX
                    .find(trimmedPrompt)
                    ?.groupValues
                    ?.drop(1)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                if (!quoted.isNullOrBlank()) {
                    extracted[singleParam] = quoted
                } else {
                    val triggerPhrases = skill.triggerPattern
                        .split(',', '|')
                        .map { it.replace(PLACEHOLDER_REGEX, "").trim() }
                        .filter { it.length >= 3 } + listOf(
                        "run skill ${skill.name}",
                        "execute skill ${skill.name}",
                        "نفذ مهارة ${skill.name}",
                        "شغل مهارة ${skill.name}",
                        skill.name.replace('_', ' '),
                        skill.name
                    )
                    for (phrase in triggerPhrases) {
                        val idx = trimmedPrompt.lowercase(Locale.ROOT).indexOf(phrase.lowercase(Locale.ROOT))
                        if (idx >= 0) {
                            val tail = trimmedPrompt.substring(idx + phrase.length)
                                .replace(Regex("""^(?:\s+(?:for|on|with|to|لـ|على|بـ))?\s*""", RegexOption.IGNORE_CASE), "")
                                .trim()
                            if (tail.isNotBlank() && !tail.contains('=')) {
                                extracted[singleParam] = tail.take(80)
                                break
                            }
                        }
                    }
                }
            }
        }

        // 4. Contextual numeric fallback for 'minutes' or 'limit'
        for (param in requiredParams) {
            if (!extracted[param].isNullOrBlank()) continue
            if (param.equals("minutes", ignoreCase = true) || param.equals("limit", ignoreCase = true)) {
                val num = Regex("""(\d{1,4})""").find(trimmedPrompt)?.groupValues?.getOrNull(1)
                if (!num.isNullOrBlank()) {
                    extracted[param] = num
                }
            }
        }

        return extracted
    }

    private fun extractTemplatePlaceholders(skill: AgentSkill): List<String> {
        val found = mutableListOf<String>()
        skill.steps.forEach { step ->
            step.arguments.values.forEach { argTemplate ->
                PLACEHOLDER_REGEX.findAll(argTemplate).forEach { m ->
                    found.add(m.groupValues[1])
                }
            }
        }
        return found.distinct()
    }

    private fun buildTemplateRegex(template: String): Regex? = runCatching {
        val sb = StringBuilder()
        var cursor = 0
        val matches = PLACEHOLDER_REGEX.findAll(template).toList()
        if (matches.isEmpty()) return null
        for ((index, m) in matches.withIndex()) {
            val staticPart = template.substring(cursor, m.range.first)
            if (staticPart.isNotEmpty()) {
                sb.append(Regex.escape(staticPart))
            }
            val isLastAndAtEnd = index == matches.lastIndex && m.range.last == template.lastIndex
            sb.append(if (isLastAndAtEnd) "(.+?)" else "([^,;|]+?)")
            cursor = m.range.last + 1
        }
        if (cursor < template.length) {
            sb.append(Regex.escape(template.substring(cursor)))
        }
        Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }.getOrNull()

    override suspend fun saveSkill(
        skill: AgentSkill,
        allowHighRiskManualSave: Boolean
    ): Result<AgentSkill> {
        val cleanName = skill.name.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_").trim('_')
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Skill name must contain at least 2 alphanumeric characters."))
        }
        if (skill.steps.isEmpty()) {
            return Result.failure(IllegalArgumentException("A reusable skill must contain at least 1 tool step."))
        }
        val dangerous = skill.steps.any { it.toolName in DANGEROUS_AUTO_SKILL_TOOLS } ||
            skill.riskLevel == ToolRiskLevel.HIGH
        if (dangerous && !allowHighRiskManualSave) {
            return Result.failure(
                SecurityException("Refused saving high-risk or destructive tool workflow into automatic skill storage.")
            )
        }

        val now = System.currentTimeMillis()
        val existing = skillDao.getByName(cleanName)
        val nextVersion = if (existing != null) existing.version + 1 else skill.version.coerceAtLeast(1)
        val finalId = existing?.id ?: skill.id.ifBlank { UUID.randomUUID().toString() }
        val finalCreated = existing?.createdAt ?: now

        val normalized = skill.copy(
            id = finalId,
            name = cleanName,
            version = nextVersion,
            createdAt = finalCreated,
            updatedAt = now
        )
        skillDao.upsertSkill(toEntity(normalized))
        return Result.success(normalized)
    }

    override suspend fun distillWorkflowIfSafe(
        name: String,
        description: String,
        triggerPattern: String,
        executedRequests: List<ToolCallRequest>,
        toolRegistry: ToolRegistry
    ): Result<AgentSkill> {
        if (executedRequests.size < 2) {
            return Result.failure(IllegalArgumentException("Need at least 2 sequential safe steps to distill a workflow skill."))
        }
        val resolvedTools = executedRequests.mapNotNull { req ->
            toolRegistry.getTool(req.toolName)?.let { req to it }
        }
        if (resolvedTools.size != executedRequests.size) {
            return Result.failure(IllegalArgumentException("Unknown tool in workflow; cannot distill skill."))
        }
        // Policy rule: Never automatically save dangerous actions as reusable skills.
        val hasDangerous = resolvedTools.any { (req, tool) ->
            tool.requiresUserConfirmation ||
                tool.riskLevel == ToolRiskLevel.HIGH ||
                req.toolName in DANGEROUS_AUTO_SKILL_TOOLS
        }
        if (hasDangerous) {
            return Result.failure(
                SecurityException("Policy block: Workflows containing high-risk or confirmation-gated tools are never auto-saved as skills.")
            )
        }

        val requiredPerms = resolvedTools
            .flatMap { (_, tool) -> tool.permissionRequirements.map { it.toString() } }
            .distinct()

        val steps = executedRequests.map { req ->
            SkillStepSpec(
                toolName = req.toolName,
                arguments = req.arguments,
                expectedOutcomeHint = "ToolExecutionStatus.SUCCESS"
            )
        }
        return saveSkill(
            skill = AgentSkill(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description.take(240),
                triggerPattern = triggerPattern.take(160),
                requiredPermissions = requiredPerms,
                parameters = emptyList(),
                steps = steps,
                provenance = "Auto-Distilled Safe Workflow",
                riskLevel = ToolRiskLevel.LOW
            ),
            allowHighRiskManualSave = false
        )
    }

    override suspend fun deleteSkill(id: String) {
        skillDao.deleteSkillById(id)
    }

    override suspend fun executeSkill(
        context: Context,
        skill: AgentSkill,
        providedParams: Map<String, String>,
        toolRegistry: ToolRegistry,
        settings: JarvisSettings,
        userConfirmed: Boolean
    ): SkillExecutionReport {
        val missingParams = skill.parameters.filter { param ->
            providedParams[param].isNullOrBlank()
        }
        if (missingParams.isNotEmpty()) {
            return SkillExecutionReport(
                skill = skill,
                overallSuccess = false,
                completedSteps = 0,
                totalSteps = skill.steps.size,
                stepResults = emptyList(),
                summary = "Missing required skill parameter(s): ${missingParams.joinToString(", ")}."
            )
        }

        val results = mutableListOf<ToolExecutionResult>()
        for (step in skill.steps) {
            val resolvedArgs = step.arguments.mapValues { (_, template) ->
                var out = template
                providedParams.forEach { (k, v) ->
                    out = out.replace("{{$k}}", v).replace("{$k}", v)
                }
                out
            }
            val result = toolRegistry.dispatch(
                context = context,
                request = ToolCallRequest(
                    toolName = step.toolName,
                    arguments = resolvedArgs,
                    source = "Skill:${skill.name}"
                ),
                settings = settings,
                userConfirmed = userConfirmed
            )
            results.add(result)
            if (result.status != ToolExecutionStatus.SUCCESS) {
                return SkillExecutionReport(
                    skill = skill,
                    overallSuccess = false,
                    completedSteps = results.count { it.status == ToolExecutionStatus.SUCCESS },
                    totalSteps = skill.steps.size,
                    stepResults = results,
                    summary = "Skill '${skill.name}' stopped at '${step.toolName}' (${result.status.name}): ${result.summary}"
                )
            }
        }

        skillDao.incrementUsage(skill.id, System.currentTimeMillis())
        return SkillExecutionReport(
            skill = skill,
            overallSuccess = true,
            completedSteps = skill.steps.size,
            totalSteps = skill.steps.size,
            stepResults = results,
            summary = "Skill '${skill.name}' v${skill.version} verified (${skill.steps.size}/${skill.steps.size} steps succeeded)."
        )
    }

    private fun toEntity(model: AgentSkill): SkillEntity {
        val stepsArr = JSONArray()
        model.steps.forEach { step ->
            val argsObj = JSONObject()
            step.arguments.forEach { (k, v) -> argsObj.put(k, v) }
            stepsArr.put(
                JSONObject().apply {
                    put("toolName", step.toolName)
                    put("arguments", argsObj)
                    put("expectedOutcomeHint", step.expectedOutcomeHint)
                }
            )
        }
        return SkillEntity(
            id = model.id,
            name = model.name,
            description = model.description,
            triggerPattern = model.triggerPattern,
            requiredPermissionsJson = JSONArray(model.requiredPermissions).toString(),
            parametersJson = JSONArray(model.parameters).toString(),
            stepsJson = stepsArr.toString(),
            validationRules = model.validationRules,
            failureHandling = model.failureHandling,
            version = model.version,
            provenance = model.provenance,
            riskLevel = model.riskLevel.name,
            usageCount = model.usageCount,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt
        )
    }

    private fun toModel(entity: SkillEntity): AgentSkill {
        val perms = parseStringArray(entity.requiredPermissionsJson)
        val params = parseStringArray(entity.parametersJson)
        val steps = parseStepsArray(entity.stepsJson)
        val risk = runCatching { ToolRiskLevel.valueOf(entity.riskLevel) }.getOrDefault(ToolRiskLevel.LOW)
        return AgentSkill(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            triggerPattern = entity.triggerPattern,
            requiredPermissions = perms,
            parameters = params,
            steps = steps,
            validationRules = entity.validationRules,
            failureHandling = entity.failureHandling,
            version = entity.version,
            provenance = entity.provenance,
            riskLevel = risk,
            usageCount = entity.usageCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun parseStringArray(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun parseStepsArray(raw: String): List<SkillStepSpec> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
            val args = linkedMapOf<String, String>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                args[k] = argsObj.optString(k)
            }
            SkillStepSpec(
                toolName = obj.optString("toolName"),
                arguments = args,
                expectedOutcomeHint = obj.optString("expectedOutcomeHint")
            )
        }.filter { it.toolName.isNotBlank() }
    }.getOrDefault(emptyList())

    companion object {
        val PLACEHOLDER_REGEX = Regex("""\{\{?([a-zA-Z0-9_]+)\}?\}""")
        private val QUOTED_PARAM_REGEX = Regex("""(?:"([^"]+)"|'([^']+)'|«([^»]+)»)""")
        val DANGEROUS_AUTO_SKILL_TOOLS = setOf(
            "initiate_phone_call",
            "compose_message",
            "accessibility_global_action",
            "tap_ui_element",
            "tap_coordinates",
            "long_press_coordinates",
            "swipe_screen",
            "capture_screen_visual",
            "mcp_call",
            "input_ui_text",
            "scroll_screen",
            "create_reminder"
        )
    }
}
