package com.example.ui.screens.device

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.tools.DeviceTelemetry
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.components.TelemetryProgressBar
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.SignalAmber
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
    onSetCloudUploadConsentGranted: (Boolean) -> Unit = {}
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 960.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.device_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (uiState is DeviceTelemetryUiState.Ready) {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(uiState.telemetry.capturedAtMillis))
                        Text(
                            text = "Updated $time • Interval ${uiState.pollingIntervalSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState is DeviceTelemetryUiState.Ready) {
                        HudBadge(
                            text = if (uiState.autoPollingEnabled) "LIVE" else "PAUSED",
                            color = if (uiState.autoPollingEnabled) NominalGreen else SignalAmber
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
                            tint = MaterialTheme.colorScheme.onSurface
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DeviceModeChips(
                selectedTab = controlsState.activeTab,
                onSelectTab = onSelectTab
            )

            Spacer(modifier = Modifier.height(10.dp))

            when (controlsState.activeTab) {
                DeviceDashboardTab.CONTROLS -> {
                    DeviceControlsConsole(
                        controlsState = controlsState,
                        onRunTool = onRunTool,
                        onCancelActiveTool = onCancelActiveTool,
                        onSetNotificationReaderEnabled = onSetNotificationReaderEnabled,
                        onSetNotificationPrivacyMaskEnabled = onSetNotificationPrivacyMaskEnabled,
                        onClearNotificationBuffer = onClearNotificationBuffer,
                        onSetAccessibilityAutomationEnabled = onSetAccessibilityAutomationEnabled
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
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Querying Android system telemetry…",
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
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HardwareCard(telemetry, Modifier.weight(1f))
                        OsBuildCard(telemetry, Modifier.weight(1f))
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        title = "HARDWARE & SILICON",
        icon = Icons.Filled.Memory,
        modifier = modifier
    ) {
        TelemetryRow("Device", "${telemetry.manufacturer} ${telemetry.model}")
        TelemetryRow("Codename", "${telemetry.brand} / ${telemetry.device}")
        TelemetryRow("SoC / Platform", telemetry.socModel)
        TelemetryRow("Hardware Tag", telemetry.hardware)
        TelemetryRow("CPU Threads", "${telemetry.cpuCoreCount} cores")
        TelemetryRow("ABIs", telemetry.supportedAbis.ifBlank { "N/A" })
    }
}

@Composable
private fun OsBuildCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    TelemetrySectionCard(
        title = "OS & SYSTEM BUILD",
        icon = Icons.Filled.PhoneAndroid,
        modifier = modifier
    ) {
        TelemetryRow("Android Release", "Android ${telemetry.androidRelease}")
        TelemetryRow("SDK Level", "API ${telemetry.sdkInt}")
        TelemetryRow("Security Patch", telemetry.securityPatch)
        TelemetryRow("Build Display", telemetry.buildDisplay.ifBlank { "Standard" })
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
        title = "MEMORY & STORAGE",
        icon = Icons.Filled.Storage,
        modifier = modifier
    ) {
        TelemetryProgressBar(
            label = "RAM UTILIZATION",
            valueText = String.format(
                Locale.US,
                "%.2f / %.2f GB",
                usedRam / 1_073_741_824f,
                totalRam / 1_073_741_824f
            ),
            progress = ramProgress,
            accentColor = if (telemetry.lowMemory) SignalAmber else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        TelemetryProgressBar(
            label = "INTERNAL STORAGE (/data)",
            valueText = String.format(
                Locale.US,
                "%.1f / %.1f GB",
                usedStorage / 1_073_741_824f,
                totalStorage / 1_073_741_824f
            ),
            progress = storageProgress,
            accentColor = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        TelemetryRow("Low Memory Alert", if (telemetry.lowMemory) "ACTIVE" else "Nominal")
    }
}

@Composable
private fun BatteryAndThermalCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    val pct = telemetry.batteryPercent.coerceAtLeast(0)
    TelemetrySectionCard(
        title = "BATTERY & THERMAL",
        icon = Icons.Filled.BatteryChargingFull,
        modifier = modifier
    ) {
        TelemetryProgressBar(
            label = "CHARGE LEVEL (${telemetry.batteryStatus})",
            valueText = if (telemetry.batteryPercent >= 0) "$pct%" else "N/A",
            progress = pct / 100f,
            accentColor = if (pct in 1..20) SignalAmber else NominalGreen
        )
        Spacer(modifier = Modifier.height(10.dp))
        TelemetryRow("Power Source", telemetry.batteryPlugged)
        TelemetryRow("Battery Temp", String.format(Locale.US, "%.1f °C", telemetry.batteryTempCelsius))
        TelemetryRow("Voltage", "${telemetry.batteryVoltageMv} mV")
        TelemetryRow("Battery Health", telemetry.batteryHealth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Thermal Status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = telemetry.thermalStatusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (telemetry.thermalCode >= 3) SignalAmber else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NetworkCard(telemetry: DeviceTelemetry, modifier: Modifier) {
    TelemetrySectionCard(
        title = "NETWORK & TRANSPORT",
        icon = Icons.Filled.Router,
        modifier = modifier
    ) {
        TelemetryRow("Link State", if (telemetry.isNetworkConnected) "Connected" else "Offline")
        TelemetryRow("Transport", telemetry.networkTransport)
        TelemetryRow("Validated Internet", if (telemetry.isNetworkValidated) "Yes" else "No")
        TelemetryRow("Metered Link", if (telemetry.isNetworkMetered) "Metered" else "Unmetered")
        TelemetryRow("Est. Downstream", "${telemetry.downstreamBandwidthKbps} kbps")
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
