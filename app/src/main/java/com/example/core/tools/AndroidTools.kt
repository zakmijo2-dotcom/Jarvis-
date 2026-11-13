package com.example.core.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class DeviceTelemetry(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val hardware: String,
    val socModel: String,
    val supportedAbis: String,
    val cpuCoreCount: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildDisplay: String,
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val lowMemory: Boolean,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val batteryPercent: Int,
    val batteryStatus: String,
    val batteryPlugged: String,
    val batteryTempCelsius: Float,
    val batteryVoltageMv: Int,
    val batteryHealth: String,
    val thermalStatusLabel: String,
    val thermalCode: Int,
    val isNetworkConnected: Boolean,
    val networkTransport: String,
    val isNetworkValidated: Boolean,
    val isNetworkMetered: Boolean,
    val downstreamBandwidthKbps: Int,
    val capturedAtMillis: Long
)

interface AndroidTools {
    suspend fun readDeviceTelemetry(): DeviceTelemetry
    fun formatTelemetrySummary(telemetry: DeviceTelemetry): String
}

class AndroidSystemTools(
    context: Context
) : AndroidTools {

    private val appContext = context.applicationContext

    override suspend fun readDeviceTelemetry(): DeviceTelemetry = withContext(Dispatchers.IO) {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val totalStorage = statFs?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
        val freeStorage = statFs?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L

        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val batteryPercent = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1

        val statusCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val batteryStatus = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val pluggedCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val batteryPlugged = when (pluggedCode) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempCelsius = tempTenths / 10f
        val batteryVoltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val healthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val batteryHealth = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Nominal"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            runCatching { powerManager.currentThermalStatus }.getOrDefault(-1)
        } else {
            -1
        }
        val thermalLabel = when (thermalCode) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unavailable"
        }

        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivity?.activeNetwork
        val caps = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
        val isMetered = !(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: true)
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular / 5G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Active Link"
        }
        val downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }

        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "N/A"
        }

        DeviceTelemetry(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase(Locale.getDefault()) },
            model = Build.MODEL,
            brand = Build.BRAND.replaceFirstChar { it.uppercase(Locale.getDefault()) },
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            socModel = socModel,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = securityPatch,
            buildDisplay = Build.DISPLAY,
            totalRamBytes = memInfo.totalMem,
            availRamBytes = memInfo.availMem,
            lowMemory = memInfo.lowMemory,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            batteryPercent = batteryPercent,
            batteryStatus = batteryStatus,
            batteryPlugged = batteryPlugged,
            batteryTempCelsius = batteryTempCelsius,
            batteryVoltageMv = batteryVoltageMv,
            batteryHealth = batteryHealth,
            thermalStatusLabel = thermalLabel,
            thermalCode = thermalCode,
            isNetworkConnected = isConnected,
            networkTransport = transport,
            isNetworkValidated = isValidated,
            isNetworkMetered = isMetered,
            downstreamBandwidthKbps = downstreamKbps,
            capturedAtMillis = System.currentTimeMillis()
        )
    }

    override fun formatTelemetrySummary(telemetry: DeviceTelemetry): String {
        val usedRamGb = (telemetry.totalRamBytes - telemetry.availRamBytes) / (1024f * 1024f * 1024f)
        val totalRamGb = telemetry.totalRamBytes / (1024f * 1024f * 1024f)
        val freeStorageGb = telemetry.freeStorageBytes / (1024f * 1024f * 1024f)
        val totalStorageGb = telemetry.totalStorageBytes / (1024f * 1024f * 1024f)
        return buildString {
            appendLine("DEVICE TELEMETRY SNAPSHOT")
            appendLine("• Hardware: ${telemetry.manufacturer} ${telemetry.model} (${telemetry.socModel})")
            appendLine("• OS: Android ${telemetry.androidRelease} (API ${telemetry.sdkInt}) • ${telemetry.cpuCoreCount} CPU threads")
            appendLine(String.format(Locale.US, "• RAM: %.2f / %.2f GB used", usedRamGb, totalRamGb))
            appendLine(String.format(Locale.US, "• Storage: %.1f GB free / %.1f GB total", freeStorageGb, totalStorageGb))
            appendLine("• Battery: ${if (telemetry.batteryPercent >= 0) "${telemetry.batteryPercent}%" else "N/A"} (${telemetry.batteryStatus}, ${telemetry.batteryTempCelsius}°C)")
            appendLine("• Thermal: ${telemetry.thermalStatusLabel} • Network: ${telemetry.networkTransport}")
        }.trim()
    }
}
