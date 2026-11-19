package com.example.core.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.core.automation.PhoneAutomationEngine
import com.example.core.mcp.McpManager
import com.example.core.security.ActionRiskTier
import com.example.core.security.AppBlocklistPolicy
import com.example.core.security.ScreenRiskClassifier
import com.example.core.vision.ScreenPerceptionEngine
import com.example.core.web.PublicHttpSafetyGuard
import com.example.core.web.PublicHttpValidationResult
import com.example.core.web.WebPageExtractor
import com.example.core.web.WebResearchConfig
import com.example.core.web.WebResearchService
import com.example.data.repository.JarvisSettings

/**
 * On-demand Screen UI Hierarchy + Offline ML Kit OCR inspection tool.
 * Enforces AppBlocklistPolicy and password redaction.
 */
class CaptureScreenOcrTool(
    private val perceptionEngineProvider: () -> ScreenPerceptionEngine?
) : JarvisTool {
    override val name = "capture_screen_ocr"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Inspect active UI hierarchy with (x,y) coordinates and run offline ML Kit OCR when text nodes are sparse."
    override val parameters = emptyList<ToolParameterSpec>()
    override val permissionRequirements = listOf(
        ToolPermissionRequirement.SpecialAccess(
            SpecialAccessType.ACCESSIBILITY_SERVICE,
            "Accessibility Service must be enabled and toggled on in Device Controls to inspect screen UI & OCR."
        )
    )
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.MEDIUM

    override fun validate(args: Map<String, String>) = ToolValidationResult.Valid(emptyMap())

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        val engine = perceptionEngineProvider() ?: ScreenPerceptionEngine(context)
        val obs = engine.observeScreen(
            includeScreenshot = false,
            includeOcr = true,
            customBlockedPackagesCsv = settings.blockedPackagesCsv
        )
        if (obs.blockedByPolicy) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = obs.blockReason ?: "Screen blocked by AppBlocklistPolicy."
            )
        }
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = obs.toPromptContextSummary(includeOcr = true),
            verifiedFacts = mapOf(
                "package" to (obs.activePackageName ?: "unknown"),
                "elements" to obs.elements.size.toString(),
                "stabilityHash" to obs.stabilityHash,
                "ocrSucceeded" to (obs.ocrResult?.succeeded == true).toString()
            )
        )
    }
}

/**
 * On-demand Visual + OCR screenshot capture tool (API 30+ Accessibility or MediaProjection fallback).
 */
class CaptureScreenVisualTool(
    private val perceptionEngineProvider: () -> ScreenPerceptionEngine?
) : JarvisTool {
    override val name = "capture_screen_visual"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Capture one-shot compressed screenshot + OCR + UI coordinates for multimodal screen understanding."
    override val parameters = emptyList<ToolParameterSpec>()
    override val permissionRequirements = listOf(
        ToolPermissionRequirement.SpecialAccess(
            SpecialAccessType.ACCESSIBILITY_SERVICE,
            "Accessibility Service must be authorized to capture screen context."
        )
    )
    override val requiresUserConfirmation = true
    override val riskLevel = ToolRiskLevel.HIGH

    override fun validate(args: Map<String, String>) = ToolValidationResult.Valid(emptyMap())

    override fun describeConfirmation(args: Map<String, String>) =
        "Capture current screen visual & OCR?" to
            "One-shot screenshot capture checks AppBlocklistPolicy first and only shares images with Cloud AI if Cloud Upload Consent is active."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        val engine = perceptionEngineProvider() ?: ScreenPerceptionEngine(context)
        val obs = engine.observeScreen(
            includeScreenshot = true,
            includeOcr = true,
            customBlockedPackagesCsv = settings.blockedPackagesCsv
        )
        if (obs.blockedByPolicy) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = obs.blockReason ?: "Screenshot capture blocked by AppBlocklistPolicy."
            )
        }
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = obs.toPromptContextSummary(includeOcr = true),
            verifiedFacts = mapOf(
                "package" to (obs.activePackageName ?: "unknown"),
                "captureSource" to obs.captureSource.name,
                "screenshotEncoded" to (obs.screenshotBase64Jpeg != null).toString(),
                "dimensions" to "${obs.screenshotWidth}x${obs.screenshotHeight}",
                "stabilityHash" to obs.stabilityHash
            )
        )
    }
}

class TapCoordinatesTool(
    private val automationEngineProvider: () -> PhoneAutomationEngine? = { null }
) : JarvisTool {
    override val name = "tap_coordinates"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Tap exact screen coordinates (x, y) via Shizuku -> Wireless ADB -> Accessibility gesture dispatch after verifying active window safety."
    override val parameters = listOf(
        ToolParameterSpec("x", ToolParameterType.INTEGER, "Horizontal pixel coordinate (>=0)", required = true, minInt = 0, maxInt = 10000),
        ToolParameterSpec("y", ToolParameterType.INTEGER, "Vertical pixel coordinate (>=0)", required = true, minInt = 0, maxInt = 10000)
    )
    override val permissionRequirements = listOf(
        ToolPermissionRequirement.SpecialAccess(
            SpecialAccessType.ACCESSIBILITY_SERVICE,
            "Accessibility Service must be enabled for gesture dispatch."
        )
    )
    override val requiresUserConfirmation = true
    override val riskLevel = ToolRiskLevel.HIGH

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val x = args["x"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Missing or invalid integer 'x'.")
        val y = args["y"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Missing or invalid integer 'y'.")
        if (x < 0 || y < 0) return ToolValidationResult.Invalid("Coordinates x and y must be non-negative.")
        return ToolValidationResult.Valid(mapOf("x" to x.toString(), "y" to y.toString()))
    }

    override fun describeConfirmation(args: Map<String, String>) =
        "Tap screen at (${args["x"]}, ${args["y"]})?" to
            "Dispatches a verified tap gesture at the specified screen coordinate."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        val x = args.getValue("x").toInt()
        val y = args.getValue("y").toInt()
        val activePkg = AccessibilityBridge.getLastObservedPackageName()
        if (AppBlocklistPolicy.isPackageBlocked(activePkg, settings.blockedPackagesCsv)) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Blocked by AppBlocklistPolicy in package '$activePkg'."
            )
        }
        val risk = ScreenRiskClassifier.assessAction(
            activePackage = activePkg,
            targetLabelOrQuery = "$x,$y",
            screenTextSample = null,
            toolName = name,
            customBlockedCsv = settings.blockedPackagesCsv
        )
        if (risk.tier == ActionRiskTier.BLOCKED) {
            return ToolExecutionResult(name, ToolExecutionStatus.FAILED, risk.reason)
        }
        val automationEngine = automationEngineProvider()
        if (automationEngine != null) {
            val res = automationEngine.tap(x, y, settings)
            return ToolExecutionResult(
                toolName = name,
                status = if (res.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
                summary = res.summary,
                verifiedFacts = mapOf(
                    "x" to x.toString(),
                    "y" to y.toString(),
                    "backend" to res.backendUsed.label,
                    "stateChangedVerified" to res.screenChanged.toString()
                ),
                requestArgs = args
            )
        }
        val controller = AccessibilityController(context)
        val verification = controller.executeCoordinateTapWithVerification(x, y, longPress = false)
        return ToolExecutionResult(
            toolName = name,
            status = if (verification.actionDispatched) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = if (verification.actionDispatched) {
                "Tapped ($x, $y). ${verification.evidence}"
            } else {
                "Coordinate tap at ($x, $y) failed to dispatch."
            },
            verifiedFacts = mapOf(
                "x" to x.toString(),
                "y" to y.toString(),
                "stateChangedVerified" to verification.stateChangedVerified.toString()
            ),
            requestArgs = args
        )
    }
}

class LongPressCoordinatesTool(
    private val automationEngineProvider: () -> PhoneAutomationEngine? = { null }
) : JarvisTool {
    override val name = "long_press_coordinates"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Long-press at screen coordinates (x, y) via Shizuku -> Wireless ADB -> Accessibility gesture dispatch."
    override val parameters = listOf(
        ToolParameterSpec("x", ToolParameterType.INTEGER, "Horizontal pixel coordinate (>=0)", required = true, minInt = 0, maxInt = 10000),
        ToolParameterSpec("y", ToolParameterType.INTEGER, "Vertical pixel coordinate (>=0)", required = true, minInt = 0, maxInt = 10000)
    )
    override val permissionRequirements = listOf(
        ToolPermissionRequirement.SpecialAccess(
            SpecialAccessType.ACCESSIBILITY_SERVICE,
            "Accessibility Service must be enabled for gesture dispatch."
        )
    )
    override val requiresUserConfirmation = true
    override val riskLevel = ToolRiskLevel.HIGH

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val x = args["x"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Missing or invalid integer 'x'.")
        val y = args["y"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Missing or invalid integer 'y'.")
        if (x < 0 || y < 0) return ToolValidationResult.Invalid("Coordinates must be non-negative.")
        return ToolValidationResult.Valid(mapOf("x" to x.toString(), "y" to y.toString()))
    }

    override fun describeConfirmation(args: Map<String, String>) =
        "Long-press at (${args["x"]}, ${args["y"]})?" to
            "Dispatches a sustained long-press gesture at the specified point."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        val x = args.getValue("x").toInt()
        val y = args.getValue("y").toInt()
        val activePkg = AccessibilityBridge.getLastObservedPackageName()
        if (AppBlocklistPolicy.isPackageBlocked(activePkg, settings.blockedPackagesCsv)) {
            return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "Blocked by AppBlocklistPolicy in '$activePkg'.")
        }
        val automationEngine = automationEngineProvider()
        if (automationEngine != null) {
            val res = automationEngine.longPress(x, y, settings = settings)
            return ToolExecutionResult(
                toolName = name,
                status = if (res.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
                summary = res.summary,
                verifiedFacts = mapOf("backend" to res.backendUsed.label, "verified" to res.verified.toString()),
                requestArgs = args
            )
        }
        val verification = AccessibilityController(context).executeCoordinateTapWithVerification(x, y, longPress = true)
        return ToolExecutionResult(
            toolName = name,
            status = if (verification.actionDispatched) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = if (verification.actionDispatched) {
                "Long-pressed ($x, $y). ${verification.evidence}"
            } else {
                "Long-press at ($x, $y) failed to dispatch."
            },
            requestArgs = args
        )
    }
}

class SwipeScreenTool(
    private val automationEngineProvider: () -> PhoneAutomationEngine? = { null }
) : JarvisTool {
    override val name = "swipe_screen"
    override val category = ToolCategory.ACCESSIBILITY
    override val description = "Swipe from (start_x, start_y) to (end_x, end_y) over duration_ms via Shizuku -> Wireless ADB -> Accessibility."
    override val parameters = listOf(
        ToolParameterSpec("start_x", ToolParameterType.INTEGER, "Start X coordinate", required = true, minInt = 0, maxInt = 10000),
        ToolParameterSpec("start_y", ToolParameterType.INTEGER, "Start Y coordinate", required = true, minInt = 0, maxInt = 10000),
        ToolParameterSpec("end_x", ToolParameterType.INTEGER, "End X coordinate", required = true, minInt = 0, maxInt = 10000),
        ToolParameterSpec("end_y", ToolParameterType.INTEGER, "End Y coordinate", required = true, minInt = 0, maxInt = 10000),
        ToolParameterSpec("duration_ms", ToolParameterType.INTEGER, "Swipe duration in ms", required = false, minInt = 100, maxInt = 1200)
    )
    override val permissionRequirements = listOf(
        ToolPermissionRequirement.SpecialAccess(
            SpecialAccessType.ACCESSIBILITY_SERVICE,
            "Accessibility Service must be enabled for swipe gestures."
        )
    )
    override val requiresUserConfirmation = true
    override val riskLevel = ToolRiskLevel.HIGH

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val sx = args["start_x"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Invalid start_x.")
        val sy = args["start_y"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Invalid start_y.")
        val ex = args["end_x"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Invalid end_x.")
        val ey = args["end_y"]?.toIntOrNull() ?: return ToolValidationResult.Invalid("Invalid end_y.")
        val dur = args["duration_ms"]?.toIntOrNull()?.coerceIn(100, 1200) ?: 280
        if (sx < 0 || sy < 0 || ex < 0 || ey < 0) return ToolValidationResult.Invalid("Coordinates must be >= 0.")
        return ToolValidationResult.Valid(
            mapOf(
                "start_x" to sx.toString(),
                "start_y" to sy.toString(),
                "end_x" to ex.toString(),
                "end_y" to ey.toString(),
                "duration_ms" to dur.toString()
            )
        )
    }

    override fun describeConfirmation(args: Map<String, String>) =
        "Swipe from (${args["start_x"]},${args["start_y"]}) to (${args["end_x"]},${args["end_y"]})?" to
            "Dispatches a custom swipe gesture on screen."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        val sx = args.getValue("start_x").toInt()
        val sy = args.getValue("start_y").toInt()
        val ex = args.getValue("end_x").toInt()
        val ey = args.getValue("end_y").toInt()
        val dur = args["duration_ms"]?.toLongOrNull() ?: 280L
        val activePkg = AccessibilityBridge.getLastObservedPackageName()
        if (AppBlocklistPolicy.isPackageBlocked(activePkg, settings.blockedPackagesCsv)) {
            return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "Blocked by AppBlocklistPolicy in '$activePkg'.")
        }
        val automationEngine = automationEngineProvider()
        if (automationEngine != null) {
            val res = automationEngine.swipe(sx, sy, ex, ey, dur, settings)
            return ToolExecutionResult(
                toolName = name,
                status = if (res.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
                summary = res.summary,
                verifiedFacts = mapOf("backend" to res.backendUsed.label, "verified" to res.verified.toString()),
                requestArgs = args
            )
        }
        val verification = AccessibilityController(context).executeSwipeWithVerification(sx, sy, ex, ey, dur)
        return ToolExecutionResult(
            toolName = name,
            status = if (verification.actionDispatched) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = if (verification.actionDispatched) "Swipe completed. ${verification.evidence}" else "Swipe failed to dispatch.",
            requestArgs = args
        )
    }
}

class OpenUrlTool : JarvisTool {
    override val name = "open_url"
    override val category = ToolCategory.APPLICATION
    override val description = "Open a public https/http URL in the Android browser (private/SSRF addresses blocked)."
    override val parameters = listOf(
        ToolParameterSpec("url", ToolParameterType.STRING, "Public URL starting with https:// or http://", required = true)
    )
    override val permissionRequirements = emptyList<ToolPermissionRequirement>()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val url = args["url"].orEmpty().trim()
        if (url.isBlank()) return ToolValidationResult.Invalid("Missing 'url' parameter.")
        val lower = url.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return ToolValidationResult.Invalid("URL must start with https:// or http://.")
        }
        return ToolValidationResult.Valid(mapOf("url" to url))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val url = args.getValue("url")
        val check = PublicHttpSafetyGuard.validateUrl(url, allowLoopback = false)
        if (check is PublicHttpValidationResult.Blocked) {
            return ToolExecutionResult(name, ToolExecutionStatus.FAILED, check.reason)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Opened URL in Android browser: $url",
                verifiedFacts = mapOf("url" to url)
            )
        }.getOrElse {
            ToolExecutionResult(name, ToolExecutionStatus.FAILED, "Unable to launch browser for $url: ${it.localizedMessage}")
        }
    }
}

class WebSearchTool(
    private val webResearchProvider: () -> WebResearchService?
) : JarvisTool {
    override val name = "web_search"
    override val category = ToolCategory.MEMORY
    override val description = "Search the live web using configured provider (Brave, SerpAPI, Tavily, Serper, Wikipedia, or Keyless fallback)."
    override val parameters = listOf(
        ToolParameterSpec("query", ToolParameterType.STRING, "Search query string", required = true)
    )
    override val permissionRequirements = emptyList<ToolPermissionRequirement>()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val q = args["query"].orEmpty().trim()
        if (q.length < 2) return ToolValidationResult.Invalid("Query must be at least 2 characters.")
        return ToolValidationResult.Valid(mapOf("query" to q))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        val service = webResearchProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "WebResearchService unavailable.")
        val config = WebResearchConfig(
            enabled = settings.webResearchEnabled,
            provider = settings.webSearchProvider,
            apiKey = settings.webSearchApiKey,
            customEndpointUrl = settings.webSearchCustomUrl,
            timeoutSeconds = settings.cloudTimeoutSeconds.coerceIn(5, 30),
            braveApiKey = settings.braveSearchApiKey,
            serpApiKey = settings.serpApiKey
        )
        val res = service.search(args.getValue("query"), config)
        return res.fold(
            onSuccess = { report ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = report.formatContextPrompt(),
                    verifiedFacts = mapOf(
                        "provider" to report.providerLabel,
                        "hits" to report.results.size.toString()
                    )
                )
            },
            onFailure = { err ->
                ToolExecutionResult(name, ToolExecutionStatus.FAILED, err.localizedMessage ?: "Web search failed.")
            }
        )
    }
}

class FetchWebpageTool(
    private val extractorProvider: () -> WebPageExtractor?
) : JarvisTool {
    override val name = "fetch_webpage"
    override val category = ToolCategory.MEMORY
    override val description = "Fetch and extract clean readable text and JSON-LD summary from a public webpage (SSRF-protected)."
    override val parameters = listOf(
        ToolParameterSpec("url", ToolParameterType.STRING, "Public webpage URL", required = true)
    )
    override val permissionRequirements = emptyList<ToolPermissionRequirement>()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val url = args["url"].orEmpty().trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return ToolValidationResult.Invalid("URL must start with https:// or http://.")
        }
        return ToolValidationResult.Valid(mapOf("url" to url))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        execute(context, args, JarvisSettings())

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult {
        if (!settings.webResearchEnabled) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.PERMISSION_DENIED,
                summary = "Web research toggle is disabled in Settings.",
                disabledOptInSetting = "webResearchEnabled"
            )
        }
        val extractor = extractorProvider() ?: WebPageExtractor()
        val res = extractor.fetchReadablePage(args.getValue("url"))
        return res.fold(
            onSuccess = { extract ->
                ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = extract.toSanitizedContextBlock(),
                    verifiedFacts = mapOf(
                        "title" to extract.title,
                        "durationMs" to extract.durationMs.toString(),
                        "chars" to extract.readableText.length.toString()
                    )
                )
            },
            onFailure = { err ->
                ToolExecutionResult(name, ToolExecutionStatus.FAILED, err.localizedMessage ?: "Webpage fetch failed.")
            }
        )
    }
}

class McpCallTool(
    private val mcpManagerProvider: () -> McpManager?
) : JarvisTool {
    override val name = "mcp_call"
    override val category = ToolCategory.APPLICATION
    override val description = "Invoke a tool exposed by an explicitly enabled Model Context Protocol (MCP) server."
    override val parameters = listOf(
        ToolParameterSpec("tool_name", ToolParameterType.STRING, "MCP remote tool name", required = true),
        ToolParameterSpec("arguments_json", ToolParameterType.STRING, "JSON object string of arguments", required = false),
        ToolParameterSpec("server_name", ToolParameterType.STRING, "Optional MCP server name", required = false)
    )
    override val permissionRequirements = emptyList<ToolPermissionRequirement>()
    override val requiresUserConfirmation = false
    override val riskLevel = ToolRiskLevel.MEDIUM

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val toolName = args["tool_name"].orEmpty().trim()
        if (toolName.isBlank()) return ToolValidationResult.Invalid("Missing 'tool_name'.")
        return ToolValidationResult.Valid(
            mapOf(
                "tool_name" to toolName,
                "arguments_json" to args["arguments_json"].orEmpty().ifBlank { "{}" },
                "server_name" to args["server_name"].orEmpty().trim()
            )
        )
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult =
        ToolExecutionResult(name, ToolExecutionStatus.FAILED, "Use executeWithSettings.")

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings
    ): ToolExecutionResult = execute(context, args, settings, userConfirmed = false)

    override suspend fun execute(
        context: Context,
        args: Map<String, String>,
        settings: JarvisSettings,
        userConfirmed: Boolean
    ): ToolExecutionResult {
        val mgr = mcpManagerProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "MCP Manager is unavailable.")
        val res = mgr.invokeTool(
            serverNameOrId = args["server_name"]?.ifBlank { null },
            toolName = args.getValue("tool_name"),
            argumentsJson = args["arguments_json"].orEmpty(),
            userConfirmed = userConfirmed
        )
        return res.fold(
            onSuccess = { out ->
                ToolExecutionResult(
                    toolName = name,
                    status = if (out.isError) ToolExecutionStatus.FAILED else ToolExecutionStatus.SUCCESS,
                    summary = out.textContent,
                    verifiedFacts = mapOf("mcpTool" to out.toolName, "isError" to out.isError.toString())
                )
            },
            onFailure = { err ->
                val msg = err.localizedMessage ?: "MCP tool call failed."
                if (msg.startsWith("CONFIRM_REQUIRED:")) {
                    ToolExecutionResult(
                        toolName = name,
                        status = ToolExecutionStatus.CONFIRMATION_REQUIRED,
                        summary = msg.removePrefix("CONFIRM_REQUIRED:").trim(),
                        confirmationPrompt = msg.removePrefix("CONFIRM_REQUIRED:").trim(),
                        requestArgs = args
                    )
                } else {
                    ToolExecutionResult(name, ToolExecutionStatus.FAILED, msg)
                }
            }
        )
    }
}
