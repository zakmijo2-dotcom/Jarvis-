package com.example.core.automation

import android.content.Context
import com.example.core.tools.AuthorizedAccessibilityAction
import com.example.core.tools.JarvisTool
import com.example.core.tools.ToolCategory
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolParameterSpec
import com.example.core.tools.ToolParameterType
import com.example.core.tools.ToolPermissionRequirement
import com.example.core.tools.ToolRiskLevel
import com.example.core.tools.ToolValidationResult
import com.example.data.repository.JarvisSettings

class WaitForScreenTool(
    private val automationEngineProvider: () -> PhoneAutomationEngine?
) : JarvisTool {
    override val name = "wait_for_screen"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Wait deterministically until the screen finishes loading, text appears, package changes, or UI updates."
    override val parameters = listOf(
        ToolParameterSpec(
            name = "condition",
            type = ToolParameterType.ENUM,
            description = "Wait condition: not_loading, text_appears, package_changes, or screen_changes",
            required = false,
            allowedValues = listOf("not_loading", "text_appears", "package_changes", "screen_changes")
        ),
        ToolParameterSpec(
            name = "value",
            type = ToolParameterType.STRING,
            description = "Target text or package substring for text_appears/package_changes",
            required = false
        ),
        ToolParameterSpec(
            name = "timeout_ms",
            type = ToolParameterType.INTEGER,
            description = "Maximum wait time in milliseconds (500-15000, default 4000)",
            required = false,
            minInt = 500,
            maxInt = 15000
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val cond = args["condition"]?.trim()?.lowercase()?.ifEmpty { "not_loading" } ?: "not_loading"
        if (cond !in setOf("not_loading", "text_appears", "package_changes", "screen_changes")) {
            return ToolValidationResult.Invalid("Invalid condition '$cond'.")
        }
        val timeout = args["timeout_ms"]?.trim()?.toLongOrNull()?.coerceIn(500L, 15_000L) ?: 4000L
        return ToolValidationResult.Valid(
            mapOf(
                "condition" to cond,
                "value" to args["value"].orEmpty().trim().take(80),
                "timeout_ms" to timeout.toString()
            )
        )
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(context: Context, args: Map<String, String>, settings: JarvisSettings): ToolExecutionResult {
        val engine = automationEngineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "PhoneAutomationEngine unavailable.", requestArgs = args)
        val cond = WaitScreenCondition.fromWire(args["condition"])
        val value = args["value"].orEmpty()
        val timeout = args["timeout_ms"]?.toLongOrNull() ?: 4000L
        val res = engine.waitUntilCondition(cond, value, timeout, settings = settings)
        return ToolExecutionResult(
            toolName = name,
            status = if (res.conditionMet) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = res.summary,
            verifiedFacts = buildMap {
                put("condition", cond.wireName)
                put("condition_met", res.conditionMet.toString())
                put("elapsed_ms", res.elapsedMs.toString())
                res.latestObservation?.activePackageName?.let { put("active_package", it) }
                res.latestClassification?.screenType?.label?.let { put("screen_type", it) }
            },
            requestArgs = args
        )
    }
}

class LocateAndTapVisualTool(
    private val automationEngineProvider: () -> PhoneAutomationEngine?
) : JarvisTool {
    override val name = "locate_and_tap_visual"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Find a UI element by label using Accessibility tree first with ML Kit OCR visual fallback, then tap via Shizuku/ADB/Accessibility."
    override val parameters = listOf(
        ToolParameterSpec(
            name = "query",
            type = ToolParameterType.STRING,
            description = "Visible button/link text or label to locate and tap",
            required = true
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.MEDIUM

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return ToolValidationResult.Invalid("query is required.")
        return ToolValidationResult.Valid(mapOf("query" to query.take(80)))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(context: Context, args: Map<String, String>, settings: JarvisSettings): ToolExecutionResult {
        val engine = automationEngineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "PhoneAutomationEngine unavailable.", requestArgs = args)
        val (match, res) = engine.locateAndTapElement(args["query"].orEmpty(), settings)
        return ToolExecutionResult(
            toolName = name,
            status = if (res.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = res.summary,
            verifiedFacts = buildMap {
                put("backend", res.backendUsed.label)
                put("verified", res.verified.toString())
                put("screen_changed", res.screenChanged.toString())
                if (match != null) {
                    put("locator_source", match.source.label)
                    put("tap_x", match.centerX.toString())
                    put("tap_y", match.centerY.toString())
                }
            },
            requestArgs = args
        )
    }
}

class NavigateDeviceTool(
    private val automationEngineProvider: () -> PhoneAutomationEngine?
) : JarvisTool {
    override val name = "navigate_device"
    override val category = ToolCategory.DEVICE
    override val description = "Navigate system controls (back, home, recents, notifications) prioritized via Shizuku -> Wireless ADB -> Accessibility."
    override val parameters = listOf(
        ToolParameterSpec(
            name = "action",
            type = ToolParameterType.ENUM,
            description = "Navigation action: back, home, recents, or notifications",
            required = true,
            allowedValues = listOf("back", "home", "recents", "notifications")
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val action = args["action"]?.trim()?.lowercase().orEmpty()
        if (action !in setOf("back", "home", "recents", "notifications")) {
            return ToolValidationResult.Invalid("action must be one of: back, home, recents, notifications.")
        }
        return ToolValidationResult.Valid(mapOf("action" to action))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(context: Context, args: Map<String, String>, settings: JarvisSettings): ToolExecutionResult {
        val engine = automationEngineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "PhoneAutomationEngine unavailable.", requestArgs = args)
        val act = AuthorizedAccessibilityAction.fromKey(args["action"].orEmpty())
            ?: return ToolExecutionResult(name, ToolExecutionStatus.VALIDATION_ERROR, "Unsupported action.", requestArgs = args)
        val res = engine.navigate(act, settings)
        return ToolExecutionResult(
            toolName = name,
            status = if (res.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = res.summary,
            verifiedFacts = mapOf(
                "action" to act.actionKey,
                "backend" to res.backendUsed.label,
                "verified" to res.verified.toString()
            ),
            requestArgs = args
        )
    }
}
