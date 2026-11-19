package com.example.core.device

import com.example.core.tools.ToolRiskLevel

enum class DeviceControlBackend(val label: String, val priorityOrder: Int) {
    SHIZUKU("Shizuku", 1),
    WIRELESS_ADB("Wireless ADB", 2),
    NATIVE_ANDROID("Native Android API", 3),
    ACCESSIBILITY("Accessibility Fallback", 4)
}

enum class PrivilegedActionId(
    val wireId: String,
    val title: String,
    val description: String,
    val supportedBackends: List<DeviceControlBackend>,
    val riskLevel: ToolRiskLevel,
    val requiresUserConfirmation: Boolean,
    val requiredPermissionLabel: String,
    val verificationMethod: String
) {
    PING_RUNTIME(
        wireId = "ping_runtime",
        title = "Verify Privileged Bridge Identity",
        description = "Check active UID/PID and privileged execution capability.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB, DeviceControlBackend.NATIVE_ANDROID),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku / Wireless ADB or App Process",
        verificationMethod = "Reads live process UID/PID identity response."
    ),
    READ_SYSTEM_PROPERTIES(
        wireId = "read_system_properties",
        title = "Inspect Android Build & Runtime State",
        description = "Read OS build fingerprint, ABI, uptime, and security patch via privileged bridge or native Build APIs.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB, DeviceControlBackend.NATIVE_ANDROID),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku / Wireless ADB (native fallback available)",
        verificationMethod = "Verifies non-empty system property payload."
    ),
    SET_MEDIA_VOLUME(
        wireId = "set_media_volume",
        title = "Set Media Volume",
        description = "Set Android STREAM_MUSIC volume percentage (0-100) with before/after readback.",
        supportedBackends = listOf(DeviceControlBackend.NATIVE_ANDROID),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "AudioManager (No runtime permission required)",
        verificationMethod = "Reads back actual STREAM_MUSIC index after update."
    ),
    NAV_HOME(
        wireId = "nav_home",
        title = "Navigate Home",
        description = "Return to the device home screen via Shizuku, Wireless ADB, Native Home Intent, or Accessibility.",
        supportedBackends = listOf(
            DeviceControlBackend.SHIZUKU,
            DeviceControlBackend.WIRELESS_ADB,
            DeviceControlBackend.NATIVE_ANDROID,
            DeviceControlBackend.ACCESSIBILITY
        ),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku / Wireless ADB / Home Intent / Accessibility fallback",
        verificationMethod = "Verifies dispatch exit code or intent resolution."
    ),
    NAV_BACK(
        wireId = "nav_back",
        title = "Navigate Back",
        description = "Send Android BACK key event via Shizuku, Wireless ADB, or Accessibility fallback.",
        supportedBackends = listOf(
            DeviceControlBackend.SHIZUKU,
            DeviceControlBackend.WIRELESS_ADB,
            DeviceControlBackend.ACCESSIBILITY
        ),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku / Wireless ADB / Accessibility fallback",
        verificationMethod = "Verifies KEYCODE_BACK exit code 0 or Accessibility boolean result."
    ),
    NAV_RECENTS(
        wireId = "nav_recents",
        title = "Open Recent Apps",
        description = "Open the Android Overview / Recents switcher via Shizuku, Wireless ADB, or Accessibility fallback.",
        supportedBackends = listOf(
            DeviceControlBackend.SHIZUKU,
            DeviceControlBackend.WIRELESS_ADB,
            DeviceControlBackend.ACCESSIBILITY
        ),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku / Wireless ADB / Accessibility fallback",
        verificationMethod = "Verifies KEYCODE_APP_SWITCH exit code 0 or Accessibility boolean result."
    ),
    FORCE_STOP_APP(
        wireId = "force_stop_app",
        title = "Force-Stop Background App",
        description = "Force-stop a non-critical third-party package via Shizuku or Wireless ADB.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB),
        riskLevel = ToolRiskLevel.HIGH,
        requiresUserConfirmation = true,
        requiredPermissionLabel = "Shizuku API or Paired Wireless ADB",
        verificationMethod = "Validates target package exists, skips critical system packages, and checks pidof after stop."
    ),
    TRIM_APP_CACHES(
        wireId = "trim_app_caches",
        title = "Trim App Cache Storage",
        description = "Ask Android PackageManager to reclaim temporary app caches up to a safe target threshold.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB),
        riskLevel = ToolRiskLevel.MEDIUM,
        requiresUserConfirmation = true,
        requiredPermissionLabel = "Shizuku API or Paired Wireless ADB",
        verificationMethod = "Compares free internal data storage bytes before and after pm trim-caches."
    ),
    UI_SCREEN_FALLBACK(
        wireId = "ui_screen_fallback",
        title = "Screen Semantic UI Interaction",
        description = "Inspect/tap/scroll/input on arbitrary third-party UI hierarchies (Accessibility Service only).",
        supportedBackends = listOf(DeviceControlBackend.ACCESSIBILITY),
        riskLevel = ToolRiskLevel.HIGH,
        requiresUserConfirmation = true,
        requiredPermissionLabel = "Accessibility Automation Opt-in + System Accessibility Service",
        verificationMethod = "Captures before/after UI hierarchy hash and verifies state transition."
    ),
    INPUT_TAP(
        wireId = "input_tap",
        title = "Inject Screen Tap",
        description = "Dispatch a bounded screen tap (x,y) via Shizuku or Wireless ADB shell input.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB),
        riskLevel = ToolRiskLevel.MEDIUM,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku API or Paired Wireless ADB",
        verificationMethod = "Verifies input tap exit code 0 and post-action screen transition."
    ),
    INPUT_SWIPE(
        wireId = "input_swipe",
        title = "Inject Screen Swipe / Long-Press",
        description = "Dispatch a bounded swipe gesture (x1,y1,x2,y2,durationMs) via Shizuku or Wireless ADB.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB),
        riskLevel = ToolRiskLevel.MEDIUM,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku API or Paired Wireless ADB",
        verificationMethod = "Verifies input swipe exit code 0 and post-action screen transition."
    ),
    INPUT_TEXT(
        wireId = "input_text",
        title = "Inject ASCII Text Input",
        description = "Type sanitized non-shell text into the currently focused field via Shizuku or Wireless ADB.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB),
        riskLevel = ToolRiskLevel.MEDIUM,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku API or Paired Wireless ADB",
        verificationMethod = "Verifies input text exit code 0."
    ),
    QUERY_TOP_APP(
        wireId = "query_top_app",
        title = "Inspect Active Foreground App",
        description = "Read current resumed activity / top package via dumpsys activity top/window.",
        supportedBackends = listOf(DeviceControlBackend.SHIZUKU, DeviceControlBackend.WIRELESS_ADB),
        riskLevel = ToolRiskLevel.LOW,
        requiresUserConfirmation = false,
        requiredPermissionLabel = "Shizuku API or Paired Wireless ADB",
        verificationMethod = "Parses foreground package name from Android WindowManager output."
    )
}

data class DeviceCommandRequest(
    val actionId: PrivilegedActionId,
    val arg1: String = "",
    val arg2: String = "",
    val userConfirmed: Boolean = false
)

data class DeviceCommandSpec(
    val actionId: PrivilegedActionId,
    val requiredCapability: String,
    val requiredPermissions: String,
    val riskLevel: ToolRiskLevel,
    val verificationMethod: String
)

data class DeviceCommandResult(
    val actionId: PrivilegedActionId,
    val success: Boolean,
    val backendUsed: DeviceControlBackend,
    val verified: Boolean,
    val verificationEvidence: String,
    val summary: String,
    val rawOutput: String = "",
    val errorReason: String? = null,
    val requiresConfirmation: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class ShizukuAvailabilityState(val label: String) {
    NOT_INSTALLED("Shizuku App Not Installed"),
    INSTALLED_SERVICE_STOPPED("Installed (Service Stopped)"),
    PERMISSION_REQUIRED("Permission Required"),
    PERMISSION_DENIED("Permission Denied"),
    CONNECTED_UNBOUND("Authorized (Binding Service...)"),
    READY("Shizuku Ready")
}

data class ShizukuStatusSnapshot(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val hasPermission: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val isUserServiceBound: Boolean = false,
    val shizukuVersion: Int = -1,
    val serverUid: Int = -1,
    val isRootBacked: Boolean = false,
    val availability: ShizukuAvailabilityState = ShizukuAvailabilityState.NOT_INSTALLED,
    val lastError: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

enum class WirelessAdbConnectionState(val label: String) {
    UNCONFIGURED("Not Configured"),
    DISCOVERING("Discovering mDNS Services"),
    PAIRING("Pairing via TLS + SPAKE2"),
    PAIRED_DISCONNECTED("Paired (Disconnected)"),
    CONNECTING("Connecting TLS Session"),
    CONNECTED("Connected & Verified"),
    ERROR("Connection Failed")
}

data class WirelessAdbStatusSnapshot(
    val connectionState: WirelessAdbConnectionState = WirelessAdbConnectionState.UNCONFIGURED,
    val isPaired: Boolean = false,
    val host: String = "127.0.0.1",
    val connectPort: Int? = null,
    val lastPairingPort: Int? = null,
    val discoveredConnectPorts: List<Int> = emptyList(),
    val discoveredPairingPorts: List<Int> = emptyList(),
    val isMdnsDiscoveryActive: Boolean = false,
    val verifiedIdentity: String? = null,
    val lastVerifiedAtMs: Long? = null,
    val lastError: String? = null,
    val troubleshootingHint: String? = null
)

data class DeviceCapabilitySnapshot(
    val shizuku: ShizukuStatusSnapshot = ShizukuStatusSnapshot(),
    val wirelessAdb: WirelessAdbStatusSnapshot = WirelessAdbStatusSnapshot(),
    val hasContactsPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasPostNotificationsPermission: Boolean = true,
    val notificationListenerAuthorized: Boolean = false,
    val notificationListenerConnected: Boolean = false,
    val accessibilityAuthorized: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val accessibilityOptInEnabled: Boolean = false
) {
    val activePrivilegedBackend: DeviceControlBackend
        get() = when {
            shizuku.availability == ShizukuAvailabilityState.READY ||
                (shizuku.isBinderAlive && shizuku.hasPermission) -> DeviceControlBackend.SHIZUKU
            wirelessAdb.connectionState == WirelessAdbConnectionState.CONNECTED -> DeviceControlBackend.WIRELESS_ADB
            else -> DeviceControlBackend.NATIVE_ANDROID
        }

    val privilegedBridgeSummary: String
        get() = when (activePrivilegedBackend) {
            DeviceControlBackend.SHIZUKU -> if (shizuku.isRootBacked) "Shizuku (UID 0)" else "Shizuku (ADB Shell)"
            DeviceControlBackend.WIRELESS_ADB -> "Wireless ADB (${wirelessAdb.host}:${wirelessAdb.connectPort ?: "?"})"
            DeviceControlBackend.NATIVE_ANDROID -> "Native Android APIs"
            DeviceControlBackend.ACCESSIBILITY -> "Accessibility Fallback"
        }
}
