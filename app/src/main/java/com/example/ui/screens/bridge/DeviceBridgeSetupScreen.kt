package com.example.ui.screens.bridge

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.device.ShizukuAvailabilityState
import com.example.core.device.ShizukuStatusSnapshot
import com.example.core.device.WirelessAdbConnectionState
import com.example.core.device.WirelessAdbStatusSnapshot
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceBridgeSetupScreen(
    uiState: DeviceBridgeSetupUiState,
    onRefreshAll: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onBindShizukuService: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onUpdateHost: (String) -> Unit,
    onUpdatePairingPort: (String) -> Unit,
    onUpdatePairingCode: (String) -> Unit,
    onUpdateConnectPort: (String) -> Unit,
    onSelectDiscoveredPairingPort: (Int) -> Unit,
    onSelectDiscoveredConnectPort: (Int) -> Unit,
    onToggleMdnsDiscovery: () -> Unit,
    onPairDevice: () -> Unit,
    onConnectAndVerify: () -> Unit,
    onVerifyLiveConnection: () -> Unit,
    onDisconnectAdb: () -> Unit,
    onClearSavedAdb: () -> Unit,
    onDismissBanner: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = JarvisThemeTokens.palette

    HudBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bridge_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = palette.text
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.cyanBadgeBg
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cable,
                                contentDescription = null,
                                tint = palette.skyAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.bridge_setup_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.text
                        )
                        Text(
                            text = "Priority: ${uiState.activePreferredRoute}",
                            style = JarvisMonoTelemetryStyle,
                            color = palette.skyAccent
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshAll,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bridge_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Shizuku and ADB status",
                        tint = palette.skyAccent
                    )
                }
            }

            if (uiState.feedbackBanner != null) {
                HudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .testTag("bridge_feedback_banner"),
                    containerColor = if (uiState.feedbackIsError) {
                        palette.crimsonBadgeBg
                    } else {
                        palette.cyanBadgeBg
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.feedbackBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.feedbackIsError) palette.errorAccent else palette.skyAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismissBanner,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("OK", color = palette.text)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("bridge_setup_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            // 1. Shizuku Primary Privileged Bridge
            item {
                val shizuku = uiState.shizuku
                val isReady = shizuku.availability == ShizukuAvailabilityState.READY
                val badgeColor = when (shizuku.availability) {
                    ShizukuAvailabilityState.READY -> palette.successContainer
                    ShizukuAvailabilityState.CONNECTED_UNBOUND,
                    ShizukuAvailabilityState.PERMISSION_REQUIRED -> palette.skyContainer
                    ShizukuAvailabilityState.INSTALLED_SERVICE_STOPPED -> palette.warningContainer
                    ShizukuAvailabilityState.PERMISSION_DENIED,
                    ShizukuAvailabilityState.NOT_INSTALLED -> palette.surfaceHigh
                }
                val badgeInk = when (shizuku.availability) {
                    ShizukuAvailabilityState.PERMISSION_DENIED,
                    ShizukuAvailabilityState.NOT_INSTALLED -> palette.textSecondary
                    else -> palette.onHue
                }

                HudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                        .testTag("shizuku_status_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = palette.skyAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Shizuku Service (Primary)",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                        }
                        HudBadge(
                            text = shizuku.availability.label,
                            color = badgeInk,
                            containerColor = badgeColor
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "JARVIS prefers Shizuku for permitted system operations once authorized. Shizuku runs at ADB shell (UID 2000) or root if configured and never grants unrestricted kernel root by itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Spacer(Modifier.height(12.dp))

                    BridgeMetricRow(
                        label = "Manager package",
                        value = if (shizuku.isInstalled) "Installed" else "Not installed"
                    )
                    EditorialHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
                    BridgeMetricRow(
                        label = "Binder & API",
                        value = if (shizuku.isBinderAlive) "Alive (v${shizuku.shizukuVersion})" else "Offline"
                    )
                    EditorialHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
                    BridgeMetricRow(
                        label = "Server identity",
                        value = when {
                            !shizuku.isBinderAlive -> "Inactive"
                            shizuku.isRootBacked -> "UID 0 (Root)"
                            shizuku.serverUid >= 0 -> "UID ${shizuku.serverUid} (Shell)"
                            else -> "ADB Shell"
                        }
                    )
                    EditorialHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
                    BridgeMetricRow(
                        label = "Permission & helper",
                        value = when {
                            isReady -> "Granted & Bound"
                            shizuku.hasPermission -> "Granted (Unbound)"
                            else -> "Not granted"
                        }
                    )

                    if (!shizuku.lastError.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = shizuku.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.errorAccent
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestShizukuPermission,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.skyContainer,
                                contentColor = palette.onHue
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("shizuku_request_permission_btn")
                        ) {
                            Text(if (shizuku.hasPermission) "Verify Permission" else "Request Permission")
                        }
                        if (shizuku.hasPermission && !isReady) {
                            OutlinedButton(
                                onClick = onBindShizukuService,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("shizuku_bind_service_btn")
                            ) {
                                Text("Bind Helper Service")
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenShizukuApp,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("shizuku_open_app_btn")
                        ) {
                            Text(if (shizuku.isInstalled) "Open Shizuku" else "Install Shizuku")
                        }
                    }
                }
            }

            // 2. Wireless ADB Pure-Kotlin Workflow
            item {
                val adb = uiState.wirelessAdb
                val isConnected = adb.connectionState == WirelessAdbConnectionState.CONNECTED
                val adbBadgeContainer = when (adb.connectionState) {
                    WirelessAdbConnectionState.CONNECTED -> palette.successContainer
                    WirelessAdbConnectionState.PAIRED_DISCONNECTED,
                    WirelessAdbConnectionState.PAIRING,
                    WirelessAdbConnectionState.CONNECTING -> palette.skyContainer
                    WirelessAdbConnectionState.ERROR -> palette.errorContainer
                    else -> palette.surfaceHigh
                }
                val adbBadgeInk = when (adb.connectionState) {
                    WirelessAdbConnectionState.UNCONFIGURED,
                    WirelessAdbConnectionState.DISCOVERING -> palette.textSecondary
                    else -> palette.onHue
                }

                HudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                        .testTag("wireless_adb_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WifiTethering,
                                contentDescription = null,
                                tint = palette.sageAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Wireless Debugging (Pure Kotlin)",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text
                            )
                        }
                        HudBadge(
                            text = adb.connectionState.label,
                            color = adbBadgeInk,
                            containerColor = adbBadgeContainer
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Pair once over Android 11+ Wireless Debugging (SPAKE2 + TLS 1.3). Your ephemeral 6-digit pairing code is masked and never saved or written to logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("adb_open_dev_settings_btn")
                        ) {
                            Icon(Icons.Filled.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Developer Options")
                        }
                        OutlinedButton(
                            onClick = onToggleMdnsDiscovery,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("adb_mdns_toggle_btn")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (adb.isMdnsDiscoveryActive) "Stop Discovery" else "Auto-Discover Ports")
                        }
                    }

                    if (adb.discoveredPairingPorts.isNotEmpty() || adb.discoveredConnectPorts.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Discovered local ports",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.skyAccent
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            adb.discoveredPairingPorts.forEach { port ->
                                FilterChip(
                                    selected = uiState.pairingPortInput == port.toString(),
                                    onClick = { onSelectDiscoveredPairingPort(port) },
                                    label = { Text("Pair :$port", style = JarvisMonoTelemetryStyle) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                            adb.discoveredConnectPorts.forEach { port ->
                                FilterChip(
                                    selected = uiState.connectPortInput == port.toString(),
                                    onClick = { onSelectDiscoveredConnectPort(port) },
                                    label = { Text("Connect :$port", style = JarvisMonoTelemetryStyle) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }
                    }

                    EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Step 1 — Pair with 6-digit code",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.text
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.hostInput,
                        onValueChange = onUpdateHost,
                        label = { Text("Host / IP (use 127.0.0.1 for this device)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adb_host_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.pairingPortInput,
                            onValueChange = onUpdatePairingPort,
                            label = { Text("Pairing port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("adb_pairing_port_input")
                        )
                        OutlinedTextField(
                            value = uiState.pairingCodeInput,
                            onValueChange = onUpdatePairingCode,
                            label = { Text("6-digit code") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("adb_pairing_code_input")
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onPairDevice,
                        enabled = !uiState.isPairingBusy,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.skyContainer,
                            contentColor = palette.onHue
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("adb_pair_button")
                    ) {
                        if (uiState.isPairingBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (uiState.isPairingBusy) "Pairing…" else "Pair Device")
                    }

                    EditorialHairlineDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Step 2 — Connect & verify session",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.text
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.connectPortInput,
                        onValueChange = onUpdateConnectPort,
                        label = { Text("Connection port (from Wireless debugging screen)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adb_connect_port_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConnectAndVerify,
                            enabled = !uiState.isConnectingBusy,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.sageContainer,
                                contentColor = palette.onHue
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("adb_connect_button")
                        ) {
                            if (uiState.isConnectingBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (uiState.isConnectingBusy) "Verifying…" else "Connect & Verify")
                        }

                        if (isConnected) {
                            OutlinedButton(
                                onClick = onVerifyLiveConnection,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("adb_verify_button")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify Live")
                            }
                            OutlinedButton(
                                onClick = onDisconnectAdb,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("adb_disconnect_button")
                            ) {
                                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Disconnect")
                            }
                        } else if (adb.isPaired) {
                            TextButton(
                                onClick = onClearSavedAdb,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("adb_clear_saved_button")
                            ) {
                                Text("Reset Saved Host")
                            }
                        }
                    }

                    if (adb.verifiedIdentity != null) {
                        Spacer(Modifier.height(10.dp))
                        BridgeMetricRow(
                            label = "Verified identity",
                            value = adb.verifiedIdentity
                        )
                    }
                }
            }

            // 3. Diagnostics & Troubleshooting
            item {
                val adb = uiState.wirelessAdb
                HudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                        .testTag("adb_troubleshooting_card")
                ) {
                    Text(
                        text = "Connection guidance & troubleshooting",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!adb.lastError.isNullOrBlank()) {
                        Text(
                            text = adb.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.errorAccent
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = adb.troubleshootingHint ?: "• Keep 'Pair device with pairing code' open until pairing finishes — Android closes the pairing port when dismissed.\n• Wireless Debugging picks a fresh connection port whenever Wi-Fi reconnects.\n• Once Shizuku is running and authorized, JARVIS automatically routes privileged actions through Shizuku first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun BridgeMetricRow(
    label: String,
    value: String
) {
    val palette = JarvisThemeTokens.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
        Text(
            text = value,
            style = JarvisMonoTelemetryStyle,
            color = palette.text
        )
    }
}

@Preview(name = "Bridge Light", showBackground = true)
@Preview(name = "Bridge Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeviceBridgeSetupPreview() {
    JarvisTheme {
        DeviceBridgeSetupScreen(
            uiState = DeviceBridgeSetupUiState(
                shizuku = ShizukuStatusSnapshot(
                    isInstalled = true,
                    isBinderAlive = true,
                    hasPermission = true,
                    isUserServiceBound = true,
                    shizukuVersion = 13,
                    serverUid = 2000,
                    availability = ShizukuAvailabilityState.READY
                ),
                wirelessAdb = WirelessAdbStatusSnapshot(
                    connectionState = WirelessAdbConnectionState.PAIRED_DISCONNECTED,
                    isPaired = true,
                    connectPort = 41523
                ),
                connectPortInput = "41523"
            ),
            onRefreshAll = {},
            onRequestShizukuPermission = {},
            onBindShizukuService = {},
            onOpenShizukuApp = {},
            onUpdateHost = {},
            onUpdatePairingPort = {},
            onUpdatePairingCode = {},
            onUpdateConnectPort = {},
            onSelectDiscoveredPairingPort = {},
            onSelectDiscoveredConnectPort = {},
            onToggleMdnsDiscovery = {},
            onPairDevice = {},
            onConnectAndVerify = {},
            onVerifyLiveConnection = {},
            onDisconnectAdb = {},
            onClearSavedAdb = {},
            onDismissBanner = {},
            onNavigateBack = {}
        )
    }
}
