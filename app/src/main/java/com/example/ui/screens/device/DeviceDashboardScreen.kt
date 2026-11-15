package com.example.ui.screens.device

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.tools.DeviceTelemetry
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.components.TelemetryProgressBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Surface
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceDashboardScreen(
    uiState: DeviceTelemetryUiState,
    onRefreshNow: () -> Unit,
    onToggleAutoPolling: () -> Unit,
    controlsState: DeviceControlsState = DeviceControlsState(),
    onSelectTab: (DeviceDashboardTab) -> Unit = {},
    onRunTool: (String, Map<String, String>) -> Unit = { _, _ -> },
    onConfirmPendingTool: () -> Unit = {},
    onCancelPendingTool: () -> Unit = {},
    onCancelActiveTool: () -> Unit = {},
    onDismissPermissionPrompt: () -> Unit = {},
    onRuntimePermissionResult: (String, Boolean) -> Unit = { _, _ -> },
    onSetNotificationReaderEnabled: (Boolean) -> Unit = {},
    onSetNotificationPrivacyMaskEnabled: (Boolean) -> Unit = {},
    onClearNotificationBuffer: () -> Unit = {},
    onSetAccessibilityAutomationEnabled: (Boolean) -> Unit = {},
    onSetCloudUploadConsentGranted: (Boolean) -> Unit = {},
    onOpenBridgeSetup: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val perm = controlsState.pendingRuntimePermission
        if (perm != null) {
            onRuntimePermissionResult(perm, granted)
        }
    }
    val standalonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshNow()
    }

    HudBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 960.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("device_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val headerPalette = JarvisThemeTokens.palette
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = headerPalette.sandContainer
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MonitorHeart,
                                contentDescription = null,
                                tint = headerPalette.onHue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.device_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = headerPalette.text
                        )
                        if (uiState is DeviceTelemetryUiState.Ready) {
                            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(uiState.telemetry.capturedAtMillis))
                            Text(
                                text = "Updated $time · ${uiState.pollingIntervalSeconds}s",
                                style = JarvisMonoTelemetryStyle,
                                color = headerPalette.textSecondary
                            )
                        }
                    }
                }
                val actionPalette = JarvisThemeTokens.palette
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiState is DeviceTelemetryUiState.Ready) {
                        HudBadge(
                            text = if (uiState.autoPollingEnabled) "Live" else "Paused",
                            color = if (uiState.autoPollingEnabled) {
                                actionPalette.onHue
                            } else {
                                actionPalette.textSecondary
                            },
                            containerColor = if (uiState.autoPollingEnabled) {
                                actionPalette.sandContainer
                            } else {
                                actionPalette.surfaceHigh
                            }
                        )
                    }
                    IconButton(
                        onClick = onToggleAutoPolling,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_polling_button")
                    ) {
                        val isPolling = (uiState as? DeviceTelemetryUiState.Ready)?.autoPollingEnabled ?: true
                        Icon(
                            imageVector = if (isPolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Toggle telemetry polling",
                            tint = actionPalette.text
                        )
                    }
                    IconButton(
                        onClick = onRefreshNow,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_telemetry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_telemetry),
                            tint = actionPalette.sandAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DeviceModeChips(
                selectedTab = controlsState.activeTab,
                onSelectTab = onSelectTab
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (controlsState.activeTab) {
                DeviceDashboardTab.CONTROLS -> {
                    DeviceControlsConsole(
                        controlsState = controlsState,
                        onRunTool = onRunTool,
                        onCancelActiveTool = onCancelActiveTool,
                        onSetNotificationReaderEnabled = onSetNotificationReaderEnabled,
                        onSetNotificationPrivacyMaskEnabled = onSetNotificationPrivacyMaskEnabled,
                        onClearNotificationBuffer = onClearNotificationBuffer,
                        onSetAccessibilityAutomationEnabled = onSetAccessibilityAutomationEnabled,
                        onOpenBridgeSetup = onOpenBridgeSetup
                    )
                }

                DeviceDashboardTab.PERMISSIONS -> {
                    DevicePermissionsPrivacyConsole(
                        controlsState = controlsState,
                        onRequestRuntimePermission = { perm -> standalonePermissionLauncher.launch(perm) },
                        onSetNotificationReaderEnabled = onSetNotificationReaderEnabled,
                        onSetNotificationPrivacyMaskEnabled = onSetNotificationPrivacyMaskEnabled,
                        onSetAccessibilityAutomationEnabled = onSetAccessibilityAutomationEnabled,
                        onSetCloudUploadConsentGranted = onSetCloudUploadConsentGranted
                    )
                }

                DeviceDashboardTab.TELEMETRY -> {
                    when (uiState) {
                        is DeviceTelemetryUiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Reading Android telemetry…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is DeviceTelemetryUiState.Ready -> {
                            TelemetryGrid(telemetry = uiState.telemetry)
                        }
                    }
                }
            }

            DeviceToolDialogs(
                controlsState = controlsState,
                onConfirmPendingTool = onConfirmPendingTool,
                onCancelPendingTool = onCancelPendingTool,
                onDismissPermissionPrompt = onDismissPermissionPrompt,
                onLaunchRuntimePermission = { perm -> permissionLauncher.launch(perm) }
            )
        }
    }
}

@Composable
private fun TelemetryGrid(telemetry: DeviceTelemetry) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 960.dp)
    ) {
        val isWide = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("device_telemetry_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HardwareCard(telemetry, Modifier.weight(1f))
                        OsBuildCard(telemetry, Modifier.weight(1f))
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MemoryAndStorageCard(telemetry, Modifier.weight(1f))
                        BatteryAndThermalCard(telemetry, Modifier.weight(1f))
                    }
                }
                item {
                    NetworkCard(telemetry, Modifier.fillMaxWidth())
                }
            } else {
                item { HardwareCard(telemetry, Modifier.fillMaxWidth()) }
                item { OsBuildCard(telemetry, Modifier.fillMaxWidth()) }
                item { MemoryAndStorageCard(telemetry, Modifier.fillMaxWidth()) }
                item { BatteryAndThermalCard(telemetry, Modifier.fillMaxWidth()) }
                item { NetworkCard(telemetry, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun HardwareCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    TelemetrySectionCard(
        title = "Hardware",
        icon = Icons.Filled.Memory,
        modifier = modifier
    ) {
        TelemetryRow("Device", "${telemetry.manufacturer} ${telemetry.model}")
        EditorialHairlineDivider()
        TelemetryRow("Codename", "${telemetry.brand} / ${telemetry.device}")
        EditorialHairlineDivider()
        TelemetryRow("SoC / Platform", telemetry.socModel)
        EditorialHairlineDivider()
        TelemetryRow("Hardware tag", telemetry.hardware)
        EditorialHairlineDivider()
        TelemetryRow("CPU threads", "${telemetry.cpuCoreCount} cores", monospace = true)
        EditorialHairlineDivider()
        TelemetryRow("ABIs", telemetry.supportedAbis.ifBlank { "N/A" }, monospace = true)
    }
}

@Composable
private fun OsBuildCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    TelemetrySectionCard(
        title = "System build",
        icon = Icons.Filled.PhoneAndroid,
        modifier = modifier
    ) {
        TelemetryRow("Android release", "Android ${telemetry.androidRelease}", monospace = true)
        EditorialHairlineDivider()
        TelemetryRow("SDK level", "API ${telemetry.sdkInt}", monospace = true)
        EditorialHairlineDivider()
        TelemetryRow("Security patch", telemetry.securityPatch, monospace = true)
        EditorialHairlineDivider()
        TelemetryRow("Build display", telemetry.buildDisplay.ifBlank { "Standard" })
    }
}

@Composable
private fun MemoryAndStorageCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    val totalRam = telemetry.totalRamBytes.coerceAtLeast(1L)
    val usedRam = (totalRam - telemetry.availRamBytes).coerceAtLeast(0L)
    val ramProgress = usedRam.toFloat() / totalRam.toFloat()

    val totalStorage = telemetry.totalStorageBytes.coerceAtLeast(1L)
    val usedStorage = (totalStorage - telemetry.freeStorageBytes).coerceAtLeast(0L)
    val storageProgress = usedStorage.toFloat() / totalStorage.toFloat()

    TelemetrySectionCard(
        title = "Memory and storage",
        icon = Icons.Filled.Storage,
        modifier = modifier
    ) {
        val palette = JarvisThemeTokens.palette
        TelemetryProgressBar(
            label = "RAM usage",
            valueText = String.format(
                Locale.US,
                "%.2f / %.2f GB",
                usedRam / 1_073_741_824f,
                totalRam / 1_073_741_824f
            ),
            progress = ramProgress,
            accentColor = if (telemetry.lowMemory) {
                palette.errorAccent
            } else {
                palette.sandAccent
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TelemetryProgressBar(
            label = "Internal storage (/data)",
            valueText = String.format(
                Locale.US,
                "%.1f / %.1f GB",
                usedStorage / 1_073_741_824f,
                totalStorage / 1_073_741_824f
            ),
            progress = storageProgress,
            accentColor = palette.sandAccent
        )
        EditorialHairlineDivider(modifier = Modifier.padding(vertical = 4.dp))
        TelemetryRow(
            label = "Low memory alert",
            value = if (telemetry.lowMemory) "Active" else "Normal",
            isAlert = telemetry.lowMemory
        )
    }
}

@Composable
private fun BatteryAndThermalCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    val palette = JarvisThemeTokens.palette
    val pct = telemetry.batteryPercent.coerceAtLeast(0)
    TelemetrySectionCard(
        title = "Battery and thermal",
        icon = Icons.Filled.BatteryChargingFull,
        modifier = modifier
    ) {
        TelemetryProgressBar(
            label = "Charge (${telemetry.batteryStatus})",
            valueText = if (telemetry.batteryPercent >= 0) "$pct%" else "N/A",
            progress = pct / 100f,
            accentColor = if (pct in 1..15) {
                palette.errorAccent
            } else {
                palette.successAccent
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        TelemetryRow("Power source", telemetry.batteryPlugged)
        EditorialHairlineDivider()
        TelemetryRow(
            "Battery temp",
            String.format(Locale.US, "%.1f °C", telemetry.batteryTempCelsius),
            monospace = true
        )
        EditorialHairlineDivider()
        TelemetryRow("Voltage", "${telemetry.batteryVoltageMv} mV", monospace = true)
        EditorialHairlineDivider()
        TelemetryRow("Battery health", telemetry.batteryHealth)
        EditorialHairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Thermal status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = telemetry.thermalStatusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (telemetry.thermalCode >= 3) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun NetworkCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    TelemetrySectionCard(
        title = "Network",
        icon = Icons.Filled.Router,
        modifier = modifier
    ) {
        TelemetryRow("Link state", if (telemetry.isNetworkConnected) "Connected" else "Offline")
        EditorialHairlineDivider()
        TelemetryRow("Transport", telemetry.networkTransport)
        EditorialHairlineDivider()
        TelemetryRow("Validated internet", if (telemetry.isNetworkValidated) "Yes" else "No")
        EditorialHairlineDivider()
        TelemetryRow("Metered link", if (telemetry.isNetworkMetered) "Metered" else "Unmetered")
        EditorialHairlineDivider()
        TelemetryRow(
            "Estimated downstream",
            "${telemetry.downstreamBandwidthKbps} kbps",
            monospace = true
        )
    }
}

@Composable
private fun TelemetrySectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    HudPanel(modifier = modifier) {
        val sectionPalette = JarvisThemeTokens.palette
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = sectionPalette.sandAccent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    isAlert: Boolean = false
) {
    val valueStyle: TextStyle = if (monospace) {
        JarvisMonoTelemetryStyle
    } else {
        MaterialTheme.typography.labelLarge
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = valueStyle,
            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(name = "Device Light", showBackground = true)
@Preview(name = "Device Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeviceDashboardScreenPreview() {
    JarvisTheme {
        DeviceDashboardScreen(
            uiState = DeviceTelemetryUiState.Ready(
                telemetry = DeviceTelemetry(
                    manufacturer = "Google",
                    model = "Pixel 9 Pro",
                    brand = "google",
                    device = "caiman",
                    socModel = "Tensor G4",
                    hardware = "caiman",
                    cpuCoreCount = 8,
                    supportedAbis = "arm64-v8a",
                    androidRelease = "15",
                    sdkInt = 35,
                    securityPatch = "2026-03-05",
                    buildDisplay = "AP4A.260305",
                    totalRamBytes = 16L * 1024 * 1024 * 1024,
                    availRamBytes = 9L * 1024 * 1024 * 1024,
                    totalStorageBytes = 256L * 1024 * 1024 * 1024,
                    freeStorageBytes = 148L * 1024 * 1024 * 1024,
                    lowMemory = false,
                    batteryPercent = 82,
                    batteryStatus = "Discharging",
                    batteryPlugged = "Unplugged",
                    batteryTempCelsius = 31.4f,
                    batteryVoltageMv = 4180,
                    batteryHealth = "Good",
                    thermalCode = 0,
                    thermalStatusLabel = "None",
                    isNetworkConnected = true,
                    networkTransport = "Wi-Fi",
                    isNetworkValidated = true,
                    isNetworkMetered = false,
                    downstreamBandwidthKbps = 128000,
                    capturedAtMillis = 1700000000000L
                ),
                pollingIntervalSeconds = 5,
                autoPollingEnabled = true
            ),
            onRefreshNow = {},
            onToggleAutoPolling = {},
            controlsState = DeviceControlsState(activeTab = DeviceDashboardTab.TELEMETRY)
        )
    }
}
