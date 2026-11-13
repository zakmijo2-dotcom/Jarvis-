package com.example.ui.screens.device

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.SignalAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceControlsConsole(
    controlsState: DeviceControlsState,
    onRunTool: (String, Map<String, String>) -> Unit,
    onCancelActiveTool: () -> Unit,
    onSetNotificationReaderEnabled: (Boolean) -> Unit,
    onSetNotificationPrivacyMaskEnabled: (Boolean) -> Unit,
    onClearNotificationBuffer: () -> Unit,
    onSetAccessibilityAutomationEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var appQuery by rememberSaveable { mutableStateOf("Settings") }
    var contactQuery by rememberSaveable { mutableStateOf("") }
    var phoneTarget by rememberSaveable { mutableStateOf("") }
    var smsBody by rememberSaveable { mutableStateOf("") }
    var volumeSlider by rememberSaveable { mutableFloatStateOf(50f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 960.dp)
            .testTag("device_controls_list"),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (controlsState.activeToolName != null) {
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Running: ${controlsState.activeToolName}…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = onCancelActiveTool,
                            modifier = Modifier.testTag("controls_cancel_tool_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        controlsState.lastResult?.let { latest ->
            item {
                ToolResultCard(result = latest, isPrimary = true)
            }
        }

        // 1. Application & Quick Settings Control
        item {
            ControlsSectionPanel(
                title = "APPLICATION & SETTINGS CONTROL",
                icon = Icons.Filled.Apps,
                badgeText = "${controlsState.registeredToolCount} TOOLS"
            ) {
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    label = { Text("App Name or Package ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_app_query_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onRunTool("launch_installed_app", mapOf("app_query" to appQuery)) },
                        modifier = Modifier.testTag("btn_launch_app")
                    ) {
                        Text("Launch App")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("go_home_screen", emptyMap()) },
                        modifier = Modifier.testTag("btn_go_home")
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null)
                        Spacer(Modifier.height(0.dp).padding(start = 4.dp))
                        Text("Go Home")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("open_android_settings", mapOf("page" to "wifi")) },
                        modifier = Modifier.testTag("btn_open_wifi_settings")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                        Text(" Wi-Fi Settings")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("open_android_settings", mapOf("page" to "bluetooth")) },
                        modifier = Modifier.testTag("btn_open_bt_settings")
                    ) {
                        Text("Bluetooth Settings")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("open_app_screen", mapOf("screen" to "camera")) }
                    ) {
                        Text("Open Camera")
                    }
                }
            }
        }

        // 2. Device & Media Control
        item {
            ControlsSectionPanel(
                title = "DEVICE, VOLUME & MEDIA CONTROL",
                icon = Icons.Filled.VolumeUp
            ) {
                Text(
                    text = "Media Volume Target: ${volumeSlider.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = volumeSlider,
                    onValueChange = { volumeSlider = it },
                    valueRange = 0f..100f,
                    onValueChangeFinished = {
                        onRunTool(
                            "adjust_media_volume",
                            mapOf("action" to "set", "percent" to volumeSlider.roundToInt().toString())
                        )
                    }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRunTool("adjust_media_volume", mapOf("action" to "down")) },
                        modifier = Modifier.testTag("btn_vol_down")
                    ) { Text("Vol -") }
                    OutlinedButton(
                        onClick = { onRunTool("adjust_media_volume", mapOf("action" to "up")) },
                        modifier = Modifier.testTag("btn_vol_up")
                    ) { Text("Vol +") }
                    OutlinedButton(
                        onClick = { onRunTool("control_media_playback", mapOf("command" to "play_pause")) },
                        modifier = Modifier.testTag("btn_media_play_pause")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Play / Pause")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("control_media_playback", mapOf("command" to "next")) }
                    ) { Text("Next Track") }
                    OutlinedButton(
                        onClick = { onRunTool("read_battery_status", emptyMap()) },
                        modifier = Modifier.testTag("btn_read_battery")
                    ) { Text("Read Battery") }
                    OutlinedButton(
                        onClick = { onRunTool("read_device_info", emptyMap()) },
                        modifier = Modifier.testTag("btn_read_device_info")
                    ) { Text("Device Info") }
                }
            }
        }

        // 3. Communication (Contacts, Confirmed Dial, Confirmed SMS)
        item {
            ControlsSectionPanel(
                title = "COMMUNICATION (CONFIRMATION-GATED)",
                icon = Icons.Filled.Contacts,
                badgeText = if (controlsState.hasContactsPermission) "CONTACTS OK" else "PERMISSION NEEDED",
                badgeNominal = controlsState.hasContactsPermission
            ) {
                OutlinedTextField(
                    value = contactQuery,
                    onValueChange = { contactQuery = it },
                    label = { Text("Search Contacts (name or digits)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_contact_query_input")
                )
                OutlinedTextField(
                    value = phoneTarget,
                    onValueChange = { phoneTarget = it },
                    label = { Text("Phone Number (for Call / SMS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_phone_input")
                )
                OutlinedTextField(
                    value = smsBody,
                    onValueChange = { smsBody = it },
                    label = { Text("SMS Draft Body") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_sms_body_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRunTool("search_contacts", mapOf("query" to contactQuery)) },
                        modifier = Modifier.testTag("btn_search_contacts")
                    ) {
                        Text("Search Contacts")
                    }
                    Button(
                        onClick = { onRunTool("initiate_phone_call", mapOf("phone_number" to phoneTarget)) },
                        modifier = Modifier.testTag("btn_initiate_call")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Text(" Call (Confirm)")
                    }
                    Button(
                        onClick = {
                            onRunTool(
                                "compose_message",
                                mapOf("recipient" to phoneTarget, "message_body" to smsBody)
                            )
                        },
                        modifier = Modifier.testTag("btn_compose_sms")
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null)
                        Text(" SMS (Confirm)")
                    }
                }
            }
        }

        // 4. Notification Reader & Privacy Controls
        item {
            ControlsSectionPanel(
                title = "NOTIFICATIONS & PRIVACY CONTROLS",
                icon = Icons.Filled.Notifications,
                badgeText = if (controlsState.isNotificationAuthorized) "OS GRANTED" else "OS DISABLED",
                badgeNominal = controlsState.isNotificationAuthorized
            ) {
                Text(
                    text = "JARVIS only captures incoming notifications in RAM when BOTH Android Notification Access is granted AND this opt-in toggle is enabled. Privacy masking redacts OTP codes and message previews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwitchRow(
                    title = "Enable Notification Reader",
                    subtitle = "Service connected: ${controlsState.isNotificationConnected} • Buffered: ${controlsState.capturedNotifications.size}",
                    checked = controlsState.settings.notificationReaderEnabled,
                    onCheckedChange = onSetNotificationReaderEnabled,
                    testTag = "switch_notification_reader"
                )
                SwitchRow(
                    title = "Mask Sensitive Notification Content & OTPs",
                    subtitle = "Hide message bodies and verification codes from reads",
                    checked = controlsState.settings.notificationPrivacyMaskEnabled,
                    onCheckedChange = onSetNotificationPrivacyMaskEnabled,
                    testTag = "switch_notification_mask"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.testTag("btn_notification_settings")
                    ) {
                        Text("Notification Access Settings")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("read_notifications", mapOf("limit" to "5")) },
                        modifier = Modifier.testTag("btn_read_notifications")
                    ) {
                        Text("Read Notifications")
                    }
                    TextButton(
                        onClick = onClearNotificationBuffer,
                        modifier = Modifier.testTag("btn_clear_notifications")
                    ) {
                        Text("Clear Buffer")
                    }
                }
            }
        }

        // 5. Accessibility Disclosure & Authorized Navigation
        item {
            ControlsSectionPanel(
                title = "ACCESSIBILITY DISCLOSURE & AUTOMATION",
                icon = Icons.Filled.AccessibilityNew,
                badgeText = if (controlsState.isAccessibilityAuthorized) "OS GRANTED" else "OS DISABLED",
                badgeNominal = controlsState.isAccessibilityAuthorized,
                panelTestTag = "accessibility_disclosure_card"
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "EXPLICIT ACCESSIBILITY DISCLOSURE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Window content retrieval is strictly disabled (canRetrieveWindowContent = false).\n" +
                                "• JARVIS never silently reads screen text, passwords, or UI trees.\n" +
                                "• Used exclusively for user-confirmed global navigation (Home, Back, Recents, Notifications).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                SwitchRow(
                    title = "Allow Authorized Accessibility Actions",
                    subtitle = "Service bound: ${controlsState.isAccessibilityConnected}",
                    checked = controlsState.settings.accessibilityAutomationEnabled,
                    onCheckedChange = onSetAccessibilityAutomationEnabled,
                    testTag = "switch_accessibility_automation"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.testTag("btn_accessibility_settings")
                    ) {
                        Text("Accessibility Settings")
                    }
                    Button(
                        onClick = { onRunTool("accessibility_global_action", mapOf("action" to "recents")) },
                        modifier = Modifier.testTag("btn_a11y_recents")
                    ) {
                        Text("Recents (Confirm)")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("accessibility_global_action", mapOf("action" to "back")) },
                        modifier = Modifier.testTag("btn_a11y_back")
                    ) {
                        Text("Back (Confirm)")
                    }
                }
            }
        }

        if (controlsState.executionLog.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT TOOL EXECUTION AUDIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(controlsState.executionLog.take(6), key = { "${it.toolName}_${it.executedAtMillis}" }) { item ->
                ToolResultCard(result = item, isPrimary = false)
            }
        }
    }
}

@Composable
fun DeviceToolDialogs(
    controlsState: DeviceControlsState,
    onConfirmPendingTool: () -> Unit,
    onCancelPendingTool: () -> Unit,
    onDismissPermissionPrompt: () -> Unit,
    onLaunchRuntimePermission: (String) -> Unit
) {
    val context = LocalContext.current
    val pendingConfirm = controlsState.pendingConfirmation
    if (pendingConfirm != null) {
        AlertDialog(
            onDismissRequest = onCancelPendingTool,
            modifier = Modifier.testTag("device_tool_confirmation_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Confirm Device Action (${pendingConfirm.request.toolName})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pendingConfirm.confirmationMessage, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        pendingConfirm.sensitivityWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmPendingTool,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_device_tool_button")
                ) {
                    Text("Confirm & Run")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelPendingTool,
                    modifier = Modifier.testTag("cancel_device_tool_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val pendingPerm = controlsState.pendingRuntimePermission
    val pendingSpecial = controlsState.pendingSpecialAccess
    if (pendingPerm != null || pendingSpecial != null) {
        AlertDialog(
            onDismissRequest = onDismissPermissionPrompt,
            modifier = Modifier.testTag("device_tool_permission_dialog"),
            title = {
                Text(if (pendingPerm != null) "Permission Required" else "${pendingSpecial?.label} Required")
            },
            text = {
                Text(
                    controlsState.permissionRationale
                        ?: "Explicit authorization is required before running this Android tool.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingPerm != null) {
                            onLaunchRuntimePermission(pendingPerm)
                        } else if (pendingSpecial != null) {
                            runCatching {
                                context.startActivity(
                                    Intent(pendingSpecial.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            onDismissPermissionPrompt()
                        }
                    }
                ) {
                    Text(if (pendingPerm != null) "Grant Permission" else "Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPermissionPrompt) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun ControlsSectionPanel(
    title: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeNominal: Boolean = true,
    panelTestTag: String? = null,
    content: @Composable () -> Unit
) {
    val mod = if (panelTestTag != null) {
        Modifier.fillMaxWidth().testTag(panelTestTag)
    } else {
        Modifier.fillMaxWidth()
    }
    HudPanel(modifier = mod) {
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (badgeText != null) {
                HudBadge(
                    text = badgeText,
                    color = if (badgeNominal) NominalGreen else SignalAmber
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ToolResultCard(result: ToolExecutionResult, isPrimary: Boolean) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.executedAtMillis))
    val badgeColor = when (result.status) {
        ToolExecutionStatus.SUCCESS -> NominalGreen
        ToolExecutionStatus.CANCELLED,
        ToolExecutionStatus.CONFIRMATION_REQUIRED -> SignalAmber
        else -> MaterialTheme.colorScheme.error
    }
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isPrimary) Modifier.testTag("latest_tool_result_card") else Modifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${result.toolName} ($time)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            HudBadge(text = result.status.name, color = badgeColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = result.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (result.verifiedFacts.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Text(
                text = "Verified: ${result.verifiedFacts.entries.joinToString(" • ") { "${it.key}=${it.value}" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun DeviceModeChips(
    selectedTab: DeviceDashboardTab,
    onSelectTab: (DeviceDashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTab == DeviceDashboardTab.TELEMETRY,
            onClick = { onSelectTab(DeviceDashboardTab.TELEMETRY) },
            label = { Text(DeviceDashboardTab.TELEMETRY.label) },
            modifier = Modifier.testTag("device_tab_telemetry")
        )
        FilterChip(
            selected = selectedTab == DeviceDashboardTab.CONTROLS,
            onClick = { onSelectTab(DeviceDashboardTab.CONTROLS) },
            label = { Text(DeviceDashboardTab.CONTROLS.label) },
            modifier = Modifier.testTag("device_tab_controls")
        )
    }
}
