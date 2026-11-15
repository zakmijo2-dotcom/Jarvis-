package com.example.core.tools

import android.content.Context
import com.example.core.device.AllowlistedCommandValidator
import com.example.core.device.DeviceCommandRequest
import com.example.core.device.DeviceCommandRouter
import com.example.core.device.PrivilegedActionId

class PrivilegedBridgeDiagnosticTool(
    private val routerProvider: () -> DeviceCommandRouter?
) : JarvisTool {
    override val name: String = "check_device_bridge"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String =
        "Check active privileged device-control capability (Shizuku, Wireless ADB, Native APIs, Accessibility fallback)."
    override val parameters: List<ToolParameterSpec> = emptyList()
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW

    override fun validate(args: Map<String, String>): ToolValidationResult =
        ToolValidationResult.Valid(emptyMap())

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val router = routerProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "DeviceCommandRouter unavailable.")
        val caps = router.capabilityManager.capabilities.value
        val pingRes = router.routeAndExecute(DeviceCommandRequest(PrivilegedActionId.PING_RUNTIME))
        val statusText = buildString {
            appendLine("Active Bridge: ${caps.privilegedBridgeSummary}")
            appendLine("Shizuku: ${caps.shizuku.availability.label} (installed=${caps.shizuku.isInstalled}, binder=${caps.shizuku.isBinderAlive}, perm=${caps.shizuku.hasPermission})")
            appendLine("Wireless ADB: ${caps.wirelessAdb.connectionState.label} (${caps.wirelessAdb.host}:${caps.wirelessAdb.connectPort ?: "-"})")
            appendLine("Accessibility Fallback: ${if (caps.accessibilityConnected) "Connected" else "Inactive"}")
            appendLine("Verified Runtime Probe: ${pingRes.verificationEvidence}")
        }.trim()

        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = statusText,
            verifiedFacts = mapOf(
                "preferredBackend" to caps.activePrivilegedBackend.label,
                "shizukuState" to caps.shizuku.availability.label,
                "wirelessAdbState" to caps.wirelessAdb.connectionState.label,
                "probeBackend" to pingRes.backendUsed.label
            )
        )
    }
}

class ForceStopAppTool(
    private val routerProvider: () -> DeviceCommandRouter?
) : JarvisTool {
    override val name: String = "force_stop_app"
    override val category: ToolCategory = ToolCategory.APPLICATION
    override val description: String =
        "Force-stop a non-critical third-party Android app via Shizuku or Wireless ADB after explicit confirmation."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "package_name",
            type = ToolParameterType.STRING,
            description = "Exact Android application package ID (e.g. com.example.app)."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = true
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.HIGH

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val pkg = args["package_name"]?.trim().orEmpty()
        if (!AllowlistedCommandValidator.isForceStopAllowedPackage(pkg)) {
            return ToolValidationResult.Invalid(
                "Package '$pkg' is invalid or a protected Android system package that JARVIS refuses to stop."
            )
        }
        return ToolValidationResult.Valid(mapOf("package_name" to pkg))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val pkg = args["package_name"].orEmpty()
        return "Confirm force-stopping '$pkg'?" to
            "JARVIS will execute allowlisted am force-stop via Shizuku (or Wireless ADB) and verify process termination. Unsaved app state may be lost."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val router = routerProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "DeviceCommandRouter unavailable.")
        val pkg = args.getValue("package_name")
        val result = router.routeAndExecute(
            DeviceCommandRequest(
                actionId = PrivilegedActionId.FORCE_STOP_APP,
                arg1 = pkg,
                userConfirmed = true
            )
        )
        return ToolExecutionResult(
            toolName = name,
            status = if (result.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = result.summary,
            verifiedFacts = mapOf(
                "backend" to result.backendUsed.label,
                "verified" to result.verified.toString(),
                "evidence" to result.verificationEvidence
            ),
            requestArgs = args
        )
    }
}

class TrimAppCachesTool(
    private val routerProvider: () -> DeviceCommandRouter?
) : JarvisTool {
    override val name: String = "trim_app_caches"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String =
        "Ask Android PackageManager (via Shizuku or Wireless ADB) to trim temporary app caches."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "target_mb",
            type = ToolParameterType.INTEGER,
            description = "Target free cache threshold in megabytes (64-4096).",
            required = false,
            minInt = 64,
            maxInt = 4096
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = true
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.MEDIUM

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val mb = args["target_mb"]?.trim()?.toIntOrNull() ?: 512
        if (mb !in 64..4096) {
            return ToolValidationResult.Invalid("Parameter 'target_mb' must be between 64 and 4096 MB.")
        }
        return ToolValidationResult.Valid(mapOf("target_mb" to mb.toString()))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val mb = args["target_mb"] ?: "512"
        return "Confirm requesting ${mb}MB app cache trim?" to
            "Executes allowlisted pm trim-caches via Shizuku or Wireless ADB without deleting app user data."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val router = routerProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "DeviceCommandRouter unavailable.")
        val mb = args["target_mb"] ?: "512"
        val result = router.routeAndExecute(
            DeviceCommandRequest(
                actionId = PrivilegedActionId.TRIM_APP_CACHES,
                arg1 = mb,
                userConfirmed = true
            )
        )
        return ToolExecutionResult(
            toolName = name,
            status = if (result.success) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = result.summary,
            verifiedFacts = mapOf(
                "backend" to result.backendUsed.label,
                "verified" to result.verified.toString(),
                "evidence" to result.verificationEvidence
            ),
            requestArgs = args
        )
    }
}
