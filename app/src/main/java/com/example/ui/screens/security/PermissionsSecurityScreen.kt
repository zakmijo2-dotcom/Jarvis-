package com.example.ui.screens.security

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.device.DeviceCapabilitySnapshot
import com.example.core.device.ShizukuAvailabilityState
import com.example.core.security.KeystoreHealthReport
import com.example.data.repository.JarvisSettings
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBadge
import com.example.ui.components.HudBackground
import com.example.ui.components.HudPanel
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens

@Composable
fun PermissionsSecurityScreen(
    capabilities: DeviceCapabilitySnapshot,
    settings: JarvisSettings,
    initialKeystoreReport: KeystoreHealthReport,
    onRecheckKeystore: () -> KeystoreHealthReport,
    onRefreshPermissions: () -> Unit,
    onSetNotificationReaderEnabled: (Boolean) -> Unit,
    onSetNotificationPrivacyMaskEnabled: (Boolean) -> Unit,
    onSetAccessibilityAutomationEnabled: (Boolean) -> Unit,
    onSetCloudUploadConsentGranted: (Boolean) -> Unit,
    onOpenDeviceBridgeSetup: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = JarvisThemeTokens.palette
    var keystoreReport by remember(initialKeystoreReport) { mutableStateOf(initialKeystoreReport) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("permissions_security_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp),
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
                                .testTag("permissions_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_navigate_back),
                                tint = palette.text
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.tealBadgeBg
                        ) {
                            Box(
                                modifier = Modifier.size(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = palette.sageAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.permissions_security_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = palette.text
                            )
                            Text(
                                text = "Least privilege · ${capabilities.privilegedBridgeSummary}",
                                style = JarvisMonoTelemetryStyle,
                                color = palette.skyAccent
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onRefreshPermissions()
                            keystoreReport = onRecheckKeystore()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("permissions_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh permission and Keystore status",
                            tint = palette.skyAccent
                        )
                    }
                }
            }

            // 1. Privileged Capability & Runtime Posture
            item {
                SecuritySectionCard(
                    title = "Runtime permissions",
                    icon = Icons.Filled.Mic,
                    badge = "Least privilege",
                    badgeHighlighted = true,
                    panelTestTag = "security_runtime_card"
                ) {
                    SecurityPermissionRow(
                        title = "Microphone (RECORD_AUDIO)",
                        description = "Speech recognition and push-to-talk / standby wake detection.",
                        granted = capabilities.hasAudioPermission,
                        actionLabel = "Grant mic",
                        onAction = { runtimeLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        testTag = "sec_grant_audio_btn"
                    )
                    EditorialHairlineDivider()
                    SecurityPermissionRow(
                        title = "Contacts (READ_CONTACTS)",
                        description = "Accessed only when searching contacts by explicit command.",
                        granted = capabilities.hasContactsPermission,
                        actionLabel = "Grant contacts",
                        onAction = { runtimeLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        testTag = "sec_grant_contacts_btn"
                    )
                    if (Build.VERSION.SDK_INT >= 33) {
                        EditorialHairlineDivider()
                        SecurityPermissionRow(
                            title = "Notifications (POST_NOTIFICATIONS)",
                            description = "Foreground service alerts for model downloads and active microphone.",
                            granted = capabilities.hasPostNotificationsPermission,
                            actionLabel = "Grant alerts",
                            onAction = { runtimeLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            testTag = "sec_grant_notif_btn"
                        )
                    }
                }
            }

            // 2. Privileged Bridge & Limited Accessibility Fallback
            item {
                val shizukuReady = capabilities.shizuku.availability == ShizukuAvailabilityState.READY
                SecuritySectionCard(
                    title = "Device control privileges",
                    icon = Icons.Filled.Cable,
                    badge = if (shizukuReady) "Shizuku Ready" else capabilities.activePrivilegedBackend.label,
                    badgeHighlighted = shizukuReady,
                    panelTestTag = "security_bridge_card"
                ) {
                    SecurityPermissionRow(
                        title = "Shizuku & Wireless ADB",
                        description = "Primary capability tier (${capabilities.shizuku.availability.label}; ADB: ${capabilities.wirelessAdb.connectionState.label}).",
                        granted = shizukuReady,
                        actionLabel = "Setup",
                        onAction = onOpenDeviceBridgeSetup,
                        testTag = "sec_open_bridge_setup_btn"
                    )
                    EditorialHairlineDivider()
                    SecurityPermissionRow(
                        title = "Accessibility Service (Limited fallback)",
                        description = "Retained strictly for screen UI tap/scroll/inspection when Shizuku/ADB cannot perform UI actions. Never scrapes passively.",
                        granted = capabilities.accessibilityAuthorized,
                        actionLabel = "OS Settings",
                        onAction = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        testTag = "sec_open_a11y_settings_btn"
                    )
                    SecuritySwitchRow(
                        title = "Allow accessibility fallback actions",
                        subtitle = "Service bound: ${capabilities.accessibilityConnected}",
                        checked = settings.accessibilityAutomationEnabled,
                        onCheckedChange = onSetAccessibilityAutomationEnabled,
                        testTag = "sec_switch_a11y"
                    )
                    EditorialHairlineDivider()
                    SecurityPermissionRow(
                        title = "Notification Listener",
                        description = "Reads recent notifications in RAM when toggled on; sensitive previews masked by default.",
                        granted = capabilities.notificationListenerAuthorized,
                        actionLabel = "OS Settings",
                        onAction = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        testTag = "sec_open_notif_settings_btn"
                    )
                    SecuritySwitchRow(
                        title = "Notification reader enabled",
                        subtitle = "Listener connected: ${capabilities.notificationListenerConnected}",
                        checked = settings.notificationReaderEnabled,
                        onCheckedChange = onSetNotificationReaderEnabled,
                        testTag = "sec_switch_notif_reader"
                    )
                    SecuritySwitchRow(
                        title = "Redact OTPs and message previews",
                        subtitle = "Masks sensitive verification tokens before AI context injection",
                        checked = settings.notificationPrivacyMaskEnabled,
                        onCheckedChange = onSetNotificationPrivacyMaskEnabled,
                        testTag = "sec_switch_notif_mask"
                    )
                }
            }

            // 3. Keystore Health & Security Guardrails
            item {
                val keystoreHealthy = keystoreReport.keystoreAvailable &&
                    keystoreReport.keyProvisioned &&
                    keystoreReport.lastError == null
                SecuritySectionCard(
                    title = "Credential keystore & privacy",
                    icon = Icons.Filled.Lock,
                    badge = if (keystoreHealthy) "Keystore OK" else "Attention",
                    badgeHighlighted = keystoreHealthy,
                    panelTestTag = "keystore_health_card"
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
                            Text(
                                text = "Android Keystore AES-256-GCM",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.text
                            )
                            Text(
                                text = keystoreReport.lastError ?: when {
                                    keystoreReport.keystoreAvailable && keystoreReport.keyProvisioned ->
                                        "Hardware-backed AndroidKeyStore key provisioned and verified."
                                    keystoreReport.keystoreAvailable ->
                                        "AndroidKeyStore detected; key alias will be provisioned on first secret save."
                                    else ->
                                        "AndroidKeyStore unavailable in current runtime environment."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (keystoreHealthy) palette.textSecondary else palette.errorAccent
                            )
                        }
                        OutlinedButton(
                            onClick = { keystoreReport = onRecheckKeystore() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("keystore_recheck_button")
                        ) {
                            Text("Verify")
                        }
                    }

                    EditorialHairlineDivider()

                    SecuritySwitchRow(
                        title = "Allow optional cloud AI requests",
                        subtitle = if (settings.cloudUploadConsentGranted) {
                            "Enabled for hybrid/cloud models. Sensitive local secrets remain encrypted."
                        } else {
                            "Off — all prompts, transcripts, and device data stay strictly local."
                        },
                        checked = settings.cloudUploadConsentGranted,
                        onCheckedChange = onSetCloudUploadConsentGranted,
                        testTag = "sec_switch_cloud_consent"
                    )

                    EditorialHairlineDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Active reliability & safety controls",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text
                        )
                        Text(
                            text = "• Allowlisted structured actions block raw shell metacharacters.\n• High-risk tools (force-stop, calls, SMS, UI automation) require explicit confirmation.\n• Bearer tokens, API keys, and Wireless ADB pairing codes are redacted from logs.\n• Cross-host HTTP redirects strip Hugging Face Authorization headers.",
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
private fun SecuritySectionCard(
    title: String,
    icon: ImageVector,
    badge: String,
    badgeHighlighted: Boolean,
    panelTestTag: String,
    content: @Composable () -> Unit
) {
    val palette = JarvisThemeTokens.palette
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
            .testTag(panelTestTag)
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.sageAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text
                )
            }
            HudBadge(
                text = badge,
                color = if (badgeHighlighted) palette.onHue else palette.textSecondary,
                containerColor = if (badgeHighlighted) palette.sageContainer else palette.surfaceHigh
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun SecurityPermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    val palette = JarvisThemeTokens.palette
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
                    color = palette.text
                )
                HudBadge(
                    text = if (granted) "Granted" else "Missing",
                    color = if (granted) palette.onHue else palette.textSecondary,
                    containerColor = if (granted) palette.sageContainer else palette.surfaceHigh
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
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

@Composable
private fun SecuritySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = JarvisThemeTokens.palette
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
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
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

@Preview(name = "Security Light", showBackground = true)
@Preview(name = "Security Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionsSecurityPreview() {
    JarvisTheme {
        PermissionsSecurityScreen(
            capabilities = DeviceCapabilitySnapshot(hasAudioPermission = true),
            settings = JarvisSettings(),
            initialKeystoreReport = KeystoreHealthReport(keystoreAvailable = true, keyProvisioned = true, lastError = null),
            onRecheckKeystore = { KeystoreHealthReport(keystoreAvailable = true, keyProvisioned = true, lastError = null) },
            onRefreshPermissions = {},
            onSetNotificationReaderEnabled = {},
            onSetNotificationPrivacyMaskEnabled = {},
            onSetAccessibilityAutomationEnabled = {},
            onSetCloudUploadConsentGranted = {},
            onOpenDeviceBridgeSetup = {},
            onNavigateBack = {}
        )
    }
}
