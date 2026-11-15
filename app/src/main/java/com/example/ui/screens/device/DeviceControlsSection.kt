package com.example.ui.screens.device

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.tools.ToolExecutionResult
import com.example.core.tools.ToolExecutionStatus
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudPanel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SwitchDefaults
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisThemeTokens
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
    val palette = JarvisThemeTokens.palette
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
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            text = "Running ${controlsState.activeToolName}…",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.sandAccent
                        )
                        OutlinedButton(
                            onClick = onCancelActiveTool,
                            shape = CircleShape,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("controls_cancel_tool_button")
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

        // 1. Application and Quick Settings Control
        item {
            ControlsSectionPanel(
                title = "Apps and navigation",
                icon = Icons.Filled.Apps,
                badgeText = "${controlsState.registeredToolCount} tools"
            ) {
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    label = { Text("App name or package ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_app_query_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val palette = JarvisThemeTokens.palette
                    Button(
                        onClick = { onRunTool("launch_installed_app", mapOf("app_query" to appQuery)) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.sandContainer,
                            contentColor = palette.onHue
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_launch_app")
                    ) {
                        Text("Launch app")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("go_home_screen", emptyMap()) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_go_home")
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Home")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("open_android_settings", mapOf("page" to "wifi")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_open_wifi_settings")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wi-Fi settings")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("open_android_settings", mapOf("page" to "bluetooth")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_open_bt_settings")
                    ) {
                        Text("Bluetooth settings")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("open_app_screen", mapOf("screen" to "camera")) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Open camera")
                    }
                }
            }
        }

        // 2. Device, Volume, and Media Control
        item {
            ControlsSectionPanel(
                title = "Volume, media, and device",
                icon = Icons.AutoMirrored.Filled.VolumeUp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Media volume target",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${volumeSlider.roundToInt()}%",
                        style = JarvisMonoTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRunTool("adjust_media_volume", mapOf("action" to "down")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_vol_down")
                    ) { Text("Vol -") }
                    OutlinedButton(
                        onClick = { onRunTool("adjust_media_volume", mapOf("action" to "up")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_vol_up")
                    ) { Text("Vol +") }
                    OutlinedButton(
                        onClick = { onRunTool("control_media_playback", mapOf("command" to "play_pause")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_media_play_pause")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play / Pause")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("control_media_playback", mapOf("command" to "next")) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("Next track") }
                    OutlinedButton(
                        onClick = { onRunTool("read_battery_status", emptyMap()) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_read_battery")
                    ) { Text("Battery status") }
                    OutlinedButton(
                        onClick = { onRunTool("read_device_info", emptyMap()) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_read_device_info")
                    ) { Text("Device info") }
                }
            }
        }

        // 3. Communication (Contacts, Confirmed Dial, Confirmed SMS)
        item {
            ControlsSectionPanel(
                title = "Calls and messages",
                icon = Icons.Filled.Contacts,
                badgeText = if (controlsState.hasContactsPermission) "Contacts ready" else "Permission needed",
                badgeHighlighted = !controlsState.hasContactsPermission
            ) {
                OutlinedTextField(
                    value = contactQuery,
                    onValueChange = { contactQuery = it },
                    label = { Text("Search contacts (name or number)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_contact_query_input")
                )
                OutlinedTextField(
                    value = phoneTarget,
                    onValueChange = { phoneTarget = it },
                    label = { Text("Phone number (for call or SMS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_phone_input")
                )
                OutlinedTextField(
                    value = smsBody,
                    onValueChange = { smsBody = it },
                    label = { Text("SMS message body") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_sms_body_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRunTool("search_contacts", mapOf("query" to contactQuery)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_search_contacts")
                    ) {
                        Text("Search contacts")
                    }
                    val palette = JarvisThemeTokens.palette
                    Button(
                        onClick = { onRunTool("initiate_phone_call", mapOf("phone_number" to phoneTarget)) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.sandContainer,
                            contentColor = palette.onHue
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_initiate_call")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Call (confirm)")
                    }
                    Button(
                        onClick = {
                            onRunTool(
                                "compose_message",
                                mapOf("recipient" to phoneTarget, "message_body" to smsBody)
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.sandContainer,
                            contentColor = palette.onHue
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_compose_sms")
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SMS (confirm)")
                    }
                }
            }
        }

        // 4. Notification Reader and Privacy Controls
        item {
            ControlsSectionPanel(
                title = "Notifications and privacy",
                icon = Icons.Filled.Notifications,
                badgeText = if (controlsState.isNotificationAuthorized) "Access granted" else "Access off",
                badgeHighlighted = controlsState.isNotificationAuthorized
            ) {
                Text(
                    text = "Incoming notifications are buffered in RAM only when both Android Notification Access is granted and this switch is enabled. Privacy masking redacts verification codes and message previews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwitchRow(
                    title = "Notification reader",
                    subtitle = "Connected: ${controlsState.isNotificationConnected} · Buffered: ${controlsState.capturedNotifications.size}",
                    checked = controlsState.settings.notificationReaderEnabled,
                    onCheckedChange = onSetNotificationReaderEnabled,
                    testTag = "switch_notification_reader"
                )
                EditorialHairlineDivider()
                SwitchRow(
                    title = "Mask sensitive content and OTPs",
                    subtitle = "Hide message bodies and verification codes from reads",
                    checked = controlsState.settings.notificationPrivacyMaskEnabled,
                    onCheckedChange = onSetNotificationPrivacyMaskEnabled,
                    testTag = "switch_notification_mask"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_notification_settings")
                    ) {
                        Text("Notification settings")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("read_notifications", mapOf("limit" to "5")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_read_notifications")
                    ) {
                        Text("Read notifications")
                    }
                    TextButton(
                        onClick = onClearNotificationBuffer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_clear_notifications")
                    ) {
                        Text("Clear buffer")
                    }
                }
            }
        }

        // 5. Accessibility Disclosure and Authorized Navigation
        item {
            ControlsSectionPanel(
                title = "Accessibility automation",
                icon = Icons.Filled.AccessibilityNew,
                badgeText = if (controlsState.isAccessibilityAuthorized) "Access granted" else "Access off",
                badgeHighlighted = controlsState.isAccessibilityAuthorized,
                panelTestTag = "accessibility_disclosure_card"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Accessibility disclosure",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "No passive background scraping: background events are ignored. On-demand screen inspection, tap, scroll, and text entry run only after your confirmation. Password fields are redacted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SwitchRow(
                    title = "Allow authorized accessibility actions",
                    subtitle = "Service bound: ${controlsState.isAccessibilityConnected}",
                    checked = controlsState.settings.accessibilityAutomationEnabled,
                    onCheckedChange = onSetAccessibilityAutomationEnabled,
                    testTag = "switch_accessibility_automation"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_accessibility_settings")
                    ) {
                        Text("Accessibility settings")
                    }
                    val palette = JarvisThemeTokens.palette
                    Button(
                        onClick = { onRunTool("accessibility_global_action", mapOf("action" to "recents")) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.sandContainer,
                            contentColor = palette.onHue
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_a11y_recents")
                    ) {
                        Text("Recents (confirm)")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("accessibility_global_action", mapOf("action" to "back")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_a11y_back")
                    ) {
                        Text("Back (confirm)")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("inspect_screen_elements", mapOf("max_elements" to "15")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_a11y_inspect_screen")
                    ) {
                        Text("Inspect screen")
                    }
                    OutlinedButton(
                        onClick = { onRunTool("scroll_screen", mapOf("direction" to "down")) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_a11y_scroll_down")
                    ) {
                        Text("Scroll down")
                    }
                }
            }
        }

        if (controlsState.executionLog.isNotEmpty()) {
            item {
                Text(
                    text = "Recent tool activity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
            items(
                items = controlsState.executionLog.take(6),
                key = { "${it.toolName}_${it.executedAtMillis}" }
            ) { item ->
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
    val palette = JarvisThemeTokens.palette
    val pendingConfirm = controlsState.pendingConfirmation
    if (pendingConfirm != null) {
        AlertDialog(
            onDismissRequest = onCancelPendingTool,
            modifier = Modifier.testTag("device_tool_confirmation_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = palette.sandAccent
                )
            },
            title = { Text("Confirm action (${pendingConfirm.request.toolName})") },
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
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.sandContainer,
                        contentColor = palette.onHue
                    ),
                    modifier = Modifier.testTag("confirm_device_tool_button")
                ) {
                    Text("Confirm and run")
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
                Text(if (pendingPerm != null) "Permission required" else "${pendingSpecial?.label} required")
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
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.sandContainer,
                        contentColor = palette.onHue
                    )
                ) {
                    Text(if (pendingPerm != null) "Grant permission" else "Open settings")
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
    badgeHighlighted: Boolean = false,
    panelTestTag: String? = null,
    content: @Composable () -> Unit
) {
    val palette = JarvisThemeTokens.palette
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.sandAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (badgeText != null) {
                HudBadge(
                    text = badgeText,
                    color = if (badgeHighlighted) palette.onHue else MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = if (badgeHighlighted) {
                        palette.sandContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        )
    }
}

@Composable
private fun ToolResultCard(result: ToolExecutionResult, isPrimary: Boolean) {
    val palette = JarvisThemeTokens.palette
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.executedAtMillis))
    val statusColor = when (result.status) {
        ToolExecutionStatus.SUCCESS -> palette.onHue
        ToolExecutionStatus.CANCELLED,
        ToolExecutionStatus.CONFIRMATION_REQUIRED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> palette.onHue
    }
    val statusContainer = when (result.status) {
        ToolExecutionStatus.SUCCESS -> palette.sageContainer
        ToolExecutionStatus.CANCELLED,
        ToolExecutionStatus.CONFIRMATION_REQUIRED -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> palette.coralContainer
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = result.toolName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = time,
                    style = JarvisMonoTelemetryStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HudBadge(
                text = result.status.name.lowercase().replace('_', ' '),
                color = statusColor,
                containerColor = statusContainer
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = result.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (result.verifiedFacts.isNotEmpty()) {
            EditorialHairlineDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = result.verifiedFacts.entries.joinToString(" · ") { "${it.key}=${it.value}" },
                style = JarvisMonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeviceModeChips(
    selectedTab: DeviceDashboardTab,
    onSelectTab: (DeviceDashboardTab) -> Unit
) {
    val palette = JarvisThemeTokens.palette
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = palette.sandContainer,
        selectedLabelColor = palette.onHue,
        selectedLeadingIconColor = palette.onHue
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTab == DeviceDashboardTab.TELEMETRY,
            onClick = { onSelectTab(DeviceDashboardTab.TELEMETRY) },
            label = { Text(DeviceDashboardTab.TELEMETRY.label) },
            colors = chipColors,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("device_tab_telemetry")
        )
        FilterChip(
            selected = selectedTab == DeviceDashboardTab.CONTROLS,
            onClick = { onSelectTab(DeviceDashboardTab.CONTROLS) },
            label = { Text(DeviceDashboardTab.CONTROLS.label) },
            colors = chipColors,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("device_tab_controls")
        )
        FilterChip(
            selected = selectedTab == DeviceDashboardTab.PERMISSIONS,
            onClick = { onSelectTab(DeviceDashboardTab.PERMISSIONS) },
            label = { Text(DeviceDashboardTab.PERMISSIONS.label) },
            colors = chipColors,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("device_tab_permissions")
        )
    }
}

@Composable
fun DevicePermissionsPrivacyConsole(
    controlsState: DeviceControlsState,
    onRequestRuntimePermission: (String) -> Unit,
    onSetNotificationReaderEnabled: (Boolean) -> Unit,
    onSetNotificationPrivacyMaskEnabled: (Boolean) -> Unit,
    onSetAccessibilityAutomationEnabled: (Boolean) -> Unit,
    onSetCloudUploadConsentGranted: (Boolean) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 960.dp)
            .testTag("device_permissions_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ControlsSectionPanel(
                title = "Runtime permissions",
                icon = Icons.Filled.Mic,
                badgeText = "Least privilege",
                badgeHighlighted = true,
                panelTestTag = "permissions_runtime_card"
            ) {
                PermissionStatusRow(
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Required for speech recognition and local voice commands.",
                    granted = controlsState.hasAudioPermission,
                    actionLabel = "Grant mic",
                    onAction = { onRequestRuntimePermission(Manifest.permission.RECORD_AUDIO) },
                    testTag = "perm_grant_audio_button"
                )
                EditorialHairlineDivider()
                PermissionStatusRow(
                    title = "Contacts (READ_CONTACTS)",
                    description = "Used only when you invoke contact search.",
                    granted = controlsState.hasContactsPermission,
                    actionLabel = "Grant contacts",
                    onAction = { onRequestRuntimePermission(Manifest.permission.READ_CONTACTS) },
                    testTag = "perm_grant_contacts_button"
                )
                if (Build.VERSION.SDK_INT >= 33) {
                    EditorialHairlineDivider()
                    PermissionStatusRow(
                        title = "Notifications (POST_NOTIFICATIONS)",
                        description = "Shows foreground microphone and download progress indicators.",
                        granted = controlsState.hasPostNotificationsPermission,
                        actionLabel = "Grant alerts",
                        onAction = { onRequestRuntimePermission(Manifest.permission.POST_NOTIFICATIONS) },
                        testTag = "perm_grant_post_notif_button"
                    )
                }
            }
        }

        item {
            ControlsSectionPanel(
                title = "Special access",
                icon = Icons.Filled.AccessibilityNew,
                badgeText = if (controlsState.isAccessibilityAuthorized || controlsState.isNotificationAuthorized) "Configured" else "OS gated",
                badgeHighlighted = controlsState.isAccessibilityAuthorized || controlsState.isNotificationAuthorized,
                panelTestTag = "permissions_special_access_card"
            ) {
                PermissionStatusRow(
                    title = "Accessibility service",
                    description = "No passive scraping; runs UI inspection, tap, or scroll only after approval.",
                    granted = controlsState.isAccessibilityAuthorized,
                    actionLabel = "Settings",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    testTag = "perm_open_a11y_settings_button"
                )
                SwitchRow(
                    title = "Allow accessibility actions",
                    subtitle = "Service bound: ${controlsState.isAccessibilityConnected}",
                    checked = controlsState.settings.accessibilityAutomationEnabled,
                    onCheckedChange = onSetAccessibilityAutomationEnabled,
                    testTag = "perm_switch_a11y"
                )
                EditorialHairlineDivider()
                PermissionStatusRow(
                    title = "Notification listener",
                    description = "Buffers recent notifications in RAM when explicitly enabled; never exported.",
                    granted = controlsState.isNotificationAuthorized,
                    actionLabel = "Settings",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    testTag = "perm_open_notif_settings_button"
                )
                SwitchRow(
                    title = "Notification reader",
                    subtitle = "Connected: ${controlsState.isNotificationConnected}",
                    checked = controlsState.settings.notificationReaderEnabled,
                    onCheckedChange = onSetNotificationReaderEnabled,
                    testTag = "perm_switch_notif_reader"
                )
                SwitchRow(
                    title = "Mask OTPs and message previews",
                    subtitle = "Redacts verification codes before model synthesis",
                    checked = controlsState.settings.notificationPrivacyMaskEnabled,
                    onCheckedChange = onSetNotificationPrivacyMaskEnabled,
                    testTag = "perm_switch_notif_mask"
                )
            }
        }

        item {
            ControlsSectionPanel(
                title = "Data privacy",
                icon = Icons.Filled.VerifiedUser,
                badgeText = if (controlsState.settings.cloudUploadConsentGranted) "Cloud consent on" else "Local private",
                badgeHighlighted = !controlsState.settings.cloudUploadConsentGranted,
                panelTestTag = "permissions_privacy_policy_card"
            ) {
                SwitchRow(
                    title = "Allow cloud API transmission",
                    subtitle = "When off, all prompts, notifications, screen snapshots, and memories stay on-device.",
                    checked = controlsState.settings.cloudUploadConsentGranted,
                    onCheckedChange = onSetCloudUploadConsentGranted,
                    testTag = "perm_switch_cloud_consent"
                )
                EditorialHairlineDivider()
                Text(
                    text = "External content from notifications, UI trees, and web snippets is isolated as untrusted input with spoofed tool markers stripped. Sensitive actions (calls, SMS, accessibility actions, reminders, skill saving) require explicit confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val palette = JarvisThemeTokens.palette
                HudBadge(
                    text = if (granted) "Granted" else "Missing",
                    color = if (granted) palette.onHue else MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = if (granted) {
                        palette.sageContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Text(actionLabel)
        }
    }
}
