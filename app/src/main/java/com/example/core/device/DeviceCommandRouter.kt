package com.example.core.device

import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Unified Device Control Router implementing strict capability-based priority routing:
 *   1. Shizuku (when available and authorized)
 *   2. Wireless ADB (when explicitly paired & verified connected)
 *   3. Native Android APIs (when sufficient for the action)
 *   4. Accessibility Service (limited fallback ONLY for UI actions or global fallback)
 */
class DeviceCommandRouter(
    val shizukuController: ShizukuController,
    val wirelessAdbController: WirelessAdbController,
    val nativeController: AndroidNativeController,
    val capabilityManager: DeviceCapabilityManager,
    val permissionManager: PermissionManager,
    private val settingsRepository: SettingsRepository? = null
) {
    private val _recentResults = MutableStateFlow<List<DeviceCommandResult>>(emptyList())
    val recentResults: StateFlow<List<DeviceCommandResult>> = _recentResults.asStateFlow()

    fun catalogSpecs(): List<DeviceCommandSpec> = PrivilegedActionId.entries.map { action ->
        DeviceCommandSpec(
            actionId = action,
            requiredCapability = action.supportedBackends.joinToString(" -> ") { it.label },
            requiredPermissions = action.requiredPermissionLabel,
            riskLevel = action.riskLevel,
            verificationMethod = action.verificationMethod
        )
    }

    fun resolvePreferredBackend(
        actionId: PrivilegedActionId,
        settings: JarvisSettings = JarvisSettings()
    ): DeviceControlBackend? {
        val shizukuStatus = shizukuController.refreshStatus()
        val adbStatus = wirelessAdbController.status.value
        val ordered = actionId.supportedBackends.sortedBy { it.priorityOrder }

        for (candidate in ordered) {
            val usable = when (candidate) {
                DeviceControlBackend.SHIZUKU ->
                    shizukuStatus.isBinderAlive && shizukuStatus.hasPermission
                DeviceControlBackend.WIRELESS_ADB ->
                    adbStatus.connectionState == WirelessAdbConnectionState.CONNECTED
                DeviceControlBackend.NATIVE_ANDROID -> true
                DeviceControlBackend.ACCESSIBILITY ->
                    permissionManager.isAccessibilityUsable(settings)
            }
            if (usable) return candidate
        }
        return null
    }

    suspend fun routeAndExecute(request: DeviceCommandRequest): DeviceCommandResult {
        val settings = settingsRepository?.let { runCatching { it.settingsFlow.first() }.getOrNull() }
            ?: JarvisSettings()

        if (request.actionId.requiresUserConfirmation && !request.userConfirmed) {
            return DeviceCommandResult(
                actionId = request.actionId,
                success = false,
                backendUsed = resolvePreferredBackend(request.actionId, settings)
                    ?: request.actionId.supportedBackends.first(),
                verified = false,
                verificationEvidence = "Blocked pending explicit user confirmation.",
                summary = "Confirmation required for ${request.actionId.title} (${request.actionId.riskLevel.label}).",
                requiresConfirmation = true
            )
        }

        val shizukuStatus = shizukuController.refreshStatus()
        val adbStatus = wirelessAdbController.status.value
        val ordered = request.actionId.supportedBackends.sortedBy { it.priorityOrder }

        var lastFailure: DeviceCommandResult? = null

        for (backend in ordered) {
            when (backend) {
                DeviceControlBackend.SHIZUKU -> {
                    if (shizukuStatus.isBinderAlive && shizukuStatus.hasPermission) {
                        val res = shizukuController.executeAllowlistedAction(request)
                        if (res.success) {
                            return record(res)
                        }
                        lastFailure = res
                    }
                }

                DeviceControlBackend.WIRELESS_ADB -> {
                    if (adbStatus.connectionState == WirelessAdbConnectionState.CONNECTED) {
                        val res = wirelessAdbController.executeAllowlistedAction(request)
                        if (res.success) {
                            return record(res)
                        }
                        lastFailure = res
                    }
                }

                DeviceControlBackend.NATIVE_ANDROID -> {
                    val res = nativeController.executeNativeAction(request)
                    if (res.success) {
                        return record(res)
                    }
                    lastFailure = res
                }

                DeviceControlBackend.ACCESSIBILITY -> {
                    if (permissionManager.isAccessibilityUsable(settings)) {
                        val res = nativeController.executeAccessibilityFallback(request.actionId)
                        if (res.success) {
                            return record(res)
                        }
                        lastFailure = res
                    }
                }
            }
        }

        val fallback = lastFailure ?: DeviceCommandResult(
            actionId = request.actionId,
            success = false,
            backendUsed = ordered.first(),
            verified = false,
            verificationEvidence = "No qualified backend (${ordered.joinToString { it.label }}) is active.",
            summary = "Cannot execute '${request.actionId.title}': configure Shizuku or Wireless ADB first.",
            errorReason = "Required capability unavailable"
        )
        return record(fallback)
    }

    private fun record(result: DeviceCommandResult): DeviceCommandResult {
        _recentResults.value = (listOf(result) + _recentResults.value).take(25)
        return result
    }
}
