package com.example.core.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.repository.JarvisSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

interface JarvisTool {
    val name: String
    val category: ToolCategory
    val description: String
    val parameters: List<ToolParameterSpec>
    val permissionRequirements: List<ToolPermissionRequirement>
    val requiresUserConfirmation: Boolean
    val riskLevel: ToolRiskLevel
        get() = if (requiresUserConfirmation) ToolRiskLevel.HIGH else ToolRiskLevel.LOW

    fun validate(args: Map<String, String>): ToolValidationResult

    fun describeConfirmation(args: Map<String, String>): Pair<String, String> =
        "Confirm executing $name?" to "User confirmation required before running this device action."

    suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult

    suspend fun execute(context: Context, args: Map<String, String>, settings: JarvisSettings): ToolExecutionResult =
        execute(context, args)

    suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings,
        userConfirmed: Boolean
    ): ToolExecutionResult = execute(context, args, settings)
}

class ToolPermissionValidator(
    private val notificationAuthChecker: (Context) -> Boolean = NotificationBridge::isListenerAuthorized,
    private val accessibilityAuthChecker: (Context) -> Boolean = AccessibilityBridge::isAccessibilityAuthorized
) {
    fun validatePermissions(
        context: Context,
        tool: JarvisTool,
        settings: JarvisSettings
    ): PermissionValidationResult {
        for (req in tool.permissionRequirements) {
            when (req) {
                is ToolPermissionRequirement.RuntimePermission -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        req.permission
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        return PermissionValidationResult.MissingRuntimePermission(
                            permission = req.permission,
                            rationale = req.rationale
                        )
                    }
                }

                is ToolPermissionRequirement.SpecialAccess -> {
                    when (req.type) {
                        SpecialAccessType.NOTIFICATION_LISTENER -> {
                            if (!settings.notificationReaderEnabled) {
                                return PermissionValidationResult.OptInDisabled(
                                    settingKey = "notificationReaderEnabled",
                                    rationale = "Notification Reader privacy toggle is disabled in Device Controls."
                                )
                            }
                            if (!notificationAuthChecker(context)) {
                                return PermissionValidationResult.MissingSpecialAccess(
                                    accessType = req.type,
                                    rationale = req.rationale
                                )
                            }
                        }

                        SpecialAccessType.ACCESSIBILITY_SERVICE -> {
                            if (!settings.accessibilityAutomationEnabled) {
                                return PermissionValidationResult.OptInDisabled(
                                    settingKey = "accessibilityAutomationEnabled",
                                    rationale = "Accessibility Automation toggle is disabled in Device Controls."
                                )
                            }
                            if (!accessibilityAuthChecker(context)) {
                                return PermissionValidationResult.MissingSpecialAccess(
                                    accessType = req.type,
                                    rationale = req.rationale
                                )
                            }
                        }
                    }
                }
            }
        }
        return PermissionValidationResult.Granted
    }
}

class ToolRegistry(
    tools: List<JarvisTool>,
    private val permissionValidator: ToolPermissionValidator = ToolPermissionValidator()
) {
    private val toolsByName: Map<String, JarvisTool>
    private val activeCancelled = AtomicBoolean(false)

    init {
        val seen = linkedMapOf<String, JarvisTool>()
        for (tool in tools) {
            require(tool.name.matches(Regex("^[a-z][a-z0-9_]{1,48}$"))) {
                "Invalid tool name: ${tool.name}"
            }
            require(!seen.containsKey(tool.name)) {
                "Duplicate tool name registered: ${tool.name}"
            }
            seen[tool.name] = tool
        }
        toolsByName = seen.toMap()
    }

    fun listTools(): List<JarvisTool> = toolsByName.values.toList()

    fun getTool(name: String): JarvisTool? = toolsByName[name.trim().lowercase()]

    fun cancelActiveExecution() {
        activeCancelled.set(true)
    }

    suspend fun dispatch(
        context: Context,
        request: ToolCallRequest,
        settings: JarvisSettings,
        userConfirmed: Boolean = false
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        activeCancelled.set(false)
        currentCoroutineContext().ensureActive()

        val normalizedName = request.toolName.trim().lowercase()
        val unsafeArg = request.arguments.entries.any { (k, v) ->
            if ((k == "url" || v.startsWith("https://") || v.startsWith("http://")) &&
                !v.contains("&&") && !v.contains("||")
            ) {
                URL_SHELL_BLOCKLIST_REGEX.containsMatchIn(v)
            } else {
                containsShellMetacharacters(v)
            }
        }
        if (containsShellMetacharacters(normalizedName) || unsafeArg) {
            return@withContext ToolExecutionResult(
                toolName = normalizedName.ifBlank { "unknown" },
                status = ToolExecutionStatus.VALIDATION_ERROR,
                summary = "Blocked unsafe characters. JARVIS tools never execute shell commands."
            )
        }

        val tool = toolsByName[normalizedName]
            ?: return@withContext ToolExecutionResult(
                toolName = normalizedName.ifBlank { "unknown" },
                status = ToolExecutionStatus.VALIDATION_ERROR,
                summary = "Unknown tool '$normalizedName'. Only registered Android API tools can run."
            )

        val validation = tool.validate(request.arguments)
        val cleanArgs = when (validation) {
            is ToolValidationResult.Valid -> validation.normalizedArgs
            is ToolValidationResult.Invalid -> {
                return@withContext ToolExecutionResult(
                    toolName = tool.name,
                    status = ToolExecutionStatus.VALIDATION_ERROR,
                    summary = validation.reason,
                    requestArgs = request.arguments
                )
            }
        }

        if (activeCancelled.get()) {
            return@withContext ToolExecutionResult(
                toolName = tool.name,
                status = ToolExecutionStatus.CANCELLED,
                summary = "Tool execution '${tool.name}' cancelled before dispatch.",
                requestArgs = cleanArgs
            )
        }

        when (val permResult = permissionValidator.validatePermissions(context, tool, settings)) {
            PermissionValidationResult.Granted -> Unit
            is PermissionValidationResult.MissingRuntimePermission -> {
                return@withContext ToolExecutionResult(
                    toolName = tool.name,
                    status = ToolExecutionStatus.PERMISSION_DENIED,
                    summary = permResult.rationale,
                    missingRuntimePermission = permResult.permission,
                    requestArgs = cleanArgs
                )
            }

            is PermissionValidationResult.MissingSpecialAccess -> {
                return@withContext ToolExecutionResult(
                    toolName = tool.name,
                    status = ToolExecutionStatus.PERMISSION_DENIED,
                    summary = permResult.rationale,
                    missingSpecialAccess = permResult.accessType,
                    requestArgs = cleanArgs
                )
            }

            is PermissionValidationResult.OptInDisabled -> {
                return@withContext ToolExecutionResult(
                    toolName = tool.name,
                    status = ToolExecutionStatus.PERMISSION_DENIED,
                    summary = permResult.rationale,
                    disabledOptInSetting = permResult.settingKey,
                    requestArgs = cleanArgs
                )
            }
        }

        if (tool.requiresUserConfirmation && !userConfirmed) {
            val (confirmMsg, _) = tool.describeConfirmation(cleanArgs)
            return@withContext ToolExecutionResult(
                toolName = tool.name,
                status = ToolExecutionStatus.CONFIRMATION_REQUIRED,
                summary = confirmMsg,
                confirmationPrompt = confirmMsg,
                requestArgs = cleanArgs
            )
        }

        if (activeCancelled.get()) {
            return@withContext ToolExecutionResult(
                toolName = tool.name,
                status = ToolExecutionStatus.CANCELLED,
                summary = "Tool execution '${tool.name}' cancelled prior to Android API call.",
                requestArgs = cleanArgs
            )
        }

        return@withContext try {
            tool.execute(context, cleanArgs, settings, userConfirmed)
        } catch (t: Throwable) {
            ToolExecutionResult(
                toolName = tool.name,
                status = ToolExecutionStatus.FAILED,
                summary = "Android API error in ${tool.name}: ${t.localizedMessage ?: t.javaClass.simpleName}",
                requestArgs = cleanArgs
            )
        }
    }

    fun buildSystemToolCatalogPrompt(): String = buildString {
        appendLine("REGISTERED ANDROID TOOLS (invoke ONLY via [TOOL_CALL]{\"tool\":\"name\",\"args\":{...}}[/TOOL_CALL] when the user asks for device control):")
        for (tool in toolsByName.values) {
            val params = if (tool.parameters.isEmpty()) {
                "none"
            } else {
                tool.parameters.joinToString(", ") { spec ->
                    val opts = if (spec.allowedValues.isNotEmpty()) " (${spec.allowedValues.joinToString("|")})" else ""
                    val req = if (spec.required) "*" else ""
                    "${spec.name}$req$opts"
                }
            }
            val confirmTag = if (tool.requiresUserConfirmation) " [CONFIRM:${tool.riskLevel.name}]" else " [${tool.riskLevel.name}]"
            appendLine("- ${tool.name}($params)$confirmTag: ${tool.description}")
        }
    }.trim()

    fun buildOpenAiToolsJsonString(): String {
        val arr = org.json.JSONArray()
        for (tool in toolsByName.values) {
            val props = org.json.JSONObject()
            val req = org.json.JSONArray()
            for (spec in tool.parameters) {
                val p = org.json.JSONObject().apply {
                    put("type", if (spec.type == ToolParameterType.INTEGER) "integer" else "string")
                    put("description", spec.description)
                    if (spec.allowedValues.isNotEmpty()) {
                        put("enum", org.json.JSONArray(spec.allowedValues))
                    }
                }
                props.put(spec.name, p)
                if (spec.required) req.put(spec.name)
            }
            val fn = org.json.JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put(
                    "parameters",
                    org.json.JSONObject().apply {
                        put("type", "object")
                        put("properties", props)
                        put("required", req)
                    }
                )
            }
            arr.put(
                org.json.JSONObject().apply {
                    put("type", "function")
                    put("function", fn)
                }
            )
        }
        return arr.toString()
    }

    fun buildGeminiToolsJsonString(): String {
        val decls = org.json.JSONArray()
        for (tool in toolsByName.values) {
            val props = org.json.JSONObject()
            val req = org.json.JSONArray()
            for (spec in tool.parameters) {
                val p = org.json.JSONObject().apply {
                    put("type", if (spec.type == ToolParameterType.INTEGER) "INTEGER" else "STRING")
                    put("description", spec.description)
                    if (spec.allowedValues.isNotEmpty()) {
                        put("enum", org.json.JSONArray(spec.allowedValues))
                    }
                }
                props.put(spec.name, p)
                if (spec.required) req.put(spec.name)
            }
            decls.put(
                org.json.JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put(
                        "parameters",
                        org.json.JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", props)
                            put("required", req)
                        }
                    )
                }
            )
        }
        return org.json.JSONArray().put(
            org.json.JSONObject().put("functionDeclarations", decls)
        ).toString()
    }

    companion object {
        private val SHELL_BLOCKLIST_REGEX = Regex("([;&|`$]|\\$\\(|&&|\\|\\||\\bsu\\b|/system/bin/)")
        private val URL_SHELL_BLOCKLIST_REGEX = Regex("([;|`$]|\\$\\(|&&|\\|\\||\\bsu\\b|/system/bin/)")

        fun containsShellMetacharacters(input: String): Boolean =
            SHELL_BLOCKLIST_REGEX.containsMatchIn(input)

        fun parseModelToolCall(modelOutput: String): ToolCallRequest? {
            val tagMatch = Regex(
                """\[TOOL_CALL\]\s*(\{.*?\})\s*\[/TOOL_CALL\]""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).find(modelOutput)
            if (tagMatch != null) {
                return parseJsonToolPayload(tagMatch.groupValues[1], source = "AI Model")
            }

            val trimmed = modelOutput.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return parseJsonToolPayload(trimmed, source = "AI Model")
            }
            return null
        }

        fun parseDirectToolCommand(userInput: String): ToolCallRequest? {
            val trimmed = userInput.trim()
            if (!trimmed.startsWith("/tool ", ignoreCase = true)) return null
            val body = trimmed.removeRange(0, 6).trim()
            if (body.isEmpty()) return null

            if (body.startsWith("{")) {
                return parseJsonToolPayload(body, source = "Direct Command")
            }

            val tokens = tokenizeCommand(body)
            if (tokens.isEmpty()) return null
            val toolName = tokens.first().lowercase()
            val args = linkedMapOf<String, String>()
            for (token in tokens.drop(1)) {
                val eq = token.indexOf('=')
                if (eq > 0) {
                    val key = token.substring(0, eq).trim()
                    val value = token.substring(eq + 1).trim().trim('"')
                    args[key] = value
                }
            }
            return ToolCallRequest(toolName = toolName, arguments = args, source = "Direct Command")
        }

        private fun parseJsonToolPayload(jsonText: String, source: String): ToolCallRequest? {
            return runCatching {
                val obj = JSONObject(jsonText)
                // 1. Canonical {"tool":"name","args":{...}}
                val directTool = obj.optString("tool").trim().lowercase()
                if (directTool.isNotEmpty()) {
                    val argsObj = obj.optJSONObject("args") ?: JSONObject()
                    return@runCatching ToolCallRequest(
                        toolName = directTool,
                        arguments = jsonObjectToStringMap(argsObj),
                        source = source
                    )
                }

                // 2. OpenAI native {"tool_calls":[{"function":{"name":"...","arguments":"{...}"}}]}
                val toolCalls = obj.optJSONArray("tool_calls")
                    ?: obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    val fn = toolCalls.optJSONObject(0)?.optJSONObject("function")
                    val fnName = fn?.optString("name").orEmpty().trim().lowercase()
                    if (fnName.isNotEmpty()) {
                        val rawArgs = fn?.optString("arguments").orEmpty().trim().ifEmpty { "{}" }
                        val argsObj = runCatching { JSONObject(rawArgs) }.getOrDefault(JSONObject())
                        return@runCatching ToolCallRequest(
                            toolName = fnName,
                            arguments = jsonObjectToStringMap(argsObj),
                            source = source
                        )
                    }
                }

                // 3. Gemini native {"functionCall":{"name":"...","args":{...}}}
                val fnCall = obj.optJSONObject("functionCall")
                    ?: obj.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                        ?.optJSONObject(0)?.optJSONObject("functionCall")
                if (fnCall != null) {
                    val fnName = fnCall.optString("name").trim().lowercase()
                    if (fnName.isNotEmpty()) {
                        val argsObj = fnCall.optJSONObject("args") ?: JSONObject()
                        return@runCatching ToolCallRequest(
                            toolName = fnName,
                            arguments = jsonObjectToStringMap(argsObj),
                            source = source
                        )
                    }
                }

                null
            }.getOrNull()
        }

        private fun jsonObjectToStringMap(argsObj: JSONObject): Map<String, String> {
            val args = linkedMapOf<String, String>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                args[key] = argsObj.optString(key, argsObj.opt(key)?.toString().orEmpty())
            }
            return args
        }

        private fun tokenizeCommand(text: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            for (ch in text) {
                when {
                    ch == '"' -> {
                        inQuotes = !inQuotes
                        current.append(ch)
                    }
                    ch.isWhitespace() && !inQuotes -> {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.clear()
                        }
                    }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }
            return result
        }
    }
}
