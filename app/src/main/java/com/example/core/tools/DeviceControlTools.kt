package com.example.core.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.util.Locale

data class LaunchableAppEntry(
    val packageName: String,
    val label: String,
    val activityName: String
)

internal fun launchIntentSafely(context: Context, intent: Intent): Boolean {
    val prepared = Intent(intent).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val resolved = prepared.resolveActivity(context.packageManager) ?: return false
    return runCatching {
        context.startActivity(prepared)
        resolved.packageName.isNotBlank()
    }.getOrDefault(false)
}

class LaunchInstalledAppTool(
    private val appDiscoverer: (Context) -> List<LaunchableAppEntry> = ::queryLaunchableApps,
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely
) : JarvisTool {
    override val name: String = "launch_installed_app"
    override val category: ToolCategory = ToolCategory.APPLICATION
    override val description: String = "Launch an installed Android application by display name or package name."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "app_query",
            type = ToolParameterType.STRING,
            description = "Installed app display name or package ID (e.g., Settings, Calculator, com.android.settings)."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val query = args["app_query"]?.trim().orEmpty()
        if (query.length < 2 || query.length > 80) {
            return ToolValidationResult.Invalid("Parameter 'app_query' must be between 2 and 80 characters.")
        }
        return ToolValidationResult.Valid(mapOf("app_query" to query))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val query = args.getValue("app_query")
        val apps = appDiscoverer(context)
        val needle = query.lowercase(Locale.getDefault())

        val match = apps.firstOrNull { it.packageName.equals(needle, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.equals(needle, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.lowercase(Locale.getDefault()).contains(needle) }
            ?: apps.firstOrNull { it.packageName.lowercase(Locale.getDefault()).contains(needle) }

        if (match == null) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "No launchable app matched '$query'. Available launcher packages checked: ${apps.size}.",
                verifiedFacts = mapOf("queried" to query, "launchableCount" to apps.size.toString()),
                requestArgs = args
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(match.packageName, match.activityName)
            }

        val launched = intentLauncher(context, launchIntent)
        return if (launched) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Launched ${match.label} (${match.packageName}).",
                verifiedFacts = mapOf(
                    "appLabel" to match.label,
                    "packageName" to match.packageName,
                    "activity" to match.activityName
                ),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Resolved ${match.label} (${match.packageName}) but Android rejected or failed to start its launcher activity.",
                verifiedFacts = mapOf("packageName" to match.packageName),
                requestArgs = args
            )
        }
    }

    companion object {
        fun queryLaunchableApps(context: Context): List<LaunchableAppEntry> {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            return resolved.mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                    .ifBlank { activityInfo.packageName }
                LaunchableAppEntry(
                    packageName = activityInfo.packageName,
                    label = label,
                    activityName = activityInfo.name
                )
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }
}

class OpenAppScreenTool(
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely
) : JarvisTool {
    override val name: String = "open_app_screen"
    override val category: ToolCategory = ToolCategory.APPLICATION
    override val description: String = "Open a supported Android system/app screen (camera, dialer, alarms, app_details)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "screen",
            type = ToolParameterType.ENUM,
            description = "Target supported screen.",
            allowedValues = listOf("camera", "dialer", "alarms", "app_details")
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val screen = args["screen"]?.trim()?.lowercase().orEmpty()
        if (screen !in listOf("camera", "dialer", "alarms", "app_details")) {
            return ToolValidationResult.Invalid("Unsupported screen '$screen'. Choose: camera, dialer, alarms, or app_details.")
        }
        return ToolValidationResult.Valid(mapOf("screen" to screen))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val screen = args.getValue("screen")
        val intent = when (screen) {
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "dialer" -> Intent(Intent.ACTION_DIAL)
            "alarms" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "app_details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            else -> null
        } ?: return ToolExecutionResult(name, ToolExecutionStatus.VALIDATION_ERROR, "Unknown screen '$screen'.")

        val started = intentLauncher(context, intent)
        return if (started) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Opened supported screen: $screen.",
                verifiedFacts = mapOf("screen" to screen, "action" to (intent.action ?: "custom")),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "No installed handler could open screen '$screen' on this device.",
                verifiedFacts = mapOf("screen" to screen),
                requestArgs = args
            )
        }
    }
}

class GoHomeScreenTool(
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely,
    private val accessibilityRunner: (AuthorizedAccessibilityAction) -> Boolean = AccessibilityBridge::performAuthorizedGlobalAction
) : JarvisTool {
    override val name: String = "go_home_screen"
    override val category: ToolCategory = ToolCategory.APPLICATION
    override val description: String = "Return to the Android home screen using AccessibilityService (if enabled) or Launcher Home intent."
    override val parameters: List<ToolParameterSpec> = emptyList()
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult =
        ToolValidationResult.Valid(emptyMap())

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        if (AccessibilityBridge.isServiceConnected.value) {
            val performed = accessibilityRunner(AuthorizedAccessibilityAction.HOME)
            if (performed) {
                return ToolExecutionResult(
                    toolName = name,
                    status = ToolExecutionStatus.SUCCESS,
                    summary = "Returned to home screen via authorized AccessibilityService.",
                    verifiedFacts = mapOf("method" to "AccessibilityService.GLOBAL_ACTION_HOME")
                )
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val launched = intentLauncher(context, homeIntent)
        return if (launched) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Returned to home screen via Android CATEGORY_HOME launcher intent.",
                verifiedFacts = mapOf("method" to "Intent.CATEGORY_HOME")
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Unable to resolve a Home launcher activity on this environment."
            )
        }
    }
}

class OpenAndroidSettingsTool(
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely
) : JarvisTool {
    override val name: String = "open_android_settings"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String = "Open a specific Android Settings page (wifi, bluetooth, sound, display, battery, notifications, accessibility, apps, main)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "page",
            type = ToolParameterType.ENUM,
            description = "Target Settings page.",
            allowedValues = SUPPORTED_PAGES.keys.toList()
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val page = args["page"]?.trim()?.lowercase().orEmpty()
        if (!SUPPORTED_PAGES.containsKey(page)) {
            return ToolValidationResult.Invalid(
                "Unsupported settings page '$page'. Valid pages: ${SUPPORTED_PAGES.keys.joinToString(", ")}."
            )
        }
        return ToolValidationResult.Valid(mapOf("page" to page))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val page = args.getValue("page")
        val action = SUPPORTED_PAGES.getValue(page)
        val started = intentLauncher(context, Intent(action))
        return if (started) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Opened Android '$page' settings ($action).",
                verifiedFacts = mapOf("page" to page, "settingsAction" to action),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Android Settings page '$page' ($action) could not be resolved on this device.",
                verifiedFacts = mapOf("page" to page, "settingsAction" to action),
                requestArgs = args
            )
        }
    }

    companion object {
        val SUPPORTED_PAGES: Map<String, String> = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
            "notifications" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "main" to Settings.ACTION_SETTINGS
        )
    }
}

class ReadBatteryStatusTool(
    private val androidTools: AndroidTools
) : JarvisTool {
    override val name: String = "read_battery_status"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String = "Read live battery charge percentage, charging source, temperature, voltage, and thermal status."
    override val parameters: List<ToolParameterSpec> = emptyList()
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult =
        ToolValidationResult.Valid(emptyMap())

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val telemetry = androidTools.readDeviceTelemetry()
        val levelLabel = if (telemetry.batteryPercent >= 0) "${telemetry.batteryPercent}%" else "Unavailable"
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = "Battery: $levelLabel (${telemetry.batteryStatus}, ${telemetry.batteryPlugged}), ${telemetry.batteryTempCelsius}°C, thermal=${telemetry.thermalStatusLabel}.",
            verifiedFacts = mapOf(
                "batteryPercent" to levelLabel,
                "status" to telemetry.batteryStatus,
                "powerSource" to telemetry.batteryPlugged,
                "tempCelsius" to String.format(Locale.US, "%.1f°C", telemetry.batteryTempCelsius),
                "voltageMv" to "${telemetry.batteryVoltageMv} mV",
                "thermal" to telemetry.thermalStatusLabel
            )
        )
    }
}

class ReadDeviceInfoTool(
    private val androidTools: AndroidTools
) : JarvisTool {
    override val name: String = "read_device_info"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String = "Read hardware model, SoC, OS version, RAM/storage usage, thermal, and active network state."
    override val parameters: List<ToolParameterSpec> = emptyList()
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult =
        ToolValidationResult.Valid(emptyMap())

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val telemetry = androidTools.readDeviceTelemetry()
        val usedRamGb = (telemetry.totalRamBytes - telemetry.availRamBytes) / 1_073_741_824f
        val totalRamGb = telemetry.totalRamBytes / 1_073_741_824f
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = androidTools.formatTelemetrySummary(telemetry),
            verifiedFacts = mapOf(
                "device" to "${telemetry.manufacturer} ${telemetry.model}",
                "soc" to telemetry.socModel,
                "os" to "Android ${telemetry.androidRelease} (API ${telemetry.sdkInt})",
                "ramUsed" to String.format(Locale.US, "%.2f / %.2f GB", usedRamGb, totalRamGb),
                "network" to telemetry.networkTransport
            )
        )
    }
}

class AdjustMediaVolumeTool : JarvisTool {
    override val name: String = "adjust_media_volume"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String = "Read or adjust Android media stream volume by percentage (0-100) or step direction (up, down, mute, unmute)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "action",
            type = ToolParameterType.ENUM,
            description = "Volume adjustment mode.",
            required = false,
            allowedValues = listOf("up", "down", "mute", "unmute", "set")
        ),
        ToolParameterSpec(
            name = "percent",
            type = ToolParameterType.INTEGER,
            description = "Target volume percent (0-100) when action='set' or specified directly.",
            required = false,
            minInt = 0,
            maxInt = 100
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val rawPercent = args["percent"]?.trim()?.takeIf { it.isNotEmpty() }
        val rawAction = args["action"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: if (rawPercent != null) "set" else "up"

        if (rawAction !in listOf("up", "down", "mute", "unmute", "set")) {
            return ToolValidationResult.Invalid("Invalid volume action '$rawAction'. Use up, down, mute, unmute, or set.")
        }
        val normalized = linkedMapOf("action" to rawAction)
        if (rawAction == "set" || rawPercent != null) {
            val pct = rawPercent?.toIntOrNull()
                ?: return ToolValidationResult.Invalid("Parameter 'percent' (0..100) is required when action='set'.")
            if (pct !in 0..100) {
                return ToolValidationResult.Invalid("Parameter 'percent' must be between 0 and 100.")
            }
            normalized["percent"] = pct.toString()
        }
        return ToolValidationResult.Valid(normalized)
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "AudioManager service unavailable.")

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val beforeVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val action = args.getValue("action")

        when (action) {
            "up" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            "down" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            "mute" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            "unmute" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                AudioManager.FLAG_SHOW_UI
            )
            "set" -> {
                val pct = args.getValue("percent").toInt()
                val targetIndex = ((pct / 100f) * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
            }
        }

        val afterVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val afterPct = ((afterVol * 100f) / maxVol).toInt().coerceIn(0, 100)
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = "Media volume ($action) verified at $afterVol/$maxVol ($afterPct%, previously $beforeVol/$maxVol).",
            verifiedFacts = mapOf(
                "previousVolume" to "$beforeVol/$maxVol",
                "currentVolume" to "$afterVol/$maxVol",
                "currentPercent" to "$afterPct%",
                "isStreamMute" to audioManager.isStreamMute(AudioManager.STREAM_MUSIC).toString()
            ),
            requestArgs = args
        )
    }
}

class ControlMediaPlaybackTool : JarvisTool {
    override val name: String = "control_media_playback"
    override val category: ToolCategory = ToolCategory.DEVICE
    override val description: String = "Send standard Android media transport key events (play_pause, next, previous, stop)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "command",
            type = ToolParameterType.ENUM,
            description = "Media transport command.",
            allowedValues = listOf("play_pause", "next", "previous", "stop")
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val command = args["command"]?.trim()?.lowercase().orEmpty()
        if (command !in listOf("play_pause", "next", "previous", "stop")) {
            return ToolValidationResult.Invalid("Unsupported media command '$command'. Use play_pause, next, previous, or stop.")
        }
        return ToolValidationResult.Valid(mapOf("command" to command))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "AudioManager service unavailable.")

        val command = args.getValue("command")
        val keyCode = when (command) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return ToolExecutionResult(name, ToolExecutionStatus.VALIDATION_ERROR, "Unsupported command.")
        }

        val now = System.currentTimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0))

        val isMusicActiveAfter = audioManager.isMusicActive
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = "Dispatched Android media key '$command' to active media session (musicActive=$isMusicActiveAfter).",
            verifiedFacts = mapOf(
                "command" to command,
                "keyCode" to keyCode.toString(),
                "isMusicActive" to isMusicActiveAfter.toString()
            ),
            requestArgs = args
        )
    }
}
