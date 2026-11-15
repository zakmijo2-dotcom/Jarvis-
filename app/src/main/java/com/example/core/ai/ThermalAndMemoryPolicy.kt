package com.example.core.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings

data class BatteryThermalSnapshot(
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 0f,
    val thermalStatusCode: Int = -1,
    val thermalStatusLabel: String = "Nominal",
    val isPowerSaveMode: Boolean = false
)

data class MemoryHeadroomReport(
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val isLowMemory: Boolean,
    val canLoadModel: Boolean,
    val reason: String? = null
)

data class ThermalAdaptationResult(
    val effectiveSettings: JarvisSettings,
    val thermalStatusCode: Int,
    val thermalStatusLabel: String,
    val wasThrottled: Boolean,
    val notice: String? = null
)

object ThermalAndMemoryPolicy {
    private const val MIN_REQUIRED_FREE_RAM_BYTES = 384L * 1024L * 1024L

    fun assessMemoryHeadroom(
        context: Context,
        expectedModelBytes: Long
    ): MemoryHeadroomReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(info)
        } else {
            return MemoryHeadroomReport(
                availableRamBytes = 0L,
                totalRamBytes = 0L,
                isLowMemory = false,
                canLoadModel = true
            )
        }

        val avail = info.availMem
        val total = info.totalMem
        val availMb = avail / (1024L * 1024L)
        if (info.lowMemory || (avail in 1 until MIN_REQUIRED_FREE_RAM_BYTES)) {
            return MemoryHeadroomReport(
                availableRamBytes = avail,
                totalRamBytes = total,
                isLowMemory = true,
                canLoadModel = false,
                reason = "OS reported low RAM ($availMb MB free). Unload background apps or switch to Cloud/smaller model before initializing weights."
            )
        }

        val advisory = if (expectedModelBytes > 0L && avail > 0L && avail < expectedModelBytes) {
            val modelMb = expectedModelBytes / (1024L * 1024L)
            "Free RAM ($availMb MB) is tight relative to artifact size ($modelMb MB); OS may reclaim background apps."
        } else {
            null
        }

        return MemoryHeadroomReport(
            availableRamBytes = avail,
            totalRamBytes = total,
            isLowMemory = false,
            canLoadModel = true,
            reason = advisory
        )
    }

    fun readThermalStatus(context: Context): Pair<Int, String> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            runCatching { pm.currentThermalStatus }.getOrDefault(-1)
        } else {
            -1
        }
        val label = when (code) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unavailable"
        }
        return code to label
    }

    fun readBatterySnapshot(context: Context): BatteryThermalSnapshot {
        val (thermalCode, thermalLabel) = readThermalStatus(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = if (tempTenths > 0) tempTenths / 10f else 0f

        return BatteryThermalSnapshot(
            batteryPercent = pct,
            isCharging = charging,
            batteryTempCelsius = tempC,
            thermalStatusCode = thermalCode,
            thermalStatusLabel = if (thermalLabel == "Unavailable") "Nominal" else thermalLabel,
            isPowerSaveMode = isPowerSave
        )
    }

    fun applyThermalAdaptation(
        settings: JarvisSettings,
        thermalCode: Int,
        thermalLabel: String
    ): ThermalAdaptationResult = applyRuntimeAdaptation(
        settings = settings,
        snapshot = BatteryThermalSnapshot(
            thermalStatusCode = thermalCode,
            thermalStatusLabel = thermalLabel
        )
    )

    fun applyRuntimeAdaptation(
        settings: JarvisSettings,
        snapshot: BatteryThermalSnapshot
    ): ThermalAdaptationResult {
        val thermalCode = snapshot.thermalStatusCode
        val thermalLabel = snapshot.thermalStatusLabel
        val lowBatteryUnplugged = !snapshot.isCharging && snapshot.batteryPercent in 1..15
        val warmBattery = snapshot.batteryTempCelsius >= 43.0f

        return when {
            thermalCode >= PowerManager.THERMAL_STATUS_SEVERE || snapshot.batteryTempCelsius >= 45.0f -> {
                val cappedTokens = settings.maxOutputTokens.coerceAtMost(256)
                val adjustedBackend = if (
                    (thermalCode >= PowerManager.THERMAL_STATUS_CRITICAL || snapshot.batteryTempCelsius >= 45.0f) &&
                    settings.inferenceBackend == InferenceBackendPreference.AUTO_GPU_FALLBACK
                ) {
                    InferenceBackendPreference.CPU_ONLY
                } else {
                    settings.inferenceBackend
                }
                ThermalAdaptationResult(
                    effectiveSettings = settings.copy(
                        maxOutputTokens = cappedTokens,
                        inferenceBackend = adjustedBackend
                    ),
                    thermalStatusCode = thermalCode,
                    thermalStatusLabel = thermalLabel,
                    wasThrottled = true,
                    notice = "Thermal protection ('$thermalLabel', ${snapshot.batteryTempCelsius.toInt()}°C): capped output to $cappedTokens tokens."
                )
            }

            lowBatteryUnplugged || snapshot.isPowerSaveMode -> {
                val cappedTokens = settings.maxOutputTokens.coerceAtMost(320)
                val adjustedBackend = if (settings.inferenceBackend == InferenceBackendPreference.AUTO_GPU_FALLBACK) {
                    InferenceBackendPreference.CPU_ONLY
                } else {
                    settings.inferenceBackend
                }
                val throttled = cappedTokens < settings.maxOutputTokens || adjustedBackend != settings.inferenceBackend
                ThermalAdaptationResult(
                    effectiveSettings = settings.copy(
                        maxOutputTokens = cappedTokens,
                        inferenceBackend = adjustedBackend
                    ),
                    thermalStatusCode = thermalCode,
                    thermalStatusLabel = thermalLabel,
                    wasThrottled = throttled,
                    notice = if (throttled) {
                        "Battery saver active (${snapshot.batteryPercent}%): capped output to $cappedTokens tokens on low-power CPU path."
                    } else null
                )
            }

            thermalCode == PowerManager.THERMAL_STATUS_MODERATE || warmBattery -> {
                val cappedTokens = settings.maxOutputTokens.coerceAtMost(384)
                val throttled = cappedTokens < settings.maxOutputTokens
                ThermalAdaptationResult(
                    effectiveSettings = settings.copy(maxOutputTokens = cappedTokens),
                    thermalStatusCode = thermalCode,
                    thermalStatusLabel = thermalLabel,
                    wasThrottled = throttled,
                    notice = if (throttled) "Thermal status '$thermalLabel': output capped to $cappedTokens tokens." else null
                )
            }

            else -> ThermalAdaptationResult(
                effectiveSettings = settings,
                thermalStatusCode = thermalCode,
                thermalStatusLabel = thermalLabel,
                wasThrottled = false
            )
        }
    }
}
