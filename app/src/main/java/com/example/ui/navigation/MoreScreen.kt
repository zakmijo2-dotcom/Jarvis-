package com.example.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.EditorialHairlineDivider
import com.example.ui.components.HudBackground
import com.example.ui.components.hairlineDp
import com.example.ui.theme.JarvisMonoTelemetryStyle
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.JarvisThemeTokens

@Composable
fun MoreScreen(
    activeModelSummary: String,
    routingSummary: String,
    shizukuSummary: String = "Unavailable",
    wirelessAdbSummary: String = "Disconnected",
    micSummary: String,
    startupDurationMs: Long? = null,
    onOpenVoice: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenBridgeSetup: () -> Unit = {},
    onOpenPermissionsSecurity: () -> Unit = {},
    onOpenActivityHistory: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val palette = JarvisThemeTokens.palette

    HudBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag("more_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.more_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = palette.surface,
                    border = BorderStroke(hairlineDp(), palette.hairline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MoreNavigationRow(
                            title = stringResource(R.string.nav_voice),
                            subtitle = stringResource(R.string.more_voice_subtitle),
                            icon = Icons.Outlined.GraphicEq,
                            iconBgColor = palette.cyanBadgeBg,
                            iconTint = palette.peachAccent,
                            onClick = onOpenVoice,
                            modifier = Modifier.testTag("nav_tab_voice")
                        )
                        EditorialHairlineDivider()
                        MoreNavigationRow(
                            title = stringResource(R.string.nav_device),
                            subtitle = stringResource(R.string.more_device_subtitle),
                            icon = Icons.Outlined.MonitorHeart,
                            iconBgColor = palette.amberBadgeBg,
                            iconTint = palette.sandAccent,
                            onClick = onOpenDevice,
                            modifier = Modifier.testTag("nav_tab_device")
                        )
                        EditorialHairlineDivider()
                        MoreNavigationRow(
                            title = stringResource(R.string.nav_settings),
                            subtitle = stringResource(R.string.more_settings_subtitle),
                            icon = Icons.Outlined.Settings,
                            iconBgColor = palette.indigoBadgeBg,
                            iconTint = palette.lavenderAccent,
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = palette.surface,
                    border = BorderStroke(hairlineDp(), palette.hairline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MoreNavigationRow(
                            title = stringResource(R.string.nav_device_bridge),
                            subtitle = stringResource(R.string.more_bridge_subtitle),
                            icon = Icons.Outlined.Cable,
                            iconBgColor = palette.cyanBadgeBg,
                            iconTint = palette.skyAccent,
                            onClick = onOpenBridgeSetup,
                            modifier = Modifier.testTag("nav_tab_bridge_setup")
                        )
                        EditorialHairlineDivider()
                        MoreNavigationRow(
                            title = stringResource(R.string.nav_permissions_security),
                            subtitle = stringResource(R.string.more_security_subtitle),
                            icon = Icons.Outlined.Shield,
                            iconBgColor = palette.tealBadgeBg,
                            iconTint = palette.sageAccent,
                            onClick = onOpenPermissionsSecurity,
                            modifier = Modifier.testTag("nav_tab_permissions_security")
                        )
                        EditorialHairlineDivider()
                        MoreNavigationRow(
                            title = stringResource(R.string.nav_activity_history),
                            subtitle = stringResource(R.string.more_history_subtitle),
                            icon = Icons.Outlined.History,
                            iconBgColor = palette.indigoBadgeBg,
                            iconTint = palette.lavenderAccent,
                            onClick = onOpenActivityHistory,
                            modifier = Modifier.testTag("nav_tab_activity_history")
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.more_overview_header),
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = palette.surface,
                        border = BorderStroke(hairlineDp(), palette.hairline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MoreStatusRow(label = "Model", value = activeModelSummary)
                            EditorialHairlineDivider()
                            MoreStatusRow(label = "Routing", value = routingSummary)
                            EditorialHairlineDivider()
                            MoreStatusRow(label = "Shizuku", value = shizukuSummary)
                            EditorialHairlineDivider()
                            MoreStatusRow(label = "Wireless ADB", value = wirelessAdbSummary)
                            EditorialHairlineDivider()
                            MoreStatusRow(label = "Microphone", value = micSummary)
                            if (startupDurationMs != null) {
                                EditorialHairlineDivider()
                                MoreStatusRow(label = "Startup latency", value = "${startupDurationMs} ms")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = JarvisThemeTokens.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconBgColor
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.text
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.textSecondary
        )
    }
}

@Composable
private fun MoreStatusRow(
    label: String,
    value: String
) {
    val palette = JarvisThemeTokens.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )
        Text(
            text = value,
            style = JarvisMonoTelemetryStyle,
            color = palette.text
        )
    }
}

@Preview(name = "More Light", showBackground = true)
@Preview(name = "More Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoreScreenPreview() {
    JarvisTheme {
        MoreScreen(
            activeModelSummary = "Gemma 3 1B · Unloaded",
            routingSummary = "Auto Hybrid",
            shizukuSummary = "Shizuku Ready",
            wirelessAdbSummary = "Not Configured",
            micSummary = "Standby off",
            startupDurationMs = 310L,
            onOpenVoice = {},
            onOpenDevice = {},
            onOpenBridgeSetup = {},
            onOpenPermissionsSecurity = {},
            onOpenActivityHistory = {},
            onOpenSettings = {}
        )
    }
}
