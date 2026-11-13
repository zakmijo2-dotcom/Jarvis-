package com.example.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings

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
            "Free RAM ($availMb MB) is tight relative to artifact size ($modelMb MB); HyperOS may reclaim background apps."
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

    fun applyThermalAdaptation(
        settings: JarvisSettings,
        thermalCode: Int,
        thermalLabel: String
    ): ThermalAdaptationResult {
        return when {
            thermalCode >= PowerManager.THERMAL_STATUS_SEVERE -> {
                val cappedTokens = settings.maxOutputTokens.coerceAtMost(256)
                val adjustedBackend = if (thermalCode >= PowerManager.THERMAL_STATUS_CRITICAL &&
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
                    notice = "Thermal status '$thermalLabel': capped output budget to $cappedTokens tokens to protect SoC thermals."
                )
            }

            thermalCode == PowerManager.THERMAL_STATUS_MODERATE -> {
                val cappedTokens = settings.maxOutputTokens.coerceAtMost(384)
                val throttled = cappedTokens < settings.maxOutputTokens
                ThermalAdaptationResult(
                    effectiveSettings = settings.copy(maxOutputTokens = cappedTokens),
                    thermalStatusCode = thermalCode,
                    thermalStatusLabel = thermalLabel,
                    wasThrottled = throttled,
                    notice = if (throttled) "Thermal status 'Moderate': output capped to $cappedTokens tokens." else null
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
