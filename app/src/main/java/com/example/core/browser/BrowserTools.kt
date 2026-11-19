package com.example.core.browser

import android.content.Context
import com.example.core.tools.JarvisTool
import com.example.core.tools.ToolCategory
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolParameterSpec
import com.example.core.tools.ToolParameterType
import com.example.core.tools.ToolPermissionRequirement
import com.example.core.tools.ToolRiskLevel
import com.example.core.tools.ToolValidationResult
import org.json.JSONObject

class BrowserOpenTool(
    private val engineProvider: () -> BrowserAutomationEngine?
) : JarvisTool {
    override val name = "browser_open"
    override val category = ToolCategory.APPLICATION
    override val description = "Open a public HTTP/HTTPS webpage inside JARVIS stateful browser session, returning page text, indexed links, and forms."
    override val parameters = listOf(
        ToolParameterSpec(name = "url", type = ToolParameterType.STRING, description = "Public webpage URL to open", required = true),
        ToolParameterSpec(name = "new_tab", type = ToolParameterType.ENUM, description = "Open in a new tab (true/false)", required = false, allowedValues = listOf("true", "false"))
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val url = args["url"]?.trim().orEmpty()
        if (url.length < 4) return ToolValidationResult.Invalid("url is required.")
        return ToolValidationResult.Valid(
            mapOf(
                "url" to url,
                "new_tab" to (args["new_tab"]?.trim()?.lowercase() ?: "false")
            )
        )
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = engineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "BrowserAutomationEngine unavailable.", requestArgs = args)
        val res = engine.openUrl(args["url"].orEmpty(), newTab = args["new_tab"] == "true")
        return res.fold(
            onSuccess = { snap ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = engine.formatPageSummary(snap),
                    verifiedFacts = mapOf(
                        "url" to snap.url,
                        "title" to snap.title.take(80),
                        "links_count" to snap.links.size.toString(),
                        "forms_count" to snap.forms.size.toString(),
                        "active_tab" to (engine.activeTabId.value?.toString() ?: "1")
                    ),
                    requestArgs = args
                )
            },
            onFailure = { err ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.FAILED,
                    summary = "Failed to open URL: ${err.localizedMessage ?: err.javaClass.simpleName}",
                    requestArgs = args
                )
            }
        )
    }
}

class BrowserReadPageTool(
    private val engineProvider: () -> BrowserAutomationEngine?
) : JarvisTool {
    override val name = "browser_read"
    override val category = ToolCategory.APPLICATION
    override val description = "Read the active browser tab's extracted text, indexed links, forms, and structured JSON-LD metadata."
    override val parameters = listOf(
        ToolParameterSpec(name = "max_chars", type = ToolParameterType.INTEGER, description = "Max text characters (400-4000, default 2000)", required = false, minInt = 400, maxInt = 4000)
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val max = args["max_chars"]?.toIntOrNull()?.coerceIn(400, 4000) ?: 2000
        return ToolValidationResult.Valid(mapOf("max_chars" to max.toString()))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = engineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "BrowserAutomationEngine unavailable.", requestArgs = args)
        val snap = engine.getCurrentPage()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "No active page open. Use browser_open first.", requestArgs = args)
        val max = args["max_chars"]?.toIntOrNull() ?: 2000
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = engine.formatPageSummary(snap, maxTextChars = max),
            verifiedFacts = mapOf("url" to snap.url, "title" to snap.title.take(80)),
            requestArgs = args
        )
    }
}

class BrowserClickLinkTool(
    private val engineProvider: () -> BrowserAutomationEngine?
) : JarvisTool {
    override val name = "browser_click_link"
    override val category = ToolCategory.APPLICATION
    override val description = "Follow a link on the currently open browser page by numeric index or matching link text."
    override val parameters = listOf(
        ToolParameterSpec(name = "index", type = ToolParameterType.INTEGER, description = "Numeric link index from browser_open/browser_read", required = false),
        ToolParameterSpec(name = "text", type = ToolParameterType.STRING, description = "Link text substring to match if index is omitted", required = false)
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val idx = args["index"]?.trim()?.toIntOrNull()
        val txt = args["text"]?.trim().orEmpty()
        if (idx == null && txt.isEmpty()) {
            return ToolValidationResult.Invalid("Provide either link 'index' or 'text'.")
        }
        return ToolValidationResult.Valid(
            buildMap {
                if (idx != null) put("index", idx.toString())
                if (txt.isNotEmpty()) put("text", txt.take(80))
            }
        )
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = engineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "BrowserAutomationEngine unavailable.", requestArgs = args)
        val res = engine.clickLink(index = args["index"]?.toIntOrNull(), textQuery = args["text"])
        return res.fold(
            onSuccess = { snap ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = engine.formatPageSummary(snap),
                    verifiedFacts = mapOf("url" to snap.url, "title" to snap.title.take(80)),
                    requestArgs = args
                )
            },
            onFailure = { err ->
                ToolExecutionResult(name, ToolExecutionStatus.FAILED, err.localizedMessage ?: "Failed to click link.", requestArgs = args)
            }
        )
    }
}

class BrowserFillFormTool(
    private val engineProvider: () -> BrowserAutomationEngine?
) : JarvisTool {
    override val name = "browser_fill_form"
    override val category = ToolCategory.APPLICATION
    override val description = "Fill and submit a form on the active browser page using form_index and JSON key-value fields."
    override val parameters = listOf(
        ToolParameterSpec(name = "form_index", type = ToolParameterType.INTEGER, description = "1-based form index (default 1)", required = false, minInt = 1, maxInt = 20),
        ToolParameterSpec(name = "fields_json", type = ToolParameterType.STRING, description = "JSON object mapping form input names to values", required = true)
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = true
    override val riskLevel = ToolRiskLevel.MEDIUM

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> =
        "Submit web form #${args["form_index"] ?: "1"}?" to
            "This transmits form field values to the website shown in your active browser tab."

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val rawJson = args["fields_json"]?.trim().orEmpty()
        if (rawJson.isEmpty()) return ToolValidationResult.Invalid("fields_json is required.")
        val parsed = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return ToolValidationResult.Invalid("fields_json must be a valid JSON object.")
        if (parsed.length() == 0) return ToolValidationResult.Invalid("fields_json must contain at least one field.")
        val formIdx = args["form_index"]?.toIntOrNull()?.coerceIn(1, 20) ?: 1
        return ToolValidationResult.Valid(mapOf("form_index" to formIdx.toString(), "fields_json" to rawJson))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = engineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "BrowserAutomationEngine unavailable.", requestArgs = args)
        val formIdx = args["form_index"]?.toIntOrNull() ?: 1
        val obj = JSONObject(args["fields_json"] ?: "{}")
        val fieldMap = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            fieldMap[k] = obj.optString(k)
        }
        val res = engine.fillAndSubmitForm(formIdx, fieldMap)
        return res.fold(
            onSuccess = { snap ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = engine.formatPageSummary(snap),
                    verifiedFacts = mapOf("url" to snap.url, "http_status" to snap.httpStatus.toString()),
                    requestArgs = args
                )
            },
            onFailure = { err ->
                ToolExecutionResult(name, ToolExecutionStatus.FAILED, err.localizedMessage ?: "Form submission failed.", requestArgs = args)
            }
        )
    }
}

class BrowserBackTool(
    private val engineProvider: () -> BrowserAutomationEngine?
) : JarvisTool {
    override val name = "browser_back"
    override val category = ToolCategory.APPLICATION
    override val description = "Navigate back to the previous webpage in the active browser tab."
    override val parameters: List<ToolParameterSpec> = emptyList()
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>) = ToolValidationResult.Valid(emptyMap())

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = engineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "BrowserAutomationEngine unavailable.", requestArgs = args)
        return engine.navigateBack().fold(
            onSuccess = { snap ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = engine.formatPageSummary(snap),
                    verifiedFacts = mapOf("url" to snap.url, "title" to snap.title.take(80)),
                    requestArgs = args
                )
            },
            onFailure = { err ->
                ToolExecutionResult(name, ToolExecutionStatus.FAILED, err.localizedMessage ?: "Cannot go back.", requestArgs = args)
            }
        )
    }
}

class BrowserTabsTool(
    private val engineProvider: () -> BrowserAutomationEngine?
) : JarvisTool {
    override val name = "browser_tabs"
    override val category = ToolCategory.APPLICATION
    override val description = "List, switch, or close open tabs in the stateful JARVIS browser session."
    override val parameters = listOf(
        ToolParameterSpec(name = "action", type = ToolParameterType.ENUM, description = "list, switch, or close", required = false, allowedValues = listOf("list", "switch", "close")),
        ToolParameterSpec(name = "tab_id", type = ToolParameterType.INTEGER, description = "Tab ID for switch or close", required = false)
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val act = args["action"]?.trim()?.lowercase()?.ifEmpty { "list" } ?: "list"
        if (act !in setOf("list", "switch", "close")) {
            return ToolValidationResult.Invalid("action must be list, switch, or close.")
        }
        val tabId = args["tab_id"]?.trim()?.toIntOrNull()
        if ((act == "switch" || act == "close") && tabId == null) {
            return ToolValidationResult.Invalid("tab_id is required for '$act'.")
        }
        return ToolValidationResult.Valid(
            buildMap {
                put("action", act)
                if (tabId != null) put("tab_id", tabId.toString())
            }
        )
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = engineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "BrowserAutomationEngine unavailable.", requestArgs = args)
        val act = args["action"] ?: "list"
        val tabId = args["tab_id"]?.toIntOrNull()
        return when (act) {
            "switch" -> {
                val tab = engine.switchTab(tabId!!)
                    ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "Tab #$tabId not found.", requestArgs = args)
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = "Switched to tab #${tab.tabId}: ${tab.title} (${tab.currentUrl})",
                    verifiedFacts = mapOf("active_tab" to tab.tabId.toString(), "url" to tab.currentUrl),
                    requestArgs = args
                )
            }
            "close" -> {
                val ok = engine.closeTab(tabId!!)
                ToolExecutionResult(
                    toolName = name,
                    status = if (ok) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
                    summary = if (ok) "Closed browser tab #$tabId." else "Tab #$tabId not found.",
                    requestArgs = args
                )
            }
            else -> {
                val tabs = engine.tabs.value
                val activeId = engine.activeTabId.value
                val summary = if (tabs.isEmpty()) {
                    "No open browser tabs."
                } else {
                    tabs.joinToString("\n") { t ->
                        val marker = if (t.tabId == activeId) "*" else " "
                        "[$marker #${t.tabId}] ${t.title.take(50)} — ${t.currentUrl}"
                    }
                }
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = summary,
                    verifiedFacts = mapOf("tab_count" to tabs.size.toString()),
                    requestArgs = args
                )
            }
        }
    }
}
